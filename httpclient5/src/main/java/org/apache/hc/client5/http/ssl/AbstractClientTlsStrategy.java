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
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http.ssl.TlsCiphers;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.http2.ssl.H2TlsSupport;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Contract(threading = ThreadingBehavior.STATELESS)
abstract class AbstractClientTlsStrategy implements TlsStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractClientTlsStrategy.class);

    private final SSLContext sslContext;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final SSLBufferMode sslBufferManagement;
    private final HostnameVerifier hostnameVerifier;
    private final TlsSessionValidator tlsSessionValidator;

    AbstractClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerifier hostnameVerifier) {
        super();
        this.sslContext = Args.notNull(sslContext, "SSL context");
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.sslBufferManagement = sslBufferManagement != null ? sslBufferManagement : SSLBufferMode.STATIC;
        this.hostnameVerifier = hostnameVerifier != null ? hostnameVerifier : HttpsSupport.getDefaultHostnameVerifier();
        this.tlsSessionValidator = new TlsSessionValidator(LOG);
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

            initializeEngine(sslEngine);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Enabled protocols: {}", Arrays.asList(sslEngine.getEnabledProtocols()));
                LOG.debug("Enabled cipher suites:{}", Arrays.asList(sslEngine.getEnabledCipherSuites()));
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

    protected void verifySession(
            final String hostname,
            final SSLSession sslsession) throws SSLException {
        tlsSessionValidator.verifySession(hostname, sslsession, hostnameVerifier);
    }

}
