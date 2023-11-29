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

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieAttributeHandler;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.psl.DomainType;
import org.apache.hc.client5.http.psl.PublicSuffixMatcher;
import org.apache.hc.client5.http.utils.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBasicCookieAttribHandlers {

    @Test
    public void testBasicDomainParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        h.parse(cookie, "www.somedomain.com");
        Assertions.assertEquals("www.somedomain.com", cookie.getDomain());
    }

    @Test
    public void testBasicDomainParseInvalid1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, ""));
    }

    @Test
    public void testBasicDomainParseInvalid2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, null));
    }

    @Test
    public void testBasicDomainValidate1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain(".somedomain.com");
        h.validate(cookie, origin);

        cookie.setDomain(".otherdomain.com");
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, origin));
        cookie.setDomain("www.otherdomain.com");
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, origin));
    }

    @Test
    public void testBasicDomainValidate2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somehost");
        h.validate(cookie, origin);

        cookie.setDomain("otherhost");
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, origin));
    }

    @Test
    public void testBasicDomainValidate3() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain(".somedomain.com");
        h.validate(cookie, origin);
    }

    @Test
    public void testBasicDomainValidate4() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain(null);
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, origin));
    }

    @Test
    public void testBasicDomainMatch1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somedomain.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somedomain.com");
        Assertions.assertTrue(h.match(cookie, origin));

        cookie.setDomain(".somedomain.com");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicDomainMatch2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somedomain.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somedomain.com");
        Assertions.assertTrue(h.match(cookie, origin));

        cookie.setDomain(".somedomain.com");
        Assertions.assertTrue(h.match(cookie, origin));

        cookie.setDomain(null);
        Assertions.assertFalse(h.match(cookie, origin));
    }

    @Test
    public void testBasicDomainMatchOneLetterPrefix() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("a.somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somedomain.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somedomain.com");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicDomainMatchMixedCase() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("a.SomeDomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somedoMain.Com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somedoMain.Com");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicDomainInvalidInput() throws Exception {
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> h.validate(null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                h.validate(new BasicClientCookie("name", "value"), null));
        Assertions.assertThrows(NullPointerException.class, () -> h.match(null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                h.match(new BasicClientCookie("name", "value"), null));
    }

    @Test
    public void testBasicPathParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        h.parse(cookie, "stuff");
        Assertions.assertEquals("stuff", cookie.getPath());
        h.parse(cookie, "");
        Assertions.assertEquals("/", cookie.getPath());
        h.parse(cookie, null);
        Assertions.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testBasicPathMatch1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff/", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch3() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff/more-stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch4() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuffed", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertFalse(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch5() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/otherstuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertFalse(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch6() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff/");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch7() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathInvalidInput() throws Exception {
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> h.match(null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                h.match(new BasicClientCookie("name", "value"), null));
    }

    @Test
    public void testBasicMaxAgeParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        h.parse(cookie, "2000");
        Assertions.assertNotNull(cookie.getExpiryInstant());
    }

    @Test
    public void testBasicMaxAgeParseInvalid() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        Assertions.assertThrows(MalformedCookieException.class, () -> h.parse(cookie, "garbage"));
        Assertions.assertThrows(MalformedCookieException.class, () -> h.parse(cookie, null));
    }

    @Test
    public void testBasicMaxAgeInvalidInput() throws Exception {
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
    }

    @Test
    public void testBasicSecureParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicSecureHandler.INSTANCE;
        h.parse(cookie, "whatever");
        Assertions.assertTrue(cookie.isSecure());
        h.parse(cookie, null);
        Assertions.assertTrue(cookie.isSecure());
    }

    @Test
    public void testBasicSecureMatch() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicSecureHandler.INSTANCE;

        final CookieOrigin origin1 = new CookieOrigin("somehost", 80, "/stuff", false);
        cookie.setSecure(false);
        Assertions.assertTrue(h.match(cookie, origin1));
        cookie.setSecure(true);
        Assertions.assertFalse(h.match(cookie, origin1));

        final CookieOrigin origin2 = new CookieOrigin("somehost", 80, "/stuff", true);
        cookie.setSecure(false);
        Assertions.assertTrue(h.match(cookie, origin2));
        cookie.setSecure(true);
        Assertions.assertTrue(h.match(cookie, origin2));
    }

    @Test
    public void testBasicSecureInvalidInput() throws Exception {
        final CookieAttributeHandler h = new BasicSecureHandler();
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> h.match(null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                h.match(new BasicClientCookie("name", "value"), null));
    }

    @Test
    public void testBasicExpiresParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new BasicExpiresHandler(DateUtils.FORMATTER_RFC1123);

        h.parse(cookie, DateUtils.formatStandardDate(Instant.now()));
        Assertions.assertNotNull(cookie.getExpiryInstant());
    }

    @Test
    public void testBasicExpiresParseInvalid() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new BasicExpiresHandler(DateUtils.FORMATTER_RFC1123);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "garbage"));
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, null));
    }

    @SuppressWarnings("unused")
    @Test
    public void testBasicExpiresInvalidInput() throws Exception {
        final CookieAttributeHandler h = new BasicExpiresHandler(DateUtils.FORMATTER_RFC1123);
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
    }

    @Test
    public void testPublicSuffixFilter() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");

        final PublicSuffixMatcher matcher = new PublicSuffixMatcher(DomainType.ICANN, Arrays.asList("co.uk", "com"), null);
        final PublicSuffixDomainFilter h = new PublicSuffixDomainFilter(BasicDomainHandler.INSTANCE, matcher);

        cookie.setDomain(".co.uk");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".co.uk");
        Assertions.assertFalse(h.match(cookie, new CookieOrigin("apache.co.uk", 80, "/stuff", false)));

        cookie.setDomain("co.uk");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "co.uk");
        Assertions.assertFalse(h.match(cookie, new CookieOrigin("apache.co.uk", 80, "/stuff", false)));

        cookie.setDomain(".co.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".co.com");
        Assertions.assertTrue(h.match(cookie, new CookieOrigin("apache.co.com", 80, "/stuff", false)));

        cookie.setDomain("co.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "co.com");
        Assertions.assertTrue(h.match(cookie, new CookieOrigin("apache.co.com", 80, "/stuff", false)));

        cookie.setDomain(".com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".com");
        Assertions.assertFalse(h.match(cookie, new CookieOrigin("apache.com", 80, "/stuff", false)));

        cookie.setDomain("com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "com");
        Assertions.assertFalse(h.match(cookie, new CookieOrigin("apache.com", 80, "/stuff", false)));

        cookie.setDomain("apache.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "apache.com");
        Assertions.assertTrue(h.match(cookie, new CookieOrigin("apache.com", 80, "/stuff", false)));

        cookie.setDomain(".apache.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, ".apache.com");
        Assertions.assertTrue(h.match(cookie, new CookieOrigin("www.apache.com", 80, "/stuff", false)));

        cookie.setDomain("localhost");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "localhost");
        Assertions.assertTrue(h.match(cookie, new CookieOrigin("localhost", 80, "/stuff", false)));
    }
    @Test
    public void testBasicHttpOnlyParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new BasicHttpOnlyHandler();
        h.parse(cookie, "true");
        Assertions.assertTrue(cookie.isHttpOnly());
        h.parse(cookie, "anyone");
        Assertions.assertTrue(cookie.isHttpOnly());
    }

}
