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

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.ConnectHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.Timeout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.testing.SSLTestContexts;

import static org.junit.Assert.assertEquals;

public class TestAsyncClientForwardProxy {

    private static final int SERVER_PORT = 8083;
    private static final int PROXY_PORT = 8084;

    private Undertow server;

    @Before
    public void before() throws Exception {
        server = Undertow.builder()
                .addHttpsListener(
                        SERVER_PORT,
                        null,
                        SSLTestContexts.createServerSSLContext(),
                        new BlockingHandler(new HttpHandler() {
                            @Override
                            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                                // Wait longer than the client connect timeout to ensure we're only measuring
                                // connect time.
                                Thread.sleep(1000L);
                                exchange.setStatusCode(200);
                            }
                        }))
                .addHttpListener(PROXY_PORT, null, new ConnectHandler(ResponseCodeHandler.HANDLE_500))
                .build();
        server.start();
    }

    @After
    public void after() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testForwardProxyRespectsSocketTimeout() throws Exception {
        try (CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setRoutePlanner(new SystemDefaultRoutePlanner(
                        null, new SimpleProxySelector("localhost", PROXY_PORT)))
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.DISABLED)
                        .setConnectTimeout(Timeout.ofMilliseconds(500))
                        .build())
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                        .build())
                .build()) {
            client.start();
            SimpleHttpResponse response = client.execute(
                    new SimpleHttpRequest("GET", URI.create("https://localhost:" + SERVER_PORT)),
                    new FutureCallback<SimpleHttpResponse>() {
                @Override
                public void completed(final SimpleHttpResponse result) {
                    // nop
                }

                @Override
                public void failed(final Exception ex) {
                    // nop
                }

                @Override
                public void cancelled() {
                    // nop
                }
            }).get();
            assertEquals(200, response.getCode());
        }
    }

    private static final class SimpleProxySelector extends ProxySelector {

        private final List<Proxy> value;

        SimpleProxySelector(final String host, final int port) {
            this.value = Collections.singletonList(
                    new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)));
        }

        @Override
        public List<Proxy> select(final URI uri) {
            return value;
        }

        @Override
        public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            // nop
        }
    }
}
