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
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.impl.TooEarlyRetryStrategy;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * Async exec-chain interceptor that re-executes the request exactly once on
 * {@code 425 Too Early} (and optionally on {@code 429}/{@code 503})
 * for idempotent requests with repeatable producers.
 *
 * @since 5.7
 */
public final class TooEarlyStatusRetryAsyncExec implements AsyncExecChainHandler {

    private static final String RETRIED_ATTR = "http.client.too_early.retried";

    private final boolean include429and503;

    public TooEarlyStatusRetryAsyncExec(final boolean include429and503) {
        this.include429and503 = include429and503;
    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback callback) throws HttpException, IOException {

        chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {

            /** True once we have decided to retry and scheduled the second proceed(). */
            private volatile boolean retryScheduled;

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                final int code = response.getCode();
                final boolean eligible = code == HttpStatus.SC_TOO_EARLY ||
                        include429and503 && (code == HttpStatus.SC_TOO_MANY_REQUESTS
                                || code == HttpStatus.SC_SERVICE_UNAVAILABLE);

                final boolean alreadyRetried =
                        Boolean.TRUE.equals(scope.clientContext.getAttribute(RETRIED_ATTR));
                final boolean idempotent = Method.normalizedValueOf(request.getMethod()).isIdempotent();
                final boolean repeatable = entityProducer == null || entityProducer.isRepeatable();

                if (eligible && !alreadyRetried && idempotent && repeatable) {
                    scope.clientContext.setAttribute(RETRIED_ATTR, Boolean.TRUE);
                    if (code == HttpStatus.SC_TOO_EARLY) {
                        scope.clientContext.setAttribute(
                                TooEarlyRetryStrategy.DISABLE_EARLY_DATA_ATTR, Boolean.TRUE);
                    }

                    // If there is no response body, retry immediately.
                    if (entityDetails == null) {
                        retryScheduled = true;
                        chain.proceed(request, entityProducer, scope, callback);
                        // No body expected; return a no-op consumer just in case.
                        return new AsyncDataConsumer() {
                            @Override
                            public void updateCapacity(final CapacityChannel c) throws IOException {
                            }

                            @Override
                            public void consume(final ByteBuffer src) throws IOException {
                                src.position(src.limit());
                            }

                            @Override
                            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                            }


                            @Override
                            public void releaseResources() {
                            }
                        };
                    }

                    // Otherwise, discard the body and retry on end-of-stream.
                    return new AsyncDataConsumer() {
                        @Override
                        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                            capacityChannel.update(Integer.MAX_VALUE);
                        }

                        @Override
                        public void consume(final ByteBuffer src) throws IOException {
                            src.position(src.limit());
                        }

                        @Override
                        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                            retryScheduled = true;
                            chain.proceed(request, entityProducer, scope, callback);
                        }


                        @Override
                        public void releaseResources() { /* no-op */ }
                    };
                }

                // Not retrying: delegate to the original callback.
                return callback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                // pass through
                callback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                // If the first exchange triggered a retry, ignore its completion:
                if (!retryScheduled) {
                    callback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                // If we already scheduled a retry, ignore failure from the first exchange.
                if (!retryScheduled) {
                    callback.failed(cause);
                }
            }
        });
    }
}
