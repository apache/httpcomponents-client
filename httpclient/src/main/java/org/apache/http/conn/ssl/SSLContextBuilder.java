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

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.annotation.NotThreadSafe;

/**
 * Builder for {@link SSLContext} instances.
 *
 * @since 4.3
 */
@NotThreadSafe
public class SSLContextBuilder {

    static final String TLS   = "TLS";
    static final String SSL   = "SSL";

    private String protocol;
    private KeyManager[] keymanagers;
    private TrustManager[] trustmanagers;
    private SecureRandom secureRandom;

    public SSLContextBuilder useTLS() {
        this.protocol = TLS;
        return this;
    }

    public SSLContextBuilder useSSL() {
        this.protocol = SSL;
        return this;
    }

    public SSLContextBuilder useProtocol(final String protocol) {
        this.protocol = protocol;
        return this;
    }

    public SSLContextBuilder loadTrustMaterial(
            final KeyStore truststore,
            final char[] truststorePassword,
            final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException {
        final TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(truststore);
        final TrustManager[] trustmanagers = tmfactory.getTrustManagers();
        if (trustmanagers != null && trustStrategy != null) {
            for (int i = 0; i < trustmanagers.length; i++) {
                final TrustManager tm = trustmanagers[i];
                if (tm instanceof X509TrustManager) {
                    trustmanagers[i] = new TrustManagerDecorator(
                            (X509TrustManager) tm, trustStrategy);
                }
            }
        }
        this.trustmanagers = trustmanagers;
        return this;
    }

    public SSLContextBuilder loadTrustMaterial(
            final KeyStore truststore,
            final TrustStrategy trustStrategy) throws NoSuchAlgorithmException, KeyStoreException {
        return loadTrustMaterial(truststore, null, trustStrategy);
    }

    public SSLContextBuilder loadTrustMaterial(
            final KeyStore truststore) throws NoSuchAlgorithmException, KeyStoreException {
        return loadTrustMaterial(truststore, null, null);
    }

    public SSLContextBuilder loadKeyMaterial(
            final KeyStore keystore,
            final char[] keystorePassword)
                throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        final KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmfactory.init(keystore, keystorePassword);
        this.keymanagers =  kmfactory.getKeyManagers();
        return this;
    }

    public SSLContext build() throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext sslcontext = SSLContext.getInstance(
                this.protocol != null ? this.protocol : TLS);
        sslcontext.init(keymanagers, trustmanagers, secureRandom);
        return sslcontext;
    }

}
