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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.annotation.Immutable;
import org.apache.http.conn.util.InetAddressUtils;

/**
 * Default {@link javax.net.ssl.HostnameVerifier} implementation.
 *
 * @since 4.4
 */
@Immutable
public final class DefaultHostnameVerifier implements HostnameVerifier {

    public static final DefaultHostnameVerifier INSTANCE = new DefaultHostnameVerifier();

    private final static Pattern WILDCARD_PATTERN = Pattern.compile(
            "^[a-z0-9\\-\\*]+(\\.[a-z0-9\\-]+){2,}$",
            Pattern.CASE_INSENSITIVE);
    /**
     * This contains a list of 2nd-level domains that aren't allowed to
     * have wildcards when combined with country-codes.
     * For example: [*.co.uk].
     * <p/>
     * The [*.co.uk] problem is an interesting one.  Should we just hope
     * that CA's would never foolishly allow such a certificate to happen?
     * Looks like we're the only implementation guarding against this.
     * Firefox, Curl, Sun Java 1.4, 5, 6 don't bother with this check.
     */
    private final static Pattern BAD_COUNTRY_WILDCARD_PATTERN = Pattern.compile(
            "^[a-z0-9\\-\\*]+\\.(ac|co|com|ed|edu|go|gouv|gov|info|lg|ne|net|or|org)\\.[a-z0-9\\-]{2}$",
            Pattern.CASE_INSENSITIVE);

    final static int DNS_NAME_TYPE        = 2;
    final static int IP_ADDRESS_TYPE      = 7;

    private final Log log = LogFactory.getLog(getClass());

    @Override
    public final boolean verify(final String host, final SSLSession session) {
        try {
            final Certificate[] certs = session.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            verify(host, x509);
            return true;
        } catch(final SSLException ex) {
            if (log.isDebugEnabled()) {
                log.debug(ex.getMessage(), ex);
            }
            return false;
        }
    }

    public final void verify(
            final String host, final X509Certificate cert) throws SSLException {
        final boolean ipv4 = InetAddressUtils.isIPv4Address(host);
        final boolean ipv6 = InetAddressUtils.isIPv6Address(host);
        final int subjectType = ipv4 || ipv6 ? IP_ADDRESS_TYPE : DNS_NAME_TYPE;
        final List<String> subjectAlts = extractSubjectAlts(cert, subjectType);
        if (subjectAlts != null && !subjectAlts.isEmpty()) {
            if (ipv4) {
                matchIPAddress(host, subjectAlts);
            } else if (ipv6) {
                matchIPv6Address(host, subjectAlts);
            } else {
                matchDNSName(host, subjectAlts);
            }
        } else {
            // CN matching has been deprecated by rfc2818 and can be used
            // as fallback only when no subjectAlts are available
            final X500Principal subjectPrincipal = cert.getSubjectX500Principal();
            final String cn = extractCN(subjectPrincipal.getName(X500Principal.RFC2253));
            if (cn == null) {
                throw new SSLException("Certificate subject for <" + host + "> doesn't contain " +
                        "a common name and does not have alternative names");
            }
            matchCN(host, cn);
        }
    }

    static void matchIPAddress(final String host, final List<String> subjectAlts) throws SSLException {
        for (int i = 0; i < subjectAlts.size(); i++) {
            final String subjectAlt = subjectAlts.get(i);
            if (host.equals(subjectAlt)) {
                return;
            }
        }
        throw new SSLException("Certificate for <" + host + "> doesn't match any " +
                "of the subject alternative names: " + subjectAlts);
    }

    static void matchIPv6Address(final String host, final List<String> subjectAlts) throws SSLException {
        final String normalisedHost = normaliseAddress(host);
        for (int i = 0; i < subjectAlts.size(); i++) {
            final String subjectAlt = subjectAlts.get(i);
            final String normalizedSubjectAlt = normaliseAddress(subjectAlt);
            if (normalisedHost.equals(normalizedSubjectAlt)) {
                return;
            }
        }
        throw new SSLException("Certificate for <" + host + "> doesn't match any " +
                "of the subject alternative names: " + subjectAlts);
    }

