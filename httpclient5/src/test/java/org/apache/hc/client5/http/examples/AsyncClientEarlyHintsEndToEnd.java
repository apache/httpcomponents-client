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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.EarlyHintsListener;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ListenerEndpoint;

/**
 * Minimal end-to-end demo for {@code 103 Early Hints} using the async client.
 *
 * <p>This example starts a tiny local async HTTP server that:
 * <ol>
 *   <li>sends a {@code 103} informational response with two {@code Link} headers, then</li>
 *   <li>completes the exchange with a final {@code 200 OK} and a short body.</li>
 * </ol>
 * The async client registers an Early Hints listener, prints any received {@code 103}
 * headers, and then prints the final status and body.</p>
 *
 * <p>Use this sample to see how to wire {@code setEarlyHintsListener(...)} and verify that
 * Early Hints do not interfere with normal response processing.</p>
 */

public class AsyncClientEarlyHintsEndToEnd {

    public static void main(final String[] args) throws Exception {
        // --- Start minimal async server that sends 103 then 200
        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/eh", () -> new AsyncServerExchangeHandler() {

                    private final byte[] body = "OK".getBytes(StandardCharsets.US_ASCII);
                    private volatile boolean sent;

                    @Override
                    public void handleRequest(final HttpRequest request,
                                              final EntityDetails entityDetails,
                                              final ResponseChannel channel,
                                              final HttpContext context)
                            throws HttpException, IOException {

                        // 103 Early Hints
                        final BasicHttpResponse hints = new BasicHttpResponse(HttpStatus.SC_EARLY_HINTS);
                        hints.addHeader("Link", "</style.css>; rel=preload; as=style");
                        hints.addHeader("Link", "</script.js>; rel=preload; as=script");
                        channel.sendInformation(hints, context);

                        // Final 200 (announce entity; body will be produced in produce())
                        final BasicHttpResponse ok = new BasicHttpResponse(HttpStatus.SC_OK);
                        ok.addHeader("Content-Type", ContentType.TEXT_PLAIN.toString());
                        final BasicEntityDetails details = new BasicEntityDetails(body.length, ContentType.TEXT_PLAIN);
                        channel.sendResponse(ok, details, context);
                    }

                    // ---- AsyncDataConsumer (request body not expected)
                    @Override
                    public void updateCapacity(final CapacityChannel ch) throws IOException {
                        ch.update(Integer.MAX_VALUE);
                    }

                    @Override
                    public void consume(final ByteBuffer src) { /* no-op */ }

                    @Override
                    public void streamEnd(final List<? extends Header> trailers) { /* no-op */ }

                    // ---- AsyncDataProducer (MUST implement both of these)
                    @Override
                    public void produce(final DataStreamChannel ch) throws IOException {
                        if (!sent) {
                            ch.write(java.nio.ByteBuffer.wrap(body));
                            ch.endStream();
                            sent = true;
                        }
                    }

                    @Override
                    public int available() {
                        return sent ? 0 : body.length;
                    }

                    @Override
                    public void failed(final Exception cause) { /* no-op for demo */ }

                    @Override
                    public void releaseResources() { /* no-op for demo */ }
                })
                .create();
        server.start();
        final Future<ListenerEndpoint> lf = server.listen(new InetSocketAddress(0), URIScheme.HTTP);
        final int port = ((InetSocketAddress) lf.get().getAddress()).getPort();

        // --- Async client with Early Hints listener
        final EarlyHintsListener hintsListener = (hints, ctx) -> {
            System.out.println("[client] Early Hints 103:");
            for (final Header h : hints.getHeaders("Link")) {
                System.out.println("  " + h.getValue());
            }
        };

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(
                        PoolingAsyncClientConnectionManagerBuilder.create()
                                .setDefaultTlsConfig(TlsConfig.DEFAULT) // plain HTTP here; keep TLS config for real targets
                                .build())
                .setEarlyHintsListener(hintsListener)
                .build()) {
            client.start();

            final SimpleHttpRequest req = SimpleRequestBuilder.get("http://localhost:" + port + "/eh").build();
            final SimpleHttpResponse resp = client.execute(req, null).get(5, TimeUnit.SECONDS);

            System.out.println("[client] final: " + resp.getCode() + " " + resp.getReasonPhrase());
            System.out.println("[client] body: " + resp.getBodyText());
        } finally {
            server.close(CloseMode.GRACEFUL);
        }
    }
}
