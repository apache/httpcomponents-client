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
package org.apache.hc.client5.http.impl.async;

import java.io.IOException;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.NoopEntityConsumer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request executor in the asynchronous request execution chain that is
 * responsible for making a decision whether a request that failed due to
 * an I/O exception or received a specific response from the target server should
 * be re-executed. Note that this exec chain handler <em>will not</em> respect
 * {@link HttpRequestRetryStrategy#getRetryInterval(HttpResponse, int, org.apache.hc.core5.http.protocol.HttpContext)}.
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
public final class AsyncHttpRequestRetryExec implements AsyncExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHttpRequestRetryExec.class);

    private final HttpRequestRetryStrategy retryStrategy;

    public AsyncHttpRequestRetryExec(final HttpRequestRetryStrategy retryStrategy) {
        Args.notNull(retryStrategy, "retryStrategy");
        this.retryStrategy = retryStrategy;
    }

    private static class State {

        volatile boolean retrying;
        volatile TimeValue delay;

    }

    private void internalExecute(
            final State state,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

        final String exchangeId = scope.exchangeId;

        chain.proceed(BasicRequestBuilder.copy(request).build(), entityProducer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                final HttpClientContext clientContext = scope.clientContext;
                if (entityProducer != null && !entityProducer.isRepeatable()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} cannot retry non-repeatable request", exchangeId);
                    }
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }
                state.retrying = retryStrategy.retryRequest(response, scope.execCount.get(), clientContext);
                if (state.retrying) {
                    state.delay = retryStrategy.getRetryInterval(response, scope.execCount.get(), clientContext);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} retrying request in {}", exchangeId, state.delay);
                    }
                    return new NoopEntityConsumer();
                } else {
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                if (state.retrying) {
                    scope.execCount.incrementAndGet();
                    scope.scheduler.scheduleExecution(request, entityProducer, scope, asyncExecCallback, state.delay);
                } else {
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                if (cause instanceof IOException) {
                    final HttpRoute route = scope.route;
                    final HttpClientContext clientContext = scope.clientContext;
                    if (entityProducer != null && !entityProducer.isRepeatable()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} cannot retry non-repeatable request", exchangeId);
                        }
                    } else if (retryStrategy.retryRequest(request, (IOException) cause, scope.execCount.get(), clientContext)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} {}", exchangeId, cause.getMessage(), cause);
                        }
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Recoverable I/O exception ({}) caught when processing request to {}",
                                    cause.getClass().getName(), route);
                        }
                        scope.execRuntime.discardEndpoint();
                        if (entityProducer != null) {
                            entityProducer.releaseResources();
                        }
                        state.retrying = true;
                        scope.execCount.incrementAndGet();
                        scope.scheduler.scheduleExecution(request, entityProducer, scope, asyncExecCallback, state.delay);
                        return;
                    }
                }
                asyncExecCallback.failed(cause);
            }

        });

    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        final State state = new State();
        state.retrying = false;
        internalExecute(state, request, entityProducer, scope, chain, asyncExecCallback);
    }

}
