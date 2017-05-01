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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.util.CredentialSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.URIAuthority;

class AsyncProtocolExec implements AsyncExecChainHandler {

    private final HttpProcessor httpProcessor;

    AsyncProtocolExec(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        final HttpRoute route = scope.route;
        final HttpClientContext clientContext = scope.clientContext;
        final URIAuthority authority = request.getAuthority();
        if (authority != null) {
            final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
            if (credsProvider instanceof CredentialsStore) {
                CredentialSupport.extractFromAuthority(authority, (CredentialsStore) credsProvider);
            }
        }

        clientContext.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        clientContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        httpProcessor.process(request, entityProducer, clientContext);

        chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                clientContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                httpProcessor.process(response, entityDetails, clientContext);

                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void completed() {
                asyncExecCallback.completed();
            }

            @Override
            public void failed(final Exception cause) {
                asyncExecCallback.failed(cause);
            }

        });
    }

}
