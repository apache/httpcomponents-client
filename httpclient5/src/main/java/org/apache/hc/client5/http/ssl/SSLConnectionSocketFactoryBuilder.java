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

import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * Builder for {@link SSLConnectionSocketFactory} instances.
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
public class SSLConnectionSocketFactoryBuilder {

    public static SSLConnectionSocketFactoryBuilder create() {
        return new SSLConnectionSocketFactoryBuilder();
    }

    private SSLContext sslContext;
    private String[] tlsVersions;
    private String[] ciphers;
    private HostnameVerifier hostnameVerifier;
    private boolean systemProperties;

    /**
     * Assigns {@link SSLContext} instance.
     */
    public SSLConnectionSocketFactoryBuilder setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Assigns enabled {@code TLS} versions.
     */
    public final SSLConnectionSocketFactoryBuilder setTlsVersions(final String... tlslVersions) {
        this.tlsVersions = tlslVersions;
        return this;
    }

    /**
     * Assigns enabled {@code TLS} versions.
     */
    public final SSLConnectionSocketFactoryBuilder setTlsVersions(final TLS... tlslVersions) {
        this.tlsVersions = new String[tlslVersions.length];
        for (int i = 0; i < tlslVersions.length; i++) {
            this.tlsVersions[i] = tlslVersions[i].id;
        }
        return this;
    }

    /**
     * Assigns enabled ciphers.
     */
    public final SSLConnectionSocketFactoryBuilder setCiphers(final String... ciphers) {
        this.ciphers = ciphers;
        return this;
    }


    /**
     * Assigns {@link HostnameVerifier} instance.
     */
    public SSLConnectionSocketFactoryBuilder setHostnameVerifier(final HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final SSLConnectionSocketFactoryBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    public SSLConnectionSocketFactory build() {
        final javax.net.ssl.SSLSocketFactory socketFactory;
        if (sslContext != null) {
            socketFactory = sslContext.getSocketFactory();
        } else {
            if (systemProperties) {
                socketFactory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            } else {
                socketFactory = SSLContexts.createDefault().getSocketFactory();
            }
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
        return new SSLConnectionSocketFactory(
                socketFactory,
                tlsVersionsCopy,
                ciphersCopy,
                hostnameVerifier != null ? hostnameVerifier : HttpsSupport.getDefaultHostnameVerifier());
    }

}
