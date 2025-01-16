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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.psl.DomainType;
import org.apache.hc.client5.http.psl.PublicSuffixList;
import org.apache.hc.client5.http.psl.PublicSuffixListParser;
import org.apache.hc.client5.http.psl.PublicSuffixMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link org.apache.hc.client5.http.ssl.DefaultHostnameVerifier}.
 */
class TestDefaultHostnameVerifier {

    private DefaultHostnameVerifier impl;
    private PublicSuffixMatcher publicSuffixMatcher;

    private static final String PUBLIC_SUFFIX_MATCHER_SOURCE_FILE = "suffixlistmatcher.txt";

    @BeforeEach
    void setup() throws IOException {
        impl = new DefaultHostnameVerifier();

        // Load the test PublicSuffixMatcher
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream(PUBLIC_SUFFIX_MATCHER_SOURCE_FILE);
        Assertions.assertNotNull(in);
        final List<PublicSuffixList> lists = PublicSuffixListParser.INSTANCE.parseByType(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        publicSuffixMatcher = new PublicSuffixMatcher(lists);
    }

    @Test
    void testVerify() throws Exception {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream in;
        X509Certificate x509;
        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);

        impl.verify("foo.com", x509);
        exceptionPlease(impl, "a.foo.com", x509);
        exceptionPlease(impl, "bar.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("\u82b1\u5b50.co.jp", x509);
        exceptionPlease(impl, "a.\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_FOO_BAR);
        x509 = (X509Certificate) cf.generateCertificate(in);
        exceptionPlease(impl, "foo.com", x509);
        exceptionPlease(impl, "a.foo.com", x509);
        impl.verify("bar.com", x509);
        exceptionPlease(impl, "a.bar.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_FOO_BAR_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        exceptionPlease(impl, "foo.com", x509);
        exceptionPlease(impl, "a.foo.com", x509);
        impl.verify("bar.com", x509);
        exceptionPlease(impl, "a.bar.com", x509);

        /*
           Java isn't extracting international subjectAlts properly.  (Or
           OpenSSL isn't storing them properly).
        */
        // DEFAULT.verify("\u82b1\u5b50.co.jp", x509 );
        // impl.verify("\u82b1\u5b50.co.jp", x509 );
        exceptionPlease(impl, "a.\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_NO_CNS_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("foo.com", x509);
        exceptionPlease(impl, "a.foo.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_NO_CNS_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("foo.com", x509);
        exceptionPlease(impl, "a.foo.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_THREE_CNS_FOO_BAR_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        exceptionPlease(impl, "foo.com", x509);
        exceptionPlease(impl, "a.foo.com", x509);
        exceptionPlease(impl, "bar.com", x509);
        exceptionPlease(impl, "a.bar.com", x509);
        impl.verify("\u82b1\u5b50.co.jp", x509);
        exceptionPlease(impl, "a.\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_WILD_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        exceptionPlease(impl, "foo.com", x509);
        impl.verify("www.foo.com", x509);
        impl.verify("\u82b1\u5b50.foo.com", x509);
        exceptionPlease(impl, "a.b.foo.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_WILD_CO_JP);
        x509 = (X509Certificate) cf.generateCertificate(in);
        // Silly test because no-one would ever be able to lookup an IP address
        // using "*.co.jp".
        impl.verify("*.co.jp", x509);
        impl.verify("foo.co.jp", x509);
        impl.verify("\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_WILD_FOO_BAR_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        // try the foo.com variations
        exceptionPlease(impl, "foo.com", x509);
        exceptionPlease(impl, "www.foo.com", x509);
        exceptionPlease(impl, "\u82b1\u5b50.foo.com", x509);
        exceptionPlease(impl, "a.b.foo.com", x509);
        // try the bar.com variations
        exceptionPlease(impl, "bar.com", x509);
        impl.verify("www.bar.com", x509);
        impl.verify("\u82b1\u5b50.bar.com", x509);
        exceptionPlease(impl, "a.b.bar.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_MULTIPLE_VALUE_AVA);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("repository.infonotary.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.S_GOOGLE_COM);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("*.google.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.S_GOOGLE_COM);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("*.Google.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.IP_1_1_1_1);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("1.1.1.1", x509);
        impl.verify("dummy-value.com", x509);

        exceptionPlease(impl, "1.1.1.2", x509);
        exceptionPlease(impl, "not-the-cn.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.EMAIL_ALT_SUBJECT_NAME);
        x509 = (X509Certificate) cf.generateCertificate(in);
        impl.verify("www.company.com", x509);
    }

    @Test
    void testSubjectAlt() throws Exception {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        final InputStream in = new ByteArrayInputStream(CertificatesToPlayWith.X509_MULTIPLE_SUBJECT_ALT);
        final X509Certificate x509 = (X509Certificate) cf.generateCertificate(in);

        Assertions.assertEquals("CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=CH",
                x509.getSubjectDN().getName());

        impl.verify("localhost.localdomain", x509);
        impl.verify("127.0.0.1", x509);

        Assertions.assertThrows(SSLException.class, () -> impl.verify("localhost", x509));
        Assertions.assertThrows(SSLException.class, () -> impl.verify("local.host", x509));
        Assertions.assertThrows(SSLException.class, () -> impl.verify("127.0.0.2", x509));
    }

    public void exceptionPlease(final DefaultHostnameVerifier hv, final String host,
                                final X509Certificate x509) {
        Assertions.assertThrows(SSLException.class, () -> hv.verify(host, x509));
    }

    @Test
    void testParseFQDN() {
        Assertions.assertEquals(Arrays.asList("blah"),
                DefaultHostnameVerifier.parseFQDN("blah"));
        Assertions.assertEquals(Arrays.asList("blah", "blah"),
                DefaultHostnameVerifier.parseFQDN("blah.blah"));
        Assertions.assertEquals(Arrays.asList("blah", "blah", "blah"),
                DefaultHostnameVerifier.parseFQDN("blah.blah.blah"));
        Assertions.assertEquals(Arrays.asList("", "", "blah", ""),
                DefaultHostnameVerifier.parseFQDN(".blah.."));
        Assertions.assertEquals(Arrays.asList(""),
                DefaultHostnameVerifier.parseFQDN(""));
        Assertions.assertEquals(Arrays.asList("", ""),
                DefaultHostnameVerifier.parseFQDN("."));
        Assertions.assertEquals(Arrays.asList("com", "domain", "host"),
                DefaultHostnameVerifier.parseFQDN("host.domain.com"));
    }

    @Test
    void testDomainRootMatching() {
        Assertions.assertFalse(DefaultHostnameVerifier.matchDomainRoot("a.b.c", null));
        Assertions.assertTrue(DefaultHostnameVerifier.matchDomainRoot("a.b.c", "a.b.c"));
        Assertions.assertFalse(DefaultHostnameVerifier.matchDomainRoot("aa.b.c", "a.b.c"));
        Assertions.assertFalse(DefaultHostnameVerifier.matchDomainRoot("a.b.c", "aa.b.c"));
        Assertions.assertTrue(DefaultHostnameVerifier.matchDomainRoot("a.a.b.c", "a.b.c"));
    }

    @Test
    void testIdentityMatching() {

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.b.c", "*.b.c"));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.b.c", "*.b.c"));

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("s.a.b.c", "*.b.c"));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("s.a.b.c", "*.b.c")); // subdomain not OK

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.gov.uk", "*.gov.uk", publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.gov.uk", "*.gov.uk", publicSuffixMatcher));  // Bad 2TLD

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("s.a.gov.uk", "*.a.gov.uk", publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("s.a.gov.uk", "*.a.gov.uk", publicSuffixMatcher));

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("s.a.gov.uk", "*.gov.uk", publicSuffixMatcher));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("s.a.gov.uk", "*.gov.uk", publicSuffixMatcher));  // BBad 2TLD/no subdomain allowed

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.gov.com", "*.gov.com", publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.gov.com", "*.gov.com", publicSuffixMatcher));

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("s.a.gov.com", "*.gov.com", publicSuffixMatcher));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("s.a.gov.com", "*.gov.com", publicSuffixMatcher)); // no subdomain allowed

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.gov.uk", "a*.gov.uk", publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.gov.uk", "a*.gov.uk", publicSuffixMatcher)); // Bad 2TLD

        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("s.a.gov.uk", "a*.gov.uk", publicSuffixMatcher)); // Bad 2TLD
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("s.a.gov.uk", "a*.gov.uk", publicSuffixMatcher)); // Bad 2TLD/no subdomain allowed

        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("a.b.c", "*.b.*"));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("a.b.c", "*.b.*"));

        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("a.b.c", "*.*.c"));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("a.b.c", "*.*.c"));

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.b.xxx.uk", "a.b.xxx.uk", publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.b.xxx.uk", "a.b.xxx.uk", publicSuffixMatcher));

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.b.xxx.uk", "*.b.xxx.uk", publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.b.xxx.uk", "*.b.xxx.uk", publicSuffixMatcher));

        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("b.xxx.uk", "b.xxx.uk", publicSuffixMatcher));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("b.xxx.uk", "b.xxx.uk", publicSuffixMatcher));

        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("b.xxx.uk", "*.xxx.uk", publicSuffixMatcher));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("b.xxx.uk", "*.xxx.uk", publicSuffixMatcher));
    }

    @Test
    void testHTTPCLIENT_1097() {
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.b.c", "a*.b.c"));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("a.b.c", "a*.b.c"));

        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("a.a.b.c", "a*.b.c"));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("a.a.b.c", "a*.b.c"));
    }

    @Test
    void testHTTPCLIENT_1255() {
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("mail.a.b.c.com", "m*.a.b.c.com"));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("mail.a.b.c.com", "m*.a.b.c.com"));
    }

    @Test
    void testHTTPCLIENT_1997_ANY() { // Only True on all domains
        String domain;
        // Unknown
        domain = "dev.b.cloud.a";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher));

        // ICANN
        domain = "dev.b.cloud.com";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher));

        // PRIVATE
        domain = "dev.b.cloud.lan";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher));
    }

    @Test
    void testHTTPCLIENT_1997_ICANN() { // Only True on ICANN domains
        String domain;
        // Unknown
        domain = "dev.b.cloud.a";
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.ICANN));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.ICANN));

        // ICANN
        domain = "dev.b.cloud.com";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.ICANN));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.ICANN));

        // PRIVATE
        domain = "dev.b.cloud.lan";
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.ICANN));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.ICANN));
    }

    @Test
    void testHTTPCLIENT_1997_PRIVATE() { // Only True on PRIVATE domains
        String domain;
        // Unknown
        domain = "dev.b.cloud.a";
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.PRIVATE));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.PRIVATE));

        // ICANN
        domain = "dev.b.cloud.com";
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.PRIVATE));
        Assertions.assertFalse(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.PRIVATE));

        // PRIVATE
        domain = "dev.b.cloud.lan";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.PRIVATE));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.PRIVATE));
    }

    @Test
    void testHTTPCLIENT_1997_UNKNOWN() { // Only True on all domains (same as ANY)
        String domain;
        // Unknown
        domain = "dev.b.cloud.a";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.UNKNOWN));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.UNKNOWN));

        // ICANN
        domain = "dev.b.cloud.com";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.UNKNOWN));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.UNKNOWN));

        // PRIVATE
        domain = "dev.b.cloud.lan";
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentity("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.UNKNOWN));
        Assertions.assertTrue(DefaultHostnameVerifier.matchIdentityStrict("service.apps." + domain, "*.apps." + domain, publicSuffixMatcher, DomainType.UNKNOWN));
    }

    // Check compressed IPv6 hostname matching
    @Test
    void testHTTPCLIENT_1316() throws Exception {
        final String host1 = "2001:0db8:aaaa:bbbb:cccc:0:0:0001";
        DefaultHostnameVerifier.matchIPv6Address(host1, Collections.singletonList(SubjectName.IP("2001:0db8:aaaa:bbbb:cccc:0:0:0001")));
        DefaultHostnameVerifier.matchIPv6Address(host1, Collections.singletonList(SubjectName.IP("2001:0db8:aaaa:bbbb:cccc::1")));
        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.matchIPv6Address(host1, Collections.singletonList(SubjectName.IP("2001:0db8:aaaa:bbbb:cccc::10"))));
        final String host2 = "2001:0db8:aaaa:bbbb:cccc::1";
        DefaultHostnameVerifier.matchIPv6Address(host2, Collections.singletonList(SubjectName.IP("2001:0db8:aaaa:bbbb:cccc:0:0:0001")));
        DefaultHostnameVerifier.matchIPv6Address(host2, Collections.singletonList(SubjectName.IP("2001:0db8:aaaa:bbbb:cccc::1")));
        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.matchIPv6Address(host2, Collections.singletonList(SubjectName.IP("2001:0db8:aaaa:bbbb:cccc::10"))));
    }

    @Test
    void testHTTPCLIENT_2149() throws Exception {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        final InputStream in = new ByteArrayInputStream(CertificatesToPlayWith.SUBJECT_ALT_IP_ONLY);
        final X509Certificate x509 = (X509Certificate) cf.generateCertificate(in);

        Assertions.assertEquals("CN=www.foo.com", x509.getSubjectDN().getName());

        impl.verify("127.0.0.1", x509);
        impl.verify("www.foo.com", x509);

        exceptionPlease(impl, "127.0.0.2", x509);
        exceptionPlease(impl, "www.bar.com", x509);
    }

    @Test
    void testExtractCN() throws Exception {
        Assertions.assertEquals("blah", DefaultHostnameVerifier.extractCN("cn=blah, ou=blah, o=blah"));
        Assertions.assertEquals("blah", DefaultHostnameVerifier.extractCN("cn=blah, cn=yada, cn=booh"));
        Assertions.assertEquals("blah", DefaultHostnameVerifier.extractCN("c = pampa ,  cn  =    blah    , ou = blah , o = blah"));
        Assertions.assertEquals("blah", DefaultHostnameVerifier.extractCN("cn=\"blah\", ou=blah, o=blah"));
        Assertions.assertEquals("blah  blah", DefaultHostnameVerifier.extractCN("cn=\"blah  blah\", ou=blah, o=blah"));
        Assertions.assertEquals("blah, blah", DefaultHostnameVerifier.extractCN("cn=\"blah, blah\", ou=blah, o=blah"));
        Assertions.assertEquals("blah, blah", DefaultHostnameVerifier.extractCN("cn=blah\\, blah, ou=blah, o=blah"));
        Assertions.assertEquals("blah", DefaultHostnameVerifier.extractCN("c = cn=uuh, cn=blah, ou=blah, o=blah"));
        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.extractCN("blah,blah"));
        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.extractCN("cn,o=blah"));
    }

    @Test
    void testMatchDNSName() throws Exception {
        DefaultHostnameVerifier.matchDNSName(
                "host.domain.com",
                Collections.singletonList(SubjectName.DNS("*.domain.com")),
                publicSuffixMatcher);
        DefaultHostnameVerifier.matchDNSName(
                "host.xx",
                Collections.singletonList(SubjectName.DNS("*.xx")),
                publicSuffixMatcher);
        DefaultHostnameVerifier.matchDNSName(
                "host.appspot.com",
                Collections.singletonList(SubjectName.DNS("*.appspot.com")),
                publicSuffixMatcher);
        DefaultHostnameVerifier.matchDNSName(
                "demo-s3-bucket.s3.eu-central-1.amazonaws.com",
                Collections.singletonList(SubjectName.DNS("*.s3.eu-central-1.amazonaws.com")),
                publicSuffixMatcher);
        DefaultHostnameVerifier.matchDNSName(
                "hostname-workspace-1.local",
                Collections.singletonList(SubjectName.DNS("hostname-workspace-1.local")),
                publicSuffixMatcher);

        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.matchDNSName(
                        "host.domain.com",
                        Collections.singletonList(SubjectName.DNS("some.other.com")),
                        publicSuffixMatcher));

        DefaultHostnameVerifier.matchDNSName(
                "host.ec2.compute-1.amazonaws.com",
                Collections.singletonList(SubjectName.DNS("host.ec2.compute-1.amazonaws.com")),
                publicSuffixMatcher);
        DefaultHostnameVerifier.matchDNSName(
                "host.ec2.compute-1.amazonaws.com",
                Collections.singletonList(SubjectName.DNS("*.ec2.compute-1.amazonaws.com")),
                publicSuffixMatcher);
        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.matchDNSName(
                        "ec2.compute-1.amazonaws.com",
                        Collections.singletonList(SubjectName.DNS("ec2.compute-1.amazonaws.com")),
                        publicSuffixMatcher));
        Assertions.assertThrows(SSLException.class, () ->
                DefaultHostnameVerifier.matchDNSName(
                        "ec2.compute-1.amazonaws.com",
                        Collections.singletonList(SubjectName.DNS("*.compute-1.amazonaws.com")),
                        publicSuffixMatcher));
    }

    @Test
    void testMatchIdentity() {
        // Test 1: IDN matching punycode
        final String unicodeHost1 = "поиск-слов.рф";
        final String punycodeHost1 = "xn----dtbqigoecuc.xn--p1ai";

        // These should now match, thanks to IDN.toASCII():
        Assertions.assertTrue(
                DefaultHostnameVerifier.matchIdentity(unicodeHost1, punycodeHost1),
                "Expected the Unicode host and its punycode to match"
        );

        // ‘example.com’ vs. an unrelated punycode domain should fail:
        Assertions.assertFalse(
                DefaultHostnameVerifier.matchIdentity("example.com", punycodeHost1),
                "Expected mismatch between example.com and xn----dtbqigoecuc.xn--p1ai"
        );

        // Test 2: Unicode host and Unicode identity
        final String unicodeHost2 = "пример.рф";
        final String unicodeIdentity2 = "пример.рф";
        Assertions.assertTrue(
                DefaultHostnameVerifier.matchIdentity(unicodeHost2, unicodeIdentity2),
                "Expected Unicode host and Unicode identity to match"
        );

        // Test 3: Punycode host and Unicode identity
        final String unicodeHost3 = "пример.рф";
        final String punycodeIdentity3 = "xn--e1afmkfd.xn--p1ai";
        Assertions.assertTrue(
                DefaultHostnameVerifier.matchIdentity(unicodeHost3, punycodeIdentity3),
                "Expected Unicode host and punycode identity to match"
        );

        // Test 4: Wildcard matching in the left-most label
        final String unicodeHost4 = "sub.пример.рф";
        final String unicodeIdentity4 = "*.пример.рф";
        Assertions.assertTrue(
                DefaultHostnameVerifier.matchIdentity(unicodeHost4, unicodeIdentity4),
                "Expected wildcard to match subdomain"
        );

        // Test 5: Invalid host
        final String invalidHost = "invalid_host";
        final String unicodeIdentity5 = "пример.рф";
        Assertions.assertFalse(
                DefaultHostnameVerifier.matchIdentity(invalidHost, unicodeIdentity5),
                "Expected invalid host to not match"
        );

        // Test 6: Invalid identity
        final String unicodeHost4b = "пример.рф";
        final String invalidIdentity = "xn--invalid-punycode";
        Assertions.assertFalse(
                DefaultHostnameVerifier.matchIdentity(unicodeHost4b, invalidIdentity),
                "Expected invalid identity to not match"
        );

        // Test 7: Mixed case comparison
        final String unicodeHost5 = "ПрИмеР.рф";
        final String unicodeIdentity6 = "пример.рф";
        Assertions.assertTrue(
                DefaultHostnameVerifier.matchIdentity(unicodeHost5, unicodeIdentity6),
                "Expected case-insensitive Unicode comparison to match"
        );


        // Test 8: Wildcard in the middle label (per RFC 2818, should match)
        final String unicodeHost6 = "sub.пример.рф";
        final String unicodeIdentity8 = "sub.*.рф";
        Assertions.assertTrue(
                DefaultHostnameVerifier.matchIdentity(unicodeHost6, unicodeIdentity8),
                "Expected wildcard in the middle label to match"
        );
    }


    @Test
    void testSimulatedByteProperties() throws Exception {
        // Simulated byte array for an IP address
        final byte[] ipAsByteArray = {1, 1, 1, 1}; // 1.1.1.1 in byte form

        final List<List<?>> entries = new ArrayList<>();
        final List<Object> entry = new ArrayList<>();
        entry.add(SubjectName.IP);
        entry.add(ipAsByteArray);
        entries.add(entry);

        // Mocking the certificate behavior
        final X509Certificate mockCert = generateX509Certificate(entries);

        final List<SubjectName> result = DefaultHostnameVerifier.getSubjectAltNames(mockCert, -1);
        Assertions.assertEquals(1, result.size(), "Should have one SubjectAltName");

        final SubjectName sn = result.get(0);
        Assertions.assertEquals(SubjectName.IP, sn.getType(), "Should be an IP type");
        // Here, you'll need logic to convert byte array to string for assertion
        Assertions.assertEquals("1.1.1.1", sn.getValue(), "IP address should match after conversion");
    }

    @Test
    void testSimulatedBytePropertiesIPv6() throws Exception {
        final byte[] ipv6AsByteArray = InetAddress.getByName("2001:db8:85a3::8a2e:370:7334").getAddress();
        // IPv6 2001:db8:85a3::8a2e:370:7334

        final List<List<?>> entries = new ArrayList<>();
        final List<Object> entry = new ArrayList<>();
        entry.add(SubjectName.IP);
        entry.add(ipv6AsByteArray);
        entries.add(entry);

        // Mocking the certificate behavior
        final X509Certificate mockCert = generateX509Certificate(entries);

        final List<SubjectName> result = DefaultHostnameVerifier.getSubjectAltNames(mockCert, -1);
        Assertions.assertEquals(1, result.size(), "Should have one SubjectAltName");

        final SubjectName sn = result.get(0);
        Assertions.assertEquals(SubjectName.IP, sn.getType(), "Should be an IP type");
        // Here, you'll need logic to convert byte array to string for assertion
        Assertions.assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", sn.getValue(), "IP address should match after conversion");
    }


    private X509Certificate generateX509Certificate(final List<List<?>> entries) {
        return new X509Certificate() {

            @Override
            public boolean hasUnsupportedCriticalExtension() {
                return false;
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
                return new byte[0];
            }

            @Override
            public byte[] getEncoded() throws CertificateEncodingException {
                return new byte[0];
            }

            @Override
            public void verify(final PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {

            }

            @Override
            public void verify(final PublicKey key, final String sigProvider) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {

            }

            @Override
            public String toString() {
                return "";
            }

            @Override
            public PublicKey getPublicKey() {
                return null;
            }

            @Override
            public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {

            }

            @Override
            public void checkValidity(final Date date) throws CertificateExpiredException, CertificateNotYetValidException {

            }

            @Override
            public int getVersion() {
                return 0;
            }

            @Override
            public BigInteger getSerialNumber() {
                return null;
            }

            @Override
            public Principal getIssuerDN() {
                return null;
            }

            @Override
            public Principal getSubjectDN() {
                return null;
            }

            @Override
            public Date getNotBefore() {
                return null;
            }

            @Override
            public Date getNotAfter() {
                return null;
            }

            @Override
            public byte[] getTBSCertificate() throws CertificateEncodingException {
                return new byte[0];
            }

            @Override
            public byte[] getSignature() {
                return new byte[0];
            }

            @Override
            public String getSigAlgName() {
                return "";
            }

            @Override
            public String getSigAlgOID() {
                return "";
            }

            @Override
            public byte[] getSigAlgParams() {
                return new byte[0];
            }

            @Override
            public boolean[] getIssuerUniqueID() {
                return new boolean[0];
            }

            @Override
            public boolean[] getSubjectUniqueID() {
                return new boolean[0];
            }

            @Override
            public boolean[] getKeyUsage() {
                return new boolean[0];
            }

            @Override
            public int getBasicConstraints() {
                return 0;
            }

            @Override
            public Collection<List<?>> getSubjectAlternativeNames() {
                return entries;
            }
        };

    }

}