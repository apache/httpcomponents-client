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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
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
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * Example: end-to-end Zstandard (zstd) response decompression with the async client.
 *
 * <p>This example starts a tiny {@code HttpCore 5} classic server that <em>always</em> replies
 * with {@code Content-Encoding: zstd}. The async client is the regular
 * {@link org.apache.hc.client5.http.impl.async.HttpAsyncClients#createDefault() default} client.
 * When the response carries the {@code zstd} content-coding, the execution chain
 * (see {@code ContentCompressionAsyncExec}) transparently installs
 * {@link org.apache.hc.client5.http.async.methods.InflatingZstdDataConsumer} and delivers
 * decoded bytes to the application entity consumer. As a consequence the client removes
 * {@code Content-Encoding}, {@code Content-Length} and {@code Content-MD5} headers and the
 * application sees plain text.</p>
 *
 * <p>The embedded server side in this example uses Apache Commons Compress only to produce
 * deterministic zstd payloads for demonstration and tests; the client side does not depend
 * on it and relies solely on HttpComponents.</p>
 *
 * <p><strong>What this demonstrates</strong></p>
 * <ul>
 *   <li>How to validate transparent zstd decoding without hitting external endpoints.</li>
 *   <li>That the async client strips {@code Content-Encoding} on success.</li>
 *   <li>No special client wiring is required beyond enabling content compression (on by default).</li>
 * </ul>
 *
 * <p><strong>Expected output</strong></p>
 * <pre>{@code
 * status=200
 * content-encoding=(stripped by client)
 * body:
 * {...decoded JSON/text...}
 * }</pre>
 *
 * @since 5.6
 */
public final class AsyncClientServerZstdExample {

    public static void main(final String[] args) throws Exception {
        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("/echo", new EchoHandler())
                .create();
        server.start();
        final int port = server.getLocalPort();
        final String url = "http://localhost:" + port + "/echo";

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final String requestBody = "Hello Zstandard world!";
            final byte[] reqCompressed = zstdCompress(requestBody.getBytes(StandardCharsets.UTF_8));

            final SimpleHttpRequest post = SimpleRequestBuilder
                    .post(url)
                    .setHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                    .setHeader(HttpHeaders.CONTENT_ENCODING, ContentCoding.ZSTD.token())
                    .build();

            final Future<Message<HttpResponse, String>> f = client.execute(
                    new BasicRequestProducer(post,
                            new BasicAsyncEntityProducer(reqCompressed, ContentType.APPLICATION_OCTET_STREAM)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    null);

            final Message<HttpResponse, String> msg = f.get();
            System.out.println("Status  : " + msg.getHead().getCode());
            System.out.println("C-E     : " + (msg.getHead().getFirstHeader("Content-Encoding") != null
                    ? msg.getHead().getFirstHeader("Content-Encoding").getValue()
                    : "(stripped by client)"));
            System.out.println("Response: " + msg.getBody());
        } finally {
            server.close(CloseMode.GRACEFUL);
        }
    }

    /**
     * Classic echo: decode request C-E:zstd, respond with zstd too.
     */
    private static final class EchoHandler implements HttpRequestHandler {
        @Override
        public void handle(final ClassicHttpRequest request,
                           final ClassicHttpResponse response,
                           final HttpContext context) throws IOException {
            try {
                final HttpEntity entity = request.getEntity();
                final byte[] data;
                if (entity != null && ContentCoding.ZSTD.token().equalsIgnoreCase(getCE(entity))) {
                    try (final InputStream in = entity.getContent();
                         final CompressorInputStream zin = new CompressorStreamFactory()
                                 .createCompressorInputStream(ContentCoding.ZSTD.token(), in)) {
                        data = readAll(zin);
                    } catch (final CompressorException e) {
                        response.setCode(HttpStatus.SC_BAD_REQUEST);
                        response.setEntity(new StringEntity("Bad zstd request", StandardCharsets.UTF_8));
                        return;
                    }
                } else {
                    data = entity != null ? readAll(entity.getContent()) : new byte[0];
                }

                final byte[] z = zstdCompress(data);
                response.setCode(HttpStatus.SC_OK);
                response.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
                response.addHeader(HttpHeaders.CONTENT_ENCODING, ContentCoding.ZSTD.token());
                response.setEntity(new ByteArrayEntity(z, ContentType.APPLICATION_OCTET_STREAM));
            } catch (final Exception ex) {
                response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.setEntity(new StringEntity("Server error", StandardCharsets.UTF_8));
            }
        }

        private static String getCE(final HttpEntity entity) {
            return entity.getContentEncoding();
        }

        private static byte[] readAll(final InputStream in) throws IOException {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            final byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toByteArray();
        }
    }

    private static byte[] zstdCompress(final byte[] plain) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final CompressorOutputStream out = new CompressorStreamFactory()
                .createCompressorOutputStream(ContentCoding.ZSTD.token(), baos)) {
            out.write(plain);
        } catch (final CompressorException e) {
            throw new IOException("zstd compressor not available", e);
        }
        return baos.toByteArray();
    }
}