    static void matchDNSName(final String host, final List<String> subjectAlts) throws SSLException {
        for (int i = 0; i < subjectAlts.size(); i++) {
            final String subjectAlt = subjectAlts.get(i);
            if (matchIdentityStrict(host, subjectAlt)) {
                return;
            }
        }
        throw new SSLException("Certificate for <" + host + "> doesn't match any " +
                "of the subject alternative names: " + subjectAlts);
    }

    static void matchCN(final String host, final String cn) throws SSLException {
        if (!matchIdentityStrict(host, cn)) {
            throw new SSLException("Certificate for <" + host + "> doesn't match " +
                    "common name of the certificate subject: " + cn);
        }
    }

    private static boolean matchIdentity(final String host, final String identity, final boolean strict) {
        if (host == null) {
            return false;
        }
        // The CN better have at least two dots if it wants wildcard
        // action.  It also can't be [*.co.uk] or [*.co.jp] or
        // [*.org.uk], etc...
        if (identity.contains("*") && WILDCARD_PATTERN.matcher(identity).matches()) {
            if (!strict || !BAD_COUNTRY_WILDCARD_PATTERN.matcher(identity).matches()) {
                final StringBuilder buf = new StringBuilder();
                buf.append("^");
                for (int i = 0; i < identity.length(); i++) {
                    final char ch = identity.charAt(i);
                    if (ch == '.') {
                        buf.append("\\.");
                    } else if (ch == '*') {
                        if (strict) {
                            buf.append("[a-z0-9\\-]*");
                        } else {
                            buf.append(".*");
                        }
                    } else {
                        buf.append(ch);
                    }
                }
                buf.append("$");
                try {
                    final Pattern identityPattern = Pattern.compile(buf.toString(), Pattern.CASE_INSENSITIVE);
                    return identityPattern.matcher(host).matches();
                } catch (PatternSyntaxException ignore) {
                    // do simple match
                }
            }
        }
        return host.equalsIgnoreCase(identity);
    }

    static boolean matchIdentity(final String host, final String identity) {
        return matchIdentity(host, identity, false);
    }

    static boolean matchIdentityStrict(final String host, final String identity) {
        return matchIdentity(host, identity, true);
    }

    static String extractCN(final String subjectPrincipal) throws SSLException {
        if (subjectPrincipal == null) {
            return null;
        }
        try {
            final LdapName subjectDN = new LdapName(subjectPrincipal);
            final List<Rdn> rdns = subjectDN.getRdns();
            for (int i = rdns.size() - 1; i >= 0; i--) {
                final Rdn rds = rdns.get(i);
                final Attributes attributes = rds.toAttributes();
                final Attribute cn = attributes.get("cn");
                if (cn != null) {
                    try {
                        final Object value = cn.get();
                        if (value != null) {
                            return value.toString();
                        }
                    } catch (NoSuchElementException ignore) {
                    } catch (NamingException ignore) {
                    }
                }
            }
            return null;
        } catch (InvalidNameException e) {
            throw new SSLException(subjectPrincipal + " is not a valid X500 distinguished name");
        }
    }

    static List<String> extractSubjectAlts(final X509Certificate cert, final int subjectType) {
        Collection<List<?>> c = null;
        try {
            c = cert.getSubjectAlternativeNames();
        } catch(final CertificateParsingException ignore) {
        }
        List<String> subjectAltList = null;
        if (c != null) {
            for (final List<?> aC : c) {
                final List<?> list = aC;
                final int type = ((Integer) list.get(0)).intValue();
                if (type == subjectType) {
                    final String s = (String) list.get(1);
                    if (subjectAltList == null) {
                        subjectAltList = new ArrayList<String>();
                    }
                    subjectAltList.add(s);
                }
            }
        }
        return subjectAltList;
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
}
