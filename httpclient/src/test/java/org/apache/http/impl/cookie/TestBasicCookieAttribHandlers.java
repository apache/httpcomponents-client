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

package org.apache.http.impl.cookie;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BasicCommentHandler;
import org.apache.http.impl.cookie.BasicDomainHandler;
import org.apache.http.impl.cookie.BasicExpiresHandler;
import org.apache.http.impl.cookie.BasicMaxAgeHandler;
import org.apache.http.impl.cookie.BasicPathHandler;
import org.apache.http.impl.cookie.BasicSecureHandler;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.impl.cookie.PublicSuffixFilter;
import org.apache.http.impl.cookie.RFC2109DomainHandler;
import org.junit.Assert;
import org.junit.Test;

public class TestBasicCookieAttribHandlers {

    @Test
    public void testBasicDomainParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicDomainHandler();
        h.parse(cookie, "www.somedomain.com");
        Assert.assertEquals("www.somedomain.com", cookie.getDomain());
    }

    @Test(expected=MalformedCookieException.class)
    public void testBasicDomainParseInvalid1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicDomainHandler();
        h.parse(cookie, "");
    }

    @Test(expected=MalformedCookieException.class)
    public void testBasicDomainParseInvalid2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicDomainHandler();
        h.parse(cookie, null);
    }

    @Test
    public void testBasicDomainValidate1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false);
        CookieAttributeHandler h = new BasicDomainHandler();

        cookie.setDomain(".somedomain.com");
        h.validate(cookie, origin);

        cookie.setDomain(".otherdomain.com");
        try {
            h.validate(cookie, origin);
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        cookie.setDomain("www.otherdomain.com");
        try {
            h.validate(cookie, origin);
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testBasicDomainValidate2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/", false);
        CookieAttributeHandler h = new BasicDomainHandler();

        cookie.setDomain("somehost");
        h.validate(cookie, origin);

        cookie.setDomain("otherhost");
        try {
            h.validate(cookie, origin);
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testBasicDomainValidate3() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        CookieAttributeHandler h = new BasicDomainHandler();

        cookie.setDomain(".somedomain.com");
        h.validate(cookie, origin);
    }

    @Test
    public void testBasicDomainValidate4() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        CookieAttributeHandler h = new BasicDomainHandler();

        cookie.setDomain(null);
        try {
            h.validate(cookie, origin);
            Assert.fail("MalformedCookieException should have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testBasicDomainMatch1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somedomain.com", 80, "/", false);
        CookieAttributeHandler h = new BasicDomainHandler();

        cookie.setDomain("somedomain.com");
        Assert.assertTrue(h.match(cookie, origin));

        cookie.setDomain(".somedomain.com");
        Assert.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicDomainMatch2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("www.somedomain.com", 80, "/", false);
        CookieAttributeHandler h = new BasicDomainHandler();

        cookie.setDomain("somedomain.com");
        Assert.assertTrue(h.match(cookie, origin));

        cookie.setDomain(".somedomain.com");
        Assert.assertTrue(h.match(cookie, origin));

        cookie.setDomain(null);
        Assert.assertFalse(h.match(cookie, origin));
    }

    @Test
    public void testBasicDomainInvalidInput() throws Exception {
        CookieAttributeHandler h = new BasicDomainHandler();
        try {
            h.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.validate(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.validate(new BasicClientCookie("name", "value"), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(new BasicClientCookie("name", "value"), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicPathParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicPathHandler();
        h.parse(cookie, "stuff");
        Assert.assertEquals("stuff", cookie.getPath());
        h.parse(cookie, "");
        Assert.assertEquals("/", cookie.getPath());
        h.parse(cookie, null);
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testBasicPathMatch1() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff");
        Assert.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch2() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff/", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff");
        Assert.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch3() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff/more-stuff", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff");
        Assert.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch4() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuffed", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff");
        Assert.assertFalse(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch5() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/otherstuff", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff");
        Assert.assertFalse(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch6() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff/");
        Assert.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathMatch7() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        CookieAttributeHandler h = new BasicPathHandler();
        Assert.assertTrue(h.match(cookie, origin));
    }

    @Test
    public void testBasicPathValidate() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieOrigin origin = new CookieOrigin("somehost", 80, "/stuff", false);
        CookieAttributeHandler h = new BasicPathHandler();
        cookie.setPath("/stuff");
        h.validate(cookie, origin);
        cookie.setPath("/stuffed");
        try {
            h.validate(cookie, origin);
            Assert.fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testBasicPathInvalidInput() throws Exception {
        CookieAttributeHandler h = new BasicPathHandler();
        try {
            h.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(new BasicClientCookie("name", "value"), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicMaxAgeParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicMaxAgeHandler();
        h.parse(cookie, "2000");
        Assert.assertNotNull(cookie.getExpiryDate());
    }

    @Test
    public void testBasicMaxAgeParseInvalid() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicMaxAgeHandler();
        try {
            h.parse(cookie, "garbage");
            Assert.fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        try {
            h.parse(cookie, null);
            Assert.fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testBasicMaxAgeInvalidInput() throws Exception {
        CookieAttributeHandler h = new BasicMaxAgeHandler();
        try {
            h.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicCommentParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicCommentHandler();
        h.parse(cookie, "whatever");
        Assert.assertEquals("whatever", cookie.getComment());
        h.parse(cookie, null);
        Assert.assertEquals(null, cookie.getComment());
    }

    @Test
    public void testBasicCommentInvalidInput() throws Exception {
        CookieAttributeHandler h = new BasicCommentHandler();
        try {
            h.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicSecureParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicSecureHandler();
        h.parse(cookie, "whatever");
        Assert.assertTrue(cookie.isSecure());
        h.parse(cookie, null);
        Assert.assertTrue(cookie.isSecure());
    }

    @Test
    public void testBasicSecureMatch() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicSecureHandler();

        CookieOrigin origin1 = new CookieOrigin("somehost", 80, "/stuff", false);
        cookie.setSecure(false);
        Assert.assertTrue(h.match(cookie, origin1));
        cookie.setSecure(true);
        Assert.assertFalse(h.match(cookie, origin1));

        CookieOrigin origin2 = new CookieOrigin("somehost", 80, "/stuff", true);
        cookie.setSecure(false);
        Assert.assertTrue(h.match(cookie, origin2));
        cookie.setSecure(true);
        Assert.assertTrue(h.match(cookie, origin2));
    }

    @Test
    public void testBasicSecureInvalidInput() throws Exception {
        CookieAttributeHandler h = new BasicSecureHandler();
        try {
            h.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            h.match(new BasicClientCookie("name", "value"), null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicExpiresParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicExpiresHandler(new String[] {DateUtils.PATTERN_RFC1123});

        DateFormat dateformat = new SimpleDateFormat(DateUtils.PATTERN_RFC1123, Locale.US);
        dateformat.setTimeZone(DateUtils.GMT);

        Date now = new Date();

        h.parse(cookie, dateformat.format(now));
        Assert.assertNotNull(cookie.getExpiryDate());
    }

    @Test
    public void testBasicExpiresParseInvalid() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        CookieAttributeHandler h = new BasicExpiresHandler(new String[] {DateUtils.PATTERN_RFC1123});
        try {
            h.parse(cookie, "garbage");
            Assert.fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
        try {
            h.parse(cookie, null);
            Assert.fail("MalformedCookieException must have been thrown");
        } catch (MalformedCookieException ex) {
            // expected
        }
    }

    @Test
    public void testBasicExpiresInvalidInput() throws Exception {
        try {
            new BasicExpiresHandler(null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        CookieAttributeHandler h = new BasicExpiresHandler(new String[] {DateUtils.PATTERN_RFC1123});
        try {
            h.parse(null, null);
            Assert.fail("IllegalArgumentException must have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testPublicSuffixFilter() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");

        PublicSuffixFilter h = new PublicSuffixFilter(new RFC2109DomainHandler());
        h.setPublicSuffixes(Arrays.asList(new String[] { "co.uk", "com" }));

        cookie.setDomain(".co.uk");
        Assert.assertFalse(h.match(cookie, new CookieOrigin("apache.co.uk", 80, "/stuff", false)));

        cookie.setDomain("co.uk");
        Assert.assertFalse(h.match(cookie, new CookieOrigin("apache.co.uk", 80, "/stuff", false)));

        cookie.setDomain(".com");
        Assert.assertFalse(h.match(cookie, new CookieOrigin("apache.com", 80, "/stuff", false)));

        cookie.setDomain("com");
        Assert.assertFalse(h.match(cookie, new CookieOrigin("apache.com", 80, "/stuff", false)));

        cookie.setDomain("localhost");
        Assert.assertTrue(h.match(cookie, new CookieOrigin("localhost", 80, "/stuff", false)));
    }

}
