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

import java.util.Calendar;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DateUtils}.
 */
public class TestDateUtils {

    @Test
    public void testBasicDateParse() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(DateUtils.GMT);
        calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date1 = calendar.getTime();

        String[] formats = new String[] {
                DateUtils.PATTERN_RFC1123
                };
        Date date2 = DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", formats, null);
        Assert.assertEquals(date1, date2);
        date2 = DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", formats);
        Assert.assertEquals(date1, date2);
        date2 = DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT");
        Assert.assertEquals(date1, date2);
    }

    @Test
    public void testInvalidInput() throws Exception {
        try {
            DateUtils.parseDate(null, null, null);
            Assert.fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", new String[] {}, null);
            Assert.fail("DateParseException should habe been thrown");
        } catch (DateParseException ex) {
            // expected
        }
        try {
            DateUtils.formatDate(null);
            Assert.fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            DateUtils.formatDate(new Date(), null);
            Assert.fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testTwoDigitYearDateParse() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(DateUtils.GMT);
        calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date1 = calendar.getTime();

        String[] formats = new String[] {
                DateUtils.PATTERN_RFC1036
                };
        Date date2 = DateUtils.parseDate("Friday, 14-Oct-05 00:00:00 GMT", formats, null);
        Assert.assertEquals(date1, date2);

        calendar.set(1900, Calendar.JANUARY, 0, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startDate = calendar.getTime();

        calendar.set(1905, Calendar.OCTOBER, 14, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        date1 = calendar.getTime();

        date2 = DateUtils.parseDate("Friday, 14-Oct-05 00:00:00 GMT", formats, startDate);
        Assert.assertEquals(date1, date2);
    }

    @Test
    public void testParseQuotedDate() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(DateUtils.GMT);
        calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date1 = calendar.getTime();

        String[] formats = new String[] {
                DateUtils.PATTERN_RFC1123
                };
        Date date2 = DateUtils.parseDate("'Fri, 14 Oct 2005 00:00:00 GMT'", formats);
        Assert.assertEquals(date1, date2);
    }

    @Test
    public void testBasicDateFormat() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(DateUtils.GMT);
        calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();

        Assert.assertEquals("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.formatDate(date));
        Assert.assertEquals("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.formatDate(date, DateUtils.PATTERN_RFC1123));
    }

    @Test
    public void testConstructor() {
        new DateParseException();
        new DateParseException("Oppsie");
    }
}
