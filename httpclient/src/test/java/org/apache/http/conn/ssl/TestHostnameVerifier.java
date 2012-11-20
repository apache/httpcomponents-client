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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Principal;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLException;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link X509HostnameVerifier}.
 */
public class TestHostnameVerifier {

    @Test
    public void testVerify() throws Exception {
        X509HostnameVerifier DEFAULT = new BrowserCompatHostnameVerifier();
        X509HostnameVerifier STRICT = new StrictHostnameVerifier();
        X509HostnameVerifier ALLOW_ALL = new AllowAllHostnameVerifier();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream in;
        X509Certificate x509;
        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);

        DEFAULT.verify("foo.com", x509);
        STRICT.verify("foo.com", x509);
        exceptionPlease(DEFAULT, "a.foo.com", x509);
        exceptionPlease(STRICT, "a.foo.com", x509);
        exceptionPlease(DEFAULT, "bar.com", x509);
        exceptionPlease(STRICT, "bar.com", x509);
        ALLOW_ALL.verify("foo.com", x509);
        ALLOW_ALL.verify("a.foo.com", x509);
        ALLOW_ALL.verify("bar.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        DEFAULT.verify("\u82b1\u5b50.co.jp", x509);
        STRICT.verify("\u82b1\u5b50.co.jp", x509);
        exceptionPlease(DEFAULT, "a.\u82b1\u5b50.co.jp", x509);
        exceptionPlease(STRICT, "a.\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_FOO_BAR);
        x509 = (X509Certificate) cf.generateCertificate(in);
        DEFAULT.verify("foo.com", x509);
        STRICT.verify("foo.com", x509);
        exceptionPlease(DEFAULT, "a.foo.com", x509);
        exceptionPlease(STRICT, "a.foo.com", x509);
        DEFAULT.verify("bar.com", x509);
        STRICT.verify("bar.com", x509);
        exceptionPlease(DEFAULT, "a.bar.com", x509);
        exceptionPlease(STRICT, "a.bar.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_FOO_BAR_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        DEFAULT.verify("foo.com", x509);
        STRICT.verify("foo.com", x509);
        exceptionPlease(DEFAULT, "a.foo.com", x509);
        exceptionPlease(STRICT, "a.foo.com", x509);
        DEFAULT.verify("bar.com", x509);
        STRICT.verify("bar.com", x509);
        exceptionPlease(DEFAULT, "a.bar.com", x509);
        exceptionPlease(STRICT, "a.bar.com", x509);

        /*
           Java isn't extracting international subjectAlts properly.  (Or
           OpenSSL isn't storing them properly).
        */
        // DEFAULT.verify("\u82b1\u5b50.co.jp", x509 );
        // STRICT.verify("\u82b1\u5b50.co.jp", x509 );
        exceptionPlease(DEFAULT, "a.\u82b1\u5b50.co.jp", x509);
        exceptionPlease(STRICT, "a.\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_NO_CNS_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        DEFAULT.verify("foo.com", x509);
        STRICT.verify("foo.com", x509);
        exceptionPlease(DEFAULT, "a.foo.com", x509);
        exceptionPlease(STRICT, "a.foo.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_NO_CNS_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        DEFAULT.verify("foo.com", x509);
        STRICT.verify("foo.com", x509);
        exceptionPlease(DEFAULT, "a.foo.com", x509);
        exceptionPlease(STRICT, "a.foo.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_THREE_CNS_FOO_BAR_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        exceptionPlease(DEFAULT, "foo.com", x509);
        exceptionPlease(STRICT, "foo.com", x509);
        exceptionPlease(DEFAULT, "a.foo.com", x509);
        exceptionPlease(STRICT, "a.foo.com", x509);
        exceptionPlease(DEFAULT, "bar.com", x509);
        exceptionPlease(STRICT, "bar.com", x509);
        exceptionPlease(DEFAULT, "a.bar.com", x509);
        exceptionPlease(STRICT, "a.bar.com", x509);
        DEFAULT.verify("\u82b1\u5b50.co.jp", x509);
        STRICT.verify("\u82b1\u5b50.co.jp", x509);
        exceptionPlease(DEFAULT, "a.\u82b1\u5b50.co.jp", x509);
        exceptionPlease(STRICT, "a.\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_WILD_FOO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        exceptionPlease(DEFAULT, "foo.com", x509);
        exceptionPlease(STRICT, "foo.com", x509);
        DEFAULT.verify("www.foo.com", x509);
        STRICT.verify("www.foo.com", x509);
        DEFAULT.verify("\u82b1\u5b50.foo.com", x509);
        STRICT.verify("\u82b1\u5b50.foo.com", x509);
        DEFAULT.verify("a.b.foo.com", x509);
        exceptionPlease(STRICT, "a.b.foo.com", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_WILD_CO_JP);
        x509 = (X509Certificate) cf.generateCertificate(in);
        // Silly test because no-one would ever be able to lookup an IP address
        // using "*.co.jp".
        DEFAULT.verify("*.co.jp", x509);
        STRICT.verify("*.co.jp", x509);
        exceptionPlease(DEFAULT, "foo.co.jp", x509);
        exceptionPlease(STRICT, "foo.co.jp", x509);
        exceptionPlease(DEFAULT, "\u82b1\u5b50.co.jp", x509);
        exceptionPlease(STRICT, "\u82b1\u5b50.co.jp", x509);

        in = new ByteArrayInputStream(CertificatesToPlayWith.X509_WILD_FOO_BAR_HANAKO);
        x509 = (X509Certificate) cf.generateCertificate(in);
        // try the foo.com variations
        exceptionPlease(DEFAULT, "foo.com", x509);
        exceptionPlease(STRICT, "foo.com", x509);
        DEFAULT.verify("www.foo.com", x509);
        STRICT.verify("www.foo.com", x509);
        DEFAULT.verify("\u82b1\u5b50.foo.com", x509);
        STRICT.verify("\u82b1\u5b50.foo.com", x509);
        DEFAULT.verify("a.b.foo.com", x509);
        exceptionPlease(STRICT, "a.b.foo.com", x509);
        // try the bar.com variations
        exceptionPlease(DEFAULT, "bar.com", x509);
        exceptionPlease(STRICT, "bar.com", x509);
        DEFAULT.verify("www.bar.com", x509);
        STRICT.verify("www.bar.com", x509);
        DEFAULT.verify("\u82b1\u5b50.bar.com", x509);
        STRICT.verify("\u82b1\u5b50.bar.com", x509);
        DEFAULT.verify("a.b.bar.com", x509);
        exceptionPlease(STRICT, "a.b.bar.com", x509);
        // try the \u82b1\u5b50.co.jp variations
        /*
           Java isn't extracting international subjectAlts properly.  (Or
           OpenSSL isn't storing them properly).
        */
        //exceptionPlease( DEFAULT, "\u82b1\u5b50.co.jp", x509 );
        //exceptionPlease( STRICT, "\u82b1\u5b50.co.jp", x509 );
        //DEFAULT.verify("www.\u82b1\u5b50.co.jp", x509 );
        //STRICT.verify("www.\u82b1\u5b50.co.jp", x509 );
        //DEFAULT.verify("\u82b1\u5b50.\u82b1\u5b50.co.jp", x509 );
        //STRICT.verify("\u82b1\u5b50.\u82b1\u5b50.co.jp", x509 );
        //DEFAULT.verify("a.b.\u82b1\u5b50.co.jp", x509 );
        //exceptionPlease(STRICT,"a.b.\u82b1\u5b50.co.jp", x509 );
    }

    @Test
    public void testSubjectAlt() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream in = new ByteArrayInputStream(CertificatesToPlayWith.X509_MULTIPLE_SUBJECT_ALT);
        X509Certificate x509 = (X509Certificate) cf.generateCertificate(in);

        X509HostnameVerifier verifier = SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;

        Assert.assertEquals("CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=CH",
                x509.getSubjectDN().getName());

        verifier.verify("localhost", x509);
        verifier.verify("localhost.localdomain", x509);
        verifier.verify("127.0.0.1", x509);

        try {
            verifier.verify("local.host", x509);
            Assert.fail("SSLException should have been thrown");
        } catch (SSLException ex) {
            // expected
        }
        try {
            verifier.verify("127.0.0.2", x509);
            Assert.fail("SSLException should have been thrown");
        } catch (SSLException ex) {
            // expected
        }

    }

    public void exceptionPlease(X509HostnameVerifier hv, String host,
                                 X509Certificate x509) {
        try {
            hv.verify(host, x509);
            Assert.fail("HostnameVerifier shouldn't allow [" + host + "]");
        }
        catch(SSLException e) {
            // whew!  we're okay!
        }
    }

    // Test helper method
    private void checkMatching(X509HostnameVerifier hv, String host,
            String[] cns, String[] alts, boolean shouldFail) {
        try {
            hv.verify(host, cns, alts);
            if (shouldFail) {
                Assert.fail("HostnameVerifier should not allow [" + host + "] to match "
                        +Arrays.toString(cns)
                        +" or "
                        +Arrays.toString(alts));
            }
        }
        catch(SSLException e) {
            if (!shouldFail) {
                Assert.fail("HostnameVerifier should have allowed [" + host + "] to match "
                        +Arrays.toString(cns)
                        +" or "
                        +Arrays.toString(alts));
            }
        }
    }

    @Test
    // Check standard wildcard matching
    public void testMatching() {
        String cns[] = {};
        String alt[] = {};
        X509HostnameVerifier bhv = new BrowserCompatHostnameVerifier();
        X509HostnameVerifier shv = new StrictHostnameVerifier();
        checkMatching(bhv, "a.b.c", cns, alt, true); // empty
        checkMatching(shv, "a.b.c", cns, alt, true); // empty

        cns = new String []{"*.b.c"};
        checkMatching(bhv, "a.b.c", cns, alt, false); // OK
        checkMatching(shv, "a.b.c", cns, alt, false); // OK

        checkMatching(bhv, "s.a.b.c", cns, alt, false); // OK
        checkMatching(shv, "s.a.b.c", cns, alt, true); // subdomain not OK

        cns = new String []{};
        alt = new String []{"dummy", "*.b.c"}; // check matches against all alts
        checkMatching(bhv, "a.b.c", cns, alt, false); // OK
        checkMatching(shv, "a.b.c", cns, alt, false); // OK

        checkMatching(bhv, "s.a.b.c", cns, alt, false); // OK
        checkMatching(shv, "s.a.b.c", cns, alt, true); // subdomain not OK

        alt = new String []{"*.gov.uk"};
        checkMatching(bhv, "a.gov.uk", cns, alt, true); // Bad 2TLD
        checkMatching(shv, "a.gov.uk", cns, alt, true); // Bad 2TLD

        checkMatching(bhv, "s.a.gov.uk", cns, alt, true); // Bad 2TLD
        checkMatching(shv, "s.a.gov.uk", cns, alt, true); // Bad 2TLD/no subdomain allowed

        alt = new String []{"*.gov.com"};
        checkMatching(bhv, "a.gov.com", cns, alt, false); // OK, gov not 2TLD here
        checkMatching(shv, "a.gov.com", cns, alt, false); // OK, gov not 2TLD here

        checkMatching(bhv, "s.a.gov.com", cns, alt, false); // OK, gov not 2TLD here
        checkMatching(shv, "s.a.gov.com", cns, alt, true); // no subdomain allowed

        cns = new String []{"a*.gov.uk"}; // 2TLD check applies to wildcards
        checkMatching(bhv, "a.gov.uk", cns, alt, true); // Bad 2TLD
        checkMatching(shv, "a.gov.uk", cns, alt, true); // Bad 2TLD

        checkMatching(bhv, "s.a.gov.uk", cns, alt, true); // Bad 2TLD
        checkMatching(shv, "s.a.gov.uk", cns, alt, true); // Bad 2TLD/no subdomain allowed

    }

    @Test
    public void testHTTPCLIENT_1097() {
        String cns[];
        String alt[] = {};
        X509HostnameVerifier bhv = new BrowserCompatHostnameVerifier();
        X509HostnameVerifier shv = new StrictHostnameVerifier();

        cns = new String []{"a*.b.c"}; // component part
        checkMatching(bhv, "a.b.c", cns, alt, false); // OK
        checkMatching(shv, "a.b.c", cns, alt, false); // OK

        checkMatching(bhv, "a.a.b.c", cns, alt, false); // OK
        checkMatching(shv, "a.a.b.c", cns, alt, true); // subdomain not OK

        checkWildcard("s*.co.uk", false); // 2 character TLD, invalid 2TLD
        checkWildcard("s*.gov.uk", false); // 2 character TLD, invalid 2TLD
        checkWildcard("s*.gouv.uk", false); // 2 character TLD, invalid 2TLD
    }

    @Test
    public void testHTTPCLIENT_1255() {
        X509HostnameVerifier bhv = new BrowserCompatHostnameVerifier();
        X509HostnameVerifier shv = new StrictHostnameVerifier();

        String cns[] = new String []{"m*.a.b.c.com"}; // component part
        String alt[] = {};
        checkMatching(bhv, "mail.a.b.c.com", cns, alt, false); // OK
        checkMatching(shv, "mail.a.b.c.com", cns, alt, false); // OK
    }

    // Helper
    private void checkWildcard(String host, boolean isOK) {
        Assert.assertTrue(host+" should be "+isOK, isOK==AbstractVerifier.acceptableCountryWildcard(host));
    }

    @Test
    // Various checks of 2TLDs
    public void testAcceptableCountryWildcards() {
        checkWildcard("*.co.org", true); // Not a 2 character TLD
        checkWildcard("s*.co.org", true); // Not a 2 character TLD
        checkWildcard("*.co.uk", false); // 2 character TLD, invalid 2TLD
        checkWildcard("*.gov.uk", false); // 2 character TLD, invalid 2TLD
        checkWildcard("*.gouv.uk", false); // 2 character TLD, invalid 2TLD
        checkWildcard("*.a.co.uk", true); // 2 character TLD, invalid 2TLD, but using subdomain
        checkWildcard("s*.a.co.uk", true); // 2 character TLD, invalid 2TLD, but using subdomain
    }

    public void testGetCNs() {
        Principal principal = Mockito.mock(Principal.class);
        X509Certificate cert = Mockito.mock(X509Certificate.class);
        Mockito.when(cert.getSubjectDN()).thenReturn(principal);
        Mockito.when(principal.toString()).thenReturn("bla,  bla, blah");
        Assert.assertArrayEquals(new String[] {}, AbstractVerifier.getCNs(cert));
        Mockito.when(principal.toString()).thenReturn("Cn=,  Cn=  , CN, OU=CN=");
        Assert.assertArrayEquals(new String[] {}, AbstractVerifier.getCNs(cert));
        Mockito.when(principal.toString()).thenReturn("  Cn=blah,  CN= blah , OU=CN=yada");
        Assert.assertArrayEquals(new String[] {"blah", " blah"}, AbstractVerifier.getCNs(cert));
    }

}
