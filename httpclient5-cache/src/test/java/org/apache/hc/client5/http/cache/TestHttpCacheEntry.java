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
package org.apache.hc.client5.http.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.impl.cache.HttpTestUtils;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestHttpCacheEntry {

    private Instant now;
    private Instant elevenSecondsAgo;
    private Instant nineSecondsAgo;
    private Resource mockResource;
    private HttpCacheEntry entry;

    @BeforeEach
    public void setUp() {
        now = Instant.now();
        elevenSecondsAgo = now.minusSeconds(11);
        nineSecondsAgo = now.minusSeconds(9);
        mockResource = mock(Resource.class);
    }

    private HttpCacheEntry makeEntry(final Header... headers) {
        return new HttpCacheEntry(elevenSecondsAgo, nineSecondsAgo,
                "GET", "/", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(headers), mockResource, null);
    }

    private HttpCacheEntry makeEntry(final Instant requestDate,
                                     final Instant responseDate,
                                     final int status,
                                     final Header[] headers,
                                     final Resource resource,
                                     final Map<String, String> variantMap) {
        return new HttpCacheEntry(requestDate, responseDate,
                "GET", "/", HttpTestUtils.headers(),
                status, HttpTestUtils.headers(headers), resource, variantMap);
    }
    @Test
    public void testGetHeadersReturnsCorrectHeaders() {
        final Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        entry = makeEntry(headers);
        assertEquals(2, entry.getHeaders("bar").length);
    }

    @Test
    public void testGetFirstHeaderReturnsCorrectHeader() {
        final Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        entry = makeEntry(headers);
        assertEquals("barValue1", entry.getFirstHeader("bar").getValue());
    }

    @Test
    public void testGetHeadersReturnsEmptyArrayIfNoneMatch() {
        final Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        entry = makeEntry(headers);
        assertEquals(0, entry.getHeaders("baz").length);
    }

    @Test
    public void testGetFirstHeaderReturnsNullIfNoneMatch() {
        final Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        entry = makeEntry(headers);
        assertNull(entry.getFirstHeader("quux"));
    }

    @Test
    public void testCacheEntryWithOneVaryHeaderHasVariants() {
        final Header[] headers = { new BasicHeader("Vary", "User-Agent") };
        entry = makeEntry(headers);
        assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithMultipleVaryHeadersHasVariants() {
        final Header[] headers = { new BasicHeader("Vary", "User-Agent"),
                new BasicHeader("Vary", "Accept-Encoding")
        };
        entry = makeEntry(headers);
        assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithVaryStarHasVariants(){
        final Header[] headers = { new BasicHeader("Vary", "*") };
        entry = makeEntry(headers);
        assertTrue(entry.hasVariants());
    }

    @Test
    public void testGetMethodReturnsCorrectRequestMethod() {
        final Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        entry = makeEntry(headers);
        assertEquals(HeaderConstants.GET_METHOD, entry.getRequestMethod());
    }

    @Test
    public void statusCodeComesFromOriginalStatusLine() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, new Header[]{}, mockResource, null);
        assertEquals(HttpStatus.SC_OK, entry.getStatus());
    }

    @Test
    public void canGetOriginalRequestDate() {
        final Instant requestDate = Instant.now();
        entry = makeEntry(requestDate, Instant.now(), HttpStatus.SC_OK, new Header[]{}, mockResource, null);
        assertEquals(requestDate, entry.getRequestInstant());
    }

    @Test
    public void canGetOriginalResponseDate() {
        final Instant responseDate = Instant.now();
        entry = makeEntry(Instant.now(), responseDate, HttpStatus.SC_OK, new Header[]{}, mockResource, null);
        assertEquals(responseDate, entry.getResponseInstant());
    }

    @Test
    public void canGetOriginalResource() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, new Header[]{}, mockResource, null);
        assertSame(mockResource, entry.getResource());
    }

    @Test
    public void canGetOriginalHeaders() {
        final Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0"),
                new BasicHeader("Date", DateUtils.formatStandardDate(now))
        };
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, headers, mockResource, null);
        final Header[] result = entry.getHeaders();
        assertEquals(headers.length, result.length);
        for(int i=0; i<headers.length; i++) {
            assertEquals(headers[i], result[i]);
        }
    }

    @Test
    public void canConstructWithoutVariants() {
        makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, new Header[]{}, mockResource, null);
    }

    @Test
    public void canProvideVariantMap() {
        makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new Header[]{}, mockResource,
                new HashMap<>());
    }

    @Test
    public void canRetrieveOriginalVariantMap() {
        final Map<String,String> variantMap = new HashMap<>();
        variantMap.put("A","B");
        variantMap.put("C","D");
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new Header[]{}, mockResource,
                variantMap);
        final Map<String,String> result = entry.getVariantMap();
        assertEquals(2, result.size());
        assertEquals("B", result.get("A"));
        assertEquals("D", result.get("C"));
    }

    @Test
    public void retrievedVariantMapIsNotModifiable() {
        final Map<String,String> variantMap = new HashMap<>();
        variantMap.put("A","B");
        variantMap.put("C","D");
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new Header[]{}, mockResource,
                variantMap);
        final Map<String,String> result = entry.getVariantMap();
        try {
            result.remove("A");
            fail("Should have thrown exception");
        } catch (final UnsupportedOperationException expected) {
        }
        try {
            result.put("E","F");
            fail("Should have thrown exception");
        } catch (final UnsupportedOperationException expected) {
        }
    }

    @Test
    public void canConvertToString() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, new Header[]{}, mockResource, null);
        assertNotNull(entry.toString());
        assertNotEquals("", entry.toString());
    }

    @Test
    public void testMissingDateHeaderIsIgnored() {
        final Header[] headers = new Header[] {};
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, headers, mockResource, null);
        assertNull(entry.getDate());
    }

    @Test
    public void testMalformedDateHeaderIsIgnored() {
        final Header[] headers = new Header[] { new BasicHeader("Date", "asdf") };
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, headers, mockResource, null);
        assertNull(entry.getDate());
    }

    @Test
    public void testValidDateHeaderIsParsed() {
        final Instant date = Instant.now().with(ChronoField.MILLI_OF_SECOND, 0);
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils.formatStandardDate(date)) };
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, headers, mockResource, null);
        final Date dateHeaderValue = entry.getDate();
        assertNotNull(dateHeaderValue);
        assertEquals(DateUtils.toDate(date), dateHeaderValue);
    }

}
