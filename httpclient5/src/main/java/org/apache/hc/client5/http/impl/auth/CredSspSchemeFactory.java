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

package org.apache.hc.client5.http.impl.auth;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeProvider;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.SSLInitializationException;

@Experimental
public class CredSspSchemeFactory implements AuthSchemeProvider
{

    private final SSLContext sslContext;

    public CredSspSchemeFactory() {
        this(createDefaultContext());
    }

    public CredSspSchemeFactory(final SSLContext sslContext) {
        this.sslContext = sslContext != null ? sslContext : createDefaultContext();
    }

    private static SSLContext createDefaultContext() throws SSLInitializationException {
        try {
            return SSLContexts.custom()
                    .loadTrustMaterial(new TrustAllStrategy())
                    .build();
        } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException ex) {
            throw new SSLInitializationException(ex.getMessage(), ex);
        }
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new CredSspScheme(sslContext);
    }

}
