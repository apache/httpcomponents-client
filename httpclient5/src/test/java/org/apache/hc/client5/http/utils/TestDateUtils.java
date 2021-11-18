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

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DateUtils}.
 */
public class TestDateUtils {

    private static Instant createInstant(final int year, final Month month, final int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.of("GMT")).toInstant();
    }

    private static Date createDate(final int year, final Month month, final int day) {
        final Instant instant = createInstant(year, month, day);
        return new Date(instant.toEpochMilli());
    }

    @Test
    public void testBasicDateParse() throws Exception {
        final Instant instant = createInstant(2005, Month.OCTOBER, 14);
        Assertions.assertEquals(instant, DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.FORMATTER_RFC1123));
        Assertions.assertEquals(instant, DateUtils.parseDate("Friday, 14 Oct 2005 00:00:00 GMT", DateUtils.FORMATTER_RFC1123));
        Assertions.assertEquals(instant, DateUtils.parseDate("Fri, 14-Oct-2005 00:00:00 GMT", DateUtils.FORMATTER_RFC1036));
        Assertions.assertEquals(instant, DateUtils.parseDate("Friday, 14-Oct-2005 00:00:00 GMT", DateUtils.FORMATTER_RFC1036));
        Assertions.assertEquals(instant.minus(2, ChronoUnit.HOURS),
                DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 CET", DateUtils.FORMATTER_RFC1123));
        Assertions.assertEquals(instant.minus(2, ChronoUnit.HOURS),
                DateUtils.parseDate("Fri, 14-Oct-05 00:00:00 CET", DateUtils.FORMATTER_RFC1036));
        Assertions.assertEquals(instant, DateUtils.parseStandardDate("Fri, 14 Oct 2005 00:00:00 GMT"));
    }

    @Test
    public void testDateParseMessage() throws Exception {
        final HeaderGroup message1 = new HeaderGroup();
        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "Fri, 14 Oct 2005 00:00:00 GMT"));
        Assertions.assertEquals(createInstant(2005, Month.OCTOBER, 14), DateUtils.parseStandardDate(message1, HttpHeaders.DATE));

        final HeaderGroup message2 = new HeaderGroup();
        message2.addHeader(new BasicHeader(HttpHeaders.DATE, "Fri, 14 Oct 2005 00:00:00 GMT"));
        message2.addHeader(new BasicHeader(HttpHeaders.DATE, "Fri, 21 Oct 2005 00:00:00 GMT"));
        Assertions.assertEquals(createInstant(2005, Month.OCTOBER, 14), DateUtils.parseStandardDate(message2, HttpHeaders.DATE));
    }

    @Test
    public void testMalformedDate() {
        Assertions.assertNull(DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT", new DateTimeFormatter[] {}));
    }

    @Test
    public void testInvalidInput() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> DateUtils.parseStandardDate(null));
        Assertions.assertThrows(NullPointerException.class, () -> DateUtils.formatStandardDate(null));
    }

    @Test
    public void testTwoDigitYearDateParse() throws Exception {
        Assertions.assertEquals(createInstant(2005, Month.OCTOBER, 14),
                DateUtils.parseDate("Friday, 14-Oct-05 00:00:00 GMT", DateUtils.FORMATTER_RFC1036));
    }

    @Test
    public void testParseQuotedDate() throws Exception {
        Assertions.assertEquals(createInstant(2005, Month.OCTOBER, 14),
                DateUtils.parseDate("'Fri, 14 Oct 2005 00:00:00 GMT'", DateUtils.FORMATTER_RFC1123));
    }

    @Test
    public void testBasicDateFormat() throws Exception {
        final Instant instant = createInstant(2005, Month.OCTOBER, 14);
        Assertions.assertEquals("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.formatStandardDate(instant));
        Assertions.assertEquals("Fri, 14 Oct 2005 00:00:00 GMT", DateUtils.formatDate(instant, DateUtils.FORMATTER_RFC1123));
        Assertions.assertEquals("Fri, 14-Oct-05 00:00:00 GMT", DateUtils.formatDate(instant, DateUtils.FORMATTER_RFC1036));
        Assertions.assertEquals("Fri Oct 14 00:00:00 2005", DateUtils.formatDate(instant, DateUtils.FORMATTER_ASCTIME));
    }

}
