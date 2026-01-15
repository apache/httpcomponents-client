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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.SocketConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.SocketTimeoutException;
import java.net.URI;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class AbstractTestSocketTimeout extends AbstractIntegrationTestBase {
    protected AbstractTestSocketTimeout(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel,
                               final boolean useUnixDomainSocket) {
        super(scheme, clientProtocolLevel, useUnixDomainSocket);
    }

    @Timeout(5)
    @ParameterizedTest
    @CsvSource({
        "10,0,0",
        "0,10,0",
        // ConnectionConfig overrides SocketConfig
        "10000,10,0",
        // ResponseTimeout overrides socket timeout
        "10000,10000,10",
    })
    void testReadTimeouts(final int socketConfigTimeout, final int connConfigTimeout, final int responseTimeout)
        throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();
        final PoolingHttpClientConnectionManager connManager = client.getConnectionManager();
        if (socketConfigTimeout > 0) {
            connManager.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(socketConfigTimeout, MILLISECONDS)
                .build());
        }
        if (connConfigTimeout > 0) {
            connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(connConfigTimeout, MILLISECONDS)
                .build());
        }

        for (final boolean drip : new boolean[]{ false, true }) {
            for (final boolean reuseConnection : new boolean[]{ false, true }) {
                if (reuseConnection) {
                    client.execute(target, getRequest(5000, 0, false), new BasicHttpClientResponseHandler());
                }
                final HttpGet request = getRequest(responseTimeout, 2500, drip);

                assertThrows(SocketTimeoutException.class, () ->
                    client.execute(target, request, new BasicHttpClientResponseHandler()),
                    String.format("drip=%s, reuseConnection=%s", drip, reuseConnection));
            }
        }
    }

    private HttpGet getRequest(final int responseTimeout, final int delay, final boolean drip) throws Exception {
        final HttpGet request = new HttpGet(new URI("/random/10240?delay=" + delay + "&drip=" + (drip ? 1 : 0)));
        if (responseTimeout > 0) {
            request.setConfig(RequestConfig.custom()
                .setUnixDomainSocket(getUnixDomainSocket())
                .setResponseTimeout(responseTimeout, MILLISECONDS)
                .build());
        }
        return request;
    }
}

public class TestSocketTimeout {
    @Nested
    class Http extends AbstractTestSocketTimeout {
        public Http() {
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, false);
        }
    }

    @Nested
    class Https extends AbstractTestSocketTimeout {
        public Https() {
            super(URIScheme.HTTPS, ClientProtocolLevel.STANDARD, false);
        }
    }

    @Nested
    class Uds extends AbstractTestSocketTimeout {
        public Uds() {
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, true);
        }
    }
}

