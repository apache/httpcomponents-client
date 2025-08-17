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
package org.apache.hc.client5.http.impl;

import java.io.IOException;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * Classic exec-chain interceptor that re-executes the request exactly once on
 * {@code 425 Too Early} (and optionally on {@code 429}/{@code 503}) for
 * retry-eligible requests with repeatable entities.
 *
 * <p>
 * RFC 8470 semantics: {@code 425} retry is limited to {@link Method#isSafe()} requests.
 * Optional {@code 429}/{@code 503} retry remains {@link Method#isIdempotent()}.
 * </p>
 *
 * @since 5.7
 */
public final class TooEarlyStatusRetryExec implements ExecChainHandler {

    private static final String RETRIED_ATTR = "http.client.too_early.retried";

    private final boolean include429and503;

    public TooEarlyStatusRetryExec(final boolean include429and503) {
        this.include429and503 = include429and503;
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {

        final ClassicHttpResponse response = chain.proceed(request, scope);

        final int code = response.getCode();
        final boolean eligible =
                code == HttpStatus.SC_TOO_EARLY ||
                        include429and503 && (code == HttpStatus.SC_TOO_MANY_REQUESTS || code == HttpStatus.SC_SERVICE_UNAVAILABLE);

        if (!eligible) {
            return response;
        }

        final boolean alreadyRetried = Boolean.TRUE.equals(scope.clientContext.getAttribute(RETRIED_ATTR));
        if (alreadyRetried) {
            return response;
        }

        final Method method = Method.normalizedValueOf(request.getMethod());
        final boolean methodOk = code == HttpStatus.SC_TOO_EARLY ? method.isSafe() : method.isIdempotent();

        final HttpEntity reqEntity = request.getEntity();
        final boolean repeatable = reqEntity == null || reqEntity.isRepeatable();

        if (!methodOk || !repeatable) {
            return response;
        }

        // RFC 8470: tell TLS/transport to avoid early data on the retry
        if (code == HttpStatus.SC_TOO_EARLY) {
            scope.clientContext.setAttribute(TooEarlyRetryStrategy.DISABLE_EARLY_DATA_ATTR, Boolean.TRUE);
        }
        scope.clientContext.setAttribute(RETRIED_ATTR, Boolean.TRUE);

        // Drain & close first response (ignore errors – we discard it anyway)
        try {
            final HttpEntity respEntity = response.getEntity();
            if (respEntity != null) {
                EntityUtils.consume(respEntity);
            }
        } catch (final Exception ignore) {
        }
        try {
            response.close();
        } catch (final Exception ignore) {
        }

        // The first exchange may have released the endpoint; reacquire before retrying.
        try {
            scope.execRuntime.discardEndpoint(); // safe even if none is held
        } catch (final Exception ignore) {
        }

        // 5.6 signature: (String id, HttpRoute route, Object state, HttpClientContext ctx)
        scope.execRuntime.acquireEndpoint(null, scope.route, null, scope.clientContext);

        // Retry once
        return chain.proceed(request, scope);
    }
}
