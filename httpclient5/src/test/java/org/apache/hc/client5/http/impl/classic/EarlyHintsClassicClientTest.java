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
package org.apache.hc.client5.http.impl.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.EarlyHintsListener;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class EarlyHintsClassicClientTest {

    static final class OneShotEarlyHintsServer implements AutoCloseable {
        private final ServerSocket server;
        private final CountDownLatch started = new CountDownLatch(1);
        private final Thread thread;

        OneShotEarlyHintsServer() throws IOException {
            this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.thread = new Thread(this::serve, "early-hints-oneshot");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int getPort() {
            return server.getLocalPort();
        }

        private void serve() {
            started.countDown();
            try (final Socket sock = server.accept();
                 final BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
                 final BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())) {

                // Read and discard request headers to end-of-headers
                readToDoubleCRLF(in);

                // Write 103 Early Hints
                write(out, "HTTP/1.1 103 Early Hints\r\n");
                write(out, "Link: </style.css>; rel=preload; as=style\r\n");
                write(out, "Link: </script.js>; rel=preload; as=script\r\n");
                write(out, "\r\n");
                out.flush(); // ensure client sees the informational response

                // Write final 200 OK
                final byte[] body = "OK".getBytes(StandardCharsets.US_ASCII);
                write(out, "HTTP/1.1 200 OK\r\n");
                write(out, "Content-Length: " + body.length + "\r\n");
                write(out, "Content-Type: text/plain\r\n");
                write(out, "Connection: close\r\n");
                write(out, "\r\n");
                out.write(body);
                out.flush();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                try {
                    server.close();
                } catch (final IOException ignore) {
                }
            }
        }

        private static void readToDoubleCRLF(final BufferedInputStream in) throws IOException {
            int state = 0;
            while (true) {
                final int b = in.read();
                if (b == -1) break;
                // CRLF CRLF state machine
                switch (state) {
                    case 0:
                        state = (b == '\r') ? 1 : 0;
                        break;
                    case 1:
                        state = (b == '\n') ? 2 : 0;
                        break;
                    case 2:
                        state = (b == '\r') ? 3 : 0;
                        break;
                    case 3:
                        state = (b == '\n') ? 4 : 0;
                        break;
                    default:
                        break;
                }
                if (state == 4) break;
            }
        }

        private static void write(final BufferedOutputStream out, final String s) throws IOException {
            out.write(s.getBytes(StandardCharsets.US_ASCII));
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    @Test
    @Timeout(10)
    void classic_client_receives_103_and_final_200_from_raw_server() throws Exception {
        try (final OneShotEarlyHintsServer srv = new OneShotEarlyHintsServer()) {
            // Listener to capture 103
            final AtomicInteger hintsCount = new AtomicInteger();
            final AtomicReference<List<String>> linkHeaders = new AtomicReference<>(new ArrayList<>());

            final EarlyHintsListener listener = (hints, ctx) -> {
                if (hints.getCode() == HttpStatus.SC_EARLY_HINTS) {
                    hintsCount.incrementAndGet();
                    final Header[] links = hints.getHeaders("Link");
                    final ArrayList<String> vals = new ArrayList<>(links.length);
                    for (final Header h : links) {
                        vals.add(h.getValue());
                    }
                    linkHeaders.set(vals);
                }
            };

            try (final CloseableHttpClient client = HttpClientBuilder.create()
                    .setEarlyHintsListener(listener)
                    .build()) {

                final HttpHost target = new HttpHost("http", "127.0.0.1", srv.getPort());
                try (final CloseableHttpResponse resp = client.execute(target, new HttpGet("/eh"))) {
                    assertEquals(HttpStatus.SC_OK, resp.getCode(), "Final response must be 200");
                }
            }

            assertEquals(1, hintsCount.get(), "Expected exactly one Early Hints callback");
            final List<String> links = linkHeaders.get();
            assertTrue(links.stream().anyMatch(v -> v.contains("</style.css>")), "Missing style preload link");
            assertTrue(links.stream().anyMatch(v -> v.contains("</script.js>")), "Missing script preload link");
        }
    }
}
