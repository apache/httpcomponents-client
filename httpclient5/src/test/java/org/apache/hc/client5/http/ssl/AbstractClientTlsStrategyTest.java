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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.Test;

class AbstractClientTlsStrategyTest {

    @Test
    void testToEscapedString_withControlCharacters() {
        // Create a X500Principal with control characters
        final X500Principal principal = new X500Principal("CN=Test\b\bName\n,O=TestOrg");

        // Create a mock subclass of AbstractClientTlsStrategy
        final AbstractClientTlsStrategy tlsStrategy = new AbstractClientTlsStrategy(
                SSLContexts.createDefault(),
                null, null, SSLBufferMode.STATIC,
                HostnameVerificationPolicy.BUILTIN,
                HttpsSupport.getDefaultHostnameVerifier()) {
            @Override
            void applyParameters(final SSLEngine sslEngine, final SSLParameters sslParameters, final String[] appProtocols) {
                // No-op for test
            }

            @Override
            TlsDetails createTlsDetails(final SSLEngine sslEngine) {
                return null;  // No-op for test
            }
        };

        // Call the toEscapedString method
        final String escaped = tlsStrategy.toEscapedString(principal);

        // Assert that control characters are properly escaped
        assertEquals("CN=Test\\x08\\x08Name\\x0a,O=TestOrg", escaped);
    }

    @Test
    void testVerifySession_escapedPeerAndIssuer() throws Exception {
        // Mock SSLSession and X509Certificate
        final SSLSession mockSession = mock(SSLSession.class);
        final X509Certificate mockCert = mock(X509Certificate.class);

        // Create a mock X500Principal with control characters
        final X500Principal peerPrincipal = new X500Principal("CN=Peer\bName,O=PeerOrg");
        final X500Principal issuerPrincipal = new X500Principal("CN=Issuer\bName,O=IssuerOrg");

        when(mockSession.getPeerCertificates()).thenReturn(new X509Certificate[]{mockCert});
        when(mockCert.getSubjectX500Principal()).thenReturn(peerPrincipal);
        when(mockCert.getIssuerX500Principal()).thenReturn(issuerPrincipal);

        // Create a mock subclass of AbstractClientTlsStrategy
        final AbstractClientTlsStrategy tlsStrategy = new AbstractClientTlsStrategy(
                SSLContexts.createDefault(),
                null, null, SSLBufferMode.STATIC,
                HostnameVerificationPolicy.BUILTIN,
                HttpsSupport.getDefaultHostnameVerifier()) {
            @Override
            void applyParameters(final SSLEngine sslEngine, final SSLParameters sslParameters, final String[] appProtocols) {
                // No-op for test
            }

            @Override
            TlsDetails createTlsDetails(final SSLEngine sslEngine) {
                return null;  // No-op for test
            }
        };

        // Test the verifySession method
        tlsStrategy.verifySession("localhost", mockSession, null);

    }


}