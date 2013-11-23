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
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpHost;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link SSLConnectionSocketFactory}.
 */
public class TestSSLSocketFactory extends LocalServerTestBase {

    private KeyStore keystore;

    @Before
    public void setUp() throws Exception {
        keystore  = KeyStore.getInstance("jks");
        final ClassLoader cl = getClass().getClassLoader();
        final URL url = cl.getResource("hc-test.keystore");
        final InputStream instream = url.openStream();
        try {
            keystore.load(instream, "nopassword".toCharArray());
        } finally {
            instream.close();
        }
    }

    @Override
    protected HttpHost getServerHttp() {
        final InetSocketAddress address = this.localServer.getServiceAddress();
        return new HttpHost(
                address.getHostName(),
                address.getPort(),
                "https");
    }

    static class TestX509HostnameVerifier implements X509HostnameVerifier {

        private boolean fired = false;

        public boolean verify(final String host, final SSLSession session) {
            return true;
        }

        public void verify(final String host, final SSLSocket ssl) throws IOException {
            this.fired = true;
        }

        public void verify(final String host, final String[] cns, final String[] subjectAlts) throws SSLException {
        }

        public void verify(final String host, final X509Certificate cert) throws SSLException {
        }

        public boolean isFired() {
            return this.fired;
        }

    }

    @Test
    public void testBasicSSL() throws Exception {
        final SSLContext serverSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();
        final SSLContext clientSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .build();

        this.localServer = new LocalTestServer(serverSSLContext);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();

        final HttpHost host = new HttpHost("localhost", 443, "https");
        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                clientSSLContext, hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = this.localServer.getServiceAddress();
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, host, remoteAddress, null, context);
        final SSLSession sslsession = sslSocket.getSession();

        Assert.assertNotNull(sslsession);
        Assert.assertTrue(hostVerifier.isFired());
    }

    @Test
    public void testClientAuthSSL() throws Exception {
        final SSLContext serverSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();
        final SSLContext clientSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();

        this.localServer = new LocalTestServer(serverSSLContext, true);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();

        final HttpHost host = new HttpHost("localhost", 443, "https");
        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(clientSSLContext, hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = this.localServer.getServiceAddress();
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, host, remoteAddress, null, context);
        final SSLSession sslsession = sslSocket.getSession();

        Assert.assertNotNull(sslsession);
        Assert.assertTrue(hostVerifier.isFired());
    }

    @Test(expected=IOException.class)
    public void testClientAuthSSLFailure() throws Exception {
        final SSLContext serverSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();
        final SSLContext clientSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .build();

        this.localServer = new LocalTestServer(serverSSLContext, true);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();

        final HttpHost host = new HttpHost("localhost", 443, "https");
        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(clientSSLContext, hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = this.localServer.getServiceAddress();
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, host, remoteAddress, null, context);
        final SSLSession sslsession = sslSocket.getSession();

        Assert.assertNotNull(sslsession);
        Assert.assertTrue(hostVerifier.isFired());
    }

    @Test
    public void testClientAuthSSLAliasChoice() throws Exception {
        final PrivateKeyStrategy aliasStrategy = new PrivateKeyStrategy() {

            public String chooseAlias(
                    final Map<String, PrivateKeyDetails> aliases, final Socket socket) {
                Assert.assertEquals(2, aliases.size());
                Assert.assertTrue(aliases.containsKey("hc-test-key-1"));
                Assert.assertTrue(aliases.containsKey("hc-test-key-2"));
                return "hc-test-key-2";
            }

        };

        final SSLContext serverSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();
        final SSLContext clientSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray(), aliasStrategy)
                .build();

        this.localServer = new LocalTestServer(serverSSLContext, true);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();

        final HttpHost host = new HttpHost("localhost", 443, "https");
        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(clientSSLContext, hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = this.localServer.getServiceAddress();
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, host, remoteAddress, null, context);
        final SSLSession sslsession = sslSocket.getSession();

        Assert.assertNotNull(sslsession);
        Assert.assertTrue(hostVerifier.isFired());
    }

    @Test(expected=SSLHandshakeException.class)
    public void testSSLTrustVerification() throws Exception {
        final SSLContext serverSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();

        this.localServer = new LocalTestServer(serverSSLContext);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();

        final HttpHost host = new HttpHost("localhost", 443, "https");
        final HttpContext context = new BasicHttpContext();
        // Use default SSL context
        final SSLContext defaultsslcontext = SSLContexts.createDefault();

        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(defaultsslcontext,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = this.localServer.getServiceAddress();
        socketFactory.connectSocket(0, socket, host, remoteAddress, null, context);
    }

    @Test
    public void testSSLTrustVerificationOverride() throws Exception {
        final SSLContext serverSSLContext = SSLContexts.custom()
                .useProtocol("TLS")
                .loadTrustMaterial(keystore)
                .loadKeyMaterial(keystore, "nopassword".toCharArray())
                .build();

        this.localServer = new LocalTestServer(serverSSLContext);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();

        final HttpHost host = new HttpHost("localhost", 443, "https");
        final HttpContext context = new BasicHttpContext();

        final TrustStrategy trustStrategy = new TrustStrategy() {

            public boolean isTrusted(
                    final X509Certificate[] chain, final String authType) throws CertificateException {
                return chain.length == 1;
            }

        };
        final SSLContext sslcontext = SSLContexts.custom()
            .loadTrustMaterial(null, trustStrategy)
            .build();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                sslcontext,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = this.localServer.getServiceAddress();
        socketFactory.connectSocket(0, socket, host, remoteAddress, null, context);
    }

    @Test
    public void testDefaultHostnameVerifier() throws Exception {
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLContexts.createDefault(),
                null);
        Assert.assertNotNull(socketFactory.getHostnameVerifier());
    }

}
