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

package org.apache.hc.client5.testing.async.extension;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalH2AsyncClient;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestAsyncResources implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TestClientResources.class);

    private final URIScheme scheme;
    private final Timeout timeout;

    private H2TestServer server;
    private InetSocketAddress socketAddress;
    private PoolingAsyncClientConnectionManager connManager;
    private CloseableHttpAsyncClient client;

    public TestAsyncResources(final URIScheme scheme, final Timeout timeout) {
        this.scheme = scheme;
        this.timeout = timeout;
    }

    @Override
    public void beforeEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Starting up test server");
        server = new H2TestServer(
                IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build(),
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null,
                null,
                null);
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Shutting down test server");

        if (client != null) {
            client.close(CloseMode.GRACEFUL);
        }

        if (connManager != null) {
            connManager.close(CloseMode.IMMEDIATE);
        }

        if (server != null) {
            server.shutdown(TimeValue.ofSeconds(5));
        }
    }

    public URIScheme scheme() {
        return this.scheme;
    }

    public H2TestServer startServer(
            final H2Config h2Config,
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        Assertions.assertNotNull(server);
        socketAddress = server.start(httpProcessor, exchangeHandlerDecorator, h2Config);
        return server;
    }

    public H2TestServer startServer(
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        Assertions.assertNotNull(server);
        socketAddress = server.start(httpProcessor, exchangeHandlerDecorator, http1Config);
        return server;
    }

    public HttpHost targetHost() {
        Assertions.assertNotNull(socketAddress);
        return new HttpHost(scheme.id, "localhost", socketAddress.getPort());
    }

    public CloseableHttpAsyncClient startClient(
            final Consumer<PoolingAsyncClientConnectionManagerBuilder> connManagerCustomizer,
            final Consumer<HttpAsyncClientBuilder> clientCustomizer) throws Exception {
        Assertions.assertNull(connManager);
        Assertions.assertNull(client);

        final PoolingAsyncClientConnectionManagerBuilder connManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
        connManagerBuilder.setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
        connManagerBuilder.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build());
        connManagerCustomizer.accept(connManagerBuilder);

        connManager = connManagerBuilder.build();

        final HttpAsyncClientBuilder clientBuilder = HttpAsyncClientBuilder.create()
                .setConnectionManager(connManager)
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build());

        clientCustomizer.accept(clientBuilder);
        client = clientBuilder.build();
        client.start();
        return client;
    }

    public CloseableHttpAsyncClient startClient(
            final Consumer<HttpAsyncClientBuilder> clientCustomizer) throws Exception {
        return startClient(b -> {
        }, clientCustomizer);
    }

    public PoolingAsyncClientConnectionManager connManager() {
        Assertions.assertNotNull(connManager);
        return connManager;
    }

    public CloseableHttpAsyncClient startH2Client(
            final Consumer<H2AsyncClientBuilder> clientCustomizer) throws Exception {
        Assertions.assertNull(connManager);
        Assertions.assertNull(client);

        final H2AsyncClientBuilder clientBuilder = H2AsyncClientBuilder.create();
        clientBuilder.setIOReactorConfig(IOReactorConfig.custom()
                .setSoTimeout(timeout)
                .build());
        clientBuilder.setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
        clientCustomizer.accept(clientBuilder);
        client = clientBuilder.build();
        client.start();
        return client;
    }

    public MinimalHttpAsyncClient startMinimalClient(
            final Http1Config http1Config,
            final H2Config h2Config,
            final Consumer<PoolingAsyncClientConnectionManagerBuilder> connManagerCustomizer) throws Exception {
        Assertions.assertNull(connManager);
        Assertions.assertNull(client);

        final PoolingAsyncClientConnectionManagerBuilder connManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
        connManagerBuilder.setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
        connManagerBuilder.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build());
        connManagerCustomizer.accept(connManagerBuilder);

        connManager = connManagerBuilder.build();

        final MinimalHttpAsyncClient minimal = HttpAsyncClients.createMinimal(
                h2Config,
                http1Config,
                IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build(),
                connManager);
        client = minimal;
        client.start();
        return minimal;
    }

    public MinimalH2AsyncClient startMinimalH2Client(final H2Config h2Config) throws Exception {
        Assertions.assertNull(client);

        final MinimalH2AsyncClient minimal = HttpAsyncClients.createHttp2Minimal(
                h2Config,
                IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build(),
                new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
        client = minimal;
        client.start();
        return minimal;
    }

}
