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

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.slf4j.Logger;

final class TlsSessionValidator {

    private final Logger log;

    TlsSessionValidator(final Logger log) {
        this.log = log;
    }

    void verifySession(
            final String hostname,
            final SSLSession sslsession,
            final HostnameVerifier hostnameVerifier) throws SSLException {

        if (log.isDebugEnabled()) {
            log.debug("Secure session established");
            log.debug(" negotiated protocol: " + sslsession.getProtocol());
            log.debug(" negotiated cipher suite: " + sslsession.getCipherSuite());

            try {

                final Certificate[] certs = sslsession.getPeerCertificates();
                final Certificate cert = certs[0];
                if (cert instanceof X509Certificate) {
                    final X509Certificate x509 = (X509Certificate) cert;
                    final X500Principal peer = x509.getSubjectX500Principal();

                    log.debug(" peer principal: " + peer.toString());
                    final Collection<List<?>> altNames1 = x509.getSubjectAlternativeNames();
                    if (altNames1 != null) {
                        final List<String> altNames = new ArrayList<>();
                        for (final List<?> aC : altNames1) {
                            if (!aC.isEmpty()) {
                                altNames.add((String) aC.get(1));
                            }
                        }
                        log.debug(" peer alternative names: " + altNames);
                    }

                    final X500Principal issuer = x509.getIssuerX500Principal();
                    log.debug(" issuer principal: " + issuer.toString());
                    final Collection<List<?>> altNames2 = x509.getIssuerAlternativeNames();
                    if (altNames2 != null) {
                        final List<String> altNames = new ArrayList<>();
                        for (final List<?> aC : altNames2) {
                            if (!aC.isEmpty()) {
                                altNames.add((String) aC.get(1));
                            }
                        }
                        log.debug(" issuer alternative names: " + altNames);
                    }
                }
            } catch (final Exception ignore) {
            }
        }

        if (hostnameVerifier != null) {
            final Certificate[] certs = sslsession.getPeerCertificates();
            if (certs.length < 1) {
                throw new SSLPeerUnverifiedException("Peer certificate chain is empty");
            }
            final Certificate peerCertificate = certs[0];
            final X509Certificate x509Certificate;
            if (peerCertificate instanceof X509Certificate) {
                x509Certificate = (X509Certificate) peerCertificate;
            } else {
                throw new SSLPeerUnverifiedException("Unexpected certificate type: " + peerCertificate.getType());
            }
            if (hostnameVerifier instanceof HttpClientHostnameVerifier) {
                ((HttpClientHostnameVerifier) hostnameVerifier).verify(hostname, x509Certificate);
            } else if (!hostnameVerifier.verify(hostname, sslsession)) {
                final List<SubjectName> subjectAlts = DefaultHostnameVerifier.getSubjectAltNames(x509Certificate);
                throw new SSLPeerUnverifiedException("Certificate for <" + hostname + "> doesn't match any " +
                        "of the subject alternative names: " + subjectAlts);
            }
        }
    }

}
