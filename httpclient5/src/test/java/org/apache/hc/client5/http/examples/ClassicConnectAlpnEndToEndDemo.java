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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/**
 * Example: advertise ALPN on a proxy {@code CONNECT}.
 *
 * <p>This demo starts:
 * <ul>
 *   <li>a tiny classic origin server on {@code localhost},</li>
 *   <li>a minimal blocking proxy that prints the received {@code ALPN} header,</li>
 *   <li>a classic client forced to tunnel via {@code CONNECT} and configured with
 *       {@code .setConnectAlpn("h2","http/1.1")}.</li>
 * </ul>
 * The proxy logs a line like {@code ALPN: h2, http%2F1.1}, and the client receives {@code 200 OK}.
 *
 * <p><b>Tip:</b> Keep the request host consistent with the server’s canonical host to avoid
 * {@code 421 Misdirected Request}. This example uses {@code localhost} for both.</p>
 *
 * @since 5.6
 */
public final class ClassicConnectAlpnEndToEndDemo {

    public static void main(final String[] args) throws Exception {
        // ---- Origin server (classic)
        final HttpServer origin = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setCanonicalHostName("localhost")
                .register("/hello", new HelloHandler())
                .create();
        origin.start();
        final int originPort = origin.getLocalPort();

        // ---- Tiny blocking proxy printing ALPN and tunneling bytes
        final ServerSocket proxyServer = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        final int proxyPort = proxyServer.getLocalPort();
        final ExecutorService proxyPool = Executors.newCachedThreadPool();
        proxyPool.submit(() -> {
            try {
                while (!proxyServer.isClosed()) {
                    final Socket clientSock = proxyServer.accept(); // blocks
                    proxyPool.submit(() -> handleConnectClient(clientSock));
                }
            } catch (final java.net.SocketException closed) {
                // server socket closed while blocking in accept() -> exit quietly
                if (!proxyServer.isClosed()) {
                    System.out.println("[proxy] accept error: " + closed);
                }
            } catch (final Exception ex) {
                System.out.println("[proxy] error: " + ex);
            }
            return null;
        });


        // ---- Client forcing CONNECT even for HTTP (so the demo stays TLS-free)
        final HttpRoutePlanner alwaysTunnelPlanner = (target, context) -> new HttpRoute(
                target,
                null,
                new HttpHost("127.0.0.1", proxyPort),
                false,
                RouteInfo.TunnelType.TUNNELLED,
                RouteInfo.LayerType.PLAIN);

        final RequestConfig reqCfg = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(10))
                .build();

        try (final CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(reqCfg)
                .setRoutePlanner(alwaysTunnelPlanner)
                // Advertise ALPN on CONNECT
                .setConnectAlpn("h2", "http/1.1")
                .build()) {

            final String url = "http://localhost:" + originPort + "/hello";
            final HttpGet get = new HttpGet(url);

            final HttpClientResponseHandler<String> handler = response -> {
                System.out.println("[client] " + new StatusLine(response));
                final int code = response.getCode();
                if (code >= 200 && code < 300) {
                    return EntityUtils.toString(response.getEntity());
                }
                throw new IOException("Unexpected response code " + code);
            };

            final String body = client.execute(get, handler);
            System.out.println("[client] body: " + body);
        } finally {
            origin.close(CloseMode.GRACEFUL);
            proxyServer.close();                 // triggers SocketException in accept()
            proxyPool.shutdown();                // let workers finish naturally
            proxyPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    // ---- Minimal CONNECT proxy (blocking) ----
    private static void handleConnectClient(final Socket client) {
        try (final Socket clientSock = client;
             final BufferedReader in = new BufferedReader(new InputStreamReader(clientSock.getInputStream(), StandardCharsets.ISO_8859_1));
             final OutputStream out = clientSock.getOutputStream()) {

            final String requestLine = in.readLine();
            if (requestLine == null || !requestLine.toUpperCase(Locale.ROOT).startsWith("CONNECT ")) {
                writeSimple(out, "HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n");
                return;
            }
            final String hostPort = requestLine.split("\\s+")[1];
            String alpnHeader = null;

            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                final int idx = line.indexOf(':');
                if (idx > 0) {
                    final String name = line.substring(0, idx).trim();
                    final String value = line.substring(idx + 1).trim();
                    if ("ALPN".equalsIgnoreCase(name)) {
                        alpnHeader = value;
                    }
                }
            }

            System.out.println("[proxy] CONNECT " + hostPort);
            System.out.println("[proxy] ALPN: " + (alpnHeader != null ? alpnHeader : "<none>"));

            final String[] hp = hostPort.split(":");
            final String host = hp[0];
            final int port = Integer.parseInt(hp[1]);

            final Socket origin = new Socket();
            origin.connect(new InetSocketAddress(host, port), 3000);

            writeSimple(out, "HTTP/1.1 200 Connection Established\r\n\r\n");

            final InputStream clientIn = clientSock.getInputStream();
            final OutputStream clientOut = clientSock.getOutputStream();
            final InputStream originIn = origin.getInputStream();
            final OutputStream originOut = origin.getOutputStream();

            final Thread t1 = new Thread(() -> pump(clientIn, originOut));
            final Thread t2 = new Thread(() -> pump(originIn, clientOut));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            origin.close();

        } catch (final Exception ex) {
            System.out.println("[proxy] error: " + ex);
        }
    }

    private static void writeSimple(final OutputStream out, final String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static void pump(final InputStream in, final OutputStream out) {
        final byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (final IOException ignore) {
        }
        try {
            out.flush();
        } catch (final IOException ignore) {
        }
    }

    private static final class HelloHandler implements HttpRequestHandler {
        @Override
        public void handle(final ClassicHttpRequest request,
                           final ClassicHttpResponse response,
                           final HttpContext context) {
            response.setCode(200);
            response.setEntity(new StringEntity("Hello through the tunnel!", ContentType.TEXT_PLAIN));
        }
    }
}
