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
package org.apache.hc.client5.http.examples;

import java.net.InetAddress;

import org.apache.hc.client5.http.ProxyStatus;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.support.ProxyStatusSupport;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * Demonstrates processing the Proxy-Status response field defined by RFC.
 */
public final class ClientProxyStatus {

    private ClientProxyStatus() {
    }

    public static void main(final String[] args) throws Exception {

        // Emulates the intermediary.
        final HttpServer proxy = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("*", new ProxyHandler())
                .create();

        proxy.start();

        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {

            final HttpGet request = new HttpGet(
                    "http://localhost:" + proxy.getLocalPort() + "/");

            httpclient.execute(request, response -> {

                System.out.println(new StatusLine(response));

                for (final Header header : response.getHeaders("Proxy-Status")) {
                    for (final ProxyStatus status :
                            ProxyStatusSupport.parse(header.getValue())) {

                        System.out.println("Proxy: " + status.getName());
                        System.out.println("Error: " + status.getError());
                    }
                }

                return null;
            });

        } finally {
            proxy.close(CloseMode.GRACEFUL);
        }
    }

    private static final class ProxyHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) {

            // HTTP/1.1 504 Gateway Timeout
            // Proxy-Status: ExampleCDN; error=connection_timeout

            response.setCode(HttpStatus.SC_GATEWAY_TIMEOUT);
            response.addHeader(
                    "Proxy-Status",
                    "ExampleCDN; error=connection_timeout");
        }
    }

}