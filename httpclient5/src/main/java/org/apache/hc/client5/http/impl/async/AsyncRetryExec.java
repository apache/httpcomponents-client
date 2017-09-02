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

import org.apache.hc.client5.http.HttpRequestRetryHandler;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class AsyncRetryExec implements AsyncExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final HttpRequestRetryHandler retryHandler;

    public AsyncRetryExec(final HttpRequestRetryHandler retryHandler) {
        Args.notNull(retryHandler, "HTTP request retry handler");
        this.retryHandler = retryHandler;
    }

    private void internalExecute(
            final int execCount,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

        chain.proceed(ExecSupport.copy(request), entityProducer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void completed() {
                asyncExecCallback.completed();
            }

            @Override
            public void failed(final Exception cause) {
                if (cause instanceof IOException) {
                    final HttpRoute route = scope.route;
                    final HttpClientContext clientContext = scope.clientContext;
                    if (retryHandler.retryRequest(request, (IOException) cause, execCount, clientContext)) {
                        if (log.isInfoEnabled()) {
                            log.info("I/O exception ("+ cause.getClass().getName() +
                                    ") caught when processing request to "
                                    + route +
                                    ": "
                                    + cause.getMessage());
                        }
                        if (log.isDebugEnabled()) {
                            log.debug(cause.getMessage(), cause);
                        }
                        if (log.isInfoEnabled()) {
                            log.info("Retrying request to " + route);
                        }
                        try {
                            scope.execRuntime.discardConnection();
                            internalExecute(execCount + 1, request, entityProducer, scope, chain, asyncExecCallback);
                            return;
                        } catch (IOException | HttpException ex) {
                            log.error(ex.getMessage(), ex);
                        }
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
        internalExecute(1, request, entityProducer, scope, chain, asyncExecCallback);
    }

}
