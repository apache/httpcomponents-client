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
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.classic.EchoHandler;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

/**
 * Base class for tests using local test server. The server will not be started per default.
 */
public abstract class LocalServerTestBase {

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
                            .setSoTimeout(5, TimeUnit.SECONDS)
                            .build());
            server.registerHandler("/echo/*", new EchoHandler());
            server.registerHandler("/random/*", new RandomHandler());
        }

        @Override
        protected void after() {
            if (server != null) {
                try {
                    server.shutdown(ShutdownType.IMMEDIATE);
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
                    .setSoTimeout(5, TimeUnit.SECONDS)
                    .build());
            clientBuilder = HttpClientBuilder.create()
                    .setConnectionManager(connManager);
        }

        @Override
        protected void after() {
            if (httpclient != null) {
                try {
                    httpclient.close();
                    httpclient = null;
                } catch (final Exception ignore) {
                }
            }
        }

    };

    public HttpHost start(
            final HttpProcessor httpProcessor,
            final Decorator<HttpServerRequestHandler> handlerDecorator) throws IOException {
        this.server.start(httpProcessor, handlerDecorator);

        if (this.httpclient == null) {
            this.httpclient = this.clientBuilder.build();
        }

        return new HttpHost("localhost", this.server.getPort(), this.scheme.name());
    }

    public HttpHost start() throws Exception {
        return start(null, null);
    }

}
