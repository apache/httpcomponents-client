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

package org.apache.hc.client5.http.utils;

import java.util.Calendar;
import java.util.Date;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DateUtils}.
 */
public class TestDateUtils {

    private static Date createDate(final int year, final int month, final int day) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(DateUtils.GMT);
        calendar.setTimeInMillis(0);
        calendar.set(year, month, day);
        return calendar.getTime();
    }

    @Test
    public void testBasicDateParse() throws Exception {
        final Date date = createDate(2005, Calendar.OCTOBER, 14);
        final String[] formats = new String[] { DateUtils.PATTERN_RFC1123 };
        Assert.assertEquals(date, DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", formats, null));
        Assert.assertEquals(date, DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", formats));
        Assert.assertEquals(date, DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT"));
    }

    @Test
    public void testDateParseMessage() throws Exception {
        final HeaderGroup message1 = new HeaderGroup();
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "Fri, 14 Oct 2005 00:00:00 GMT"));
        Assert.assertEquals(createDate(2005, Calendar.OCTOBER, 14), DateUtils.parseDate(message1, HttpHeaders.DATE));

        final HeaderGroup message2 = new HeaderGroup();
        message2.addHeader(new BasicHeader(HttpHeaders.DATE, "Fri, 14 Oct 2005 00:00:00 GMT"));
        message2.addHeader(new BasicHeader(HttpHeaders.DATE, "Fri, 21 Oct 2005 00:00:00 GMT"));
        Assert.assertEquals(createDate(2005, Calendar.OCTOBER, 14), DateUtils.parseDate(message2, HttpHeaders.DATE));
    }

    @Test
    public void testMalformedDate() {
        Assert.assertNull(DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", new String[] {}, null));
    }

    @Test
    public void testInvalidInput() throws Exception {
        try {
            DateUtils.parseDate(null, null, null);
            Assert.fail("NullPointerException should habe been thrown");
        } catch (final NullPointerException ex) {
            // expected
        }
        try {
            DateUtils.formatDate(null);
            Assert.fail("NullPointerException should habe been thrown");
        } catch (final NullPointerException ex) {
            // expected
        }
        try {
            DateUtils.formatDate(new Date(), null);
            Assert.fail("NullPointerException should habe been thrown");
        } catch (final NullPointerException ex) {
            // expected
        }
    }

    @Test
    public void testTwoDigitYearDateParse() throws Exception {
        final String[] formats = new String[] { DateUtils.PATTERN_RFC1036 };
        Assert.assertEquals(createDate(2005, Calendar.OCTOBER, 14), DateUtils.parseDate("Friday, 14-Oct-05 00:00:00 GMT", formats, null));
        Assert.assertEquals(createDate(1905, Calendar.OCTOBER, 14), DateUtils.parseDate("Friday, 14-Oct-05 00:00:00 GMT", formats,
                createDate(1900, Calendar.JANUARY, 0)));
    }

    @Test
    public void testParseQuotedDate() throws Exception {
        final Date date1 = createDate(2005, Calendar.OCTOBER, 14);
        final String[] formats = new String[] { DateUtils.PATTERN_RFC1123 };
        final Date date2 = DateUtils.parseDate("'Fri, 14 Oct 2005 00:00:00 GMT'", formats);
        Assert.assertEquals(date1, date2);
    }

    @Test
    public void testBasicDateFormat() throws Exception {
        final Date date = createDate(2005, Calendar.OCTOBER, 14);
        Assert.assertEquals("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.formatDate(date));
        Assert.assertEquals("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.formatDate(date, DateUtils.PATTERN_RFC1123));
    }

    @Test
    public void testIsBefore() throws Exception {
        final HeaderGroup message1 = new HeaderGroup();
        final HeaderGroup message2 = new HeaderGroup();
        Assert.assertThat(DateUtils.isBefore(null, null, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        Assert.assertThat(DateUtils.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, "eh?"));
        Assert.assertThat(DateUtils.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, "Tuesday, 26-Dec-2017 00:00:00 GMT"));
        Assert.assertThat(DateUtils.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "Wednesday, 25-Dec-2017 00:00:00 GMT"));
        Assert.assertThat(DateUtils.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(true));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "Thursday, 27-Dec-2017 00:00:00 GMT"));
        Assert.assertThat(DateUtils.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
    }

    @Test
    public void testIsAfter() throws Exception {
        final HeaderGroup message1 = new HeaderGroup();
        final HeaderGroup message2 = new HeaderGroup();
        Assert.assertThat(DateUtils.isAfter(null, null, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        Assert.assertThat(DateUtils.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, "eh?"));
        Assert.assertThat(DateUtils.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, "Tuesday, 26-Dec-2017 00:00:00 GMT"));
        Assert.assertThat(DateUtils.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "Thursday, 27-Dec-2017 00:00:00 GMT"));
        Assert.assertThat(DateUtils.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(true));
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "Wednesday, 25-Dec-2017 00:00:00 GMT"));
        Assert.assertThat(DateUtils.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
    }
}
