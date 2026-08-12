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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.cookie.CommonCookieAttributeHandler;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.cookie.SameSite;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestRFC6265CookieSpec {

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
    void testParseNamelessCookieWithLeadingEquals() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "=blah ; this = stuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertEquals("", cookies.get(0).getName());
        Assertions.assertEquals("blah", cookies.get(0).getValue());
    }

    @Test
    void testParseNamelessCookieWithoutEquals() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertEquals("", cookies.get(0).getName());
        Assertions.assertEquals("blah", cookies.get(0).getValue());
    }

    @Test
    void testParseNamelessCookieWithoutEqualsTrailingSemicolon() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertEquals("", cookies.get(0).getName());
        Assertions.assertEquals("blah", cookies.get(0).getValue());
    }

    @Test
    void testParseEmptyCookieIgnored() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        // Both name and value empty -> ignored.
        Assertions.assertTrue(cookiespec.parse(new BasicHeader("Set-Cookie", "; Path=/"), origin).isEmpty());
        Assertions.assertTrue(cookiespec.parse(new BasicHeader("Set-Cookie", "="), origin).isEmpty());
    }

    @Test
    void testNamelessCookieWithPrefixValueRejectedUnconditionally() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);

        // A nameless cookie whose value carries the __Host- prefix is rejected even though it is
        // secure, host-only and has Path=/ -- i.e. it would satisfy the normal __Host- requirements.
        final List<Cookie> host = cookiespec.parse(
                new BasicHeader("Set-Cookie", "__Host-SID; Secure; Path=/"), origin);
        Assertions.assertEquals(1, host.size());
        Assertions.assertEquals("", host.get(0).getName());
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(host.get(0), origin));

        // Likewise for the __Secure- prefix, even when the cookie is secure.
        final List<Cookie> secure = cookiespec.parse(
                new BasicHeader("Set-Cookie", "__Secure-SID; Secure"), origin);
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(secure.get(0), origin));
    }

    @Test
    void testFormatNamelessCookie() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final Cookie cookie = new BasicClientCookie("", "foo");
        final List<Header> headers = cookiespec.formatCookies(Collections.singletonList(cookie));
        Assertions.assertEquals(1, headers.size());
        // A nameless cookie is sent as just its value, without a leading '='.
        Assertions.assertEquals("foo", headers.get(0).getValue());
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

    @Test
    void testHostPrefixAcceptsSecureHostOnlyRootPathCookie() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final BasicClientCookie cookie = new BasicClientCookie("__Host-SID", "value");
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setAttribute(Cookie.PATH_ATTR, "/");
        Assertions.assertDoesNotThrow(() -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testHostPrefixRejectsInsecureCookie() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final BasicClientCookie cookie = new BasicClientCookie("__Host-SID", "value");
        cookie.setPath("/");
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testHostPrefixRejectsDomainAttribute() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final BasicClientCookie cookie = new BasicClientCookie("__Host-SID", "value");
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setAttribute(Cookie.PATH_ATTR, "/");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "example.com");
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testHostPrefixRejectsNonRootPath() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/app", true);
        final BasicClientCookie cookie = new BasicClientCookie("__Host-SID", "value");
        cookie.setSecure(true);
        cookie.setPath("/app");
        cookie.setAttribute(Cookie.PATH_ATTR, "/app");
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testSecurePrefixRejectsInsecureCookie() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final BasicClientCookie cookie = new BasicClientCookie("__Secure-SID", "value");
        cookie.setPath("/");
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testSecurePrefixAcceptsSecureCookie() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final BasicClientCookie cookie = new BasicClientCookie("__Secure-SID", "value");
        cookie.setSecure(true);
        // The __Secure- prefix only requires the Secure attribute; a Domain and a non-root Path are permitted.
        cookie.setPath("/app");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "example.com");
        Assertions.assertDoesNotThrow(() -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testNamePrefixMatchIsCaseInsensitive() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final BasicClientCookie cookie = new BasicClientCookie("__HOST-SID", "value");
        cookie.setPath("/");
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testOrdinaryCookieNameNotSubjectToPrefixRules() {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 80, "/", false);
        final BasicClientCookie cookie = new BasicClientCookie("SID", "value");
        Assertions.assertDoesNotThrow(() -> cookiespec.validate(cookie, origin));
    }

    @Test
    void testHostPrefixRejectsDefaultedPath() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        // No explicit Path attribute: the path is defaulted to "/", which the __Host- prefix must not accept.
        final Header header = new BasicHeader("Set-Cookie", "__Host-SID=value; Secure");
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookies.get(0), origin));
    }

    @Test
    void testSecurePrefixCookieRejectedFromNonSecureOrigin() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 80, "/", false);
        final Header header = new BasicHeader("Set-Cookie", "__Secure-SID=value; Secure");
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookies.get(0), origin));
    }

    @Test
    void testHostPrefixCookieRejectedFromNonSecureOrigin() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 80, "/", false);
        final Header header = new BasicHeader("Set-Cookie", "__Host-SID=value; Secure; Path=/");
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(cookies.get(0), origin));
    }

    @Test
    void testSameSiteParsedAndExposed() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        final Header header = new BasicHeader("Set-Cookie", "SID=value; SameSite=lax");
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(1, cookies.size());
        // Recognized case-insensitively and exposed as the canonical enumeration value.
        Assertions.assertEquals(SameSite.LAX, cookies.get(0).getSameSite());
    }

    @Test
    void testSameSiteAbsentOrUnrecognizedYieldsNull() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);
        Assertions.assertNull(cookiespec.parse(
                new BasicHeader("Set-Cookie", "SID=value"), origin).get(0).getSameSite());
        Assertions.assertNull(cookiespec.parse(
                new BasicHeader("Set-Cookie", "SID=value; SameSite=bogus"), origin).get(0).getSameSite());
    }

    @Test
    void testSameSiteNoneRequiresSecure() throws Exception {
        final RFC6265StrictSpec cookiespec = new RFC6265StrictSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 443, "/", true);

        final List<Cookie> insecure = cookiespec.parse(
                new BasicHeader("Set-Cookie", "SID=value; SameSite=None"), origin);
        Assertions.assertThrows(MalformedCookieException.class,
                () -> cookiespec.validate(insecure.get(0), origin));

        final List<Cookie> secure = cookiespec.parse(
                new BasicHeader("Set-Cookie", "SID=value; SameSite=None; Secure"), origin);
        Assertions.assertDoesNotThrow(() -> cookiespec.validate(secure.get(0), origin));
    }

    @Test
    void testCookieExceedingSizeLimitIsIgnored() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 80, "/", false);
        final char[] chars = new char[4100];
        Arrays.fill(chars, 'a');
        final Header header = new BasicHeader("Set-Cookie", "SID=" + new String(chars));
        // name + value exceeds 4096 octets -> the cookie is ignored entirely.
        Assertions.assertTrue(cookiespec.parse(header, origin).isEmpty());
    }

    @Test
    void testAttributeExceedingSizeLimitIsIgnored() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 80, "/", false);
        final char[] chars = new char[1100];
        Arrays.fill(chars, 'a');
        final Header header = new BasicHeader("Set-Cookie", "SID=value; Path=/" + new String(chars));
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        // The cookie is retained but the oversized attribute is dropped.
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertFalse(cookies.get(0).containsAttribute(Cookie.PATH_ATTR));
    }

    @Test
    void testHostOnlyFlagReflectsDomainAttribute() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();
        final CookieOrigin origin = new CookieOrigin("host.example.com", 80, "/", false);
        Assertions.assertTrue(cookiespec.parse(
                new BasicHeader("Set-Cookie", "SID=value"), origin).get(0).isHostOnly());
        Assertions.assertFalse(cookiespec.parse(
                new BasicHeader("Set-Cookie", "SID=value; Domain=example.com"), origin).get(0).isHostOnly());
    }

}
