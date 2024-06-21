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

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

final class MinimalTestClientBuilder implements TestAsyncClientBuilder {

    private final PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder;

    private Timeout timeout;
    private TlsStrategy tlsStrategy;
    private Http1Config http1Config;
    private H2Config h2Config;

    public MinimalTestClientBuilder() {
        this.connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
    }

    @Override
    public ClientProtocolLevel getProtocolLevel() {
        return ClientProtocolLevel.MINIMAL;
    }

    @Override
    public TestAsyncClientBuilder setTimeout(final Timeout timeout) {
        this.timeout = timeout;
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
    public TestAsyncClientBuilder setHttp1Config(final Http1Config http1Config) {
        this.http1Config = http1Config;
        return this;
    }

    @Override
    public TestAsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    @Override
    public TestAsyncClient build() throws Exception {
        final PoolingAsyncClientConnectionManager connectionManager = connectionManagerBuilder
                .setTlsStrategy(tlsStrategy != null ? tlsStrategy : new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setSocketTimeout(timeout)
                        .setConnectTimeout(timeout)
                        .build())
                .build();
        final CloseableHttpAsyncClient client = HttpAsyncClients.createMinimal(
                        h2Config,
                        http1Config,
                        IOReactorConfig.custom()
                                .setSoTimeout(timeout)
                                .build(),
                connectionManager);
        return new TestAsyncClient(client, connectionManager);
    }

}
