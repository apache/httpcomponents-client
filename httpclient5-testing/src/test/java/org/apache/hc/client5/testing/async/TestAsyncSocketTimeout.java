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
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

abstract class AbstractTestSocketTimeout extends AbstractIntegrationTestBase {
    protected AbstractTestSocketTimeout(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel,
                                        final ServerProtocolLevel serverProtocolLevel, final boolean useUnixDomainSocket) {
        super(scheme, clientProtocolLevel, serverProtocolLevel, useUnixDomainSocket);
    }

    @Timeout(5)
    @ParameterizedTest
    @CsvSource({
        "10,0",
        "0,10",
        // ResponseTimeout overrides socket timeout
        "10000,10",
    })
    void testReadTimeouts(final int connConfigTimeout, final int responseTimeout) throws Throwable {
        checkAssumptions();
        configureServer(bootstrap -> bootstrap
                .register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        if (connConfigTimeout > 0) {
            connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(connConfigTimeout, MILLISECONDS)
                .build());
        }

        for (final boolean drip : new boolean[]{ false, true }) {
            for (final boolean reuseConnection : new boolean[]{ false, true }) {
                if (reuseConnection) {
                    client.execute(getRequest(2500, 0, false, target), null).get();
                }
                final SimpleHttpRequest request = getRequest(responseTimeout, 2500, drip, target);

                final Throwable cause = assertThrows(ExecutionException.class,
                    () -> client.execute(request, null).get()).getCause();
                assertInstanceOf(SocketTimeoutException.class, cause,
                    String.format("drip=%s, reuseConnection=%s", drip, reuseConnection));
            }
        }

        closeClient(client);
    }

    private SimpleHttpRequest getRequest(final int responseTimeout, final int delay, final boolean drip,
                                         final HttpHost target) throws Exception {
        final SimpleHttpRequest request = SimpleHttpRequest.create(Method.GET, target,
            "/random/10240?delay=" + delay + "&drip=" + (drip ? 1 : 0));
        if (responseTimeout > 0) {
            request.setConfig(RequestConfig.custom()
                .setUnixDomainSocket(getUnixDomainSocket())
                .setResponseTimeout(responseTimeout, MILLISECONDS).build());
        }
        return request;
    }

    void checkAssumptions() {
    }

    void closeClient(final TestAsyncClient client) {
        client.close(CloseMode.GRACEFUL);
    }
}

public class TestAsyncSocketTimeout {
    @Nested
    class Http extends AbstractTestSocketTimeout {
        public Http() {
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD, false);
        }
    }

    @Nested
    class Https extends AbstractTestSocketTimeout {
        public Https() {
            super(URIScheme.HTTPS, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD, false);
        }
    }

    @Nested
    class Uds extends AbstractTestSocketTimeout {
        public Uds() {
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD, true);
        }

        @Override
        void checkAssumptions() {
            assumeTrue(determineJRELevel() >= 16, "Async UDS requires Java 16+");
        }
    }
}

