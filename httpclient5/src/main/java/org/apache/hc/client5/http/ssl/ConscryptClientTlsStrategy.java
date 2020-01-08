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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.ssl.H2TlsSupport;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.conscrypt.Conscrypt;

/**
 * TLS upgrade strategy for non-blocking client connections using Conscrypt TLS library.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class ConscryptClientTlsStrategy extends AbstractClientTlsStrategy {

    public static TlsStrategy getDefault() {
        return new ConscryptClientTlsStrategy(
                SSLContexts.createDefault(),
                HttpsSupport.getDefaultHostnameVerifier());
    }

    public static TlsStrategy getSystemDefault() {
        return new ConscryptClientTlsStrategy(
                SSLContexts.createSystemDefault(),
                HttpsSupport.getSystemProtocols(),
                HttpsSupport.getSystemCipherSuits(),
                SSLBufferMode.STATIC,
                HttpsSupport.getDefaultHostnameVerifier());
    }

    public ConscryptClientTlsStrategy(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final SSLBufferMode sslBufferManagement,
            final HostnameVerifier hostnameVerifier) {
        super(sslContext, supportedProtocols, supportedCipherSuites, sslBufferManagement, hostnameVerifier);
    }

    public ConscryptClientTlsStrategy(
            final SSLContext sslcontext,
            final HostnameVerifier hostnameVerifier) {
        this(sslcontext, null, null, SSLBufferMode.STATIC, hostnameVerifier);
    }

    public ConscryptClientTlsStrategy(final SSLContext sslcontext) {
        this(sslcontext, HttpsSupport.getDefaultHostnameVerifier());
    }

    @Override
    void applyParameters(final SSLEngine sslEngine, final SSLParameters sslParameters, final String[] appProtocols) {
        if (Conscrypt.isConscrypt(sslEngine)) {
            sslEngine.setSSLParameters(sslParameters);
            Conscrypt.setApplicationProtocols(sslEngine, appProtocols);
        } else {
            H2TlsSupport.setApplicationProtocols(sslParameters, appProtocols);
            sslEngine.setSSLParameters(sslParameters);
        }
    }

    @Override
    TlsDetails createTlsDetails(final SSLEngine sslEngine) {
        if (Conscrypt.isConscrypt(sslEngine)) {
            return new TlsDetails(sslEngine.getSession(), Conscrypt.getApplicationProtocol(sslEngine));
        }
        return null;
    }

    public static boolean isSupported() {
        try {
            final Class<?> clazz = Class.forName("org.conscrypt.Conscrypt");
            final Method method = clazz.getMethod("isAvailable");
            return (Boolean) method.invoke(null);
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

}
