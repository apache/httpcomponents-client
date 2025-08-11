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
import org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

abstract class AbstractTestSocketTimeout extends AbstractIntegrationTestBase {
    protected AbstractTestSocketTimeout(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel,
                                        final ServerProtocolLevel serverProtocolLevel, final boolean useUnixDomainSocket) {
        super(scheme, clientProtocolLevel, serverProtocolLevel, useUnixDomainSocket);
    }

    @Timeout(5)
    @ParameterizedTest
    @ValueSource(strings = {
        "150,0,150,false",
        "0,150,150,false",
        "150,0,150,true",
        "0,150,150,true",
        // ResponseTimeout overrides socket timeout
        "2000,150,150,false",
        "2000,150,150,true"
    })
    void testReadTimeouts(final String param) throws Throwable {
        checkAssumptions();
        final String[] params = param.split(",");
        final int connConfigTimeout = Integer.parseInt(params[0]);
        final long responseTimeout = Integer.parseInt(params[1]);
        final long expectedDelayMs = Long.parseLong(params[2]);
        final boolean drip = Boolean.parseBoolean(params[3]);
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
        final SimpleHttpRequest request = SimpleHttpRequest.create(Method.GET, target,
            "/random/10240?delay=500&drip=" + (drip ? 1 : 0));
        if (responseTimeout > 0) {
            request.setConfig(RequestConfig.custom()
                .setUnixDomainSocket(getUnixDomainSocket())
                .setResponseTimeout(responseTimeout, MILLISECONDS).build());
        }

        final long startTime = System.nanoTime();
        final Throwable cause = assertThrows(ExecutionException.class, () -> client.execute(request, null).get())
            .getCause();
        final long actualDelayMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        assertInstanceOf(SocketTimeoutException.class, cause);
        assertTrue(actualDelayMs > expectedDelayMs / 2,
            format("Socket read timed out too soon (only %,d out of %,d ms)", actualDelayMs, expectedDelayMs));
        assertTrue(actualDelayMs < expectedDelayMs * 2,
            format("Socket read timed out too late (%,d out of %,d ms)", actualDelayMs, expectedDelayMs));

        closeClient(client);
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
            final String[] components = VersionInfo
                .loadVersionInfo("org.apache.hc.core5", getClass().getClassLoader())
                .getRelease()
                .split("[-.]");
            final int majorVersion = Integer.parseInt(components[0]);
            final int minorVersion = Integer.parseInt(components[1]);
            assumeFalse(majorVersion <= 5 && minorVersion <= 3, "Async UDS requires HttpCore 5.4+");
        }
    }
}

