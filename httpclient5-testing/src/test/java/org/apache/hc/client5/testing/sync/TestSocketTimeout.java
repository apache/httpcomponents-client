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
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractTestSocketTimeout extends AbstractIntegrationTestBase {
    protected AbstractTestSocketTimeout(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel,
                               final boolean useUnixDomainSocket) {
        super(scheme, clientProtocolLevel, useUnixDomainSocket);
    }

    @Timeout(5)
    @ParameterizedTest
    @ValueSource(strings = {
        "150,0,0,150,false",
        "0,150,0,150,false",
        "150,0,0,150,true",
        "0,150,0,150,true",
        // ConnectionConfig overrides SocketConfig
        "50,150,0,150,false",
        "1000,150,0,150,false",
        "50,150,0,150,true",
        "1000,150,0,150,true",
        // ResponseTimeout overrides socket timeout
        "2000,2000,150,150,false",
        "2000,2000,150,150,true"
    })
    void testReadTimeouts(final String param) throws Exception {
        final String[] params = param.split(",");
        final int socketConfigTimeout = Integer.parseInt(params[0]);
        final int connConfigTimeout = Integer.parseInt(params[1]);
        final long responseTimeout = Integer.parseInt(params[2]);
        final long expectedDelayMs = Long.parseLong(params[3]);
        final boolean drip = Boolean.parseBoolean(params[4]);
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
        final HttpGet request = new HttpGet(new URI("/random/10240?delay=1000&drip=" + (drip ? 1 : 0)));
        if (responseTimeout > 0) {
            request.setConfig(RequestConfig.custom()
                .setUnixDomainSocket(getUnixDomainSocket())
                .setResponseTimeout(responseTimeout, MILLISECONDS)
                .build());
        }

        final long startTime = System.nanoTime();
        assertThrows(SocketTimeoutException.class, () ->
            client.execute(target, request, new BasicHttpClientResponseHandler()));
        final long actualDelayMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        assertTrue(actualDelayMs > expectedDelayMs / 2,
            format("Socket read timed out too soon (only %,d out of %,d ms)", actualDelayMs, expectedDelayMs));
        assertTrue(actualDelayMs < expectedDelayMs * 3,
            format("Socket read timed out too late (%,d out of %,d ms)", actualDelayMs, expectedDelayMs));
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
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, false);
        }
    }

    @Nested
    class Uds extends AbstractTestSocketTimeout {
        public Uds() {
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, true);
        }
    }
}

