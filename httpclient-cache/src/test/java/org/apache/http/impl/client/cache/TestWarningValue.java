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
package org.apache.http.impl.client.cache;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

public class TestWarningValue {

    @Test
    public void testParseSingleWarnValue() {
        Header h = new BasicHeader("Warning","110 fred \"stale\"");
        WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(1, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
    }

    @Test
    public void testParseMultipleWarnValues() {
        Header h = new BasicHeader("Warning","110 fred \"stale\", 111 wilma \"other\"");
        WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
    }

    @Test
    public void testMidHeaderParseErrorRecovery() {
        Header h = new BasicHeader("Warning","110 fred \"stale\", bogus, 111 wilma \"other\"");
        WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
    }

    @Test
    public void testTrickyCommaMidHeaderParseErrorRecovery() {
        Header h = new BasicHeader("Warning","110 fred \"stale\", \"bogus, dude\", 111 wilma \"other\"");
        WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
    }

    @Test
    public void testParseErrorRecoveryAtEndOfHeader() {
        Header h = new BasicHeader("Warning","110 fred \"stale\", 111 wilma \"other\", \"bogus, dude\"");
        WarningValue[] result = WarningValue.getWarningValues(h);
        Assert.assertEquals(2, result.length);
        WarningValue wv = result[0];
        Assert.assertEquals(110, wv.getWarnCode());
        Assert.assertEquals("fred", wv.getWarnAgent());
        Assert.assertEquals("\"stale\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
        wv = result[1];
        Assert.assertEquals(111, wv.getWarnCode());
        Assert.assertEquals("wilma", wv.getWarnAgent());
        Assert.assertEquals("\"other\"", wv.getWarnText());
        Assert.assertNull(wv.getWarnDate());
    }

    @Test
    public void testConstructSingleWarnValue() {
        WarningValue impl = new WarningValue("110 fred \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithIPv4Address() {
        WarningValue impl = new WarningValue("110 192.168.1.1 \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("192.168.1.1", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithHostname() {
        WarningValue impl = new WarningValue("110 foo.example.com \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("foo.example.com", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithHostnameAndPort() {
        WarningValue impl = new WarningValue("110 foo.example.com:8080 \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("foo.example.com:8080", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithIPv4AddressAndPort() {
        WarningValue impl = new WarningValue("110 192.168.1.1:8080 \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("192.168.1.1:8080", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithPseudonym() {
        WarningValue impl = new WarningValue("110 ca$hm0ney \"stale\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("ca$hm0ney", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithTextWithSpaces() {
        WarningValue impl = new WarningValue("110 fred \"stale stuff\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale stuff\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithTextWithCommas() {
        WarningValue impl = new WarningValue("110 fred \"stale, stuff\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale, stuff\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithTextWithEscapedQuotes() {
        WarningValue impl = new WarningValue("110 fred \"stale\\\" stuff\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\\\" stuff\"", impl.getWarnText());
        Assert.assertNull(impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithAscTimeWarnDate() throws Exception {
        WarningValue impl = new WarningValue("110 fred \"stale\" \"Sun Nov  6 08:49:37 1994\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Date target = DateUtils.parseDate("Sun Nov  6 08:49:37 1994");
        Assert.assertEquals(target, impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithRFC850WarnDate() throws Exception {
        WarningValue impl = new WarningValue("110 fred \"stale\" \"Sunday, 06-Nov-94 08:49:37 GMT\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Date target = DateUtils.parseDate("Sunday, 06-Nov-94 08:49:37 GMT");
        Assert.assertEquals(target, impl.getWarnDate());
    }

    @Test
    public void testConstructWarnValueWithRFC1123WarnDate() throws Exception {
        WarningValue impl = new WarningValue("110 fred \"stale\" \"Sun, 06 Nov 1994 08:49:37 GMT\"");
        Assert.assertEquals(110, impl.getWarnCode());
        Assert.assertEquals("fred", impl.getWarnAgent());
        Assert.assertEquals("\"stale\"", impl.getWarnText());
        Date target = DateUtils.parseDate("Sun, 06 Nov 1994 08:49:37 GMT");
        Assert.assertEquals(target, impl.getWarnDate());
    }

}
