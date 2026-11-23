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

package org.apache.hc.client5.http.ssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the SPKI pinning bypass fixed by restricting
 * {@link SpkiPinningClientTlsStrategy} to the peer's leaf certificate: a real TLS
 * handshake (no fake session, no mocks) against a server whose presented chain
 * contains a certificate unrelated to the one that signed the handshake.
 */
class SpkiPinningRealHandshakeTest {

    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    // A literal IP address resolves to exactly one address: using a hostname like
    // "localhost" here would let DefaultHttpClientConnectionOperator silently retry
    // the connection against a second resolved address (e.g. the IPv6 loopback) after
    // the first attempt's pinning SSLException, hanging on this single-shot server.
    private static final String TARGET_HOST = "127.0.0.1";

    @Test
    void pinOnAppendedUnrelatedCertificateIsRejectedOverRealHandshake(@TempDir final Path tempDir) throws Exception {
        final KeyPairAndCert leaf = generateSelfSigned(tempDir, "leaf", "CN=" + TARGET_HOST, "ip:" + TARGET_HOST);
        final KeyPairAndCert other = generateSelfSigned(tempDir, "other", "CN=unrelated", null);

        // Server presents its real leaf certificate first, but also sends a second,
        // unrelated certificate it holds no private key for, exactly as a server already
        // trusted for the host could append the certificate it wants a pin to match.
        final SSLContext serverContext = buildServerContext(leaf.privateKey, leaf.certificate, other.certificate);
        // Client trusts the leaf certificate directly (it is the one actually signing the handshake).
        final SSLContext clientContext = buildClientContext(leaf.certificate);

        try (TestTlsServer server = new TestTlsServer(serverContext)) {
            final SpkiPinningClientTlsStrategy pinOnUnrelatedCert = SpkiPinningClientTlsStrategy
                    .newBuilder(clientContext)
                    .add(TARGET_HOST, sha256Pin(other.certificate))
                    .build();

            final Exception failure = assertThrows(Exception.class,
                    () -> get(pinOnUnrelatedCert, server.getPort()));
            assertNotNull(findSslPinningFailure(failure),
                    "expected an SSLException reporting a pinning failure, got: " + failure);
        }
    }

    @Test
    void pinOnLeafCertificateIsAcceptedOverRealHandshake(@TempDir final Path tempDir) throws Exception {
        final KeyPairAndCert leaf = generateSelfSigned(tempDir, "leaf", "CN=" + TARGET_HOST, "ip:" + TARGET_HOST);
        final KeyPairAndCert other = generateSelfSigned(tempDir, "other", "CN=unrelated", null);

        final SSLContext serverContext = buildServerContext(leaf.privateKey, leaf.certificate, other.certificate);
        final SSLContext clientContext = buildClientContext(leaf.certificate);

        try (TestTlsServer server = new TestTlsServer(serverContext)) {
            final SpkiPinningClientTlsStrategy pinOnLeaf = SpkiPinningClientTlsStrategy
                    .newBuilder(clientContext)
                    .add(TARGET_HOST, sha256Pin(leaf.certificate))
                    .build();

            assertEquals(200, get(pinOnLeaf, server.getPort()));
        }
    }

