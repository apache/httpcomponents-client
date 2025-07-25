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
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
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
 * <p>
 * If this handler is active, pay particular attention to the placement
 * of other handlers within the handler chain relative to the retry handler.
 * Use {@link ChainElement#RETRY} as name when referring to this handler.
 * </p>
 * <p>
 * If a custom handler is placed <b>before</b> the retry handler, the handler will
 * see the initial request and the final outcome after the last retry. Elapsed time
 * will account for any delays imposed by the retry handler.
 * </p>
 *
 * <p>
 * A custom handler which is placed <b>after</b> the retry handler will be invoked for
 * each individual retry. Elapsed time will measure each individual http request,
 * without the delay imposed by the retry handler.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public class HttpRequestRetryExec implements ExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestRetryExec.class);

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
        final HttpHost target = route.getTargetHost();
        final HttpClientContext context = scope.clientContext;
        ClassicHttpRequest currentRequest = request;

        for (int execCount = 1;; execCount++) {
            try {
                final ClassicHttpResponse response = chain.proceed(currentRequest, scope);
                try {
                    final HttpEntity entity = request.getEntity();
                    if (entity != null && !entity.isRepeatable()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} cannot retry non-repeatable request", exchangeId);
                        }
                        return response;
                    }
                    if (retryStrategy.retryRequest(response, execCount, context)) {
                        response.close();
                        final TimeValue delay = retryStrategy.getRetryInterval(response, execCount, context);
                        if (LOG.isInfoEnabled()) {
                            LOG.info("{} {} responded with status {}; " +
                                            "request will be automatically re-executed in {} (exec count {})",
                                    exchangeId, target, response.getCode(), delay, execCount + 1);
                        }
                        pause(delay);
                        currentRequest = ClassicRequestBuilder.copy(scope.originalRequest).build();
                    } else {
                        return response;
                    }
                } catch (final RuntimeException ex) {
                    response.close();
                    throw ex;
                }
            } catch (final IOException ex) {
                if (scope.execRuntime.isExecutionAborted()) {
                    throw new RequestFailedException("Request aborted");
                }
                final HttpEntity requestEntity = request.getEntity();
                if (requestEntity != null && !requestEntity.isRepeatable()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} cannot retry non-repeatable request", exchangeId);
                    }
                    throw ex;
                }
                if (retryStrategy.retryRequest(request, ex, execCount, context)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} {}", exchangeId, ex.getMessage(), ex);
                    }
                    final TimeValue delay = retryStrategy.getRetryInterval(request, ex, execCount, context);
                    if (LOG.isInfoEnabled()) {
                        LOG.info("{} recoverable I/O exception ({}) caught when sending request to {};" +
                                        "request will be automatically re-executed in {} (exec count {})",
                                exchangeId, ex.getClass().getName(), target, delay, execCount + 1);
                    }
                    pause(delay);
                    currentRequest = ClassicRequestBuilder.copy(scope.originalRequest).build();
                    continue;
                }
                if (ex instanceof NoHttpResponseException) {
                    final NoHttpResponseException updatedex = new NoHttpResponseException(
                            target.toHostString() + " failed to respond");
                    updatedex.setStackTrace(ex.getStackTrace());
                    throw updatedex;
                }
                throw ex;
            }
        }
    }

    private static void pause(final TimeValue delay) throws InterruptedIOException {
        if (TimeValue.isPositive(delay)) {
            try {
                delay.sleep();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
    }

}
