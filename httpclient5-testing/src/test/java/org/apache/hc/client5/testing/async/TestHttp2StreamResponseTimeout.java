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

import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.H2StreamTimeoutException;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

abstract class AbstractTestHttp2StreamResponseTimeout extends AbstractIntegrationTestBase {

    public AbstractTestHttp2StreamResponseTimeout(final URIScheme scheme) {
        this(scheme, false);
    }

    public AbstractTestHttp2StreamResponseTimeout(final URIScheme scheme, final boolean useUnixDomainSocket) {
        super(scheme, ClientProtocolLevel.STANDARD, ServerProtocolLevel.H2_ONLY, useUnixDomainSocket);
    }

    void checkAssumptions() {
    }

    @Test
    void testResponseTimeout() throws Exception {
        checkAssumptions();
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofMinutes(1))
                .build());
        connManager.setDefaultTlsConfig(TlsConfig.custom()
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .build());

        final SimpleHttpRequest request1 = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/random/1024")
                .setRequestConfig(RequestConfig.custom()
                        .setUnixDomainSocket(getUnixDomainSocket())
                        .build())
                .build();
        final SimpleHttpRequest request2 = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/random/1024?delay=1000")
                .setRequestConfig(RequestConfig.custom()
                        .setUnixDomainSocket(getUnixDomainSocket())
                        .setResponseTimeout(Timeout.ofMilliseconds(100))
                        .build())
                .build();

        final Future<SimpleHttpResponse> future1 = client.execute(request1, null, null);
        final Future<SimpleHttpResponse> future2 = client.execute(request2, null, null);
        final SimpleHttpResponse response1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(response1);
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertInstanceOf(H2StreamTimeoutException.class, exception.getCause());

        final PoolStats totalStats = connManager.getTotalStats();
        Assertions.assertTrue(totalStats.getAvailable() > 0);
    }

}

@Disabled
public class TestHttp2StreamResponseTimeout {

    @Nested
    class Http extends AbstractTestHttp2StreamResponseTimeout {
        public Http() {
            super(URIScheme.HTTP, false);
        }
    }

    @Nested
    class Https extends AbstractTestHttp2StreamResponseTimeout {
        public Https() {
            super(URIScheme.HTTPS, false);
        }
    }

    @Nested
    class Uds extends AbstractTestHttp2StreamResponseTimeout {
        public Uds() {
            super(URIScheme.HTTP, true);
        }

        @Override
        void checkAssumptions() {
            assumeTrue(determineJRELevel() >= 16, "Async UDS requires Java 16+");
        }
    }

}
