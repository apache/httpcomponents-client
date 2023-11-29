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
package org.apache.hc.client5.testing.async;

import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public abstract class TestHttp1ClientAuthentication extends AbstractHttpAsyncClientAuthenticationTest<CloseableHttpAsyncClient> {

    public TestHttp1ClientAuthentication(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected H2TestServer startServer(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        return startServer(Http1Config.DEFAULT, null, exchangeHandlerDecorator);
    }

    @Override
    protected CloseableHttpAsyncClient startClientCustom(final Consumer<TestClientBuilder> clientCustomizer) throws Exception {

        return startClient(new Consumer<HttpAsyncClientBuilder>() {

            @Override
            public void accept(final HttpAsyncClientBuilder builder) {

                clientCustomizer.accept(new TestClientBuilder() {

                    @Override
                    public TestClientBuilder setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
                        builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
                        return this;
                    }

                    @Override
                    public TestClientBuilder setTargetAuthenticationStrategy(final AuthenticationStrategy targetAuthStrategy) {
                        builder.setTargetAuthenticationStrategy(targetAuthStrategy);
                        return this;
                    }

                    @Override
                    public TestClientBuilder addResponseInterceptor(final HttpResponseInterceptor responseInterceptor) {
                        builder.addResponseInterceptorLast(responseInterceptor);
                        return this;
                    }

                    @Override
                    public TestClientBuilder addRequestInterceptor(final HttpRequestInterceptor requestInterceptor) {
                        builder.addRequestInterceptorFirst(requestInterceptor);
                        return this;
                    }

                });

            }

        });
    }

    @Test
    public void testBasicAuthenticationSuccessNonPersistentConnection() throws Exception {
        final H2TestServer server = startServer(exchangeHandler ->
                new AuthenticatingAsyncDecorator(exchangeHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                    @Override
                    protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                        unauthorized.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    }
                });
        server.register("*", AsyncEchoHandler::new);
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();

        final CredentialsProvider credsProvider = Mockito.mock(CredentialsProvider.class);
        Mockito.when(credsProvider.getCredentials(Mockito.any(), Mockito.any()))
                .thenReturn(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();
        final Future<SimpleHttpResponse> future = client.execute(request, context, null);
        final HttpResponse response = future.get();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Mockito.verify(credsProvider).getCredentials(
                Mockito.eq(new AuthScope(target, "test realm", "basic")), Mockito.any());
    }

}