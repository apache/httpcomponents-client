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
 *
 * @deprecated Use {@link DefaultClientTlsStrategy}
 */
@Deprecated
public class SSLConnectionSocketFactoryBuilder {

    public static SSLConnectionSocketFactoryBuilder create() {
        return new SSLConnectionSocketFactoryBuilder();
    }

    private SSLContext sslContext;
    private String[] tlsVersions;
    private String[] ciphers;
    private HostnameVerifier hostnameVerifier;

    /**
     * Sets {@link SSLContext} instance.
     *
     * @return this instance.
     */
    public SSLConnectionSocketFactoryBuilder setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Sets enabled {@code TLS} versions.
     *
     * @return this instance.
     */
    public final SSLConnectionSocketFactoryBuilder setTlsVersions(final String... tlslVersions) {
        this.tlsVersions = tlslVersions;
        return this;
    }

    /**
     * Sets enabled {@code TLS} versions.
     *
     * @return this instance.
     */
    public final SSLConnectionSocketFactoryBuilder setTlsVersions(final TLS... tlslVersions) {
        this.tlsVersions = new String[tlslVersions.length];
        for (int i = 0; i < tlslVersions.length; i++) {
            this.tlsVersions[i] = tlslVersions[i].id;
        }
        return this;
    }

    /**
     * Sets enabled ciphers.
     *
     * @return this instance.
     */
    public final SSLConnectionSocketFactoryBuilder setCiphers(final String... ciphers) {
        this.ciphers = ciphers;
        return this;
    }


    /**
     * Sets {@link HostnameVerifier} instance.
     *
     * @return this instance.
     */
    public SSLConnectionSocketFactoryBuilder setHostnameVerifier(final HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * Ignored.
     */
    public final SSLConnectionSocketFactoryBuilder useSystemProperties() {
        return this;
    }

    public SSLConnectionSocketFactory build() {
        final javax.net.ssl.SSLSocketFactory socketFactory;
        if (sslContext != null) {
            socketFactory = sslContext.getSocketFactory();
        } else {
            socketFactory = SSLContexts.createDefault().getSocketFactory();
        }
        return new SSLConnectionSocketFactory(
                socketFactory,
                tlsVersions,
                ciphers,
                hostnameVerifier != null ? hostnameVerifier : HttpsSupport.getDefaultHostnameVerifier());
    }

}
