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

import java.time.Instant;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestWarningValue {

    @Test
    public void testParseSingleWarnValue() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assertions.assertEquals(1, result.length);
        final WarningValue wv = result[0];
        Assertions.assertEquals(110, wv.getWarnCode());
        Assertions.assertEquals("fred", wv.getWarnAgent());
        Assertions.assertEquals("\"stale\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
    }

    @Test
    public void testParseMultipleWarnValues() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", 111 wilma \"other\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assertions.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assertions.assertEquals(110, wv.getWarnCode());
        Assertions.assertEquals("fred", wv.getWarnAgent());
        Assertions.assertEquals("\"stale\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
        wv = result[1];
        Assertions.assertEquals(111, wv.getWarnCode());
        Assertions.assertEquals("wilma", wv.getWarnAgent());
        Assertions.assertEquals("\"other\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
    }

    @Test
    public void testMidHeaderParseErrorRecovery() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", bogus, 111 wilma \"other\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assertions.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assertions.assertEquals(110, wv.getWarnCode());
        Assertions.assertEquals("fred", wv.getWarnAgent());
        Assertions.assertEquals("\"stale\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
        wv = result[1];
        Assertions.assertEquals(111, wv.getWarnCode());
        Assertions.assertEquals("wilma", wv.getWarnAgent());
        Assertions.assertEquals("\"other\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
    }

    @Test
    public void testTrickyCommaMidHeaderParseErrorRecovery() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", \"bogus, dude\", 111 wilma \"other\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assertions.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assertions.assertEquals(110, wv.getWarnCode());
        Assertions.assertEquals("fred", wv.getWarnAgent());
        Assertions.assertEquals("\"stale\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
        wv = result[1];
        Assertions.assertEquals(111, wv.getWarnCode());
        Assertions.assertEquals("wilma", wv.getWarnAgent());
        Assertions.assertEquals("\"other\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
    }

    @Test
    public void testParseErrorRecoveryAtEndOfHeader() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", 111 wilma \"other\", \"bogus, dude\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assertions.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assertions.assertEquals(110, wv.getWarnCode());
        Assertions.assertEquals("fred", wv.getWarnAgent());
        Assertions.assertEquals("\"stale\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
        wv = result[1];
        Assertions.assertEquals(111, wv.getWarnCode());
        Assertions.assertEquals("wilma", wv.getWarnAgent());
        Assertions.assertEquals("\"other\"", wv.getWarnText());
        Assertions.assertNull(wv.getWarnDate());
    }

    @Test
    public void testConstructSingleWarnValue() {
        final WarningValue impl = new WarningValue("110 fred \"stale\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithIPv4Address() {
        final WarningValue impl = new WarningValue("110 192.168.1.1 \"stale\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("192.168.1.1", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithHostname() {
        final WarningValue impl = new WarningValue("110 foo.example.com \"stale\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("foo.example.com", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithHostnameAndPort() {
        final WarningValue impl = new WarningValue("110 foo.example.com:8080 \"stale\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("foo.example.com:8080", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithIPv4AddressAndPort() {
        final WarningValue impl = new WarningValue("110 192.168.1.1:8080 \"stale\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("192.168.1.1:8080", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithPseudonym() {
        final WarningValue impl = new WarningValue("110 ca$hm0ney \"stale\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("ca$hm0ney", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithTextWithSpaces() {
        final WarningValue impl = new WarningValue("110 fred \"stale stuff\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale stuff\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithTextWithCommas() {
        final WarningValue impl = new WarningValue("110 fred \"stale, stuff\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale, stuff\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithTextWithEscapedQuotes() {
        final WarningValue impl = new WarningValue("110 fred \"stale\\\" stuff\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\\\" stuff\"", impl.getWarnText());
        Assertions.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithAscTimeWarnDate() throws Exception {
        final WarningValue impl = new WarningValue("110 fred \"stale\" \"Sun Nov  6 08:49:37 1994\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        final Instant target = DateUtils.parseStandardDate("Sun Nov  6 08:49:37 1994");
        Assertions.assertEquals(target, impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithRFC850WarnDate() throws Exception {
        final WarningValue impl = new WarningValue("110 fred \"stale\" \"Sunday, 06-Nov-94 08:49:37 GMT\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        final Instant target = DateUtils.parseStandardDate("Sunday, 06-Nov-94 08:49:37 GMT");
        Assertions.assertEquals(target, impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithRFC1123WarnDate() throws Exception {
        final WarningValue impl = new WarningValue("110 fred \"stale\" \"Sun, 06 Nov 1994 08:49:37 GMT\"");
        Assertions.assertEquals(110, impl.getWarnCode());
        Assertions.assertEquals("fred", impl.getWarnAgent());
        Assertions.assertEquals("\"stale\"", impl.getWarnText());
        final Instant target = DateUtils.parseStandardDate("Sun, 06 Nov 1994 08:49:37 GMT");
        Assertions.assertEquals(target, impl.getWarnDate());
    }

}
