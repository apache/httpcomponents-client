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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Deadline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHttpAsyncTlsRequired extends AbstractIntegrationTestBase {

    private final AtomicInteger serverRequests = new AtomicInteger();

    private PoolingAsyncClientConnectionManager connManager;
    private CloseableHttpAsyncClient plainClient;
    private CloseableHttpAsyncClient tlsClient;

    public TestHttpAsyncTlsRequired() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
    }

    @AfterEach
    void cleanup() {
        if (plainClient != null) {
            plainClient.close(CloseMode.IMMEDIATE);
        }
        if (tlsClient != null) {
            tlsClient.close(CloseMode.IMMEDIATE);
        }
        if (connManager != null) {
            connManager.close(CloseMode.IMMEDIATE);
        }
    }

    private HttpHost startCountingServer() throws Exception {
        configureServer(bootstrap -> bootstrap.register("*", () -> new AbstractSimpleServerExchangeHandler() {

            @Override
            protected SimpleHttpResponse handle(
                    final SimpleHttpRequest request,
                    final HttpCoreContext context) {
                serverRequests.incrementAndGet();
                final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                response.setBody("Whatever", ContentType.TEXT_PLAIN);
                return response;
            }
        }));
        return startServer();
    }

    private static AsyncExecChainHandler recorder(final AtomicInteger transmitted) {
        return (request, entityProducer, scope, chain, asyncExecCallback) -> {
            transmitted.incrementAndGet();
            chain.proceed(request, entityProducer, scope, asyncExecCallback);
        };
    }

    private static CloseableHttpAsyncClient newClient(
            final PoolingAsyncClientConnectionManager connManager,
            final boolean tlsRequired,
            final boolean recordRightAfterTlsCheck,
            final AtomicInteger transmitted) {
        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setConnectionManagerShared(true)
                .setTlsRequired(tlsRequired);
        if (recordRightAfterTlsCheck) {
            builder.addExecInterceptorAfter(ChainElement.TLS_REQUIRED.name(), "recorder", recorder(transmitted));
        } else {
            builder.addExecInterceptorLast("recorder", recorder(transmitted));
        }
        final CloseableHttpAsyncClient client = builder.build();
        client.start();
        return client;
    }

    // The result future completes before the connection is returned to the pool.
    private static PoolStats awaitRelease(final PoolingAsyncClientConnectionManager connManager) throws InterruptedException {
        final Deadline deadline = Deadline.calculate(TIMEOUT);
        for (;;) {
            final PoolStats stats = connManager.getTotalStats();
            if (stats.getLeased() == 0) {
                return stats;
            }
            Assertions.assertFalse(deadline.isExpired(), "Connection not released: " + stats);
            Thread.sleep(10);
        }
    }

    private static SimpleHttpRequest get(final HttpHost target) {
        return SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();
    }

    @Test
    void rejectedCleartextRequestIsNeverTransmitted() throws Exception {
        final HttpHost target = startCountingServer();
        final AtomicInteger transmitted = new AtomicInteger();
        connManager = PoolingAsyncClientConnectionManagerBuilder.create().build();
        tlsClient = newClient(connManager, true, true, transmitted);

        final Future<SimpleHttpResponse> future = tlsClient.execute(get(target), null);
        final ExecutionException ex = Assertions.assertThrows(ExecutionException.class,
                () -> future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertInstanceOf(UnsupportedSchemeException.class, ex.getCause());

        Assertions.assertEquals(0, transmitted.get());
        Assertions.assertEquals(0, serverRequests.get());
    }

    @Test
    void rejectedCleartextRequestDoesNotUseSharedWarmPool() throws Exception {
        final HttpHost target = startCountingServer();
        final AtomicInteger plainTransmitted = new AtomicInteger();
        final AtomicInteger tlsTransmitted = new AtomicInteger();
        connManager = PoolingAsyncClientConnectionManagerBuilder.create().build();
        plainClient = newClient(connManager, false, false, plainTransmitted);
        tlsClient = newClient(connManager, true, false, tlsTransmitted);

        final SimpleHttpResponse first = plainClient.execute(get(target), null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, first.getCode());
        Assertions.assertEquals(1, awaitRelease(connManager).getAvailable());

        final Future<SimpleHttpResponse> rejected = tlsClient.execute(get(target), null);
        final ExecutionException ex = Assertions.assertThrows(ExecutionException.class,
                () -> rejected.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertInstanceOf(UnsupportedSchemeException.class, ex.getCause());
        Assertions.assertEquals(0, tlsTransmitted.get());

        final SimpleHttpResponse second = plainClient.execute(get(target), null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, second.getCode());

        Assertions.assertEquals(2, plainTransmitted.get());
        Assertions.assertEquals(2, serverRequests.get());
        Assertions.assertEquals(1, awaitRelease(connManager).getAvailable());
    }

}
