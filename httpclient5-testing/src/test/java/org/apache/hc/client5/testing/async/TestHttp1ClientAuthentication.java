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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

abstract class TestHttp1ClientAuthentication extends AbstractHttpAsyncClientAuthenticationTest {

    public TestHttp1ClientAuthentication(final URIScheme scheme) {
        super(scheme, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
    }

    @Test
    void testBasicAuthenticationSuccessNonPersistentConnection() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("*", AsyncEchoHandler::new)
                .setExchangeHandlerDecorator(exchangeHandler ->
                        new AuthenticatingAsyncDecorator(exchangeHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                            }
                        }));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

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