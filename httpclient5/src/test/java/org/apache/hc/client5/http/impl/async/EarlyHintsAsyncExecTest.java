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
package org.apache.hc.client5.http.impl.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.EarlyHintsListener;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
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
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


class EarlyHintsAsyncExecTest {

    private HttpAsyncServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
     void async_client_receives_103_early_hints_and_final_200() throws Exception {
        server = AsyncServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(5))
                        .build())
                .register("/eh", () -> new AsyncServerExchangeHandler() {

                    private final byte[] body = "OK".getBytes(StandardCharsets.US_ASCII);
                    private volatile boolean sentBody;

                    @Override
                    public void handleRequest(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final ResponseChannel channel,
                            final HttpContext context) throws HttpException {
                        // Send 103 Early Hints
                        final BasicHttpResponse hints = new BasicHttpResponse(HttpStatus.SC_EARLY_HINTS);
                        hints.addHeader("Link", "</style.css>; rel=preload; as=style");
                        hints.addHeader("Link", "</script.js>; rel=preload; as=script");
                        try {
                            channel.sendInformation(hints, context);
                        } catch (final Exception ex) {
                            throw new HttpException(ex.getMessage(), ex);
                        }

                        // Send final 200 response head; body via produce()
                        final BasicHttpResponse ok = new BasicHttpResponse(HttpStatus.SC_OK);
                        ok.addHeader("Content-Type", ContentType.TEXT_PLAIN.toString());
                        final BasicEntityDetails details =
                                new BasicEntityDetails(body.length, ContentType.TEXT_PLAIN);
                        try {
                            channel.sendResponse(ok, details, context);
                        } catch (final Exception ex) {
                            throw new HttpException(ex.getMessage(), ex);
                        }
                    }

                    @Override
                    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                        capacityChannel.update(Integer.MAX_VALUE);
                    }

                    @Override
                    public void consume(final ByteBuffer src) { /* no-op */ }

                    @Override
                    public void streamEnd(final List<? extends Header> trailers) { /* no-op */ }

                    // ---- AsyncDataProducer
                    @Override
                    public void produce(final DataStreamChannel channel) throws IOException {
                        if (!sentBody) {
                            channel.write(ByteBuffer.wrap(body));
                            channel.endStream();
                            sentBody = true;
                        }
                    }

                    @Override
                    public int available() {
                        return sentBody ? 0 : body.length;
                    }

                    @Override
                    public void failed(final Exception cause) { /* no-op for test */ }

                    @Override
                    public void releaseResources() { /* no-op for test */ }
                })
                .create();

        server.start();

        // Bind to ephemeral port and retrieve it from the listener endpoint
        final Future<ListenerEndpoint> lf = server.listen(new InetSocketAddress(0), URIScheme.HTTP);
        final ListenerEndpoint ep = lf.get(5, TimeUnit.SECONDS);
        final int port = ((InetSocketAddress) ep.getAddress()).getPort();

        final AtomicInteger hintsCount = new AtomicInteger();
        final AtomicReference<List<String>> linkHeaders = new AtomicReference<>(new ArrayList<>());

        final EarlyHintsListener listener = (hints, ctx) -> {
            if (hints.getCode() == HttpStatus.SC_EARLY_HINTS) {
                hintsCount.incrementAndGet();
                final Header[] hs = hints.getHeaders("Link");
                final ArrayList<String> vals = new ArrayList<String>(hs.length);
                for (final Header h : hs) {
                    vals.add(h.getValue());
                }
                linkHeaders.set(vals);
            }
        };

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setEarlyHintsListener(listener)
                .build()) {

            client.start();

            final SimpleHttpResponse resp = client.execute(
                    SimpleRequestBuilder.get("http://localhost:" + port + "/eh").build(),
                    null).get(5, TimeUnit.SECONDS);

            assertEquals(HttpStatus.SC_OK, resp.getCode(), "Final response must be 200");
            assertEquals("OK", resp.getBodyText());
        }

        assertEquals(1, hintsCount.get(), "Expected exactly one 103 Early Hints callback");
        final List<String> links = linkHeaders.get();
        boolean hasCss = false, hasJs = false;
        for (final String v : links) {
            if (v.contains("</style.css>")) {
                hasCss = true;
            }
            if (v.contains("</script.js>")) {
                hasJs = true;
            }
        }
        assertTrue(hasCss, "Missing style preload link");
        assertTrue(hasJs, "Missing script preload link");
    }
}
