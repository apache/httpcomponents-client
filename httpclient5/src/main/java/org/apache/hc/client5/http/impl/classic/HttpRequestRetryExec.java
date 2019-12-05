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
package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request executor in the request execution chain that is responsible for
 * making a decision whether a request that failed due to an I/O exception
 * or received a specific response from the target server should
 * be re-executed.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public class HttpRequestRetryExec implements ExecChainHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final HttpRequestRetryStrategy retryStrategy;

    public HttpRequestRetryExec(
            final HttpRequestRetryStrategy retryStrategy) {
         Args.notNull(retryStrategy, "retryStrategy");
         this.retryStrategy = retryStrategy;
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "request");
        Args.notNull(scope, "scope");
        final String exchangeId = scope.exchangeId;
        final HttpRoute route = scope.route;
        final HttpClientContext context = scope.clientContext;
        ClassicHttpRequest currentRequest = request;

        for (int execCount = 1;; execCount++) {
            final ClassicHttpResponse response;
            try {
                 response = chain.proceed(currentRequest, scope);
            } catch (final IOException ex) {
                if (scope.execRuntime.isExecutionAborted()) {
                    throw new RequestFailedException("Request aborted");
                }
                final HttpEntity requestEntity = request.getEntity();
                if (requestEntity != null && !requestEntity.isRepeatable()) {
                    if (log.isDebugEnabled()) {
                        log.debug("{}: cannot retry non-repeatable request", exchangeId);
                    }
                    throw ex;
                }
                if (retryStrategy.retryRequest(request, ex, execCount, context)) {
                    if (log.isDebugEnabled()) {
                        log.debug("{}: {}", exchangeId, ex.getMessage(), ex);
                    }
                    if (log.isInfoEnabled()) {
                        log.info("Recoverable I/O exception ({}) caught when processing request to {}",
                                ex.getClass().getName(), route);
                    }
                    currentRequest = ClassicRequestCopier.INSTANCE.copy(scope.originalRequest);
                    continue;
                } else {
                    if (ex instanceof NoHttpResponseException) {
                        final NoHttpResponseException updatedex = new NoHttpResponseException(
                                route.getTargetHost().toHostString() + " failed to respond");
                        updatedex.setStackTrace(ex.getStackTrace());
                        throw updatedex;
                    }
                    throw ex;
                }
            }

            try {
                final HttpEntity entity = request.getEntity();
                if (entity != null && !entity.isRepeatable()) {
                    if (log.isDebugEnabled()) {
                        log.debug("{}: cannot retry non-repeatable request", exchangeId);
                    }
                    return response;
                }
                if (retryStrategy.retryRequest(response, execCount, context)) {
                    response.close();
                    final TimeValue nextInterval =
                            retryStrategy.getRetryInterval(response, execCount, context);
                    if (TimeValue.isPositive(nextInterval)) {
                        try {
                            if (log.isDebugEnabled()) {
                                log.debug("{}: wait for {}", exchangeId, nextInterval);
                            }
                            nextInterval.sleep();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException();
                        }
                    }
                    currentRequest = ClassicRequestCopier.INSTANCE.copy(scope.originalRequest);
                } else {
                    return response;
                }
            } catch (final RuntimeException ex) {
                response.close();
                throw ex;
            }
        }
    }

}
