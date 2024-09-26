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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.hc.client5.http.impl.cache.HttpTestUtils;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestHttpCacheEntry {

    private Instant now;
    private Instant elevenSecondsAgo;
    private Instant nineSecondsAgo;
    private Resource mockResource;
    private HttpCacheEntry entry;

    @BeforeEach
    void setUp() {
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
                                     final Collection<String> variants) {
        return new HttpCacheEntry(requestDate, responseDate,
                "GET", "/", HttpTestUtils.headers(),
                status, HttpTestUtils.headers(headers), resource, variants);
    }

    private HttpCacheEntry makeEntry(final Instant requestDate,
                                     final Instant responseDate,
                                     final int status,
                                     final Header... headers) {
        return new HttpCacheEntry(requestDate, responseDate,
                "GET", "/", HttpTestUtils.headers(),
                status, HttpTestUtils.headers(headers), mockResource, null);
    }

    @Test
    void testGetHeadersReturnsCorrectHeaders() {
        entry = makeEntry(
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2"));
        assertEquals(2, entry.getHeaders("bar").length);
    }

    @Test
    void testGetFirstHeaderReturnsCorrectHeader() {
        entry = makeEntry(
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2"));
        assertEquals("barValue1", entry.getFirstHeader("bar").getValue());
    }

    @Test
    void testGetHeadersReturnsEmptyArrayIfNoneMatch() {
        entry = makeEntry(
                new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2"));
        assertEquals(0, entry.getHeaders("baz").length);
    }

    @Test
    void testGetFirstHeaderReturnsNullIfNoneMatch() {
        entry = makeEntry(
                new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2"));
        assertNull(entry.getFirstHeader("quux"));
    }

    @Test
    void testGetMethodReturnsCorrectRequestMethod() {
        entry = makeEntry(
                new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2"));
        assertEquals(Method.GET.name(), entry.getRequestMethod());
    }

    @Test
    void statusCodeComesFromOriginalStatusLine() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK);
        assertEquals(HttpStatus.SC_OK, entry.getStatus());
    }

    @Test
    void canGetOriginalRequestDate() {
        final Instant requestDate = Instant.now();
        entry = makeEntry(requestDate, Instant.now(), HttpStatus.SC_OK);
        assertEquals(requestDate, entry.getRequestInstant());
    }

    @Test
    void canGetOriginalResponseDate() {
        final Instant responseDate = Instant.now();
        entry = makeEntry(Instant.now(), responseDate, HttpStatus.SC_OK);
        assertEquals(responseDate, entry.getResponseInstant());
    }

    @Test
    void canGetOriginalResource() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK);
        assertSame(mockResource, entry.getResource());
    }

    @Test
    void canGetOriginalHeaders() {
        final Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0"),
                new BasicHeader("Date", DateUtils.formatStandardDate(now))
        };
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK, headers, mockResource, null);
        final Header[] result = entry.getHeaders();
        assertEquals(headers.length, result.length);
        for (int i = 0; i < headers.length; i++) {
            assertEquals(headers[i], result[i]);
        }
    }

    @Test
    void canRetrieveOriginalVariantMap() {
        final Set<String> variants = new HashSet<>();
        variants.add("A");
        variants.add("B");
        variants.add("C");
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new Header[]{}, mockResource,
                variants);
        final Set<String> result = entry.getVariants();
        assertEquals(3, result.size());
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
        assertTrue(result.contains("C"));
        assertFalse(result.contains("D"));
    }

    @Test
    void retrievedVariantMapIsNotModifiable() {
        final Set<String> variants = new HashSet<>();
        variants.add("A");
        variants.add("B");
        variants.add("C");
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new Header[]{}, mockResource,
                variants);
        final Set<String> result = entry.getVariants();
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.remove("A"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> result.add("D"));
    }

    @Test
    void canConvertToString() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK);
        assertNotNull(entry.toString());
        assertNotEquals("", entry.toString());
    }

    @Test
    void testMissingDateHeaderIsIgnored() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK);
        assertNull(entry.getInstant());
    }

    @Test
    void testMalformedDateHeaderIsIgnored() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new BasicHeader("Date", "asdf"));
        assertNull(entry.getInstant());
    }

    @Test
    void testValidDateHeaderIsParsed() {
        final Instant date = Instant.now().with(ChronoField.MILLI_OF_SECOND, 0);
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new BasicHeader("Date", DateUtils.formatStandardDate(date)));
        final Instant dateHeaderValue = entry.getInstant();
        assertNotNull(dateHeaderValue);
        assertEquals(date, dateHeaderValue);
    }

    @Test
    void testEpochDateHeaderIsParsed() {
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new BasicHeader("Date", DateUtils.formatStandardDate(Instant.EPOCH)));
        final Instant dateHeaderValue = entry.getInstant();
        assertNotNull(dateHeaderValue);
        assertEquals(Instant.EPOCH, dateHeaderValue);
    }

    @Test
    void testDateParsedOnce() {
        final Instant date = Instant.now().with(ChronoField.MILLI_OF_SECOND, 0);
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new BasicHeader("Date", DateUtils.formatStandardDate(date)));
        final Instant dateHeaderValue = entry.getInstant();
        assertNotNull(dateHeaderValue);
        assertSame(dateHeaderValue, entry.getInstant());
        assertSame(dateHeaderValue, entry.getInstant());
    }

    @Test
    void testExpiresParsedOnce() {
        final Instant date = Instant.now().with(ChronoField.MILLI_OF_SECOND, 0);
        entry = makeEntry(Instant.now(), Instant.now(), HttpStatus.SC_OK,
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(date)));
        final Instant lastModifiedHeaderValue = entry.getLastModified();
        assertNotNull(lastModifiedHeaderValue);
        assertSame(lastModifiedHeaderValue, entry.getLastModified());
        assertSame(lastModifiedHeaderValue, entry.getLastModified());
    }

    private static Instant createInstant(final int year, final Month month, final int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.of("GMT")).toInstant();
    }

    @Test
    void testIsCacheEntryNewer() {
        assertFalse(HttpCacheEntry.isNewer(null, null));
        entry = makeEntry();
        final HeaderGroup message = new HeaderGroup();
        assertFalse(HttpCacheEntry.isNewer(entry, message));

        entry = makeEntry(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message.setHeader(new BasicHeader(HttpHeaders.DATE, "eh?"));
        assertFalse(HttpCacheEntry.isNewer(entry, message));

        entry = makeEntry(new BasicHeader(HttpHeaders.DATE, "huh?"));
        message.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertFalse(HttpCacheEntry.isNewer(entry, message));

        entry = makeEntry(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 27))));
        message.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertTrue(HttpCacheEntry.isNewer(entry, message));

        entry = makeEntry(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 25))));
        message.setHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(createInstant(2017, Month.DECEMBER, 26))));
        assertFalse(HttpCacheEntry.isNewer(entry, message));
    }

}
