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

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that implements commons aspects of the client side authentication cache keeping.
 *
 * @since 5.2
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class AuthCacheKeeper {

    private static final Logger LOG = LoggerFactory.getLogger(AuthCacheKeeper.class);

    private final SchemePortResolver schemePortResolver;

    public AuthCacheKeeper(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
    }

    public void updateOnChallenge(final HttpHost host,
                                  final String pathPrefix,
                                  final AuthExchange authExchange,
                                  final HttpContext context) {
        clearCache(host, pathPrefix, HttpClientContext.adapt(context));
    }

    public void updateOnNoChallenge(final HttpHost host,
                                    final String pathPrefix,
                                    final AuthExchange authExchange,
                                    final HttpContext context) {
        if (authExchange.getState() == AuthExchange.State.SUCCESS) {
            updateCache(host, pathPrefix, authExchange.getAuthScheme(), HttpClientContext.adapt(context));
        }
    }

    public void updateOnResponse(final HttpHost host,
                                 final String pathPrefix,
                                 final AuthExchange authExchange,
                                 final HttpContext context) {
        if (authExchange.getState() == AuthExchange.State.FAILURE) {
            clearCache(host, pathPrefix, HttpClientContext.adapt(context));
        }
    }

    public void loadPreemptively(final HttpHost host,
                                 final String pathPrefix,
                                 final AuthExchange authExchange,
                                 final HttpContext context) {
        if (authExchange.getState() == AuthExchange.State.UNCHALLENGED) {
            AuthScheme authScheme = loadFromCache(host, pathPrefix, HttpClientContext.adapt(context));
            if (authScheme == null && pathPrefix != null) {
                authScheme = loadFromCache(host, null, HttpClientContext.adapt(context));
            }
            if (authScheme != null) {
                authExchange.select(authScheme);
            }
        }
    }

    private AuthScheme loadFromCache(final HttpHost host,
                                     final String pathPrefix,
                                     final HttpClientContext clientContext) {
        final AuthCache authCache = clientContext.getAuthCache();
        if (authCache != null) {
            final AuthScheme authScheme = authCache.get(host, pathPrefix);
            if (authScheme != null) {
                if (LOG.isDebugEnabled()) {
                    final String exchangeId = clientContext.getExchangeId();
                    LOG.debug("{} Re-using cached '{}' auth scheme for {}{}", exchangeId, authScheme.getName(), host,
                            pathPrefix != null ? pathPrefix : "");
                }
                return authScheme;
            }
        }
        return null;
    }

    private void updateCache(final HttpHost host,
                             final String pathPrefix,
                             final AuthScheme authScheme,
                             final HttpClientContext clientContext) {
        final boolean cacheable = authScheme.getClass().getAnnotation(AuthStateCacheable.class) != null;
        if (cacheable) {
            AuthCache authCache = clientContext.getAuthCache();
            if (authCache == null) {
                authCache = new BasicAuthCache(schemePortResolver);
                clientContext.setAuthCache(authCache);
            }
            if (LOG.isDebugEnabled()) {
                final String exchangeId = clientContext.getExchangeId();
                LOG.debug("{} Caching '{}' auth scheme for {}{}", exchangeId, authScheme.getName(), host,
                        pathPrefix != null ? pathPrefix : "");
            }
            authCache.put(host, pathPrefix, authScheme);
        }
    }

    private void clearCache(final HttpHost host,
                            final String pathPrefix,
                            final HttpClientContext clientContext) {
        final AuthCache authCache = clientContext.getAuthCache();
        if (authCache != null) {
            if (LOG.isDebugEnabled()) {
                final String exchangeId = clientContext.getExchangeId();
                LOG.debug("{} Clearing cached auth scheme for {}{}", exchangeId, host,
                        pathPrefix != null ? pathPrefix : "");
            }
            authCache.remove(host, pathPrefix);
        }
    }

}
