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

package org.apache.http.localserver;

import java.net.URL;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLTestContexts {

    private static KeyManagerFactory createKeyManagerFactory() throws NoSuchAlgorithmException {
        final String algo = KeyManagerFactory.getDefaultAlgorithm();
        try {
            return KeyManagerFactory.getInstance(algo);
        } catch (final NoSuchAlgorithmException ex) {
            return KeyManagerFactory.getInstance("SunX509");
        }
    }

    public static SSLContext createServerSSLContext() throws Exception {
        final ClassLoader cl = SSLTestContexts.class.getClassLoader();
        final URL url = cl.getResource("test.keystore");
        final KeyStore keystore  = KeyStore.getInstance("jks");
        keystore.load(url.openStream(), "nopassword".toCharArray());
        final KeyManagerFactory kmfactory = createKeyManagerFactory();
        kmfactory.init(keystore, "nopassword".toCharArray());
        final KeyManager[] keymanagers = kmfactory.getKeyManagers();
        final SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(keymanagers, null, null);
        return sslcontext;
    }

    private static TrustManagerFactory createTrustManagerFactory() throws NoSuchAlgorithmException {
        final String algo = TrustManagerFactory.getDefaultAlgorithm();
        try {
            return TrustManagerFactory.getInstance(algo);
        } catch (final NoSuchAlgorithmException ex) {
            return TrustManagerFactory.getInstance("SunX509");
        }
    }

    public static SSLContext createClientSSLContext() throws Exception {
        final ClassLoader cl = SSLTestContexts.class.getClassLoader();
        final URL url = cl.getResource("test.keystore");
        final KeyStore keystore  = KeyStore.getInstance("jks");
        keystore.load(url.openStream(), "nopassword".toCharArray());
        final TrustManagerFactory tmfactory = createTrustManagerFactory();
        tmfactory.init(keystore);
        final TrustManager[] trustmanagers = tmfactory.getTrustManagers();
        final SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, trustmanagers, null);
        return sslcontext;
    }

}
