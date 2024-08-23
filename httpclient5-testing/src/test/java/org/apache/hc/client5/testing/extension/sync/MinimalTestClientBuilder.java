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

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.MinimalHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

final class MinimalTestClientBuilder implements TestClientBuilder {

    private Timeout timeout;

    private HttpClientConnectionManager connectionManager;

    public MinimalTestClientBuilder() {
    }

    @Override
    public ClientProtocolLevel getProtocolLevel() {
        return ClientProtocolLevel.MINIMAL;
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

        final MinimalHttpClient minimalClient = HttpClients.createMinimal(connectionManagerCopy);
        return new TestClient(minimalClient, connectionManagerCopy);
    }

}
