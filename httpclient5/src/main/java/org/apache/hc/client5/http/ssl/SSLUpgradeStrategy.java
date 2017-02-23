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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.apache.hc.client5.http.psl.PublicSuffixMatcherLoader;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferManagement;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default SSL upgrade strategy for non-blocking connections.
 *
 * @since 5.0
 */
public class SSLUpgradeStrategy implements TlsStrategy {

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

    public static HostnameVerifier getDefaultHostnameVerifier() {
        return new DefaultHostnameVerifier(PublicSuffixMatcherLoader.getDefault());
    }

    public static TlsStrategy getDefault() {
        return new SSLUpgradeStrategy(
                SSLContexts.createDefault(),
                getDefaultHostnameVerifier());
    }

    public static TlsStrategy getSystemDefault() {
        return new SSLUpgradeStrategy(
                SSLContexts.createSystemDefault(),
                split(System.getProperty("https.protocols")),
                split(System.getProperty("https.cipherSuites")),
                SSLBufferManagement.STATIC,
                getDefaultHostnameVerifier());
    }

    private final Logger log = LogManager.getLogger(getClass());

    private final SSLContext sslContext;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final SSLBufferManagement sslBufferManagement;
    private final HostnameVerifier hostnameVerifier;

    public SSLUpgradeStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferManagement sslBufferManagement,
            final HostnameVerifier hostnameVerifier) {
        super();
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.sslBufferManagement = sslBufferManagement != null ? sslBufferManagement : SSLBufferManagement.STATIC;
        this.hostnameVerifier = hostnameVerifier != null ? hostnameVerifier : getDefaultHostnameVerifier();
    }

    public SSLUpgradeStrategy(
            final SSLContext sslcontext,
            final HostnameVerifier hostnameVerifier) {
        this(sslcontext, null, null, SSLBufferManagement.STATIC, hostnameVerifier);
    }

    public SSLUpgradeStrategy(final SSLContext sslcontext) {
        this(sslcontext, getDefaultHostnameVerifier());
    }

    @Override
    public void upgrade(
            final TransportSecurityLayer tlsSession,
            final String hostname,
            final SocketAddress localAddress,
            final SocketAddress remoteAddress,
            final String... parameters) {
        tlsSession.start(sslContext, sslBufferManagement, new SSLSessionInitializer() {

            @Override
            public void initialize(final SSLEngine sslengine) throws SSLException {
                if (supportedProtocols != null) {
                    sslengine.setEnabledProtocols(supportedProtocols);
                } else {
                    // If supported protocols are not explicitly set, remove all SSL protocol versions
                    final String[] allProtocols = sslengine.getEnabledProtocols();
                    final List<String> enabledProtocols = new ArrayList<>(allProtocols.length);
                    for (final String protocol: allProtocols) {
                        if (!protocol.startsWith("SSL")) {
                            enabledProtocols.add(protocol);
                        }
                    }
                    if (!enabledProtocols.isEmpty()) {
                        sslengine.setEnabledProtocols(enabledProtocols.toArray(new String[enabledProtocols.size()]));
                    }
                }
                if (supportedCipherSuites != null) {
                    sslengine.setEnabledCipherSuites(supportedCipherSuites);
                }
                initializeEngine(sslengine);

                if (log.isDebugEnabled()) {
                    log.debug("Enabled protocols: " + Arrays.asList(sslengine.getEnabledProtocols()));
                    log.debug("Enabled cipher suites:" + Arrays.asList(sslengine.getEnabledCipherSuites()));
                }
            }

        }, new SSLSessionVerifier() {

            @Override
            public void verify(final IOSession iosession, final SSLSession sslsession) throws SSLException {
                // TODO: fix after upgrading to HttpCore 5.0a3
                verifySession(hostname, sslsession);
            }

        });
    }

    protected void initializeEngine(final SSLEngine engine) {
    }

    protected void verifySession(
            final String hostname,
            final SSLSession sslsession) throws SSLException {

        if (log.isDebugEnabled()) {
            log.debug("Secure session established");
            log.debug(" negotiated protocol: " + sslsession.getProtocol());
            log.debug(" negotiated cipher suite: " + sslsession.getCipherSuite());

            try {

                final Certificate[] certs = sslsession.getPeerCertificates();
                final X509Certificate x509 = (X509Certificate) certs[0];
                final X500Principal peer = x509.getSubjectX500Principal();

                log.debug(" peer principal: " + peer.toString());
                final Collection<List<?>> altNames1 = x509.getSubjectAlternativeNames();
                if (altNames1 != null) {
                    final List<String> altNames = new ArrayList<>();
                    for (final List<?> aC : altNames1) {
                        if (!aC.isEmpty()) {
                            altNames.add((String) aC.get(1));
                        }
                    }
                    log.debug(" peer alternative names: " + altNames);
                }

                final X500Principal issuer = x509.getIssuerX500Principal();
                log.debug(" issuer principal: " + issuer.toString());
                final Collection<List<?>> altNames2 = x509.getIssuerAlternativeNames();
                if (altNames2 != null) {
                    final List<String> altNames = new ArrayList<>();
                    for (final List<?> aC : altNames2) {
                        if (!aC.isEmpty()) {
                            altNames.add((String) aC.get(1));
                        }
                    }
                    log.debug(" issuer alternative names: " + altNames);
                }
            } catch (final Exception ignore) {
            }
        }

        if (this.hostnameVerifier instanceof HttpClientHostnameVerifier) {
            final Certificate[] certs = sslsession.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            ((HttpClientHostnameVerifier) this.hostnameVerifier).verify(hostname, x509);
        } else if (!this.hostnameVerifier.verify(hostname, sslsession)) {
            final Certificate[] certs = sslsession.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            final List<SubjectName> subjectAlts = DefaultHostnameVerifier.getSubjectAltNames(x509);
            throw new SSLPeerUnverifiedException("Certificate for <" + hostname + "> doesn't match any " +
                    "of the subject alternative names: " + subjectAlts);
        }
    }

}
