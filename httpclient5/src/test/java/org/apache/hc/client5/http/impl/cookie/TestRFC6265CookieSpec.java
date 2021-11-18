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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestRFC6265CookieSpec {

    @Test
    public void testParseCookieBasics() throws Exception {
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
    public void testParseCookieQuotedValue() throws Exception {
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
    public void testParseCookieWrongHeader() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie2", "blah");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.parse(header, origin));
    }

    @Test
    public void testParseCookieMissingName() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "=blah ; this = stuff;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(0, cookies.size());
    }

    @Test
    public void testParseCookieMissingValue1() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        final List<Cookie> cookies = cookiespec.parse(header, origin);
        Assertions.assertEquals(0, cookies.size());
    }

    @Test
    public void testParseCookieMissingValue2() throws Exception {
        final RFC6265CookieSpec cookiespec = new RFC6265CookieSpec();

        final Header header = new BasicHeader("Set-Cookie", "blah;");
        final CookieOrigin origin = new CookieOrigin("host", 80, "/path/", true);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                cookiespec.parse(header, origin));
    }

    @Test
    public void testParseCookieEmptyValue() throws Exception {
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
    public void testParseCookieWithAttributes() throws Exception {
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
    public void testParseCookieWithAttributes2() throws Exception {
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
    public void testParseCookieWithAttributes3() throws Exception {
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
    public void testValidateCookieBasics() throws Exception {
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
    public void testMatchCookie() throws Exception {
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
    public void testMatchCookieNoMatch() throws Exception {
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
    public void testFormatCookiesBasics() throws Exception {
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
    public void testFormatCookiesIllegalCharsInValue() throws Exception {
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
    public void testParseCookieMultipleAttributes() throws Exception {
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
    public void testParseCookieMaxAgeOverExpires() throws Exception {
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
