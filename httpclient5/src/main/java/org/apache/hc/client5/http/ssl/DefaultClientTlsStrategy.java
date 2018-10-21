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

import java.net.SocketAddress;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.ssl.H2TlsSupport;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default SSL upgrade strategy for non-blocking client connections.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultClientTlsStrategy implements TlsStrategy {

    public static TlsStrategy getDefault() {
        return new DefaultClientTlsStrategy(
                SSLContexts.createDefault(),
                HttpsSupport.getDefaultHostnameVerifier());
    }

    public static TlsStrategy getSystemDefault() {
        return new DefaultClientTlsStrategy(
                SSLContexts.createSystemDefault(),
                HttpsSupport.getSystemProtocols(),
                HttpsSupport.getSystemCipherSuits(),
                SSLBufferMode.STATIC,
                HttpsSupport.getDefaultHostnameVerifier());
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SSLContext sslContext;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final SSLBufferMode sslBufferManagement;
    private final HostnameVerifier hostnameVerifier;
    private final Factory<SSLEngine, TlsDetails> tlsDetailsFactory;
    private final TlsSessionValidator tlsSessionValidator;

    public DefaultClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerifier hostnameVerifier,
            final Factory<SSLEngine, TlsDetails> tlsDetailsFactory) {
        super();
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.sslBufferManagement = sslBufferManagement != null ? sslBufferManagement : SSLBufferMode.STATIC;
        this.hostnameVerifier = hostnameVerifier != null ? hostnameVerifier : HttpsSupport.getDefaultHostnameVerifier();
        this.tlsDetailsFactory = tlsDetailsFactory;
        this.tlsSessionValidator = new TlsSessionValidator(log);
    }

    public DefaultClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerifier hostnameVerifier) {
        this(sslContext, supportedProtocols, supportedCipherSuites, sslBufferManagement, hostnameVerifier, null);
    }

    public DefaultClientTlsStrategy(
            final SSLContext sslcontext,
            final HostnameVerifier hostnameVerifier) {
        this(sslcontext, null, null, SSLBufferMode.STATIC, hostnameVerifier, null);
    }

    public DefaultClientTlsStrategy(final SSLContext sslcontext) {
        this(sslcontext, HttpsSupport.getDefaultHostnameVerifier());
    }

    @Override
    public boolean upgrade(
            final TransportSecurityLayer tlsSession,
            final HttpHost host,
            final SocketAddress localAddress,
            final SocketAddress remoteAddress,
            final Object attachment) {
        tlsSession.startTls(sslContext, host, sslBufferManagement, H2TlsSupport.enforceRequirements(attachment, new SSLSessionInitializer() {

            @Override
            public void initialize(final NamedEndpoint endpoint, final SSLEngine sslEngine) {
                if (supportedProtocols != null) {
                    sslEngine.setEnabledProtocols(supportedProtocols);
                }
                if (supportedCipherSuites != null) {
                    sslEngine.setEnabledCipherSuites(supportedCipherSuites);
                }
                initializeEngine(sslEngine);

                if (log.isDebugEnabled()) {
                    log.debug("Enabled protocols: " + Arrays.asList(sslEngine.getEnabledProtocols()));
                    log.debug("Enabled cipher suites:" + Arrays.asList(sslEngine.getEnabledCipherSuites()));
                }
            }

        }), new SSLSessionVerifier() {

            @Override
            public TlsDetails verify(final NamedEndpoint endpoint, final SSLEngine sslEngine) throws SSLException {
                verifySession(host.getHostName(), sslEngine.getSession());
                return tlsDetailsFactory != null ? tlsDetailsFactory.create(sslEngine) : null;
            }

        });
        return true;
    }

    protected void initializeEngine(final SSLEngine sslEngine) {
    }

    protected void verifySession(
            final String hostname,
            final SSLSession sslsession) throws SSLException {
        tlsSessionValidator.verifySession(hostname, sslsession, hostnameVerifier);
    }

}
