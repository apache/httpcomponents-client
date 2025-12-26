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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * Async client/server demo with Brotli in both directions:
 * <p>
 * - Client sends a Brotli-compressed request body (Content-Encoding: br)
 * - Server decompresses request, then responds with a Brotli-compressed body
 * - Client checks the response Content-Encoding and decompresses if needed
 * <p>
 * Notes:
 * - Encoding uses brotli4j (native JNI); make sure matching native dependency is on the runtime classpath.
 * - Decoding here uses Commons Compress via CompressorStreamFactory("br").
 */
public final class AsyncClientServerBrotliRoundTrip {

    static {
        Brotli4jLoader.ensureAvailability();
    }

    private static final String BR = "br";

    public static void main(final String[] args) throws Exception {
        final HttpServer server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("/echo", new EchoHandler())
                .create();
        server.start();
        final int port = server.getLocalPort();
        final String url = "http://localhost:" + port + "/echo";

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final String requestBody = "Hello Brotli world (round-trip)!";
            System.out.println("Request (plain): " + requestBody);

            // --- client compresses request ---
            final byte[] reqCompressed = brotliCompress(requestBody.getBytes(StandardCharsets.UTF_8));

            final SimpleHttpRequest post = SimpleRequestBuilder.post(url)
                    .setHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                    .setHeader(HttpHeaders.CONTENT_ENCODING, BR)
                    .build();

            final Future<Message<HttpResponse, byte[]>> f = client.execute(
                    new BasicRequestProducer(post,
                            new BasicAsyncEntityProducer(reqCompressed, ContentType.APPLICATION_OCTET_STREAM)),
                    new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()),
                    null);

            final Message<HttpResponse, byte[]> msg = f.get();
            final HttpResponse head = msg.getHead();
            final byte[] respBodyRaw = msg.getBody() != null ? msg.getBody() : new byte[0];

            System.out.println("Status           : " + head.getCode());
            final Header ce = head.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
            final boolean isBr = ce != null && BR.equalsIgnoreCase(ce.getValue());
            System.out.println("Response C-E     : " + (isBr ? BR : "(none)"));

            final byte[] respPlain = isBr ? brotliDecompress(respBodyRaw) : respBodyRaw;
            System.out.println("Response (plain) : " + new String(respPlain, StandardCharsets.UTF_8));
        } finally {
            server.close(CloseMode.GRACEFUL);
        }
    }

    /**
     * Server handler:
     * - If request has Content-Encoding: br, decompress it
     * - Echo the text back, but re-encode the response with Brotli (Content-Encoding: br)
     */
    private static final class EchoHandler implements HttpRequestHandler {
        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws IOException {

            final HttpEntity entity = request.getEntity();
            if (entity == null) {
                response.setCode(HttpStatus.SC_BAD_REQUEST);
                response.setEntity(new StringEntity("Missing request body", StandardCharsets.UTF_8));
                return;
            }

            try {
                final byte[] requestPlain;
                final Header ce = request.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
                if (ce != null && BR.equalsIgnoreCase(ce.getValue())) {
                    try (final InputStream in = entity.getContent();
                         final CompressorInputStream bin =
                                 new CompressorStreamFactory().createCompressorInputStream(BR, in)) {
                        requestPlain = readAll(bin);
                    }
                } else {
                    try (final InputStream in = entity.getContent()) {
                        requestPlain = readAll(in);
                    }
                }

                final String echoed = new String(requestPlain, StandardCharsets.UTF_8);

                // --- server compresses response with Brotli ---
                final byte[] respCompressed = brotliCompress(echoed.getBytes(StandardCharsets.UTF_8));
                response.setCode(HttpStatus.SC_OK);
                response.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
                response.addHeader(HttpHeaders.CONTENT_ENCODING, BR);
                response.setEntity(new ByteArrayEntity(respCompressed, ContentType.APPLICATION_OCTET_STREAM));

            } catch (final CompressorException ex) {
                response.setCode(HttpStatus.SC_BAD_REQUEST);
                response.setEntity(new StringEntity("Invalid Brotli payload", StandardCharsets.UTF_8));
            } catch (final Exception ex) {
                response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.setEntity(new StringEntity("Server error", StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Utility: read entire stream into a byte[] (demo-only).
     */
    private static byte[] readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /**
     * Compress a byte[] with Brotli using brotli4j.
     */
    private static byte[] brotliCompress(final byte[] plain) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BrotliOutputStream out = new BrotliOutputStream(baos)) {
            out.write(plain);
        }
        return baos.toByteArray();
    }

    /**
     * Decompress a Brotli-compressed byte[] using Commons Compress.
     */
    private static byte[] brotliDecompress(final byte[] compressed) throws IOException {
        try (final InputStream in = new ByteArrayInputStream(compressed);
             final CompressorInputStream bin = new CompressorStreamFactory().createCompressorInputStream(BR, in)) {
            return readAll(bin);
        } catch (final CompressorException e) {
            throw new IOException("Failed to decompress Brotli data", e);
        }
    }
}
