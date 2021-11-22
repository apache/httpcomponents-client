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
import org.junit.Assert;
import org.junit.Test;

public class TestWarningValue {

    @Test
    public void testParseSingleWarnValue() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(1, result.length);
        final WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
    }

    @Test
    public void testParseMultipleWarnValues() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", 111 wilma \"other\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
    }

    @Test
    public void testMidHeaderParseErrorRecovery() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", bogus, 111 wilma \"other\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
    }

    @Test
    public void testTrickyCommaMidHeaderParseErrorRecovery() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", \"bogus, dude\", 111 wilma \"other\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
    }

    @Test
    public void testParseErrorRecoveryAtEndOfHeader() {
        final Header h = new BasicHeader("Warning","110 fred \"stale\", 111 wilma \"other\", \"bogus, dude\"");
        final WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnInstant());
    }

    @Test
    public void testConstructSingleWarnValue() {
        final WarningValue impl = new WarningValue("110 fred \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithIPv4Address() {
        final WarningValue impl = new WarningValue("110 192.168.1.1 \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("192.168.1.1", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithHostname() {
        final WarningValue impl = new WarningValue("110 foo.example.com \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("foo.example.com", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithHostnameAndPort() {
        final WarningValue impl = new WarningValue("110 foo.example.com:8080 \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("foo.example.com:8080", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithIPv4AddressAndPort() {
        final WarningValue impl = new WarningValue("110 192.168.1.1:8080 \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("192.168.1.1:8080", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithPseudonym() {
        final WarningValue impl = new WarningValue("110 ca$hm0ney \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("ca$hm0ney", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithTextWithSpaces() {
        final WarningValue impl = new WarningValue("110 fred \"stale stuff\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale stuff\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithTextWithCommas() {
        final WarningValue impl = new WarningValue("110 fred \"stale, stuff\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale, stuff\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithTextWithEscapedQuotes() {
        final WarningValue impl = new WarningValue("110 fred \"stale\\\" stuff\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\\\" stuff\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithAscTimeWarnDate() throws Exception {
        final WarningValue impl = new WarningValue("110 fred \"stale\" \"Sun Nov  6 08:49:37 1994\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        final Instant target = DateUtils.parseStandardDate("Sun Nov  6 08:49:37 1994");
        Assert.assertEquals(target, impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithRFC850WarnDate() throws Exception {
        final WarningValue impl = new WarningValue("110 fred \"stale\" \"Sunday, 06-Nov-94 08:49:37 GMT\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        final Instant target = DateUtils.parseStandardDate("Sunday, 06-Nov-94 08:49:37 GMT");
        Assert.assertEquals(target, impl.getWarnInstant());
    }

    @Test
    public void testConstructWarnValueWithRFC1123WarnDate() throws Exception {
        final WarningValue impl = new WarningValue("110 fred \"stale\" \"Sun, 06 Nov 1994 08:49:37 GMT\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        final Instant target = DateUtils.parseStandardDate("Sun, 06 Nov 1994 08:49:37 GMT");
        Assert.assertEquals(target, impl.getWarnInstant());
    }

}
