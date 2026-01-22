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
package org.apache.hc.client5.testing.sync;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.ConnectionConfig.Builder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.tls.TlsHandshakeTimeoutServer;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTlsHandshakeTimeout {
    @Timeout(5)
    @ParameterizedTest
    @CsvSource({
        "10,0,false",
        "10,0,true",
        "0,10,false",
        "0,10,true",
        // handshakeTimeout overrides connectTimeout
        "30000,10,false",
        "30000,10,true",
    })
    void testTimeout(
        final int connTimeout,
        final int handshakeTimeout,
        final boolean sendServerHello
    ) throws Exception {
        final Builder connectionConfig = ConnectionConfig.custom().setSocketTimeout(300, SECONDS);
        final TlsConfig.Builder tlsConfig = TlsConfig.custom();
        if (connTimeout > 0) {
            connectionConfig.setConnectTimeout(connTimeout, MILLISECONDS);
        }
        if (handshakeTimeout > 0) {
            tlsConfig.setHandshakeTimeout(handshakeTimeout, MILLISECONDS);
        }
        final PoolingHttpClientConnectionManager connMgr = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(connectionConfig.build())
            .setTlsSocketStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext(), HostnameVerificationPolicy.CLIENT, NoopHostnameVerifier.INSTANCE))
            .setDefaultTlsConfig(tlsConfig.build())
            .build();
        try (
            final TlsHandshakeTimeoutServer server = new TlsHandshakeTimeoutServer(sendServerHello);
            final CloseableHttpClient client = HttpClientBuilder.create()
                .setConnectionManager(connMgr)
                .disableAutomaticRetries()
                .build()
        ) {
            server.start();

            final HttpUriRequestBase request = new HttpGet("https://127.0.0.1:" + server.getPort());
            assertTimeout(request, client);
        }
    }

    @SuppressWarnings("deprecation")
    private static void assertTimeout(final ClassicHttpRequest request, final HttpClient client) {
        final Exception ex = assertThrows(Exception.class, () -> client.execute(request));
        assertTrue(ex.getMessage().contains("Read timed out"), ex.getMessage());
    }
}
