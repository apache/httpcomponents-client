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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.apache.hc.client5.http.psl.PublicSuffixMatcher;
import org.apache.hc.client5.http.utils.DnsUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link javax.net.ssl.HostnameVerifier} implementation.
 *
 * @since 4.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class DefaultHostnameVerifier implements HttpClientHostnameVerifier {

    enum HostNameType {

        IPv4(7), IPv6(7), DNS(2);

        final int subjectType;

        HostNameType(final int subjectType) {
            this.subjectType = subjectType;
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHostnameVerifier.class);

    private final PublicSuffixMatcher publicSuffixMatcher;

    /**
     * Constructs new instance with a PublicSuffixMatcher.
     *
     * @param publicSuffixMatcher a PublicSuffixMatcher.
     */
    public DefaultHostnameVerifier(final PublicSuffixMatcher publicSuffixMatcher) {
        this.publicSuffixMatcher = publicSuffixMatcher;
    }

    /**
     * Constructs new instance without a PublicSuffixMatcher.
     */
    public DefaultHostnameVerifier() {
        this(null);
    }

    @Override
    public boolean verify(final String host, final SSLSession session) {
        try {
            final Certificate[] certs = session.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            verify(host, x509);
            return true;
        } catch (final SSLException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(ex.getMessage(), ex);
            }
            return false;
        }
    }

    @Override
    public void verify(final String host, final X509Certificate cert) throws SSLException {
        final HostNameType hostType = determineHostFormat(host);
        switch (hostType) {
        case IPv4:
            matchIPAddress(host, getSubjectAltNames(cert, SubjectName.IP));
            break;
        case IPv6:
            matchIPv6Address(host, getSubjectAltNames(cert, SubjectName.IP));
            break;
        default:
            final List<SubjectName> subjectAlts = getSubjectAltNames(cert, SubjectName.DNS);
            if (subjectAlts.isEmpty()) {
                // CN matching has been deprecated by rfc2818 and can be used
                // as fallback only when no subjectAlts of type SubjectName.DNS are available
                matchCN(host, cert, this.publicSuffixMatcher);
            } else {
                matchDNSName(host, subjectAlts, this.publicSuffixMatcher);
            }
        }
    }

    static void matchIPAddress(final String host, final List<SubjectName> subjectAlts) throws SSLPeerUnverifiedException {
        for (final SubjectName subjectAlt : subjectAlts) {
            if (subjectAlt.getType() == SubjectName.IP) {
                if (host.equals(subjectAlt.getValue())) {
                    return;
                }
            }
        }
        throw new SSLPeerUnverifiedException("Certificate for <" + host + "> doesn't match any " +
                "of the subject alternative names: " + subjectAlts);
    }

    static void matchIPv6Address(final String host, final List<SubjectName> subjectAlts) throws SSLPeerUnverifiedException {
        final String normalisedHost = normaliseAddress(host);
        for (final SubjectName subjectAlt : subjectAlts) {
            if (subjectAlt.getType() == SubjectName.IP) {
                final String normalizedSubjectAlt = normaliseAddress(subjectAlt.getValue());
                if (normalisedHost.equals(normalizedSubjectAlt)) {
                    return;
                }
            }
        }
        throw new SSLPeerUnverifiedException("Certificate for <" + host + "> doesn't match any " +
                "of the subject alternative names: " + subjectAlts);
    }

    static void matchDNSName(final String host, final List<SubjectName> subjectAlts,
                             final PublicSuffixMatcher publicSuffixMatcher) throws SSLPeerUnverifiedException {
        final String normalizedHost = DnsUtils.normalizeUnicode(host);
        for (final SubjectName subjectAlt : subjectAlts) {
            if (subjectAlt.getType() == SubjectName.DNS) {
                final String normalizedSubjectAlt = DnsUtils.normalizeUnicode(subjectAlt.getValue());
                if (matchIdentity(normalizedHost, normalizedSubjectAlt, publicSuffixMatcher, true)) {
                    return;
                }
            }
        }
        throw new SSLPeerUnverifiedException("Certificate for <" + host + "> doesn't match any " +
                "of the subject alternative names: " + subjectAlts);
    }

    static void matchCN(final String host, final X509Certificate cert,
                        final PublicSuffixMatcher publicSuffixMatcher) throws SSLException {
        final X500Principal subjectPrincipal = cert.getSubjectX500Principal();
        final String cn = extractCN(subjectPrincipal.getName(X500Principal.RFC2253));
        if (cn == null) {
            throw new SSLPeerUnverifiedException("Certificate subject for <" + host + "> doesn't contain " +
                    "a common name and does not have alternative names");
        }
        final String normalizedHost = DnsUtils.normalizeUnicode(host);
        final String normalizedCn = DnsUtils.normalizeUnicode(cn);
        if (!matchIdentity(normalizedHost, normalizedCn, publicSuffixMatcher, true)) {
            throw new SSLPeerUnverifiedException("Certificate for <" + host + "> doesn't match " +
                    "common name of the certificate subject: " + cn);
        }
    }

    static List<CharSequence> parseFQDN(final CharSequence s) {
        if (s == null) {
            return null;
        }
        final LinkedList<CharSequence> elements = new LinkedList<>();
        int pos = 0;
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            if (ch == '.') {
                elements.addFirst(s.subSequence(pos, i));
                pos = i + 1;
            }
        }
        elements.addFirst(s.subSequence(pos, s.length()));
        return elements;
    }

    static boolean matchDomainRoot(final String host, final String domainRoot) {
        if (domainRoot == null) {
            return false;
        }
        final List<CharSequence> hostElements = parseFQDN(host);
        final List<CharSequence> rootElements = parseFQDN(domainRoot);
        if (hostElements.size() >= rootElements.size()) {
            for (int i = 0; i < rootElements.size(); i++) {
                final CharSequence s1 = rootElements.get(i);
                final CharSequence s2 = hostElements.get(i);
                if (!s1.equals(s2)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    static boolean matchIdentity(final String host, final String identity,
                                         final PublicSuffixMatcher publicSuffixMatcher,
                                         final boolean strict) {
        if (publicSuffixMatcher != null && host.contains(".")) {
            if (!publicSuffixMatcher.verifyInternal(identity)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Public Suffix List verification failed for identity '{}'", identity);
                }
                return false;
            }
        }

        // RFC 2818, 3.1. Server Identity
        // "...Names may contain the wildcard
        // character * which is considered to match any single domain name
        // component or component fragment..."
        // Based on this statement presuming only singular wildcard is legal
        final int asteriskIdx = identity.indexOf('*');
        if (asteriskIdx != -1) {
            final String prefix = identity.substring(0, asteriskIdx);
            final String suffix = identity.substring(asteriskIdx + 1);

            if (!prefix.isEmpty() && !host.startsWith(prefix)) {
                return false;
            }
            if (!suffix.isEmpty() && !host.endsWith(suffix)) {
                return false;
            }
            // Additional sanity checks on content selected by wildcard can be done here
            if (strict) {
                final String remainder = host.substring(
                        prefix.length(),
                        host.length() - suffix.length()
                );
                return !remainder.contains(".");
            }
            return true;
        }

        // Direct Unicode comparison
        return host.equalsIgnoreCase(identity);
    }

    static String extractCN(final String subjectPrincipal) throws SSLException {
        if (subjectPrincipal == null) {
            return null;
        }
        final List<NameValuePair> attributes = DistinguishedNameParser.INSTANCE.parse(subjectPrincipal);
        for (final NameValuePair attribute: attributes) {
            if (TextUtils.isBlank(attribute.getName()) || attribute.getValue() == null) {
                throw new SSLException(subjectPrincipal + " is not a valid X500 distinguished name");
            }
            if (attribute.getName().equalsIgnoreCase("cn")) {
                return attribute.getValue();
            }
        }
        return null;
    }

    static HostNameType determineHostFormat(final String host) {
        if (InetAddressUtils.isIPv4(host)) {
            return HostNameType.IPv4;
        }
        String s = host;
        if (s.startsWith("[") && s.endsWith("]")) {
            s = host.substring(1, host.length() - 1);
        }
        if (InetAddressUtils.isIPv6(s)) {
            return HostNameType.IPv6;
        }
        return HostNameType.DNS;
    }

    static List<SubjectName> getSubjectAltNames(final X509Certificate cert) {
        return getSubjectAltNames(cert, -1);
    }

    static List<SubjectName> getSubjectAltNames(final X509Certificate cert, final int subjectName) {
        try {
            final Collection<List<?>> entries = cert.getSubjectAlternativeNames();
            if (entries == null) {
                return Collections.emptyList();
            }
            final List<SubjectName> result = new ArrayList<>();
            for (final List<?> entry : entries) {
                final Integer type = entry.size() >= 2 ? (Integer) entry.get(0) : null;
                if (type != null) {
                    if (type == subjectName || -1 == subjectName) {
                        final Object o = entry.get(1);
                        if (o instanceof String) {
                            result.add(new SubjectName((String) o, type));
                        } else if (o instanceof byte[]) {
                            final byte[] bytes = (byte[]) o;
                            if (type == SubjectName.IP) {
                                if (bytes.length == 4) {
                                    result.add(new SubjectName(byteArrayToIp(bytes), type)); // IPv4
                                } else if (bytes.length == 16) {
                                    result.add(new SubjectName(byteArrayToIPv6(bytes), type)); // IPv6
                                }
                            }
                        }
                    }
                }
            }
            return result;
        } catch (final CertificateParsingException ignore) {
            return Collections.emptyList();
        }
    }

    /*
     * Normalize IPv6 or DNS name.
     */
    static String normaliseAddress(final String hostname) {
        if (hostname == null) {
            return hostname;
        }
        try {
            final InetAddress inetAddress = InetAddress.getByName(hostname);
            return inetAddress.getHostAddress();
        } catch (final UnknownHostException unexpected) { // Should not happen, because we check for IPv6 address above
            return hostname;
        }
    }

    private static String byteArrayToIp(final byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Invalid byte array length for IPv4 address");
        }
        return (bytes[0] & 0xFF) + "." +
                (bytes[1] & 0xFF) + "." +
                (bytes[2] & 0xFF) + "." +
                (bytes[3] & 0xFF);
    }

    private static String byteArrayToIPv6(final byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Invalid byte array length for IPv6 address");
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += 2) {
            sb.append(String.format("%02x%02x", bytes[i], bytes[i + 1]));
            if (i < bytes.length - 2) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

}
