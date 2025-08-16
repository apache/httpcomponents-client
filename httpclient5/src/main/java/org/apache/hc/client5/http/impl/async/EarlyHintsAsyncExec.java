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

import org.apache.hc.client5.http.EarlyHintsListener;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

/**
 * Execution chain handler that delivers {@code 103 Early Hints}
 * informational responses to a user-provided
 * {@link org.apache.hc.client5.http.EarlyHintsListener}
 * without affecting processing of the final (non-1xx) response.
 *
 * <p>This handler forwards each {@code 103} informational response to the
 * listener. All other responses (including the final response) are delegated
 * unchanged.</p>
 *
 * <p>For security and interoperability, applications typically act only on
 * headers considered safe in Early Hints (for example, {@code Link} with
 * {@code rel=preload} or {@code rel=preconnect}).</p>
 *
 * @see org.apache.hc.client5.http.EarlyHintsListener
 * @see org.apache.hc.core5.http.HttpStatus#SC_EARLY_HINTS
 * @see org.apache.hc.core5.http.nio.ResponseChannel#sendInformation(org.apache.hc.core5.http.HttpResponse, org.apache.hc.core5.http.protocol.HttpContext)
 * @since 5.6
 */

public final class EarlyHintsAsyncExec implements AsyncExecChainHandler {
    private final EarlyHintsListener listener;

    public EarlyHintsAsyncExec(final EarlyHintsListener listener) {
        this.listener = listener;
    }

    @Override
    public void execute(final HttpRequest request,
                        final AsyncEntityProducer entityProducer,
                        final AsyncExecChain.Scope scope,
                        final AsyncExecChain chain,
                        final AsyncExecCallback callback) throws HttpException, IOException {

        if (listener == null) {
            chain.proceed(request, entityProducer, scope, callback);
            return;
        }

        chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {
            @Override
            public void handleInformationResponse(final HttpResponse response)
                    throws HttpException, java.io.IOException {
                if (response.getCode() == HttpStatus.SC_EARLY_HINTS) {
                    listener.onEarlyHints(response, scope.clientContext);
                }
                callback.handleInformationResponse(response);
            }

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response, final EntityDetails entityDetails)
                    throws HttpException, java.io.IOException {
                return callback.handleResponse(response, entityDetails);
            }

            @Override
            public void completed() {
                callback.completed();
            }

            @Override
            public void failed(final Exception cause) {
                callback.failed(cause);
            }
        });
    }
}
