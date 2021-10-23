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

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

import org.apache.hc.client5.http.cookie.CookieAttributeHandler;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.utils.DateUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestLaxCookieAttribHandlers {

    @Test
    public void testParseMaxAge() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxMaxAgeHandler();
        h.parse(cookie, "2000");
        Assert.assertNotNull(cookie.getExpiryDate());
    }

    @Test
    public void testParseMaxNegative() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxMaxAgeHandler();
        h.parse(cookie, "-2000");
        Assert.assertNotNull(cookie.getExpiryDate());
    }

    @Test
    public void testParseMaxZero() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxMaxAgeHandler();
        h.parse(cookie, "0000");
        Assert.assertNotNull(cookie.getExpiryDate());
    }

    @Test
    public void testBasicMaxAgeParseEmpty() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxMaxAgeHandler();
        h.parse(cookie, "  ");
        Assert.assertNull(cookie.getExpiryDate());
    }

    @Test
    public void testBasicMaxAgeParseInvalid() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxMaxAgeHandler();
        h.parse(cookie, "garbage");
        Assert.assertNull(cookie.getExpiryDate());
    }

    @Test
    public void testBasicMaxAgeInvalidInput() throws Exception {
        final CookieAttributeHandler h = new LaxMaxAgeHandler();
        Assert.assertThrows(NullPointerException.class, () -> h.parse(null, "stuff"));
    }

    @Test
    public void testExpiryGarbage() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, ";;blah,blah;yada  "));
    }

    @Test
    public void testParseExpiry() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "1:0:12 8-jan-2012");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(2012, expiryDate.get(ChronoField.YEAR));
        Assert.assertEquals(1, expiryDate.get(ChronoField.MONTH_OF_YEAR));
        Assert.assertEquals(8, expiryDate.get(ChronoField.DAY_OF_MONTH));
        Assert.assertEquals(1, expiryDate.get(ChronoField.HOUR_OF_DAY));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MINUTE_OF_HOUR));
        Assert.assertEquals(12, expiryDate.get(ChronoField.SECOND_OF_MINUTE));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MILLI_OF_SECOND));
    }

    @Test
    public void testParseExpiryInvalidTime0() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, null);
        Assert.assertNull(cookie.getExpiryDate());
    }

    @Test
    public void testParseExpiryInvalidTime1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "1:0:122 8 dec 1980"));
    }

    @Test
    public void testParseExpiryInvalidTime2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "24:00:00 8 dec 1980"));
    }

    @Test
    public void testParseExpiryInvalidTime3() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "23:60:00 8 dec 1980"));
    }

    @Test
    public void testParseExpiryInvalidTime4() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "23:00:60 8 dec 1980"));
    }

    @Test
    public void testParseExpiryFunnyTime() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "1:59:00blah; 8-feb-2000");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(2000, expiryDate.get(ChronoField.YEAR_OF_ERA));
        Assert.assertEquals(2, expiryDate.get(ChronoField.MONTH_OF_YEAR));
        Assert.assertEquals(8, expiryDate.get(ChronoField.DAY_OF_MONTH));
        Assert.assertEquals(1, expiryDate.get(ChronoField.HOUR_OF_DAY));
        Assert.assertEquals(59, expiryDate.get(ChronoField.MINUTE_OF_HOUR));
        Assert.assertEquals(0, expiryDate.get(ChronoField.SECOND_OF_MINUTE));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MILLI_OF_SECOND));
    }

    @Test
    public void testParseExpiryInvalidDayOfMonth1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "12:00:00 888 mar 1880"));
    }

    @Test
    public void testParseExpiryInvalidDayOfMonth2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "12:00:00 0 mar 1880"));
    }

    @Test
    public void testParseExpiryInvalidDayOfMonth3() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "12:00:00 32 mar 1880"));
    }

    @Test
    public void testParseExpiryFunnyDayOfMonth() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "12:00:00 8blah;mar;1880");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(1880, expiryDate.get(ChronoField.YEAR));
        Assert.assertEquals(3, expiryDate.get(ChronoField.MONTH_OF_YEAR));
        Assert.assertEquals(8, expiryDate.get(ChronoField.DAY_OF_MONTH));
        Assert.assertEquals(12, expiryDate.get(ChronoField.HOUR_OF_DAY));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MINUTE_OF_HOUR));
        Assert.assertEquals(0, expiryDate.get(ChronoField.SECOND_OF_MINUTE));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MILLI_OF_SECOND));
    }

    @Test
    public void testParseExpiryInvalidMonth() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "1:00:00 8 dek 80"));
    }

    @Test
    public void testParseExpiryFunnyMonth() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "23:59:59; 1-ApriLLLLL-2008");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(2008, expiryDate.get(ChronoField.YEAR));
        Assert.assertEquals(4, expiryDate.get(ChronoField.MONTH_OF_YEAR));
        Assert.assertEquals(1, expiryDate.get(ChronoField.DAY_OF_MONTH));
        Assert.assertEquals(23, expiryDate.get(ChronoField.HOUR_OF_DAY));
        Assert.assertEquals(59, expiryDate.get(ChronoField.MINUTE_OF_HOUR));
        Assert.assertEquals(59, expiryDate.get(ChronoField.SECOND_OF_MINUTE));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MILLI_OF_SECOND));
    }

    @Test
    public void testParseExpiryInvalidYearTooShort() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "1:00:00 8 dec 8"));
    }

    @Test
    public void testParseExpiryInvalidYearTooLong() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "1:00:00 8 dec 88888"));
    }

    @Test
    public void testParseExpiryInvalidYearTooLongAgo() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        Assert.assertThrows(MalformedCookieException.class, () ->
                h.parse(cookie, "1:00:00 8 dec 1600"));
    }

    @Test
    public void testParseExpiryFunnyYear() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "23:59:59; 1-Apr-2008blah");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(2008, expiryDate.get(ChronoField.YEAR));
        Assert.assertEquals(4, expiryDate.get(ChronoField.MONTH_OF_YEAR));
        Assert.assertEquals(1, expiryDate.get(ChronoField.DAY_OF_MONTH));
        Assert.assertEquals(23, expiryDate.get(ChronoField.HOUR_OF_DAY));
        Assert.assertEquals(59, expiryDate.get(ChronoField.MINUTE_OF_HOUR));
        Assert.assertEquals(59, expiryDate.get(ChronoField.SECOND_OF_MINUTE));
        Assert.assertEquals(0, expiryDate.get(ChronoField.MILLI_OF_SECOND));
    }

    @Test
    public void testParseExpiryYearTwoDigit1() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "23:59:59; 1-Apr-70");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(1970, expiryDate.get(ChronoField.YEAR));
    }

    @Test
    public void testParseExpiryYearTwoDigit2() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "23:59:59; 1-Apr-99");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(1999, expiryDate.get(ChronoField.YEAR));
    }

    @Test
    public void testParseExpiryYearTwoDigit3() throws Exception {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        final CookieAttributeHandler h = new LaxExpiresHandler();
        h.parse(cookie, "23:59:59; 1-Apr-00");

        final LocalDateTime expiryDate = DateUtils.toUTC(cookie.getExpiryDate());
        Assert.assertNotNull(expiryDate);

        Assert.assertEquals(2000, expiryDate.get(ChronoField.YEAR));
    }

}
