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

package org.apache.hc.client5.http.impl.cookie;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.cookie.CommonCookieAttributeHandler;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.CookieSpecSupport;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestRFC6265CookieSpec {

    @Test
    void testEmptyDomainAttributeIgnored() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("www.example.com", 80, "/", false);

        final Cookie cookie = cookiespec.parse(
                new BasicHeader("Set-Cookie", "name=value; Domain=example.com; Domain="), origin).get(0);
        Assertions.assertEquals("example.com", cookie.getDomain());
        Assertions.assertEquals("example.com", cookie.getAttribute(Cookie.DOMAIN_ATTR));
        Assertions.assertTrue(cookiespec.match(cookie, origin));

        final Cookie hostOnlyCookie = cookiespec.parse(
                new BasicHeader("Set-Cookie", "name=value; Domain="), origin).get(0);
        Assertions.assertEquals("www.example.com", hostOnlyCookie.getDomain());
        Assertions.assertFalse(hostOnlyCookie.containsAttribute(Cookie.DOMAIN_ATTR));
        Assertions.assertTrue(cookiespec.match(hostOnlyCookie, origin));
        Assertions.assertFalse(cookiespec.match(hostOnlyCookie,
                new CookieOrigin("api.example.com", 80, "/", false)));

        final Cookie cookieWithEmptyDomainFirst = cookiespec.parse(
                new BasicHeader("Set-Cookie", "name=value; Domain=; Domain=example.com"), origin).get(0);
        Assertions.assertEquals("example.com", cookieWithEmptyDomainFirst.getDomain());
    }

    @Test
    void testTrailingDotDomainAttributeRejected() {
        final RFC6265CookieSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("example.com", 80, "/", false);

        Assertions.assertThrows(MalformedCookieException.class, () -> cookiespec.parse(
                new BasicHeader("Set-Cookie", "name=value; Domain=example.com."), origin));
        Assertions.assertThrows(MalformedCookieException.class, () -> cookiespec.parse(
                new BasicHeader("Set-Cookie", "name=value; Domain=example.com; Domain=foo."), origin));
    }

    @Test
    void testNoArgSpecsUsePublicSuffixMatcher() throws Exception {
        final CookieOrigin publicSuffixOrigin = new CookieOrigin("foo.com", 80, "/", false);
        final CookieOrigin exampleOrigin = new CookieOrigin("foo.example.com", 80, "/", false);
        final CookieOrigin exampleSibling = new CookieOrigin("bar.example.com", 80, "/", false);
        final CookieOrigin publicSuffixSibling = new CookieOrigin("bar.com", 80, "/", false);
        final List<CookieSpec> cookieSpecs = Arrays.asList(new RFC6265LaxSpec(), new RFC6265StrictSpec(),
                new RFC6265CookieSpecFactory().create(null));

        for (final CookieSpec cookieSpec : cookieSpecs) {
            final Cookie publicSuffixCookie = cookieSpec.parse(
                    new BasicHeader("Set-Cookie", "name=value; Domain=com"), publicSuffixOrigin).get(0);
            Assertions.assertFalse(cookieSpec.match(publicSuffixCookie, publicSuffixSibling));

            final Cookie exampleCookie = cookieSpec.parse(
                    new BasicHeader("Set-Cookie", "name=value; Domain=example.com"), exampleOrigin).get(0);
            Assertions.assertTrue(cookieSpec.match(exampleCookie, exampleSibling));
        }
    }

    /**
     * xn--9tfky.id (ᬩᬮᬶ.id) is a real Mozilla Public Suffix List entry whose
     * Unicode label is unassigned under the Unicode 3.2 repertoire java.net.IDN enforces by
     * default. Without IDN.ALLOW_UNASSIGNED the matcher never decodes it, so it falls
     * through to treating it as an ordinary registrable domain instead of a public suffix.
     */
    @Test
    void testNoArgSpecsRejectUnassignedCodePointSuffix() throws Exception {
        final CookieOrigin evilTenant = new CookieOrigin("evil-tenant.xn--9tfky.id", 80, "/", false);
        final CookieOrigin otherTenant = new CookieOrigin("other-tenant.xn--9tfky.id", 80, "/", false);
        final CookieOrigin legitOrigin = new CookieOrigin("foo.legit.xn--9tfky.id", 80, "/", false);
        final CookieOrigin legitSibling = new CookieOrigin("bar.legit.xn--9tfky.id", 80, "/", false);
        final List<CookieSpec> cookieSpecs = Arrays.asList(new RFC6265LaxSpec(), new RFC6265StrictSpec());

        for (final CookieSpec cookieSpec : cookieSpecs) {
            // A cookie for the bare public suffix itself must not reach another tenant under it.
            final Cookie suffixCookie = cookieSpec.parse(
                    new BasicHeader("Set-Cookie", "name=value; Domain=xn--9tfky.id"), evilTenant).get(0);
            Assertions.assertFalse(cookieSpec.match(suffixCookie, otherTenant));

            // A cookie for a genuine registrable domain under the suffix is still a valid
            // cookie domain and reaches siblings under it, like any ordinary domain.
            final Cookie legitCookie = cookieSpec.parse(
                    new BasicHeader("Set-Cookie", "name=value; Domain=legit.xn--9tfky.id"), legitOrigin).get(0);
            Assertions.assertTrue(cookieSpec.match(legitCookie, legitSibling));
        }
    }

    @Test
    void testPathMatchingWithTrailingSlash() throws Exception {
        final CookieSpec cookieSpec = new RFC6265StrictSpec();
        final String host = "example.com";
        final CookieOrigin setOrigin = new CookieOrigin(host, 80, "/set", false);

        final Cookie trailingSlashCookie = cookieSpec.parse(
                new BasicHeader("Set-Cookie", "a=b; Path=/foo/"), setOrigin).get(0);
        Assertions.assertFalse(cookieSpec.match(trailingSlashCookie, new CookieOrigin(host, 80, "/foo", false)));
        Assertions.assertTrue(cookieSpec.match(trailingSlashCookie, new CookieOrigin(host, 80, "/foo/", false)));
        Assertions.assertTrue(cookieSpec.match(trailingSlashCookie, new CookieOrigin(host, 80, "/foo/bar", false)));

        final Cookie pathCookie = cookieSpec.parse(
                new BasicHeader("Set-Cookie", "a=b; Path=/foo"), setOrigin).get(0);
        Assertions.assertTrue(cookieSpec.match(pathCookie, new CookieOrigin(host, 80, "/foo", false)));
        Assertions.assertTrue(cookieSpec.match(pathCookie, new CookieOrigin(host, 80, "/foo/", false)));
        Assertions.assertTrue(cookieSpec.match(pathCookie, new CookieOrigin(host, 80, "/foo/bar", false)));
        Assertions.assertFalse(cookieSpec.match(pathCookie, new CookieOrigin(host, 80, "/foobar", false)));

        final Cookie rootPathCookie = cookieSpec.parse(
                new BasicHeader("Set-Cookie", "a=b; Path=/"), setOrigin).get(0);
        Assertions.assertTrue(cookieSpec.match(rootPathCookie, new CookieOrigin(host, 80, "/elsewhere", false)));
    }

    @Test
    void testExpiresIgnoresMismatchedDayOfWeekInRelaxedMode() throws Exception {
        final CookieOrigin origin = new CookieOrigin("example.com", 80, "/", false);
        final Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        final CookieSpecFactory strictFactory = CookieSpecSupport.createDefaultBuilder()
                .build().lookup(StandardCookieSpec.STRICT);
        final CookieSpecFactory relaxedFactory = CookieSpecSupport.createDefaultBuilder()
                .build().lookup(StandardCookieSpec.RELAXED);
        final List<CookieSpec> strictCookieSpecs = Arrays.asList(
                new RFC6265StrictSpec(),
                strictFactory.create(null),
                new RFC6265CookieSpecFactory(RFC6265CookieSpecFactory.CompatibilityLevel.IE_MEDIUM_SECURITY, null)
                        .create(null));

        for (final CookieSpec cookieSpec : strictCookieSpecs) {
            Assertions.assertThrows(MalformedCookieException.class, () -> cookieSpec.parse(new BasicHeader(
                    "Set-Cookie", "a=b; Expires=Mon, 01 Jan 2030 00:00:00 GMT"), origin));
            final Cookie rightWeekday = cookieSpec.parse(new BasicHeader(
                    "Set-Cookie", "a=b; Expires=Tue, 01 Jan 2030 00:00:00 GMT"), origin).get(0);
            Assertions.assertEquals(expiry, rightWeekday.getExpiryInstant());
            for (final String invalidDate : Arrays.asList(
                    "Mon, 31 Feb 2030 00:00:00 GMT", "Tue, 30 Feb 2030 00:00:00 GMT")) {
                Assertions.assertThrows(MalformedCookieException.class, () -> cookieSpec.parse(new BasicHeader(
                        "Set-Cookie", "a=b; Expires=" + invalidDate), origin));
            }
        }

        final CookieSpec relaxedSpec = relaxedFactory.create(null);
        final Cookie wrongWeekday = relaxedSpec.parse(new BasicHeader(
                "Set-Cookie", "a=b; Expires=Mon, 01 Jan 2030 00:00:00 GMT"), origin).get(0);
        Assertions.assertEquals(expiry, wrongWeekday.getExpiryInstant());
        final Cookie rightWeekday = relaxedSpec.parse(new BasicHeader(
                "Set-Cookie", "a=b; Expires=Tue, 01 Jan 2030 00:00:00 GMT"), origin).get(0);
        Assertions.assertEquals(expiry, rightWeekday.getExpiryInstant());
    }

    @Test
    void testParseCookieBasics() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final Header header = new BasicHeader("Set-Cookie", "name = value ; this = stuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);

        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("name", cookie.getName());
        Assertions.assertEquals("value", cookie.getValue());
        Assertions.assertEquals("/path", cookie.getPath());
        Assertions.assertEquals("host", cookie.getDomain());
        Assertions.assertEquals("stuff", cookie.getAttribute("this"));
        Assertions.assertNull(cookie.getAttribute("that"));

        Mockito.verify(h1).parse(ArgumentMatchers.any(), ArgumentMatchers.eq("stuff"));
        Mockito.verify(h2, Mockito.never()).parse(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    }

    @Test
    void testParseCookieQuotedValue() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "name = \" one, two, three; four \" ; this = stuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);

        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("name", cookie.getName());
        Assertions.assertEquals(" one, two, three; four ", cookie.getValue());
        Assertions.assertEquals("stuff", cookie.getAttribute("this"));
    }

    @Test
    void testParseCookieMissingName() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "=blah ; this = stuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.parse(header, origin));
    }

    @Test
    void testParseCookieMissingValue1() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.parse(header, origin));
    }

    @Test
    void testParseCookieMissingValue2() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.parse(header, origin));
    }

    @Test
    void testParseCookieEmptyValue() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah=;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("blah", cookie.getName());
        Assertions.assertEquals("", cookie.getValue());
    }

    @Test
    void testParseCookieWithAttributes() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final Header header = new BasicHeader("Set-Cookie", "name = value ; p1 = v ; p2 = v,0; p3 ; p4");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);

        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("name", cookie.getName());
        Assertions.assertEquals("value", cookie.getValue());
        Assertions.assertEquals("v", cookie.getAttribute("p1"));
        Assertions.assertEquals("v,0", cookie.getAttribute("p2"));
        Assertions.assertTrue(cookie.containsAttribute("p3"));
        Assertions.assertTrue(cookie.containsAttribute("p4"));
        Assertions.assertFalse(cookie.containsAttribute("p5"));
    }

    @Test
    void testParseCookieWithAttributes2() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final Header header = new BasicHeader("Set-Cookie", "name = value ; p1 = v");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);

        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("name", cookie.getName());
        Assertions.assertEquals("value", cookie.getValue());
        Assertions.assertEquals("v", cookie.getAttribute("p1"));
    }

    @Test
    void testParseCookieWithAttributes3() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final Header header = new BasicHeader("Set-Cookie", "name = value ; p1 =");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);

        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("name", cookie.getName());
        Assertions.assertEquals("value", cookie.getValue());
        Assertions.assertEquals("", cookie.getAttribute("p1"));
    }

    @Test
    void testParseCookieWithHttpOnly() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "name = value ; HttpOnly");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);

        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertTrue(cookie.containsAttribute(Cookie.HTTP_ONLY_ATTR));
    }

    @Test
    void testValidateCookieBasics() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookiespec.validate(cookie, origin);

        Mockito.verify(h1).validate(cookie, origin);
        Mockito.verify(h2).validate(cookie, origin);
    }

    @Test
    void testValidateCookieRejectsControlCharsInName() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final BasicClientCookie cookie = new BasicClientCookie("sid\r\nInjected: evil", "value");
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.validate(cookie, origin));
    }

    @Test
    void testValidateCookieRejectsControlCharsInValue() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        // NUL is neither whitespace nor a delimiter, so it survives parsing and
        // must be caught by validation before the cookie reaches the store.
        final Header header = new BasicHeader("Set-Cookie", "sid=abc\u0000def");
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        final Cookie cookie = cookies.get(0);
        Assertions.assertEquals("abc\u0000def", cookie.getValue());
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.validate(cookie, origin));
    }

    @Test
    void testMatchCookie() {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");

        Mockito.when(h1.match(cookie, origin)).thenReturn(true);
        Mockito.when(h2.match(cookie, origin)).thenReturn(true);

        Assertions.assertTrue(cookiespec.match(cookie, origin));

        Mockito.verify(h1).match(cookie, origin);
        Mockito.verify(h2).match(cookie, origin);
    }

    @Test
    void testMatchCookieNoMatch() {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("that");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");

        Mockito.when(h1.match(cookie, origin)).thenReturn(false);
        Mockito.when(h2.match(cookie, origin)).thenReturn(false);

        Assertions.assertFalse(cookiespec.match(cookie, origin));

        Mockito.verify(h1).match(cookie, origin);
        Mockito.verify(h2, Mockito.never()).match(cookie, origin);
    }

    @Test
    void testFormatCookiesBasics() {
        final Cookie cookie1 = new BasicClientCookie("name1", "value");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final List<Header> headers = cookiespec.formatCookies(Collections.singletonList(cookie1));
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(1, headers.size());
        final Header header = headers.get(0);
        Assertions.assertEquals("Cookie", header.getName());
        Assertions.assertEquals("name1=value", header.getValue());
    }

    @Test
    void testFormatCookiesIllegalCharsInValue() {
        final Cookie cookie1 = new BasicClientCookie("name1", "value");
        final Cookie cookie2 = new BasicClientCookie("name2", "some value");
        final Cookie cookie3 = new BasicClientCookie("name3", "\"\\\"");
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final List<Header> headers = cookiespec.formatCookies(Arrays.asList(cookie1, cookie2, cookie3));
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(1, headers.size());
        final Header header = headers.get(0);
        Assertions.assertEquals("Cookie", header.getName());
        Assertions.assertEquals("name1=value; name2=\"some value\"; name3=\"\\\"\\\\\\\"\"", header.getValue());
    }

    @Test
    void testFormatCookiesReplacesControlCharsInName() {
        final Cookie cookie = new BasicClientCookie("sid\r\nInjected: evil", "value");
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final List<Header> headers = cookiespec.formatCookies(Collections.singletonList(cookie));
        Assertions.assertEquals(1, headers.size());
        final String wire = ((FormattedHeader) headers.get(0)).getBuffer().toString();
        Assertions.assertFalse(wire.indexOf('\r') >= 0 || wire.indexOf('\n') >= 0);
        Assertions.assertEquals("Cookie: sid??Injected: evil=value", wire);
    }

    @Test
    void testFormatCookiesReplacesControlCharsInValue() {
        final Cookie cookie = new BasicClientCookie("sid", "abc\r\nInjected: evil");
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final List<Header> headers = cookiespec.formatCookies(Collections.singletonList(cookie));
        Assertions.assertEquals(1, headers.size());
        final String wire = ((FormattedHeader) headers.get(0)).getBuffer().toString();
        Assertions.assertFalse(wire.indexOf('\r') >= 0 || wire.indexOf('\n') >= 0);
        Assertions.assertEquals("Cookie: sid=\"abc??Injected: evil\"", wire);
    }

    @Test
    void testParseCookieMultipleAttributes() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("this");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1);

        final Header header = new BasicHeader("Set-Cookie", "name = value ; this = stuff; this = morestuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        cookiespec.parse(header, origin);

        Mockito.verify(h1).parse(ArgumentMatchers.any(), ArgumentMatchers.eq("morestuff"));
        Mockito.verify(h1, Mockito.times(1)).parse(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    }

    @Test
    void testParseCookieMaxAgeOverExpires() throws Exception {
        final CommonCookieAttributeHandler h1 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h1.getAttributeName()).thenReturn("Expires");
        final CommonCookieAttributeHandler h2 = Mockito.mock(CommonCookieAttributeHandler.class);
        Mockito.when(h2.getAttributeName()).thenReturn("Max-Age");

        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec(h1, h2);

        final Header header = new BasicHeader("Set-Cookie", "name = value ; expires = stuff; max-age = otherstuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        cookiespec.parse(header, origin);

        Mockito.verify(h1, Mockito.never()).parse(ArgumentMatchers.any(), ArgumentMatchers.anyString());
        Mockito.verify(h2).parse(ArgumentMatchers.any(), ArgumentMatchers.eq("otherstuff"));
    }

}
