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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.client5.http.async.methods.DeflatingZstdEntityProducer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * Example: streaming a request body compressed with Zstandard (zstd) using the async client.
 *
 * <p>This example runs entirely with HttpComponents. It starts a tiny classic
 * {@code HttpCore 5} server on an ephemeral port that <em>decodes</em> requests carrying
 * {@code Content-Encoding: zstd} (via Apache Commons Compress) and echoes the plain text.
 * The async client builds the request body with
 * {@link org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer} and wraps it in
 * {@link org.apache.hc.client5.http.async.methods.DeflatingZstdEntityProducer} so the payload
 * is compressed <strong>on the fly</strong> as it is streamed to the wire (no {@code InputStream}
 * in the client pipeline).</p>
 *
 * <p><strong>What it demonstrates</strong></p>
 * <ul>
 *   <li>How to send a POST request whose entity is compressed with <b>zstd</b> while streaming.</li>
 *   <li>How to advertise the request coding with {@code Content-Encoding: zstd}.</li>
 *   <li>Chunked transfer with proper back-pressure handling on the client side.</li>
 * </ul>
 *
 * <p><strong>Outline</strong></p>
 * <ol>
 *   <li>Start an embedded classic server that accepts {@code /echo} and, if present,
 *       decodes {@code Content-Encoding: zstd} before reading the text; it replies with
 *       plain text.</li>
 *   <li>Create the default async client.</li>
 *   <li>Wrap the plain entity in {@link org.apache.hc.client5.http.async.methods.DeflatingZstdEntityProducer}
 *       and send it with {@link org.apache.hc.core5.http.nio.support.BasicRequestProducer}.</li>
 *   <li>Read the echoed response as a string (normal, uncompressed text).</li>
 * </ol>
 *
 * <p><strong>Expected output</strong></p>
 * <pre>{@code
 * Request : Hello Zstandard request body!
 * Status  : 200
 * Response: echo: Hello Zstandard request body!
 * }</pre>
 *
 * <p><strong>Notes</strong></p>
 * <ul>
 *   <li>The <em>client</em> path is fully NIO and ByteBuffer-based; no {@code InputStream} is used.</li>
 *   <li>The embedded server in this example uses Apache Commons Compress <em>only</em> to decode
 *       incoming zstd for demonstration; production servers may decode differently.</li>
 *   <li>Ensure {@code com.github.luben:zstd-jni} is on the runtime classpath for the client.</li>
 * </ul>
 *
 * @since 5.6
 */

public final class AsyncClientZstdCompressionExample {

    public static void main(final String[] args) throws Exception {
        // --- tiny classic server that decodes zstd requests and echoes plain text ---
        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("/echo", new EchoHandler())
                .create();
        server.start();
        final int port = server.getLocalPort();
        final String url = "http://localhost:" + port + "/echo";

        try (CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final String payload = "Hello Zstandard request body!";
            System.out.println("Request : " + payload);

            final StringAsyncEntityProducer plain = new StringAsyncEntityProducer(payload, ContentType.TEXT_PLAIN);
            final DeflatingZstdEntityProducer zstd = new DeflatingZstdEntityProducer(plain);

            final SimpleHttpRequest post = SimpleRequestBuilder.post(url)
                    // header is optional; BasicRequestProducer will use producer metadata too
                    .setHeader(HttpHeaders.CONTENT_ENCODING, ContentCoding.ZSTD.token())
                    .setHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                    .build();

            final Future<Message<HttpResponse, String>> f = client.execute(
                    new BasicRequestProducer(post, zstd),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    null);

            final Message<HttpResponse, String> msg = f.get();
            System.out.println("Status  : " + msg.getHead().getCode());
            System.out.println("Response: " + msg.getBody());
        } finally {
            server.close(CloseMode.GRACEFUL);
        }
    }

    /**
     * Classic echo handler that decodes request Content-Encoding: zstd and returns plain text.
     */
    private static final class EchoHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws IOException {

            try (InputStream in = new CompressorStreamFactory()
                    .createCompressorInputStream(ContentCoding.ZSTD.token(), request.getEntity().getContent())) {

                final byte[] data = readAll(in);
                final String text = new String(data, StandardCharsets.UTF_8);

                response.setCode(HttpStatus.SC_OK);
                response.addHeader("Content-Encoding", ContentCoding.ZSTD.token());

                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (final OutputStream zstdOut = new CompressorStreamFactory()
                        .createCompressorOutputStream("zstd", baos)) {
                    zstdOut.write(text.getBytes(StandardCharsets.UTF_8));
                }
                response.setEntity(new ByteArrayEntity(baos.toByteArray(), ContentType.TEXT_PLAIN));
            } catch (final CompressorException ex) {
                response.setCode(HttpStatus.SC_BAD_REQUEST);
                response.setEntity(new StringEntity("Unable to process compressed payload", StandardCharsets.UTF_8));
            }
        }

        private static byte[] readAll(final InputStream in) throws IOException {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
