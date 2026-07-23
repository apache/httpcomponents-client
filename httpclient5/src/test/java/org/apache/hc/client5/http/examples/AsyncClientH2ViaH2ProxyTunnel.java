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

import java.io.File;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * Full example of pure HTTP/2 client execution through an HTTP/2 proxy tunnel.
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 *   <li>Proxy endpoint speaks HTTP/2.</li>
 *   <li>Proxy supports CONNECT for the requested target.</li>
 *   <li>Target endpoint supports HTTP/2.</li>
 * </ul>
 *
 * <p>
 * This example configures a tunneled and layered route:
 * {@code client -> (h2) proxy -> CONNECT tunnel -> TLS -> (h2) target}.
 * </p>
 */
public class AsyncClientH2ViaH2ProxyTunnel {

    private static TlsStrategy createTlsStrategy() throws Exception {
        final String trustStore = System.getProperty("h2.truststore");
        if (trustStore == null || trustStore.isEmpty()) {
            return new H2ClientTlsStrategy();
        }
        final String trustStorePassword = System.getProperty("h2.truststore.password", "changeit");
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(new File(trustStore), trustStorePassword.toCharArray())
                .build();
        return new H2ClientTlsStrategy(sslContext);
    }

    public static void main(final String[] args) throws Exception {
        final String proxyScheme = System.getProperty("h2.proxy.scheme", "http");
        final String proxyHost = System.getProperty("h2.proxy.host", "localhost");
        final int proxyPort = Integer.parseInt(System.getProperty("h2.proxy.port", "8080"));
        final String targetScheme = System.getProperty("h2.target.scheme", "https");
        final String targetHost = System.getProperty("h2.target.host", "origin");
        final int targetPort = Integer.parseInt(System.getProperty("h2.target.port", "9443"));
        final String[] requestUris = System.getProperty("h2.paths", "/").split(",");

        final HttpHost proxy = new HttpHost(proxyScheme, proxyHost, proxyPort);
        final HttpHost target = new HttpHost(targetScheme, targetHost, targetPort);

        final HttpRoutePlanner routePlanner = (final HttpHost routeTarget, final org.apache.hc.core5.http.protocol.HttpContext context) ->
                new HttpRoute(routeTarget, null, proxy, URIScheme.HTTPS.same(routeTarget.getSchemeName()));
        final TlsStrategy tlsStrategy = createTlsStrategy();

        try (CloseableHttpAsyncClient client = H2AsyncClientBuilder.create()
                .setRoutePlanner(routePlanner)
                .setTlsStrategy(tlsStrategy)
                .build()) {

            client.start();

            final CountDownLatch latch = new CountDownLatch(requestUris.length);

            for (final String requestUri : requestUris) {
                final String normalizedRequestUri = requestUri.trim();
                final SimpleHttpRequest request = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath(normalizedRequestUri)
                        .build();
                final HttpClientContext clientContext = HttpClientContext.create();

                client.execute(
                        SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(),
                        clientContext,
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse response) {
                                latch.countDown();
                                System.out.println(request + " -> " + new StatusLine(response));
                                System.out.println("Protocol: " + clientContext.getProtocolVersion());
                                System.out.println(response.getBodyText());
                            }

                            @Override
                            public void failed(final Exception ex) {
                                latch.countDown();
                                System.out.println(request + " -> " + ex);
                            }

                            @Override
                            public void cancelled() {
                                latch.countDown();
                                System.out.println(request + " cancelled");
                            }

                        });
            }

            latch.await();
            client.close(CloseMode.GRACEFUL);
        }
    }
}
