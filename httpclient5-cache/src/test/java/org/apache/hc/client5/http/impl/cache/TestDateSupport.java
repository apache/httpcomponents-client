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

package org.apache.hc.client5.http.impl.cache;

import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DateSupport}.
 */
public class TestDateSupport {

    private static Instant createInstant(final int year, final Month month, final int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.of("GMT")).toInstant();
    }

    @Test
    public void testIsBefore() throws Exception {
        final HeaderGroup message1 = new HeaderGroup();
        final HeaderGroup message2 = new HeaderGroup();
        assertThat(DateSupport.isBefore(null, null, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        assertThat(DateSupport.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, "eh?"));
        assertThat(DateSupport.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertThat(DateSupport.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 25))));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertThat(DateSupport.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(true));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 27))));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertThat(DateSupport.isBefore(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
    }

    @Test
    public void testIsAfter() throws Exception {
        final HeaderGroup message1 = new HeaderGroup();
        final HeaderGroup message2 = new HeaderGroup();
        assertThat(DateSupport.isAfter(null, null, HttpHeaders.DATE), CoreMatchers.equalTo(false));
        assertThat(DateSupport.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, "eh?"));
        assertThat(DateSupport.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertThat(DateSupport.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 27))));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertThat(DateSupport.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(true));

        message1.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 25))));
        message2.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertThat(DateSupport.isAfter(message1, message2, HttpHeaders.DATE), CoreMatchers.equalTo(false));
    }

}
