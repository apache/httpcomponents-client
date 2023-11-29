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
import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * TLS upgrade strategy for non-blocking client connections.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultClientTlsStrategy extends AbstractClientTlsStrategy {

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

    /**
     * @deprecated To be removed.
     */
    @Deprecated
    private Factory<SSLEngine, TlsDetails> tlsDetailsFactory;

    /**
     * @deprecated Use {@link DefaultClientTlsStrategy#DefaultClientTlsStrategy(SSLContext, String[], String[], SSLBufferMode, HostnameVerifier)}
     */
    @Deprecated
    public DefaultClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerifier hostnameVerifier,
            final Factory<SSLEngine, TlsDetails> tlsDetailsFactory) {
        super(sslContext, supportedProtocols, supportedCipherSuites, sslBufferManagement, hostnameVerifier);
        this.tlsDetailsFactory = tlsDetailsFactory;
    }

    public DefaultClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerifier hostnameVerifier) {
        super(sslContext, supportedProtocols, supportedCipherSuites, sslBufferManagement, hostnameVerifier);
    }

    public DefaultClientTlsStrategy(
            final SSLContext sslContext,
            final HostnameVerifier hostnameVerifier) {
        this(sslContext, null, null, SSLBufferMode.STATIC, hostnameVerifier);
    }

    public DefaultClientTlsStrategy(final SSLContext sslContext) {
        this(sslContext, HttpsSupport.getDefaultHostnameVerifier());
    }

    @Override
    void applyParameters(final SSLEngine sslEngine, final SSLParameters sslParameters, final String[] appProtocols) {
        sslParameters.setApplicationProtocols(appProtocols);
        sslEngine.setSSLParameters(sslParameters);
    }

    @Override
    @SuppressWarnings("deprecated")
    TlsDetails createTlsDetails(final SSLEngine sslEngine) {
        return tlsDetailsFactory != null ? tlsDetailsFactory.create(sslEngine) : null;
    }

}
