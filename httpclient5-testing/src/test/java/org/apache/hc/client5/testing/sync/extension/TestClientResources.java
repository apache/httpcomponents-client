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

package org.apache.hc.client5.testing.sync.extension;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.MinimalHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClientResources implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TestClientResources.class);

    private final URIScheme scheme;
    private final Timeout timeout;

    private ClassicTestServer server;
    private PoolingHttpClientConnectionManager connManager;
    private CloseableHttpClient client;

    public TestClientResources(final URIScheme scheme, final Timeout timeout) {
        this.scheme = scheme;
        this.timeout = timeout;
    }

    @Override
    public void beforeEach(final ExtensionContext extensionContext) throws Exception {
        LOG.debug("Starting up test server");
        server = new ClassicTestServer(
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null,
                SocketConfig.custom()
                        .setSoTimeout(timeout)
                        .build());
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
            server.shutdown(CloseMode.IMMEDIATE);
        }
    }

    public URIScheme scheme() {
        return this.scheme;
    }

    public ClassicTestServer startServer(
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<HttpServerRequestHandler> handlerDecorator) throws IOException {
        Assertions.assertNotNull(server);
        server.start(http1Config, httpProcessor, handlerDecorator);
        return server;
    }

    public HttpHost targetHost() {
        Assertions.assertNotNull(server);
        return new HttpHost(scheme.id, "localhost", server.getPort());
    }

    public CloseableHttpClient startClient(
            final Consumer<PoolingHttpClientConnectionManagerBuilder> connManagerCustomizer,
            final Consumer<HttpClientBuilder> clientCustomizer) throws Exception {
        Assertions.assertNull(connManager);
        Assertions.assertNull(client);

        final PoolingHttpClientConnectionManagerBuilder connManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        connManagerBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(SSLTestContexts.createClientSSLContext()));
        connManagerBuilder.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(timeout)
                .build());
        connManagerBuilder.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .build());
        connManagerCustomizer.accept(connManagerBuilder);

        connManager = connManagerBuilder.build();

        final HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(connManager);
        clientCustomizer.accept(clientBuilder);
        client = clientBuilder.build();
        return client;
    }

    public MinimalHttpClient startMinimalClient() throws Exception {
        Assertions.assertNull(connManager);
        Assertions.assertNull(client);

        final PoolingHttpClientConnectionManagerBuilder connManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        connManagerBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(SSLTestContexts.createClientSSLContext()));
        connManagerBuilder.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(timeout)
                .build());
        connManagerBuilder.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .build());
        connManager = connManagerBuilder.build();

        final MinimalHttpClient minimalClient = HttpClients.createMinimal(connManager);
        client = minimalClient;
        return minimalClient;
    }

    public CloseableHttpClient startClient(
            final Consumer<HttpClientBuilder> clientCustomizer) throws Exception {
        return startClient(b -> {}, clientCustomizer);
    }

    public PoolingHttpClientConnectionManager connManager() {
        Assertions.assertNotNull(connManager);
        return connManager;
    }

}
