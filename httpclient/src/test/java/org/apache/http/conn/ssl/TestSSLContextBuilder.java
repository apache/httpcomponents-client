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

import java.net.URL;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import org.apache.http.localserver.LocalServerTestBase;
import org.junit.Test;

/**
 * Unit tests for {@link org.apache.http.conn.ssl.SSLContextBuilder}.
 */
public class TestSSLContextBuilder extends LocalServerTestBase {

    @Test
    public void testKeyWithAlternatePassword() throws Exception {
        final String keystorePassword = "nopassword";
        final String keyPassword = "password";

        final ClassLoader cl = getClass().getClassLoader();
        final URL url = cl.getResource("test-keypasswd.keystore");
        final KeyStore keystore  = KeyStore.getInstance("jks");
        keystore.load(url.openStream(), keystorePassword.toCharArray());

        new SSLContextBuilder()
                .loadKeyMaterial(keystore, keyPassword != null ? keyPassword.toCharArray() : null)
                .loadTrustMaterial(keystore)
                .build();
    }

    @Test(expected=UnrecoverableKeyException.class)
    public void testKeyWithAlternatePasswordInvalid() throws Exception {
        final String keystorePassword = "nopassword";
        final String keyPassword = "!password";

        final ClassLoader cl = getClass().getClassLoader();
        final URL url = cl.getResource("test-keypasswd.keystore");
        final KeyStore keystore  = KeyStore.getInstance("jks");
        keystore.load(url.openStream(), keystorePassword.toCharArray());

        new SSLContextBuilder()
                .loadKeyMaterial(keystore, keyPassword != null ? keyPassword.toCharArray() : null)
                .loadTrustMaterial(keystore)
                .build();
    }

}
