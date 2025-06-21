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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * <h2>End-to-end “Zstandard” demo for HttpClient&nbsp;5.6 +</h2>
 *
 * <p>This standalone {@code main()} program shows how the new
 * {@link org.apache.hc.client5.http.entity.EntityBuilder#compressed
 * EntityBuilder#compressed(ContentCoding)} API can be used to
 * transparently <strong>compress</strong> a request entity with
 * Zstandard (<em>zstd</em>) on the client side and then
 * <strong>decompress</strong> it on the server side—while the reverse
 * happens for the response.</p>
 *
 * <h3>What the code does</h3>
 * <ol>
 *   <li>Spins up a tiny in-JVM {@code com.sun.net.httpserver.HttpServer}
 *       bound to an ephemeral port.</li>
 *   <li>The client sends a {@code POST /echo} whose body is compressed
 *       with <em>zstd</em> simply by calling
 *       {@code compressed(ContentCoding.ZSTD)}.</li>
 *   <li>The server handler decodes the request body with Commons Compress
 *       ({@link org.apache.commons.compress.compressors.CompressorStreamFactory}),
 *       echoes the text back, re-encodes it with Zstandard, and sets
 *       {@code Content-Encoding: zstd}.</li>
 *   <li>The client receives the response—HttpClient 5.6+ notices the
 *       header, automatically picks the decoder you registered via
 *       {@link org.apache.hc.client5.http.entity.compress.ContentDecoderRegistry},
 *       and hands the caller the already-decompressed text.</li>
 * </ol>
 *
 * <h3>How to run</h3>
 * <ul>
 *   <li>Java 8-17 (only standard {@code com.sun.net.httpserver} API used).</li>
 *   <li>Maven dependencies (all <em>compile + runtime</em>):<br>
 *     ─ {@code httpclient5 ≥ 5.6-SNAPSHOT}<br>
 *     ─ {@code commons-compress ≥ 1.21}<br>
 *     ─ {@code com.github.luben:zstd-jni} (automatically pulled by
 *       Commons Compress for the native codec)
 *   </li>
 *   <li>JDK 17+ users: you will see a one-time warning that
 *       {@code zstd-jni} loads native code. Add<br>
 *       {@code --enable-native-access=ALL-UNNAMED} to the VM options
 *       if you want to silence it.</li>
 *   <li>Launch from your IDE or:
 *   <pre>{@code
 *   mvn -q exec:java \
 *       -Dexec.mainClass=org.apache.hc.client5.testing.classic.ZstdRoundTrip
 *   }</pre></li>
 * </ul>
 *
 * <h3>Why Zstandard and not GZIP?</h3>
 * GZIP support has always been built-in.  This demo illustrates the
 * new <em>pluggable</em> compression framework: as soon as
 * Commons Compress + the native helper JAR are on the class-path,
 * HttpClient discovers the codec and you can opt-in at the
 * <cite>EntityBuilder</cite> call-site—no other code changes needed.
 *
 * @since 5.6
 */
public final class ClientServerCompressionExample {

    private ClientServerCompressionExample() {
    }

    public static void main(final String[] args) throws Exception {

        /* ──────────────────────────────────────────────
           1. Tiny echo server that understands “br|zstd…”
           ────────────────────────────────────────────── */
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", new EchoHandler());
        server.start();
        final int port = server.getAddress().getPort();
        System.out.println("Server   : http://localhost:" + port + "/echo");

        /* ──────────────────────────────────────────────
           2. Build and send a Zstd-compressed POST
           ────────────────────────────────────────────── */
        try (final CloseableHttpClient client = HttpClients.createDefault()) {

            final HttpPost post = new HttpPost("http://localhost:" + port + "/echo");
            post.setEntity(EntityBuilder.create()
                    .setText("Hello Zstandard world!")
                    .compressed(ContentCoding.ZSTD)   // ← NEW API
                    .build());

            System.out.println("Client → : sending request …");

            final String reply = client.execute(post,
                    rsp -> EntityUtils.toString(rsp.getEntity(), StandardCharsets.UTF_8));

            System.out.println("Client ← : got reply  «" + reply + "»");
        } finally {
            server.stop(0);
        }
    }

    /* ──────────────────────────────────────────────
       Server handler: decode-echo-encode
       ────────────────────────────────────────────── */
    private static final class EchoHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange ex) {
            try {
                /* ----- decode body (if any) ----- */
                final InputStream in = new CompressorStreamFactory()
                        .createCompressorInputStream("zstd", ex.getRequestBody());

                final byte[] data = readAll(in);
                final String text = new String(data, StandardCharsets.UTF_8);

                System.out.println("Server   : received «" + text + "»");

                /* ----- encode response ----- */
                ex.getResponseHeaders().add("Content-Encoding", "zstd");
                ex.sendResponseHeaders(200, 0); // chunked
                final OutputStream raw = ex.getResponseBody();
                try (final OutputStream zstdOut = new CompressorStreamFactory()
                        .createCompressorOutputStream("zstd", raw)) {
                    zstdOut.write(text.getBytes(StandardCharsets.UTF_8));
                }
            } catch (final Exception ignored) {

            } finally {
                ex.close();
            }
        }

        private static byte[] readAll(final InputStream in) throws IOException {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            final byte[] tmp = new byte[8 * 1024];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
            return buf.toByteArray();
        }
    }
}
