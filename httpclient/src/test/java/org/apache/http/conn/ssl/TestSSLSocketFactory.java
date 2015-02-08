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
import java.net.InetSocketAddress;
import java.net.Socket;
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
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
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
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), SSLConnectionSocketFactory.getDefaultHostnameVerifier());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        try {
            final SSLSession sslsession = sslSocket.getSession();

            Assert.assertNotNull(sslsession);
        } finally {
            sslSocket.close();
        }
    }

    @Test
    public void testClientAuthSSL() throws Exception {
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        try {
            final SSLSession sslsession = sslSocket.getSession();

            Assert.assertNotNull(sslsession);
            Assert.assertTrue(hostVerifier.isFired());
        } finally {
            sslSocket.close();
        }
    }

    @Test(expected=IOException.class)
    public void testClientAuthSSLFailure() throws Exception {
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
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final TestX509HostnameVerifier hostVerifier = new TestX509HostnameVerifier();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext(), hostVerifier);
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        try {
            final SSLSession sslsession = sslSocket.getSession();

            Assert.assertNotNull(sslsession);
            Assert.assertTrue(hostVerifier.isFired());
        } finally {
            sslSocket.close();
        }
    }

    @Test(expected=SSLException.class)
    public void testSSLTrustVerification() throws Exception {
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        // Use default SSL context
        final SSLContext defaultsslcontext = SSLContexts.createDefault();

        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(defaultsslcontext,
                NoopHostnameVerifier.INSTANCE);

        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        sslSocket.close();
    }

    @Test
    public void testSSLTrustVerificationOverride() throws Exception {
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .create();
        this.server.start();

        final HttpContext context = new BasicHttpContext();

        final TrustStrategy trustStrategy = new TrustStrategy() {

            @Override
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
                NoopHostnameVerifier.INSTANCE);

        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        sslSocket.close();
    }

    @Test
    public void testTLSOnly() throws Exception {
        this.server = ServerBootstrap.bootstrap()
                .setServerInfo(LocalServerTestBase.ORIGIN)
                .setSslContext(SSLTestContexts.createServerSSLContext())
                .setSslSetupHandler(new SSLServerSetupHandler() {

                    @Override
                    public void initialize(final SSLServerSocket socket) throws SSLException {
                        socket.setEnabledProtocols(new String[] {"TLSv1"});
                    }

                })
                .create();
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        final SSLSocket sslSocket = (SSLSocket) socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
        final SSLSession sslsession = sslSocket.getSession();
        Assert.assertNotNull(sslsession);
    }

    @Test(expected=IOException.class)
    public void testSSLDisabledByDefault() throws Exception {
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
        this.server.start();

        final HttpContext context = new BasicHttpContext();
        final SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                SSLTestContexts.createClientSSLContext());
        final Socket socket = socketFactory.createSocket(context);
        final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", this.server.getLocalPort());
        final HttpHost target = new HttpHost("localhost", this.server.getLocalPort(), "https");
        socketFactory.connectSocket(0, socket, target, remoteAddress, null, context);
    }
}
