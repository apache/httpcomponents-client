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

package org.apache.hc.client5.testing.sync;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
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
            this.server.close(CloseMode.GRACEFUL);
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
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            try (final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(
                    TimeValue.ZERO_MILLISECONDS,
                    socket,
                    target,
                    remoteAddress,
                    null,
                    context)) {
                final SSLSession sslsession = sslSocket.getSession();

                Assert.assertNotNull(sslsession);
                Assert.assertTrue(hostVerifier.isFired());
            }
        }
    }

    @Test
    public void testBasicDefaultHostnameVerifier() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(SSLTestContexts.createClientSSLContext())
                .build();
        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            try (final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(
                    TimeValue.ZERO_MILLISECONDS,
                    socket,
                    target,
                    remoteAddress,
                    null,
                    context)) {
                final SSLSession sslsession = sslSocket.getSession();

                Assert.assertNotNull(sslsession);
            }
        }
    }

    @Test
    public void testClientAuthSSL() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            try (final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(
                    TimeValue.ZERO_MILLISECONDS,
                    socket,
                    target,
                    remoteAddress,
                    null,
                    context)) {
                final SSLSession sslsession = sslSocket.getSession();

                Assert.assertNotNull(sslsession);
                Assert.assertTrue(hostVerifier.isFired());
            }
        }
    }

    @Test(expected = IOException.class)
    public void testClientAuthSSLFailure() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new Callback<SSLParameters>() {

                    @Override
                    public void execute(final SSLParameters sslParameters) {
                        sslParameters.setNeedClientAuth(true);
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            try (final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(
                    TimeValue.ZERO_MILLISECONDS,
                    socket, target,
                    remoteAddress,
                    null,
                    context)) {
                final SSLSession sslsession = sslSocket.getSession();

                Assert.assertNotNull(sslsession);
                Assert.assertTrue(hostVerifier.isFired());
                sslSocket.getInputStream().read();
            }
        }
    }

    @Test(expected = SSLException.class)
    public void testSSLTrustVerification() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        // Use default SSL context
        final SSLContext defaultsslcontext = SSLContexts.createDefault();

        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(defaultsslcontext,
                NoopHostnameVerifier.INSTANCE);

        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            try (final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(TimeValue.ZERO_MILLISECONDS, socket, target, remoteAddress,
                    null, context)) {
                // empty for now
            }
        }
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

        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            try (final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(TimeValue.ZERO_MILLISECONDS, socket, target, remoteAddress,
                    null, context)) {
                // empty for now
            }
        }
    }

    @Test(expected = IOException.class)
    public void testSSLDisabledByDefault() throws Exception {
        // @formatter:off
        this.server = ServerBootstrap.bootstrap()
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new Callback<SSLParameters>() {

                    @Override
                    public void execute(final SSLParameters sslParameters) {
                        sslParameters.setProtocols(new String[] {"SSLv3"});
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            socketFactory.connectSocket(TimeValue.ZERO_MILLISECONDS, socket, target, remoteAddress, null, context);
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
                "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
        };
        for (final String cipherSuite : weakCiphersSuites) {
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
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new Callback<SSLParameters>() {

                    @Override
                    public void execute(final SSLParameters sslParameters) {
                        sslParameters.setProtocols(new String[] {cipherSuite});
                    }

                })
                .create();
        // @formatter:on
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        try (final Socket socket = socketFactory.createSocket(context)) {
            final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
            final HttpHost target = new HttpHost("https", "localhost", this.server.getLocalPort());
            socketFactory.connectSocket(TimeValue.ZERO_MILLISECONDS, socket, target, remoteAddress, null, context);
        }
    }
}
