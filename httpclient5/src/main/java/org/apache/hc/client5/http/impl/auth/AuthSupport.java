/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.impl.auth;

import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;

/**
 * Shared target authentication exchange priming logic used by {@link org.apache.hc.client5.http.impl.classic.ProtocolExec},
 * {@link org.apache.hc.client5.http.impl.async.AsyncProtocolExec}, and the caching execution handlers, which need to
 * know what the former two would do to a request before it reaches them, without actually sending one.
 *
 * @since 5.7
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class AuthSupport {

    private AuthSupport() {
    }

    /**
     * Resets the given target {@link AuthExchange} when the path prefix of a previous exchange no longer
     * applies to the current request, then attempts to preemptively populate it from the auth cache. This
     * is the exact step {@code ProtocolExec} and {@code AsyncProtocolExec} perform before entering their
     * 401/407 challenge-response loop; it is factored out here so it can also be used to determine, without
     * sending a request, what those classes would do.
     */
    public static void primeAuthExchange(
            final HttpHost target,
            final Supplier<String> pathPrefixSupplier,
            final AuthExchange targetAuthExchange,
            final AuthCacheKeeper authCacheKeeper,
            final HttpClientContext context) {
        if (!targetAuthExchange.isConnectionBased() &&
                targetAuthExchange.getPathPrefix() != null &&
                !pathPrefixSupplier.get().startsWith(targetAuthExchange.getPathPrefix())) {
            targetAuthExchange.reset();
        }
        if (authCacheKeeper != null && context.getAuthCache() != null) {
            authCacheKeeper.loadPreemptivelyFromCache(target, pathPrefixSupplier, targetAuthExchange, context);
        }
    }

    /**
     * Determines whether generating an auth response for the given target auth exchange can be previewed
     * speculatively, that is, without a request actually being sent to the origin. Generating a response
     * is not always free of side effects: Digest advances a nonce count that the server expects to see
     * used exactly once, so invoking it on a lookup that turns out to be a cache hit (no request is ever
     * sent) would desynchronize the client from the server. Only schemes annotated {@link AuthStateCacheable}
     * are known, by construction, to have no such effect: that annotation is also what allows them to be
     * reused preemptively from the auth cache across unrelated requests in the first place.
     * <p>
     * An exchange left mid-challenge, with a queue of pending auth options and no scheme selected yet, is
     * never safe to preview, regardless of what is or is not currently selected: {@code addAuthResponse}
     * pops and selects the next pending option from that queue and generates a response for it as a side
     * effect of the mere preview, which can advance that scheme's internal state exactly as if a request
     * had actually been sent.
     */
    public static boolean isPreviewSafe(final AuthExchange authExchange) {
        if (authExchange.getAuthOptions() != null) {
            return false;
        }
        final AuthScheme authScheme = authExchange.getAuthScheme();
        return authScheme != null && authScheme.getClass().isAnnotationPresent(AuthStateCacheable.class);
    }

}
