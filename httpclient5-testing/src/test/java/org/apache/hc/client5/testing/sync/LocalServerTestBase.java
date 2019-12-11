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

import java.io.IOException;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

/**
 * Base class for tests using local test server. The server will not be started per default.
 */
public abstract class LocalServerTestBase {

    public static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    public static final Timeout LONG_TIMEOUT = Timeout.ofSeconds(60);

    public LocalServerTestBase(final URIScheme scheme) {
        this.scheme = scheme;
    }

    public LocalServerTestBase() {
        this(URIScheme.HTTP);
    }

    protected final URIScheme scheme;

    protected ClassicTestServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            server = new ClassicTestServer(
                    scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null,
                    SocketConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build());
            server.registerHandler("/echo/*", new EchoHandler());
            server.registerHandler("/random/*", new RandomHandler());
        }

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.shutdown(CloseMode.IMMEDIATE);
                    server = null;
                } catch (final Exception ignore) {
                }
            }
        }

    };

    protected PoolingHttpClientConnectionManager connManager;
    protected HttpClientBuilder clientBuilder;
    protected CloseableHttpClient httpclient;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = new PoolingHttpClientConnectionManager();
            connManager.setDefaultSocketConfig(SocketConfig.custom()
                    .setSoTimeout(TIMEOUT)
                    .build());
            clientBuilder = HttpClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(TIMEOUT)
                            .setConnectTimeout(TIMEOUT)
                            .build())
                    .setConnectionManager(connManager);
        }

        @Override
        protected void after() {
            Closer.closeQuietly(httpclient);
            httpclient = null;
        }

    };

    public HttpHost start(
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<HttpServerRequestHandler> handlerDecorator) throws IOException {
        this.server.start(http1Config, httpProcessor, handlerDecorator);

        if (this.httpclient == null) {
            this.httpclient = this.clientBuilder.build();
        }

        return new HttpHost(this.scheme.name(), "localhost", this.server.getPort());
    }

    public HttpHost start(
            final HttpProcessor httpProcessor,
            final Decorator<HttpServerRequestHandler> handlerDecorator) throws IOException {
        return start(null, httpProcessor, handlerDecorator);
    }

    public HttpHost start() throws Exception {
        return start(null, null, null);
    }

}
