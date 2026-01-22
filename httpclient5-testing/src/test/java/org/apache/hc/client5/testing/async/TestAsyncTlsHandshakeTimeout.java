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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.ConnectionConfig.Builder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.tls.TlsHandshakeTimeoutServer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.hc.core5.http2.HttpVersionPolicy.FORCE_HTTP_2;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class TestAsyncTlsHandshakeTimeout {
    @Timeout(5)
    @ParameterizedTest
    @CsvSource({
        "10,0,false,",
        "10,0,true,",
        "0,10,false,",
        "0,10,true,",
        // handshakeTimeout overrides connectTimeout
        "30000,10,false,",
        "30000,10,true,",
        // ALPN and HTTP/2
        "0,10,false,FORCE_HTTP_2",
        "0,10,true,FORCE_HTTP_2",
        "0,10,false,NEGOTIATE",
        "0,10,true,NEGOTIATE",
    })
    void testTimeout(
        final int connTimeout,
        final int handshakeTimeout,
        final boolean sendServerHello,
        final HttpVersionPolicy httpVersionPolicy
    ) throws Exception {
        final Builder connectionConfig = ConnectionConfig.custom().setSocketTimeout(300, SECONDS);
        final TlsConfig.Builder tlsConfig = TlsConfig.custom();
        if (connTimeout > 0) {
            connectionConfig.setConnectTimeout(connTimeout, MILLISECONDS);
        }
        if (handshakeTimeout > 0) {
            tlsConfig.setHandshakeTimeout(handshakeTimeout, MILLISECONDS);
        }
        if (httpVersionPolicy != null) {
            tlsConfig.setVersionPolicy(httpVersionPolicy);
        }

        final AtomicReference<Exception> uncaughtException = new AtomicReference<>();
        final PoolingAsyncClientConnectionManager connMgr = PoolingAsyncClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(connectionConfig.build())
            .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
            .setDefaultTlsConfig(tlsConfig.build())
            .build();
        try (
            final TlsHandshakeTimeoutServer server = new TlsHandshakeTimeoutServer(sendServerHello);
            final CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setIoReactorExceptionCallback(uncaughtException::set)
                .setIOReactorConfig(IOReactorConfig.custom()
                    .setSelectInterval(TimeValue.ofMilliseconds(10))
                    .build())
                .setConnectionManager(connMgr)
                .build()
        ) {
            server.start();
            client.start();

            final SimpleHttpRequest request = SimpleHttpRequest.create("GET", "https://127.0.0.1:" + server.getPort());
            assertTimeout(request, client);
        }
        final Exception ex = uncaughtException.get();
        if (ex != null) {
            assumeFalse(httpVersionPolicy == FORCE_HTTP_2, "Known bug");
            throw ex;
        }
    }

    private static void assertTimeout(final SimpleHttpRequest request, final CloseableHttpAsyncClient client) {
        final Throwable ex = assertThrows(ExecutionException.class,
            () -> client.execute(request, null).get()).getCause();
        assertTrue(ex.getMessage().contains("10 MILLISECONDS"), ex.getMessage());
    }
}
