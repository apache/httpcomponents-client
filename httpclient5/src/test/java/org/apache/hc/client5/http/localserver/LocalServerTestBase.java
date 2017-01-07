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

package org.apache.hc.client5.http.localserver;

import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.io.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.io.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for tests using local test server. The server will not be started per default.
 */
public abstract class LocalServerTestBase {

    public enum ProtocolScheme { http, https };

    protected final ProtocolScheme scheme;

    protected ServerBootstrap serverBootstrap;
    protected HttpServer server;
    protected PoolingHttpClientConnectionManager connManager;
    protected HttpClientBuilder clientBuilder;
    protected CloseableHttpClient httpclient;

    public LocalServerTestBase(final ProtocolScheme scheme) {
        this.scheme = scheme;
    }

    public LocalServerTestBase() {
        this(ProtocolScheme.http);
    }

    public String getSchemeName() {
        return this.scheme.name();
    }

    @Before
    public void setUp() throws Exception {
        final SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(15000)
                .build();
        this.serverBootstrap = ServerBootstrap.bootstrap()
                .setSocketConfig(socketConfig)
                .registerHandler("/echo/*", new EchoHandler())
                .registerHandler("/random/*", new RandomHandler());
        if (this.scheme.equals(ProtocolScheme.https)) {
            this.serverBootstrap.setSslContext(SSLTestContexts.createServerSSLContext());
        }

        this.connManager = new PoolingHttpClientConnectionManager();
        this.connManager.setDefaultSocketConfig(socketConfig);
        this.clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(this.connManager);
    }

    @After
    public void shutDown() throws Exception {
        if (this.httpclient != null) {
            this.httpclient.close();
        }
        if (this.server != null) {
            this.server.shutdown(10, TimeUnit.SECONDS);
        }
    }

    public HttpHost start() throws Exception {
        this.server = this.serverBootstrap.create();
        this.server.start();

        if (this.httpclient == null) {
            this.httpclient = this.clientBuilder.build();
        }

        return new HttpHost("localhost", this.server.getLocalPort(), this.scheme.name());
    }

}
