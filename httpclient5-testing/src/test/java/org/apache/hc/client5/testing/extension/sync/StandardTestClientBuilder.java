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

package org.apache.hc.client5.testing.extension.sync;

import java.util.Collection;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

final class StandardTestClientBuilder implements TestClientBuilder {

    private final HttpClientBuilder clientBuilder;

    private Timeout timeout;

    private HttpClientConnectionManager connectionManager;

    public StandardTestClientBuilder() {
        this.clientBuilder = HttpClientBuilder.create();
    }

    @Override
    public ClientProtocolLevel getProtocolLevel() {
        return ClientProtocolLevel.STANDARD;
    }

    @Override
    public TestClientBuilder setTimeout(final Timeout timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public TestClientBuilder setConnectionManager(final HttpClientConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        return this;
    }

    @Override
    public TestClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        this.clientBuilder.addResponseInterceptorFirst(interceptor);
        return this;
    }

    @Override
    public TestClientBuilder addResponseInterceptorLast(final HttpResponseInterceptor interceptor) {
        this.clientBuilder.addResponseInterceptorLast(interceptor);
        return this;
    }

    @Override
    public TestClientBuilder addRequestInterceptorFirst(final HttpRequestInterceptor interceptor) {
        this.clientBuilder.addRequestInterceptorFirst(interceptor);
        return this;
    }

    @Override
    public TestClientBuilder addRequestInterceptorLast(final HttpRequestInterceptor interceptor) {
        this.clientBuilder.addRequestInterceptorLast(interceptor);
        return this;
    }

    @Override
    public TestClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler) {
        this.clientBuilder.setUserTokenHandler(userTokenHandler);
        return this;
    }

    @Override
    public TestClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        this.clientBuilder.setDefaultHeaders(defaultHeaders);
        return this;
    }

    @Override
    public TestClientBuilder setRetryStrategy(final HttpRequestRetryStrategy retryStrategy) {
        this.clientBuilder.setRetryStrategy(retryStrategy);
        return this;
    }

    @Override
    public TestClientBuilder setTargetAuthenticationStrategy(final AuthenticationStrategy targetAuthStrategy) {
        this.clientBuilder.setTargetAuthenticationStrategy(targetAuthStrategy);
        return this;
    }

    @Override
    public TestClientBuilder setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.clientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        return this;
    }

    @Override
    public TestClientBuilder setRequestExecutor(final HttpRequestExecutor requestExec) {
        this.clientBuilder.setRequestExecutor(requestExec);
        return this;
    }

    @Override
    public TestClientBuilder addExecInterceptorFirst(final String name, final ExecChainHandler interceptor) {
        this.clientBuilder.addExecInterceptorFirst(name, interceptor);
        return this;
    }

    @Override
    public TestClientBuilder addExecInterceptorLast(final String name, final ExecChainHandler interceptor) {
        this.clientBuilder.addExecInterceptorLast(name, interceptor);
        return this;
    }

    @Override
    public TestClient build() throws Exception {
        final HttpClientConnectionManager connectionManagerCopy = connectionManager != null ? connectionManager :
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setTlsSocketStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                        .setDefaultSocketConfig(SocketConfig.custom()
                                .setSoTimeout(timeout)
                                .build())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(timeout)
                                .build())
                        .build();

        final CloseableHttpClient client = clientBuilder
                .setConnectionManager(connectionManagerCopy)
                .build();
        return new TestClient(client, connectionManagerCopy);
    }

}
