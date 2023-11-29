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

import java.util.function.Consumer;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.MinimalH2AsyncClient;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.testing.async.extension.TestAsyncResources;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractIntegrationTestBase {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncResources testResources;

    protected AbstractIntegrationTestBase(final URIScheme scheme) {
        this.testResources = new TestAsyncResources(scheme, TIMEOUT);
    }

    public URIScheme scheme() {
        return testResources.scheme();
    }

    public H2TestServer startServer(
            final H2Config h2Config,
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        return testResources.startServer(h2Config, httpProcessor, exchangeHandlerDecorator);
    }

    public H2TestServer startServer(
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        return testResources.startServer(http1Config, httpProcessor, exchangeHandlerDecorator);
    }

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    public CloseableHttpAsyncClient startClient(
            final Consumer<PoolingAsyncClientConnectionManagerBuilder> connManagerCustomizer,
            final Consumer<HttpAsyncClientBuilder> clientCustomizer) throws Exception {
        return testResources.startClient(connManagerCustomizer, clientCustomizer);
    }

    public CloseableHttpAsyncClient startClient(
            final Consumer<HttpAsyncClientBuilder> clientCustomizer) throws Exception {
        return testResources.startClient(clientCustomizer);
    }

    public PoolingAsyncClientConnectionManager connManager() {
        return testResources.connManager();
    }

    public CloseableHttpAsyncClient startH2Client(
            final Consumer<H2AsyncClientBuilder> clientCustomizer) throws Exception {
        return testResources.startH2Client(clientCustomizer);
    }

    public MinimalHttpAsyncClient startMinimalClient(
            final Http1Config http1Config,
            final H2Config h2Config,
            final Consumer<PoolingAsyncClientConnectionManagerBuilder> connManagerCustomizer) throws Exception {
        return testResources.startMinimalClient(http1Config, h2Config, connManagerCustomizer);
    }

    public MinimalHttpAsyncClient startMinimalH2Client(
            final Http1Config http1Config,
            final H2Config h2Config,
            final Consumer<PoolingAsyncClientConnectionManagerBuilder> connManagerCustomizer) throws Exception {
        return testResources.startMinimalClient(http1Config, h2Config, connManagerCustomizer);
    }

    public MinimalH2AsyncClient startMinimalH2Client(final H2Config h2Config) throws Exception {
        return testResources.startMinimalH2Client(h2Config);
    }

}