    private static int get(final SpkiPinningClientTlsStrategy tlsStrategy, final int port) throws IOException {
        final PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsStrategy)
                .build();
        // Disable automatic retries: this one-shot test server services a single
        // connection, and a retry after the pinning SSLException would hang waiting
        // for a second accept() that never comes.
        try (CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .disableAutomaticRetries()
                .build()) {
            final HttpGet get = new HttpGet("https://" + TARGET_HOST + ":" + port + "/");
            return client.execute(get, response -> response.getCode());
        }
    }

    private static SSLException findSslPinningFailure(final Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof SSLException && cur.getMessage() != null
                    && cur.getMessage().contains("SPKI pinning failure")) {
                return (SSLException) cur;
            }
        }
        return null;
    }

    private static String sha256Pin(final X509Certificate cert) throws Exception {
        final byte[] spki = cert.getPublicKey().getEncoded();
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
        return "sha256/" + Base64.getEncoder().encodeToString(digest);
    }

    private static SSLContext buildServerContext(final PrivateKey leafKey, final X509Certificate leafCert,
                                                  final X509Certificate otherCert) throws Exception {
        // JKS, unlike PKCS12, does not validate that a stored chain is internally
        // consistent on setKeyEntry: this mirrors a real server that is free to send
        // any chain it chooses, regardless of whether it holds every private key in it.
        final KeyStore serverKeyStore = KeyStore.getInstance("JKS");
        serverKeyStore.load(null, null);
        serverKeyStore.setKeyEntry("server", leafKey, STORE_PASSWORD, new Certificate[]{leafCert, otherCert});

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, STORE_PASSWORD);

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    private static SSLContext buildClientContext(final X509Certificate trustedLeaf) throws Exception {
        final KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("leaf", trustedLeaf);

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;
    }

    private static final class KeyPairAndCert {
        final PrivateKey privateKey;
        final X509Certificate certificate;

        KeyPairAndCert(final PrivateKey privateKey, final X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }

    private static KeyPairAndCert generateSelfSigned(final Path tempDir, final String alias, final String dname,
                                                      final String sanValue) throws Exception {
        final Path store = tempDir.resolve(alias + ".p12");
        final List<String> cmd = new ArrayList<>(Arrays.asList(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool",
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-dname", dname,
                "-keystore", store.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(STORE_PASSWORD),
                "-keypass", new String(STORE_PASSWORD)));
        if (sanValue != null) {
            cmd.add("-ext");
            cmd.add("san=" + sanValue);
        }
        final Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        final String output = readAll(process.getInputStream());
        final int exit = process.waitFor();
        if (exit != 0) {
            fail("keytool failed (" + exit + "): " + output);
        }

        final KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            ks.load(in, STORE_PASSWORD);
        }
        final PrivateKey key = (PrivateKey) ks.getKey(alias, STORE_PASSWORD);
        final X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        return new KeyPairAndCert(key, cert);
    }

    private static String readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Minimal single-shot HTTPS server: accepts one connection, completes the TLS
     * handshake with the configured {@link SSLContext}, discards the request, and
     * returns an empty 200 response. If the client closes the connection during
     * post-handshake verification (as happens on a pinning failure), the pending
     * read simply unblocks with end-of-stream.
     */
    private static final class TestTlsServer implements Closeable {
        private final SSLServerSocket serverSocket;
        private final Thread thread;

        TestTlsServer(final SSLContext sslContext) throws IOException {
            final SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            // Bind the wildcard address, not a specific loopback address: "localhost" can
            // resolve to either the IPv4 or the IPv6 loopback depending on the platform, and
            // this must accept the handshake whichever one the client actually connects on.
            this.serverSocket = (SSLServerSocket) ssf.createServerSocket(0, 50);
            this.thread = new Thread(this::run, "spki-pinning-test-tls-server");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void run() {
            try (Socket socket = serverSocket.accept()) {
                final InputStream in = socket.getInputStream();
                final byte[] buf = new byte[1024];
                int total = 0;
                // Read until the end of the request headers (blank line) or EOF.
                while (total < buf.length) {
                    final int n = in.read(buf, total, buf.length - total);
                    if (n == -1) {
                        return;
                    }
                    total += n;
                    if (indexOfBlankLine(buf, total) >= 0) {
                        break;
                    }
                }
                final OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 0\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (final IOException ignore) {
                // Expected when the client aborts the handshake (pinning failure case).
            }
        }

        private static int indexOfBlankLine(final byte[] buf, final int len) {
            for (int i = 0; i + 3 < len; i++) {
                if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
