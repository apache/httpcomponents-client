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

import java.util.function.Consumer;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.testing.nio.H2TestServer;

public abstract class TestH2ClientAuthentication extends AbstractHttpAsyncClientAuthenticationTest<CloseableHttpAsyncClient> {

    public TestH2ClientAuthentication(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected H2TestServer startServer(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        return startServer(H2Config.DEFAULT, null, exchangeHandlerDecorator);
    }

    @Override
    protected CloseableHttpAsyncClient startClientCustom(final Consumer<TestClientBuilder> clientCustomizer) throws Exception {

        return startH2Client(new Consumer<H2AsyncClientBuilder>() {

            @Override
            public void accept(final H2AsyncClientBuilder builder) {

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

}