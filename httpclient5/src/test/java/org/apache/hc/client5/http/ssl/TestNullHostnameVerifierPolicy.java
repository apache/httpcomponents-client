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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Handshakes against a server whose certificate is issued for {@code localhost} to make sure
 * that a strategy created without a {@link javax.net.ssl.HostnameVerifier} still verifies the host name.
 */
class TestNullHostnameVerifierPolicy {

    private static final char[] PASSWORD = "nopassword".toCharArray();

    private SSLContext sslContext;
    private SSLServerSocket serverSocket;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        sslContext = SSLContexts.custom()
                .loadTrustMaterial(getClass().getResource("/test.keystore"), PASSWORD)
                .loadKeyMaterial(getClass().getResource("/test.keystore"), PASSWORD, PASSWORD)
                .build();
        serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serverThread = new Thread(() -> {
            try (final SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.startHandshake();
            } catch (final IOException ignore) {
                // the client may abort the handshake
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        serverSocket.close();
        serverThread.join(5000);
    }

    private SSLSocket upgrade(final AbstractClientTlsStrategy strategy, final String target) throws IOException {
        final Socket socket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
        try {
            return strategy.upgrade(socket, target, serverSocket.getLocalPort(), null, null);
        } catch (final IOException | RuntimeException ex) {
            socket.close();
            throw ex;
        }
    }

    @SuppressWarnings("deprecation")
    private AbstractClientTlsStrategy defaultStrategy() {
        return new DefaultClientTlsStrategy(
                sslContext, null, null, SSLBufferMode.STATIC,
                (HostnameVerifier) null, (Factory<SSLEngine, TlsDetails>) null);
    }

    @SuppressWarnings("deprecation")
    private AbstractClientTlsStrategy conscryptStrategy() {
        return new ConscryptClientTlsStrategy(
                sslContext, null, null, SSLBufferMode.STATIC, (HostnameVerifier) null);
    }

    @Test
    void testDefaultStrategyRejectsMismatchingHost() {
        assertThrows(SSLException.class, () -> upgrade(defaultStrategy(), "example.invalid"));
    }

    @Test
    void testDefaultStrategyAcceptsMatchingHost() throws Exception {
        try (final SSLSocket socket = upgrade(defaultStrategy(), "localhost")) {
            assertNotNull(socket.getSession().getPeerCertificates());
        }
    }

    @Test
    void testConscryptStrategyRejectsMismatchingHost() {
        assertThrows(SSLException.class, () -> upgrade(conscryptStrategy(), "example.invalid"));
    }

    @Test
    void testConscryptStrategyAcceptsMatchingHost() throws Exception {
        try (final SSLSocket socket = upgrade(conscryptStrategy(), "localhost")) {
            assertNotNull(socket.getSession().getPeerCertificates());
        }
    }

    @Test
    void testConscryptDefaultConstructorRejectsMismatchingHost() {
        assertThrows(SSLException.class, () -> upgrade(new ConscryptClientTlsStrategy(sslContext), "example.invalid"));
    }

    @Test
    void testDefaultConstructorRejectsMismatchingHost() {
        assertThrows(SSLException.class, () -> upgrade(new DefaultClientTlsStrategy(sslContext), "example.invalid"));
    }

}
