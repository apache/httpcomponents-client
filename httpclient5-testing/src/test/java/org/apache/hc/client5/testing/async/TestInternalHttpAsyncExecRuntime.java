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

import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.InternalTestHttpAsyncExecRuntime;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.client5.testing.extension.async.TestAsyncResources;
import org.apache.hc.client5.testing.extension.async.TestAsyncServer;
import org.apache.hc.client5.testing.extension.async.TestAsyncServerBootstrap;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureContribution;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.support.AsyncClientPipeline;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestInternalHttpAsyncExecRuntime {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncResources testResources;

    public TestInternalHttpAsyncExecRuntime() {
        this.testResources = new TestAsyncResources(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD, TIMEOUT);
    }

    public void configureServer(final Consumer<TestAsyncServerBootstrap> serverCustomizer) {
        testResources.configureServer(serverCustomizer);
    }

    public HttpHost startServer() throws Exception {
        final TestAsyncServer server = testResources.server();
        final InetSocketAddress inetSocketAddress = server.start();
        return new HttpHost(testResources.scheme().id, "localhost", inetSocketAddress.getPort());
    }

    public TestAsyncClient startClient() throws Exception {
        final TestAsyncClient client = testResources.client();
        client.start();
        return client;
    }

    static final int REQ_NUM = 5;

    HttpRequest createRequest(final HttpHost target) {
        return BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/random/20000")
                .addHeader(HttpHeaders.HOST, target.toHostString())
                .build();
    }

    @Test
    void testExecutionCancellation_http11HardCancellation_connectionMarkedNonReusable() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final ConnectionInitiator connectionInitiator = client.getImplementation();
        final PoolingAsyncClientConnectionManager connectionManager = client.getConnectionManager();
        for (int i = 0; i < REQ_NUM; i++) {
            final HttpClientContext context = HttpClientContext.create();

            final InternalTestHttpAsyncExecRuntime testRuntime = new InternalTestHttpAsyncExecRuntime(
                    connectionManager,
                    connectionInitiator,
                    TlsConfig.custom()
                            .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                            .build());
            final Future<Boolean> connectFuture = testRuntime.leaseAndConnect(target, context);
            Assertions.assertTrue(connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));

            final BasicFuture<Message<HttpResponse, byte[]>> resultFuture = new BasicFuture<>(null);
            final Cancellable cancellable = testRuntime.execute(
                    "test-" + i,
                    AsyncClientPipeline.assemble()
                            .request(createRequest(target)).noContent()
                            .response().asByteArray()
                            .result(new FutureContribution<Message<HttpResponse, byte[]>>(resultFuture) {

                                @Override
                                public void completed(final Message<HttpResponse, byte[]> result) {
                                    resultFuture.completed(result);
                                }

                            })
                            .create(),
                    context);
            // sleep a bit
            Thread.sleep(i % 10);
            cancellable.cancel();

            // The message exchange is expected to get aborted
            try {
                resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            } catch (final CancellationException expected) {
            }
            Assertions.assertTrue(testRuntime.isAborted());
            testRuntime.discardEndpoint();
        }
    }

    @Test
    void testExecutionCancellation_http11NoHardCancellation_connectionAlive() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final ConnectionInitiator connectionInitiator = client.getImplementation();
        final PoolingAsyncClientConnectionManager connectionManager = client.getConnectionManager();
        for (int i = 0; i < REQ_NUM; i++) {
            final HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(RequestConfig.custom()
                    .setHardCancellationEnabled(false)
                    .build());

            final InternalTestHttpAsyncExecRuntime testRuntime = new InternalTestHttpAsyncExecRuntime(
                    connectionManager,
                    connectionInitiator,
                    TlsConfig.custom()
                            .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                            .build());
            final Future<Boolean> connectFuture = testRuntime.leaseAndConnect(target, context);
            Assertions.assertTrue(connectFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));

            final BasicFuture<Message<HttpResponse, byte[]>> resultFuture = new BasicFuture<>(null);
            final Cancellable cancellable = testRuntime.execute(
                    "test-" + i,
                    AsyncClientPipeline.assemble()
                            .request(createRequest(target)).noContent()
                            .response().asByteArray()
                            .result(new FutureContribution<Message<HttpResponse, byte[]>>(resultFuture) {

                                @Override
                                public void completed(final Message<HttpResponse, byte[]> result) {
                                    resultFuture.completed(result);
                                }

                            })
                            .create(),
                    context);
            // sleep a bit
            Thread.sleep(i % 10);
            cancellable.cancel();

            // The message exchange should not get aborted and is expected to successfully complete
            final Message<HttpResponse, byte[]> message = resultFuture.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(message);
            Assertions.assertFalse(testRuntime.isAborted());
            // The underlying connection is expected to stay valid
            Assertions.assertTrue(testRuntime.isEndpointConnected());
            testRuntime.markConnectionReusable(null, TimeValue.ofMinutes(1));
            testRuntime.releaseEndpoint();

            final PoolStats totalStats = connectionManager.getTotalStats();
            Assertions.assertTrue(totalStats.getAvailable() > 0);
        }
    }

}
