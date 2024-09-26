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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;

import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http.ssl.TlsCiphers;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.http2.ssl.H2TlsSupport;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Contract(threading = ThreadingBehavior.STATELESS)
abstract class AbstractClientTlsStrategy implements TlsStrategy, TlsSocketStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractClientTlsStrategy.class);

    private final SSLContext sslContext;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final SSLBufferMode sslBufferManagement;
    private final HostnameVerificationPolicy hostnameVerificationPolicy;
    private final HostnameVerifier hostnameVerifier;

    AbstractClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerificationPolicy hostnameVerificationPolicy,
            final HostnameVerifier hostnameVerifier) {
        super();
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.sslBufferManagement = sslBufferManagement != null ? sslBufferManagement : SSLBufferMode.STATIC;
        this.hostnameVerificationPolicy = hostnameVerificationPolicy != null ? hostnameVerificationPolicy : HostnameVerificationPolicy.BOTH;
        this.hostnameVerifier = hostnameVerifier != null ? hostnameVerifier :
                (this.hostnameVerificationPolicy == HostnameVerificationPolicy.BUILTIN ? NoopHostnameVerifier.INSTANCE : HttpsSupport.getDefaultHostnameVerifier());
    }

    /**
     * @deprecated use {@link #upgrade(TransportSecurityLayer, NamedEndpoint, Object, Timeout, FutureCallback)}
     */
    @Deprecated
    @Override
    public boolean upgrade(
            final TransportSecurityLayer tlsSession,
            final HttpHost host,
            final SocketAddress localAddress,
            final SocketAddress remoteAddress,
            final Object attachment,
            final Timeout handshakeTimeout) {
        upgrade(tlsSession, host, attachment, handshakeTimeout, null);
        return true;
    }

    @Override
    public void upgrade(
            final TransportSecurityLayer tlsSession,
            final NamedEndpoint endpoint,
            final Object attachment,
            final Timeout handshakeTimeout,
            final FutureCallback<TransportSecurityLayer> callback) {
        tlsSession.startTls(sslContext, endpoint, sslBufferManagement, (e, sslEngine) -> {

            final TlsConfig tlsConfig = attachment instanceof TlsConfig ? (TlsConfig) attachment : TlsConfig.DEFAULT;
            final HttpVersionPolicy versionPolicy = tlsConfig.getHttpVersionPolicy();

            final SSLParameters sslParameters = sslEngine.getSSLParameters();
            final String[] supportedProtocols = tlsConfig.getSupportedProtocols();
            if (supportedProtocols != null) {
                sslParameters.setProtocols(supportedProtocols);
            } else if (this.supportedProtocols != null) {
                sslParameters.setProtocols(this.supportedProtocols);
            } else if (versionPolicy != HttpVersionPolicy.FORCE_HTTP_1) {
                sslParameters.setProtocols(TLS.excludeWeak(sslParameters.getProtocols()));
            }
            final String[] supportedCipherSuites = tlsConfig.getSupportedCipherSuites();
            if (supportedCipherSuites != null) {
                sslParameters.setCipherSuites(supportedCipherSuites);
            } else if (this.supportedCipherSuites != null) {
                sslParameters.setCipherSuites(this.supportedCipherSuites);
            } else if (versionPolicy == HttpVersionPolicy.FORCE_HTTP_2) {
                sslParameters.setCipherSuites(TlsCiphers.excludeH2Blacklisted(sslParameters.getCipherSuites()));
            }

            if (versionPolicy != HttpVersionPolicy.FORCE_HTTP_1) {
                H2TlsSupport.setEnableRetransmissions(sslParameters, false);
            }

            applyParameters(sslEngine, sslParameters, H2TlsSupport.selectApplicationProtocols(versionPolicy));

            if (hostnameVerificationPolicy == HostnameVerificationPolicy.BUILTIN || hostnameVerificationPolicy == HostnameVerificationPolicy.BOTH) {
                sslParameters.setEndpointIdentificationAlgorithm(URIScheme.HTTPS.id);
            }

            initializeEngine(sslEngine);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Enabled protocols: {}", Arrays.asList(sslEngine.getEnabledProtocols()));
                LOG.debug("Enabled cipher suites: {}", Arrays.asList(sslEngine.getEnabledCipherSuites()));
                LOG.debug("Starting handshake ({})", handshakeTimeout);
            }
        }, (e, sslEngine) -> {
            verifySession(endpoint.getHostName(), sslEngine.getSession());
            final TlsDetails tlsDetails = createTlsDetails(sslEngine);
            final String negotiatedCipherSuite = sslEngine.getSession().getCipherSuite();
            if (tlsDetails != null && ApplicationProtocol.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                if (TlsCiphers.isH2Blacklisted(negotiatedCipherSuite)) {
                    throw new SSLHandshakeException("Cipher suite `" + negotiatedCipherSuite
                        + "` does not provide adequate security for HTTP/2");
                }
            }
            return tlsDetails;
        }, handshakeTimeout, callback);
    }

    abstract void applyParameters(SSLEngine sslEngine, SSLParameters sslParameters, String[] appProtocols);

    abstract TlsDetails createTlsDetails(SSLEngine sslEngine);

    protected void initializeEngine(final SSLEngine sslEngine) {
    }

    protected void initializeSocket(final SSLSocket socket) {
    }

    protected void verifySession(
            final String hostname,
            final SSLSession sslsession) throws SSLException {
        verifySession(hostname, sslsession,
                hostnameVerificationPolicy == HostnameVerificationPolicy.CLIENT || hostnameVerificationPolicy == HostnameVerificationPolicy.BOTH ? hostnameVerifier : null);
    }

    @Override
    public SSLSocket upgrade(final Socket socket,
                             final String target,
                             final int port,
                             final Object attachment,
                             final HttpContext context) throws IOException {
        final SSLSocket upgradedSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                socket,
                target,
                port,
                false);
        try {
            executeHandshake(upgradedSocket, target, attachment);
            return upgradedSocket;
        } catch (IOException | RuntimeException ex) {
            Closer.closeQuietly(upgradedSocket);
            throw ex;
        }
    }

    private void executeHandshake(
            final SSLSocket upgradedSocket,
            final String target,
            final Object attachment) throws IOException {
        final TlsConfig tlsConfig = attachment instanceof TlsConfig ? (TlsConfig) attachment : TlsConfig.DEFAULT;

        final SSLParameters sslParameters = upgradedSocket.getSSLParameters();
        if (supportedProtocols != null) {
            sslParameters.setProtocols(supportedProtocols);
        } else {
            sslParameters.setProtocols(TLS.excludeWeak(upgradedSocket.getEnabledProtocols()));
        }
        if (supportedCipherSuites != null) {
            sslParameters.setCipherSuites(supportedCipherSuites);
        } else {
            sslParameters.setCipherSuites(TlsCiphers.excludeWeak(upgradedSocket.getEnabledCipherSuites()));
        }
        if (hostnameVerificationPolicy == HostnameVerificationPolicy.BUILTIN || hostnameVerificationPolicy == HostnameVerificationPolicy.BOTH) {
            sslParameters.setEndpointIdentificationAlgorithm(URIScheme.HTTPS.id);
        }
        upgradedSocket.setSSLParameters(sslParameters);

        final Timeout handshakeTimeout = tlsConfig.getHandshakeTimeout();
        if (handshakeTimeout != null) {
            upgradedSocket.setSoTimeout(handshakeTimeout.toMillisecondsIntBound());
        }

        initializeSocket(upgradedSocket);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Enabled protocols: {}", (Object) upgradedSocket.getEnabledProtocols());
            LOG.debug("Enabled cipher suites: {}", (Object) upgradedSocket.getEnabledCipherSuites());
            LOG.debug("Starting handshake ({})", handshakeTimeout);
        }
        upgradedSocket.startHandshake();
        verifySession(target, upgradedSocket.getSession());
    }

    void verifySession(
            final String hostname,
            final SSLSession sslsession,
            final HostnameVerifier hostnameVerifier) throws SSLException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Secure session established");
            LOG.debug(" negotiated protocol: {}", sslsession.getProtocol());
            LOG.debug(" negotiated cipher suite: {}", sslsession.getCipherSuite());

            try {

                final Certificate[] certs = sslsession.getPeerCertificates();
                final Certificate cert = certs[0];
                if (cert instanceof X509Certificate) {
                    final X509Certificate x509 = (X509Certificate) cert;
                    final X500Principal peer = x509.getSubjectX500Principal();

                    LOG.debug("Peer principal: {}", toEscapedString(peer));
                    final Collection<List<?>> altNames1 = x509.getSubjectAlternativeNames();
                    if (altNames1 != null) {
                        final List<String> altNames = new ArrayList<>();
                        for (final List<?> aC : altNames1) {
                            if (!aC.isEmpty()) {
                                altNames.add(Objects.toString(aC.get(1), null));
                            }
                        }
                        LOG.debug(" peer alternative names: {}", altNames);
                    }

                    final X500Principal issuer = x509.getIssuerX500Principal();
                    LOG.debug("Issuer principal: {}", toEscapedString(issuer));
                    final Collection<List<?>> altNames2 = x509.getIssuerAlternativeNames();
                    if (altNames2 != null) {
                        final List<String> altNames = new ArrayList<>();
                        for (final List<?> aC : altNames2) {
                            if (!aC.isEmpty()) {
                                altNames.add(Objects.toString(aC.get(1), null));
                            }
                        }
                        LOG.debug(" issuer alternative names: {}", altNames);
                    }
                }
            } catch (final Exception ignore) {
            }
        }

        if (hostnameVerifier != null) {
            final Certificate[] certs = sslsession.getPeerCertificates();
            if (certs.length < 1) {
                throw new SSLPeerUnverifiedException("Peer certificate chain is empty");
            }
            final Certificate peerCertificate = certs[0];
            final X509Certificate x509Certificate;
            if (peerCertificate instanceof X509Certificate) {
                x509Certificate = (X509Certificate) peerCertificate;
            } else {
                throw new SSLPeerUnverifiedException("Unexpected certificate type: " + peerCertificate.getType());
            }
            if (hostnameVerifier instanceof HttpClientHostnameVerifier) {
                ((HttpClientHostnameVerifier) hostnameVerifier).verify(hostname, x509Certificate);
            } else if (!hostnameVerifier.verify(hostname, sslsession)) {
                final List<SubjectName> subjectAlts = DefaultHostnameVerifier.getSubjectAltNames(x509Certificate);
                throw new SSLPeerUnverifiedException("Certificate for <" + hostname + "> doesn't match any " +
                        "of the subject alternative names: " + subjectAlts);
            }
        }
    }

    /**
     * Converts an X500Principal to a cleaned string by escaping control characters.
     * <p>
     * This method processes the RFC2253 format of the X500Principal and escapes
     * any ISO control characters to avoid issues in logging or other outputs.
     * Control characters are replaced with their escaped hexadecimal representation.
     * </p>
     *
     * <p><strong>Note:</strong> For testing purposes, this method is package-private
     * to allow access within the same package. This allows tests to verify the correct
     * behavior of the escaping process.</p>
     *
     * @param principal the X500Principal to escape
     * @return the escaped string representation of the X500Principal
     */
    @Internal
    String toEscapedString(final X500Principal principal) {
        final String principalValue = principal.getName(X500Principal.RFC2253);
        final StringBuilder sanitizedPrincipal = new StringBuilder(principalValue.length());
        for (final char c : principalValue.toCharArray()) {
            if (Character.isISOControl(c)) {
                sanitizedPrincipal.append(String.format("\\x%02x", (int) c));
            } else {
                sanitizedPrincipal.append(c);
            }
        }
        return sanitizedPrincipal.toString();
    }

}
