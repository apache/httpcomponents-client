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
package org.apache.http.impl.conn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;

/**
 * @since 4.1
 */
@ThreadSafe
public final class SchemeRegistryFactory {

    /**
     * Initializes default scheme registry based on JSSE defaults. System properties will
     * not be taken into consideration.
     */
    public static SchemeRegistry createDefault() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
        registry.register(
                new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));
        return registry;
    }

    private final static char[] EMPTY_PASSWORD = "".toCharArray();

    /**
     * Initializes default scheme registry using system properties as described in
     * <a href="http://download.oracle.com/javase/1,5.0/docs/guide/security/jsse/JSSERefGuide.html">
     * "JavaTM Secure Socket Extension (JSSE) Reference Guide for the JavaTM 2 Platform
     * Standard Edition 5</a>
     *
     * @since 4.2
     */
    public static SchemeRegistry createSystemDefault() throws IOException, GeneralSecurityException {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        TrustManagerFactory tmfactory = null;

        String trustAlgorithm = System.getProperty("ssl.TrustManagerFactory.algorithm");
        if (trustAlgorithm == null) {
            trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        }
        String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType");
        if (trustStoreType == null) {
            trustStoreType = KeyStore.getDefaultType();
        }
        if ("none".equalsIgnoreCase(trustStoreType)) {
            tmfactory = TrustManagerFactory.getInstance(trustAlgorithm);
        } else {
            File trustStoreFile = null;
            String s = System.getProperty("javax.net.ssl.trustStore");
            if (s != null) {
                trustStoreFile = new File(s);
                tmfactory = TrustManagerFactory.getInstance(trustAlgorithm);
                String trustStoreProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
                KeyStore trustStore;
                if (trustStoreProvider != null) {
                    trustStore = KeyStore.getInstance(trustStoreType, trustStoreProvider);
                } else {
                    trustStore = KeyStore.getInstance(trustStoreType);
                }
                String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
                FileInputStream instream = new FileInputStream(trustStoreFile);
                try {
                    trustStore.load(instream, trustStorePassword != null ?
                            trustStorePassword.toCharArray() : EMPTY_PASSWORD);
                } finally {
                    instream.close();
                }
                tmfactory.init(trustStore);
            } else {
                File javaHome = new File(System.getProperty("java.home"));
                File file = new File(javaHome, "lib/security/jssecacerts");
                if (!file.exists()) {
                    file = new File(javaHome, "lib/security/cacerts");
                    trustStoreFile = file;
                } else {
                    trustStoreFile = file;
                }
                tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
                if (trustStorePassword == null) {
                    trustStorePassword = "changeit";
                }
                FileInputStream instream = new FileInputStream(trustStoreFile);
                try {
                    trustStore.load(instream, trustStorePassword.toCharArray());
                } finally {
                    instream.close();
                }
                tmfactory.init(trustStore);
            }
        }

        KeyManagerFactory kmfactory = null;
        String keyAlgorithm = System.getProperty("ssl.KeyManagerFactory.algorithm");
        if (keyAlgorithm == null) {
            keyAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        }
        String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType");
        if (keyStoreType == null) {
            keyStoreType = KeyStore.getDefaultType();
        }
        if ("none".equalsIgnoreCase(keyStoreType)) {
            kmfactory = KeyManagerFactory.getInstance(keyAlgorithm);
        } else {
            File keyStoreFile = null;
            String s = System.getProperty("javax.net.ssl.keyStore");
            if (s != null) {
                keyStoreFile = new File(s);
            }
            if (keyStoreFile != null) {
                kmfactory = KeyManagerFactory.getInstance(keyAlgorithm);
                String keyStoreProvider = System.getProperty("javax.net.ssl.keyStoreProvider");
                KeyStore keyStore;
                if (keyStoreProvider != null) {
                    keyStore = KeyStore.getInstance(keyStoreType, keyStoreProvider);
                } else {
                    keyStore = KeyStore.getInstance(keyStoreType);
                }
                String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
                FileInputStream instream = new FileInputStream(keyStoreFile);
                try {
                    keyStore.load(instream, keyStorePassword != null ?
                            keyStorePassword.toCharArray() : EMPTY_PASSWORD);
                } finally {
                    instream.close();
                }
                kmfactory.init(keyStore, keyStorePassword != null ?
                        keyStorePassword.toCharArray() : EMPTY_PASSWORD);
            }
        }

        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(
                kmfactory != null ? kmfactory.getKeyManagers() : null,
                tmfactory != null ? tmfactory.getTrustManagers() : null,
                null);

        registry.register(
                new Scheme("https", 443, new SSLSocketFactory(sslcontext)));
        return registry;
    }
}

