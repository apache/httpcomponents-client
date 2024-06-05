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

package org.apache.http.conn.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpHost;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.SSLServerSetupHandler;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.localserver.SSLTestContexts;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link SSLConnectionSocketFactory}.
 */
public class TestSSLSocketFactory {

    private HttpServer server;

    @After
    public void shutDown() throws Exception {
        if (this.server != null) {
            this.server.shutdown(10, TimeUnit.SECONDS);
        }
    }

    static class TestX509HostnameVerifier implements HostnameVerifier {

        private boolean fired = false;

        @Override
        public boolean verify(final String host, final SSLSession session) {
            this.fired = true;
            return true;
        }

        public boolean isFired() {
            return this.fired;
        }

    }

    @Test
    public void testBasicSSL() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        try {
            final SSLSession sslsession = sslSocket.getSession();

            Assert.assertNotNull(sslsession);
            Assert.assertTrue(hostVerifier.isFired());
        } finally {
            sslSocket.close();
        }
    }

    @Test
    public void testBasicDefaultHostnameVerifier() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), SSLConnectionSocketFactory.getDefaultHostnameVerifier());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        try {
            final SSLSession sslsession = sslSocket.getSession();

            Assert.assertNotNull(sslsession);
        } finally {
            sslSocket.close();
        }
    }

    @Test
    public void testClientAuthSSL() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        try {
            final SSLSession sslsession = sslSocket.getSession();

            Assert.assertNotNull(sslsession);
            Assert.assertTrue(hostVerifier.isFired());
        } finally {
            sslSocket.close();
        }
    }

    @Test(expected = IOException.class)
    public void testClientAuthSSLFailure() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new SSLServerSetupHandler() {

                    @Override
                    public void initialize(final SSLServerSocket socket) throws SSLException {
                        socket.setNeedClientAuth(true);
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        try {
            final InputStream inputStream = sslSocket.getInputStream();
            Assert.assertEquals(-1, inputStream.read());

            final SSLSession sslsession = sslSocket.getSession();
            Assert.assertNotNull(sslsession);
            Assert.assertTrue(hostVerifier.isFired());
        } finally {
            sslSocket.close();
        }
    }

    @Test(expected = SSLException.class)
    public void testSSLTrustVerification() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        // Use default SSL context
        final SSLContext defaultsslcontext = SSLContexts.createDefault();

        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(defaultsslcontext,
                NoopHostnameVerifier.INSTANCE);

        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        sslSocket.close();
    }

    @Test
    public void testSSLTrustVerificationOverrideWithCustsom() throws Exception {
        final TrustStrategy trustStrategy = new TrustStrategy() {

            @Override
            public boolean isTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
                return chain.length == 1;
            }

        };
        testSSLTrustVerificationOverride(trustStrategy);
    }

    @Test
    public void testSSLTrustVerificationOverrideWithTrustSelfSignedStrategy() throws Exception {
        testSSLTrustVerificationOverride(TrustSelfSignedStrategy.INSTANCE);
    }

    @Test
    public void testSSLTrustVerificationOverrideWithTrustAllStrategy() throws Exception {
        testSSLTrustVerificationOverride(TrustAllStrategy.INSTANCE);
    }

    private void testSSLTrustVerificationOverride(final TrustStrategy trustStrategy)
            throws Exception, IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();

        // @formatter:off
        final SSLContext sslcontext = SSLContexts.custom()
            .loadTrustMaterial(null, trustStrategy)
            .build();
        // @formatter:on
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslcontext,
                NoopHostnameVerifier.INSTANCE);

        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        sslSocket.close();
    }

    @Test
    public void testTLSOnly() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new SSLServerSetupHandler() {

                    @Override
                    public void initialize(final SSLServerSocket socket) throws SSLException {
                        socket.setEnabledProtocols(new String[] {"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"});
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null,
                context);
        final SSLSession sslsession = sslSocket.getSession();
        Assert.assertNotNull(sslsession);
    }

    @Test(expected = IOException.class)
    public void testSSLDisabledByDefault() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new SSLServerSetupHandler() {

                    @Override
                    public void initialize(final SSLServerSocket socket) throws SSLException {
                        socket.setEnabledProtocols(new String[] {"SSLv3"});
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
    }

    @Test
    public void testSSLTimeout() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final Socket sslSocket = socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        final InputStream inputStream = sslSocket.getInputStream();
        try {
            sslSocket.setSoTimeout(1);
            inputStream.read();
            Assert.fail("SocketTimeoutException expected");
        } catch (final SocketTimeoutException ex){
            Assert.assertThat(sslSocket.isClosed(), CoreMatchers.equalTo(false));
            Assert.assertThat(socket.isClosed(), CoreMatchers.equalTo(false));
        } finally {
            inputStream.close();
        }
    }

    @Test
    public void testStrongCipherSuites() {
        final String[] strongCipherSuites = {
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                "TLS_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_GCM_SHA384"
        };
        for (final String cipherSuite : strongCipherSuites) {
            Assert.assertFalse(SSLConnectionSocketFactory.isWeakCipherSuite(cipherSuite));
        }
    }

    @Test
    public void testWeakCiphersDisabledByDefault() {
        final String[] weakCiphersSuites = {
                "SSL_RSA_WITH_RC4_128_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DH_anon_WITH_AES_128_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_RSA_WITH_NULL_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
                "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_NULL_SHA256",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
//                "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
        };
        for (final String cipherSuite : weakCiphersSuites) {
            Assert.assertTrue(SSLConnectionSocketFactory.isWeakCipherSuite(cipherSuite));
            try {
                testWeakCipherDisabledByDefault(cipherSuite);
                Assert.fail("IOException expected");
            } catch (final Exception e) {
                Assert.assertTrue(e instanceof IOException || e instanceof IllegalArgumentException);
            }
        }
    }

    private void testWeakCipherDisabledByDefault(final String cipherSuite) throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new SSLServerSetupHandler() {

                    @Override
                    public void initialize(final SSLServerSocket socket) {
                        socket.setEnabledCipherSuites(new String[] {cipherSuite});
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        final Socket socket = socketFactory.createSocket(context);
        try {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
            socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        } finally {
            socket.close();
        }
    }
}
