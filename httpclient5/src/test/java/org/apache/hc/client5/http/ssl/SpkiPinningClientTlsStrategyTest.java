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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.net.IDN;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

class SpkiPinningClientTlsStrategyTest {

    private static String sha256Pin(final byte[] spki) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
        return "sha256/" + Base64.getEncoder().encodeToString(digest);
    }

    @Test
    void exactHostMatch() throws Exception {
        final byte[] spki = new byte[]{1, 2, 3, 4, 5};
        final String pin = sha256Pin(spki);

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com", pin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(spki));
        assertDoesNotThrow(() -> strategy.enforcePins("api.example.com", session));
    }

    @Test
    void wildcardMatch() throws Exception {
        final byte[] spki = new byte[]{9, 9, 9, 9};
        final String pin = sha256Pin(spki);

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("*.example.com", pin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(spki));
        assertDoesNotThrow(() -> strategy.enforcePins("svc.example.com", session));
    }

    @Test
    void pinningFailure() throws Exception {
        final byte[] spki = new byte[]{7, 7, 7};
        final String wrongPin = sha256Pin(new byte[]{8, 8, 8});

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com", wrongPin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(spki));
        assertThrows(SSLException.class, () -> strategy.enforcePins("api.example.com", session));
    }

    @Test
    void wildcardDoesNotMatchMultiLabel() throws Exception {
        final byte[] spki = new byte[]{4, 2, 4, 2};
        final String pin = sha256Pin(spki);

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("*.example.com", pin)
                .build();

        // a.b.example.com should NOT match single-label wildcard -> no pinning enforced -> no throw
        final SSLSession session = new FakeSession(new X509WithKey(new byte[]{1, 2, 3}));
        assertDoesNotThrow(() -> strategy.enforcePins("a.b.example.com", session));
    }

    @Test
    void backupPinSucceedsWhenFirstPinDoesNotMatch() throws Exception {
        final byte[] spkiGood = new byte[]{10, 11, 12, 13};
        final byte[] spkiBad = new byte[]{99, 99, 99, 99};
        final String wrongPin = sha256Pin(spkiBad);
        final String goodPin = sha256Pin(spkiGood);

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                // wrong pin first, correct pin second
                .add("api.example.com", wrongPin, goodPin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(spkiGood));
        assertDoesNotThrow(() -> strategy.enforcePins("api.example.com", session));
    }

    @Test
    void idnExactHostMatch() throws Exception {
        // Host: bücher.example -> xn--bcher-kva.example
        final byte[] spki = new byte[]{42, 42, 42, 42};
        final String pin = sha256Pin(spki);

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("bücher.example", pin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(spki));
        // enforcePins expects IDNA ASCII (like verifySession would pass)
        final String ascii = IDN.toASCII("bücher.example").toLowerCase(Locale.ROOT);
        assertDoesNotThrow(() -> strategy.enforcePins(ascii, session));
    }

    @Test
    void invalidBase64PinRejected() {
        assertThrows(IllegalArgumentException.class, () -> SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com", "sha256/###not_base64###"));
    }

    @Test
    void wrongLengthPinRejected() {
        // Base64 of 1 byte -> decoded length != 32
        final String shortPin = "sha256/" + Base64.getEncoder().encodeToString(new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com", shortPin));
    }

    @Test
    void emptyPinsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com"));
    }

    @Test
    void invalidWildcardPatternRejected() {
        // "*.": not a valid single-label wildcard
        assertThrows(IllegalArgumentException.class, () -> SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("*.", "sha256/" + Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void wildcardConfiguredButWrongPinFails() throws Exception {
        final byte[] spki = new byte[]{5, 5, 5, 5};
        final String wrongPin = sha256Pin(new byte[]{6, 6, 6, 6});

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("*.example.com", wrongPin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(spki));
        assertThrows(SSLException.class, () -> strategy.enforcePins("svc.example.com", session));
    }

    @Test
    void noConfiguredPinsForHostShortCircuits() throws Exception {
        // Pins configured for other domain, not for foo.bar
        final byte[] spki = new byte[]{1, 1, 1, 1};
        final String pin = sha256Pin(spki);

        final SpkiPinningClientTlsStrategy strategy = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com", pin)
                .build();

        final SSLSession session = new FakeSession(new X509WithKey(new byte[]{9, 9, 9}));
        // No rules match -> pinning not enforced -> no throw
        assertDoesNotThrow(() -> strategy.enforcePins("foo.bar", session));
    }

    @Test
    void verifySessionInvalidIdnThrowsSslException() throws NoSuchAlgorithmException {
        final SpkiPinningClientTlsStrategy s = SpkiPinningClientTlsStrategy
                .newBuilder(SSLContext.getDefault())
                .add("api.example.com", "sha256/" + Base64.getEncoder().encodeToString(new byte[32]))
                .build();
        final SSLSession session = new FakeSession(new X509WithKey(new byte[]{1}));
        assertThrows(SSLException.class, () -> s.verifySession("\uDC00bad", session));
    }

    @SuppressWarnings("deprecation")
    private static final class X509WithKey extends X509Certificate {
        private final PublicKey key;

        X509WithKey(final byte[] spki) {
            this.key = new PublicKey() {
                @Override
                public String getAlgorithm() {
                    return "RSA";
                }

                @Override
                public String getFormat() {
                    return "X.509";
                }

                @Override
                public byte[] getEncoded() {
                    return spki;
                }
            };
        }

        @Override
        public PublicKey getPublicKey() {
            return key;
        }

        @Override
        public void checkValidity() {
        }

        @Override
        public void checkValidity(final Date date) {
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return new X500Principal("CN=issuer");
        }

        @Override
        public Principal getSubjectDN() {
            return new X500Principal("CN=subject");
        }

        @Override
        public Date getNotBefore() {
            return new Date(0L);
        }

        @Override
        public Date getNotAfter() {
            return new Date(4102444800000L);
        } // ~2100-01-01

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "NONE";
        }

        @Override
        public String getSigAlgOID() {
            return "0.0";
        }

        @Override
        public byte[] getSigAlgParams() {
            return null;
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(final PublicKey key) {
        }

        @Override
        public void verify(final PublicKey key, final String sigProvider) {
        }

        @Override
        public String toString() {
            return "X509WithKey";
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return new X500Principal("CN=issuer");
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return new X500Principal("CN=subject");
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(final String oid) {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static final class FakeSession implements SSLSession {
        private final X509Certificate[] chain;

        FakeSession(final X509Certificate cert) {
            this.chain = new X509Certificate[]{cert};
        }

        @Override
        public Certificate[] getPeerCertificates() {
            return chain;
        }

        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getProtocol() {
            return "TLSv1.3";
        }

        @Override
        public String getCipherSuite() {
            return "TLS_AES_128_GCM_SHA256";
        }

        @Override
        public Principal getPeerPrincipal() {
            return null;
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public java.security.cert.Certificate[] getLocalCertificates() {
            return new java.security.cert.Certificate[0];
        }

        @Override
        public String getPeerHost() {
            return "api.example.com";
        }

        @Override
        public int getPeerPort() {
            return 443;
        }

        @Override
        public int getPacketBufferSize() {
            return 0;
        }

        @Override
        public int getApplicationBufferSize() {
            return 0;
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public Object getValue(final String s) {
            return null;
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public void putValue(final String s, final Object o) {
        }

        @Override
        public void removeValue(final String s) {
        }

        @Override
        public javax.net.ssl.SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public byte[] getId() {
            return new byte[0];
        }
    }
}
