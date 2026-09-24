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

import java.time.Duration;
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

class TestBasicCookieAttribHandlers {

    @Test
    void testBasicDomainParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        h.parse(cookie, "www.somedomain.com");
        Assertions.assertEquals("www.somedomain.com", cookie.getDomain());
    }

    @Test
    void testBasicDomainParseInvalid1() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, ""));
    }

    @Test
    void testBasicDomainParseInvalid2() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, null));
    }

    @Test
    void testBasicDomainValidate1() throws Exception {
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
    void testBasicDomainValidate2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somehost");
        h.validate(cookie, origin);

        cookie.setDomain("otherhost");
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, origin));
    }

    @Test
    void testBasicDomainValidate3() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain(".somedomain.com");
        h.validate(cookie, origin);
    }

    @Test
    void testBasicDomainValidate4() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain(null);
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, origin));
    }

    @Test
    void testBasicDomainMatch1() {
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
    void testBasicDomainMatch2() {
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
    void testBasicDomainMatchOneLetterPrefix() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("a.somedomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somedomain.com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somedomain.com");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicDomainMatchMixedCase() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("a.SomeDomain.com", 80, "/", false);
        final CookieAttributeHandler h = BasicDomainHandler.INSTANCE;

        cookie.setDomain("somedoMain.Com");
        cookie.setAttribute(Cookie.DOMAIN_ATTR, "somedoMain.Com");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicDomainInvalidInput() {
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
    void testBasicPathParse() throws Exception {
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
    void testBasicPathMatch1() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicPathMatch2() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff/", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicPathMatch3() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff/more-stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicPathMatch4() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuffed", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertFalse(h.match(cookie, origin));
    }

    @Test
    void testBasicPathMatch5() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/otherstuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff");
        Assertions.assertFalse(h.match(cookie, origin));
    }

    @Test
    void testBasicPathMatch6() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        cookie.setPath("/stuff/");
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicPathMatch7() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        Assertions.assertTrue(h.match(cookie, origin));
    }

    @Test
    void testBasicPathInvalidInput() {
        final CookieAttributeHandler h = BasicPathHandler.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> h.match(null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                h.match(new BasicClientCookie("name", "value"), null));
    }

    @Test
    void testBasicMaxAgeParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        h.parse(cookie, "2000");
        Assertions.assertNotNull(cookie.getExpiryInstant());
    }

    @Test
    void testBasicMaxAgeParseDeleteNow() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        h.parse(cookie, "0");
        Assertions.assertEquals(Instant.EPOCH, cookie.getExpiryInstant());
        final BasicClientCookie cookie2 = new BasicClientCookie("name", "value");
        h.parse(cookie2, "-1");
        Assertions.assertEquals(Instant.EPOCH, cookie2.getExpiryInstant());
    }

    @Test
    void testBasicMaxAgeParseInvalid() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        Assertions.assertThrows(MalformedCookieException.class, () -> h.parse(cookie, "garbage"));
        Assertions.assertThrows(MalformedCookieException.class, () -> h.parse(cookie, null));
    }

    @Test
    void testBasicMaxAgeInvalidInput() {
        final CookieAttributeHandler h = BasicMaxAgeHandler.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
    }

    @Test
    void testBasicSecureParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicSecureHandler.INSTANCE;
        h.parse(cookie, "whatever");
        Assertions.assertTrue(cookie.isSecure());
        h.parse(cookie, null);
        Assertions.assertTrue(cookie.isSecure());
    }

    @Test
    void testBasicSecureMatch() {
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
    void testBasicSecureValidate() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = BasicSecureHandler.INSTANCE;

        final CookieOrigin nonSecureOrigin = new CookieOrigin("somehost", 80, "/stuff", false);
        final CookieOrigin secureOrigin = new CookieOrigin("somehost", 443, "/stuff", true);

        // A secure cookie must not be accepted over a non-secure connection.
        cookie.setSecure(true);
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(cookie, nonSecureOrigin));
        Assertions.assertDoesNotThrow(() -> h.validate(cookie, secureOrigin));

        // A non-secure cookie is unaffected by the origin's security.
        cookie.setSecure(false);
        Assertions.assertDoesNotThrow(() -> h.validate(cookie, nonSecureOrigin));
    }

    @Test
    void testBasicSameSiteValidate() {
        final CookieAttributeHandler h = BasicSameSiteHandler.INSTANCE;
        final CookieOrigin origin = new CookieOrigin("somehost", 443, "/stuff", true);

        final BasicClientCookie none = new BasicClientCookie("name", "value");
        none.setAttribute(Cookie.SAME_SITE_ATTR, "None");
        // SameSite=None without Secure is rejected.
        Assertions.assertThrows(MalformedCookieException.class, () -> h.validate(none, origin));
        // SameSite=None with Secure is accepted.
        none.setSecure(true);
        Assertions.assertDoesNotThrow(() -> h.validate(none, origin));

        final BasicClientCookie lax = new BasicClientCookie("name", "value");
        lax.setAttribute(Cookie.SAME_SITE_ATTR, "Lax");
        Assertions.assertDoesNotThrow(() -> h.validate(lax, origin));
    }

    @Test
    void testMaxAgeExpiryCappedAt400Days() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final Instant before = Instant.now();
        // A Max-Age of ~68 years must be capped to 400 days from now.
        BasicMaxAgeHandler.INSTANCE.parse(cookie, Integer.toString(Integer.MAX_VALUE));
        final Instant expiry = cookie.getExpiryInstant();
        Assertions.assertNotNull(expiry);
        Assertions.assertFalse(expiry.isAfter(before.plus(Duration.ofDays(400)).plusSeconds(5)));
        Assertions.assertTrue(expiry.isAfter(before.plus(Duration.ofDays(399))));
    }

    @Test
    void testMaxAgeLargerThanIntegerAcceptedAndCapped() throws Exception {
        final Instant before = Instant.now();
        final Instant upper = before.plus(Duration.ofDays(400)).plusSeconds(5);
        final Instant lower = before.plus(Duration.ofDays(399));

        // Just beyond Integer.MAX_VALUE: must be accepted, not rejected, then capped.
        final BasicClientCookie c1 = new BasicClientCookie("name", "value");
        BasicMaxAgeHandler.INSTANCE.parse(c1, "3000000000");
        Assertions.assertNotNull(c1.getExpiryInstant());
        Assertions.assertFalse(c1.getExpiryInstant().isAfter(upper));
        Assertions.assertTrue(c1.getExpiryInstant().isAfter(lower));

        // Far beyond Long.MAX_VALUE: still accepted and capped rather than overflowing.
        final BasicClientCookie c2 = new BasicClientCookie("name", "value");
        BasicMaxAgeHandler.INSTANCE.parse(c2, "99999999999999999999999999");
        Assertions.assertNotNull(c2.getExpiryInstant());
        Assertions.assertFalse(c2.getExpiryInstant().isAfter(upper));
        Assertions.assertTrue(c2.getExpiryInstant().isAfter(lower));
    }

    @Test
    void testBasicSecureInvalidInput() {
        final CookieAttributeHandler h = new BasicSecureHandler();
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> h.match(null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                h.match(new BasicClientCookie("name", "value"), null));
    }

    @Test
    void testBasicExpiresParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new BasicExpiresHandler(DateUtils.FORMATTER_RFC1123);

        h.parse(cookie, DateUtils.formatStandardDate(Instant.now()));
        Assertions.assertNotNull(cookie.getExpiryInstant());
    }

    @Test
    void testBasicExpiresParseInvalid() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new BasicExpiresHandler(DateUtils.FORMATTER_RFC1123);
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "garbage"));
        Assertions.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, null));
    }

    @SuppressWarnings("unused")
    @Test
    void testBasicExpiresInvalidInput() {
        final CookieAttributeHandler h = new BasicExpiresHandler(DateUtils.FORMATTER_RFC1123);
        Assertions.assertThrows(NullPointerException.class, () -> h.parse(null, null));
    }

    @Test
    void testPublicSuffixFilter() {
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
    void testBasicHttpOnlyParse() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new BasicHttpOnlyHandler();
        h.parse(cookie, "true");
        Assertions.assertTrue(cookie.isHttpOnly());
        h.parse(cookie, "anyone");
        Assertions.assertTrue(cookie.isHttpOnly());
    }
}
