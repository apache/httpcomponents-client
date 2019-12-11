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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.ReflectionUtils;

/**
 * Builder for client {@link TlsStrategy} instances.
 * <p>
 * When a particular component is not explicitly set this class will
 * use its default implementation. System properties will be taken
 * into account when configuring the default implementations when
 * {@link #useSystemProperties()} method is called prior to calling
 * {@link #build()}.
 * </p>
 * <ul>
 *  <li>ssl.TrustManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.trustStoreType</li>
 *  <li>javax.net.ssl.trustStore</li>
 *  <li>javax.net.ssl.trustStoreProvider</li>
 *  <li>javax.net.ssl.trustStorePassword</li>
 *  <li>ssl.KeyManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.keyStoreType</li>
 *  <li>javax.net.ssl.keyStore</li>
 *  <li>javax.net.ssl.keyStoreProvider</li>
 *  <li>javax.net.ssl.keyStorePassword</li>
 *  <li>https.protocols</li>
 *  <li>https.cipherSuites</li>
 * </ul>
 *
 * @since 5.0
 */
public class ClientTlsStrategyBuilder {

    public static ClientTlsStrategyBuilder create() {
        return new ClientTlsStrategyBuilder();
    }

    private SSLContext sslContext;
    private String[] tlsVersions;
    private String[] ciphers;
    private SSLBufferMode sslBufferMode;
    private HostnameVerifier hostnameVerifier;
    private Factory<SSLEngine, TlsDetails> tlsDetailsFactory;
    private boolean systemProperties;

    /**
     * Assigns {@link SSLContext} instance.
     */
    public ClientTlsStrategyBuilder setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Assigns enabled {@code TLS} versions.
     */
    public final ClientTlsStrategyBuilder setTlsVersions(final String... tlslVersions) {
        this.tlsVersions = tlslVersions;
        return this;
    }

    /**
     * Assigns enabled {@code TLS} versions.
     */
    public final ClientTlsStrategyBuilder setTlsVersions(final TLS... tlslVersions) {
        this.tlsVersions = new String[tlslVersions.length];
        for (int i = 0; i < tlslVersions.length; i++) {
            this.tlsVersions[i] = tlslVersions[i].id;
        }
        return this;
    }

    /**
     * Assigns enabled ciphers.
     */
    public final ClientTlsStrategyBuilder setCiphers(final String... ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    /**
     * Assigns {@link SSLBufferMode} value.
     */
    public ClientTlsStrategyBuilder setSslBufferMode(final SSLBufferMode sslBufferMode) {
        this.sslBufferMode = sslBufferMode;
        return this;
    }

    /**
     * Assigns {@link HostnameVerifier} instance.
     */
    public ClientTlsStrategyBuilder setHostnameVerifier(final HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * Assigns {@link TlsDetails} {@link Factory} instance.
     */
    public ClientTlsStrategyBuilder setTlsDetailsFactory(final Factory<SSLEngine, TlsDetails> tlsDetailsFactory) {
        this.tlsDetailsFactory = tlsDetailsFactory;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final ClientTlsStrategyBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    public TlsStrategy build() {
        final SSLContext sslContextCopy;
        if (sslContext != null) {
            sslContextCopy = sslContext;
        } else {
            sslContextCopy = systemProperties ? SSLContexts.createSystemDefault() : SSLContexts.createDefault();
        }
        final String[] tlsVersionsCopy;
        if (tlsVersions != null) {
            tlsVersionsCopy = tlsVersions;
        } else {
            tlsVersionsCopy = systemProperties ? HttpsSupport.getSystemProtocols() : null;
        }
        final String[] ciphersCopy;
        if (ciphers != null) {
            ciphersCopy = ciphers;
        } else {
            ciphersCopy = systemProperties ? HttpsSupport.getSystemCipherSuits() : null;
        }
        final Factory<SSLEngine, TlsDetails> tlsDetailsFactoryCopy;
        if (tlsDetailsFactory != null) {
            tlsDetailsFactoryCopy = tlsDetailsFactory;
        } else {
            tlsDetailsFactoryCopy = new Factory<SSLEngine, TlsDetails>() {
                @Override
                public TlsDetails create(final SSLEngine sslEngine) {
                    final SSLSession sslSession = sslEngine.getSession();
                    final String applicationProtocol = ReflectionUtils.callGetter(sslEngine,
                        "ApplicationProtocol", String.class);
                    return new TlsDetails(sslSession, applicationProtocol);
                }
            };
        }
        return new DefaultClientTlsStrategy(
                sslContextCopy,
                tlsVersionsCopy,
                ciphersCopy,
                sslBufferMode != null ? sslBufferMode : SSLBufferMode.STATIC,
                hostnameVerifier != null ? hostnameVerifier : HttpsSupport.getDefaultHostnameVerifier(),
                tlsDetailsFactoryCopy);
    }

}
