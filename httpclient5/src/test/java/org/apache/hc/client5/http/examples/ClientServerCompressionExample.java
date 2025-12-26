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
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * A minimal end-to-end example that shows how the new
 * {@code EntityBuilder.compressed(ContentCoding)} method (added in&nbsp;5.6)
 * can be used to send a request body compressed with Zstandard and to receive
 * a transparently decompressed response.
 * <p>
 * The code spins up an in-process {@link HttpServer} on a random port.  The
 * client builds a simple {@code POST /echo} request whose entity is
 * compressed by calling
 * {@code .compressed(ContentCoding.ZSTD)}.  The server handler decodes the
 * incoming body with Apache Commons Compress, echoes the text, re-encodes it
 * with Zstandard, sets {@code Content-Encoding: zstd} and returns it.  On the
 * way back, HttpClient detects the header, selects the matching decoder that
 * was registered automatically through {@code ContentDecoderRegistry} and
 * hands application code a plain {@code String}.
 * <p>
 * To run the example you need HttpClient 5.6-SNAPSHOT, Commons Compress
 * 1.21 or newer and its transitive dependency {@code zstd-jni}.  On JDK 17+
 * the first invocation of the Zstandard codec prints a warning about loading
 * native code; add {@code --enable-native-access=ALL-UNNAMED} if you prefer a
 * clean console.
 *
 * @since 5.6
 */
public final class ClientServerCompressionExample {

    private ClientServerCompressionExample() {
    }

    public static void main(final String[] args) throws Exception {
        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        final HttpServer server = ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .setListenerPort(port)
                .setCanonicalHostName("localhost")
                .register("/echo", new EchoHandler())
                .create();
        server.start();
        final int actualPort = server.getLocalPort();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String requestBody = "Hello Zstandard world!";
            System.out.println("Request : " + requestBody);

            final ClassicHttpRequest post = ClassicRequestBuilder
                    .post("http://localhost:" + actualPort + "/echo")
                    .setEntity(EntityBuilder.create()
                            .setText(requestBody)
                            .compressed(ContentCoding.ZSTD)
                            .build())
                    .build();

            final String reply = client.execute(
                    post,
                    rsp -> EntityUtils.toString(rsp.getEntity(), StandardCharsets.UTF_8));

            System.out.println("Response: " + reply);
        } finally {
            server.close(CloseMode.GRACEFUL);
        }
    }

    /**
     * Simple echo handler that decodes and re-encodes Zstandard bodies.
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
