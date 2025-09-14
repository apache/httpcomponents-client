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

package org.apache.hc.client5.testing.extension.async;

import java.nio.file.Path;
import java.util.Collection;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

final class StandardTestClientBuilder implements TestAsyncClientBuilder {

    private final PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder;
    private final HttpAsyncClientBuilder clientBuilder;

    private AsyncClientConnectionManager connectionManager;
    private Timeout timeout;
    private TlsStrategy tlsStrategy;

    public StandardTestClientBuilder() {
        this.connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
        this.clientBuilder = HttpAsyncClientBuilder.create();
    }

    @Override
    public ClientProtocolLevel getProtocolLevel() {
        return ClientProtocolLevel.STANDARD;
    }

    @Override
    public TestAsyncClientBuilder setTimeout(final Timeout timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public TestAsyncClientBuilder setConnectionManager(final AsyncClientConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        return this;
    }

    @Override
    public TestAsyncClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        this.clientBuilder.addResponseInterceptorFirst(interceptor);
        return this;
    }

    @Override
    public TestAsyncClientBuilder addResponseInterceptorLast(final HttpResponseInterceptor interceptor) {
        this.clientBuilder.addResponseInterceptorLast(interceptor);
        return this;
    }

    @Override
    public TestAsyncClientBuilder addRequestInterceptorFirst(final HttpRequestInterceptor interceptor) {
        this.clientBuilder.addRequestInterceptorFirst(interceptor);
        return this;
    }

    @Override
    public TestAsyncClientBuilder addRequestInterceptorLast(final HttpRequestInterceptor interceptor) {
        this.clientBuilder.addRequestInterceptorLast(interceptor);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    @Override
    public TestAsyncClientBuilder setDefaultTlsConfig(final TlsConfig tlsConfig) {
        this.connectionManagerBuilder.setDefaultTlsConfig(tlsConfig);
        return this;
    }

    @Override
    public TestAsyncClientBuilder useMessageMultiplexing() {
        this.connectionManagerBuilder.setMessageMultiplexing(true);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setHttp1Config(final Http1Config http1Config) {
        this.clientBuilder.setHttp1Config(http1Config);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.clientBuilder.setH2Config(h2Config);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler) {
        this.clientBuilder.setUserTokenHandler(userTokenHandler);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        this.clientBuilder.setDefaultHeaders(defaultHeaders);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setRetryStrategy(final HttpRequestRetryStrategy retryStrategy) {
        this.clientBuilder.setRetryStrategy(retryStrategy);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setTargetAuthenticationStrategy(final AuthenticationStrategy targetAuthStrategy) {
        this.clientBuilder.setTargetAuthenticationStrategy(targetAuthStrategy);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.clientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setDefaultCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        return this;
    }

    @Override
    public TestAsyncClientBuilder setUnixDomainSocket(final Path unixDomainSocket) {
        this.clientBuilder.setDefaultRequestConfig(RequestConfig.custom()
            .setUnixDomainSocket(unixDomainSocket)
            .build());
        return this;
    }

    @Override
    public TestAsyncClient build() throws Exception {
        final AsyncClientConnectionManager connectionManagerCopy = connectionManager == null ? connectionManagerBuilder
                .setTlsStrategy(tlsStrategy != null ? tlsStrategy : new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setSocketTimeout(timeout)
                        .setConnectTimeout(timeout)
                        .build())
                .build() : connectionManager;
        final CloseableHttpAsyncClient client = clientBuilder
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSelectInterval(TimeValue.ofMilliseconds(10))
                        .setSoTimeout(timeout)
                        .build())
                .setConnectionManager(connectionManagerCopy)
                .build();
        return new TestAsyncClient(client, connectionManagerCopy);
    }

}
