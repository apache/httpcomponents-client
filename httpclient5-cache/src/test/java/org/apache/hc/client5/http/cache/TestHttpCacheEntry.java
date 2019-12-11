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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;

public class TestHttpCacheEntry {

    private Date now;
    private Date elevenSecondsAgo;
    private Date nineSecondsAgo;
    private Resource mockResource;
    private HttpCacheEntry entry;

    @Before
    public void setUp() {
        now = new Date();
        elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);
        mockResource = mock(Resource.class);
    }

    private HttpCacheEntry makeEntry(final Header[] headers) {
        return new HttpCacheEntry(elevenSecondsAgo, nineSecondsAgo, HttpStatus.SC_OK, headers, mockResource);
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
        assertEquals(null, entry.getFirstHeader("quux"));
    }

    @Test
    public void testCacheEntryWithNoVaryHeaderDoesNotHaveVariants() {
        final Header[] headers = new Header[0];
        entry = makeEntry(headers);
        assertFalse(entry.hasVariants());
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

    @SuppressWarnings("unused")
    @Test
    public void mustProvideRequestDate() {
        try {
            new HttpCacheEntry(null, new Date(), HttpStatus.SC_OK, new Header[]{}, mockResource);
            fail("Should have thrown exception");
        } catch (final NullPointerException expected) {
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void mustProvideResponseDate() {
        try {
            new HttpCacheEntry(new Date(), null, HttpStatus.SC_OK, new Header[]{}, mockResource);
            fail("Should have thrown exception");
        } catch (final NullPointerException expected) {
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void mustProvideResponseHeaders() {
        try {
            new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK, null, mockResource);
            fail("Should have thrown exception");
        } catch (final NullPointerException expected) {
        }
    }

    @Test
    public void statusCodeComesFromOriginalStatusLine() {
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK, new Header[]{}, mockResource);
        assertEquals(HttpStatus.SC_OK, entry.getStatus());
    }

    @Test
    public void canGetOriginalRequestDate() {
        final Date requestDate = new Date();
        entry = new HttpCacheEntry(requestDate, new Date(), HttpStatus.SC_OK, new Header[]{}, mockResource);
        assertSame(requestDate, entry.getRequestDate());
    }

    @Test
    public void canGetOriginalResponseDate() {
        final Date responseDate = new Date();
        entry = new HttpCacheEntry(new Date(), responseDate, HttpStatus.SC_OK, new Header[]{}, mockResource);
        assertSame(responseDate, entry.getResponseDate());
    }

    @Test
    public void canGetOriginalResource() {
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK, new Header[]{}, mockResource);
        assertSame(mockResource, entry.getResource());
    }

    @Test
    public void canGetOriginalHeaders() {
        final Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0"),
                new BasicHeader("Date", DateUtils.formatDate(now))
        };
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK, headers, mockResource);
        final Header[] result = entry.getHeaders();
        assertEquals(headers.length, result.length);
        for(int i=0; i<headers.length; i++) {
            assertEquals(headers[i], result[i]);
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void canConstructWithoutVariants() {
        new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK, new Header[]{}, mockResource);
    }

    @SuppressWarnings("unused")
    @Test
    public void canProvideVariantMap() {
        new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
                new Header[]{}, mockResource,
                new HashMap<String,String>());
    }

    @Test
    public void canRetrieveOriginalVariantMap() {
        final Map<String,String> variantMap = new HashMap<>();
        variantMap.put("A","B");
        variantMap.put("C","D");
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
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
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
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
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
                new Header[]{}, mockResource);
        assertNotNull(entry.toString());
        assertFalse("".equals(entry.toString()));
    }

    @Test
    public void testMissingDateHeaderIsIgnored() {
        final Header[] headers = new Header[] {};
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
                                   headers, mockResource);
        assertNull(entry.getDate());
    }

    @Test
    public void testMalformedDateHeaderIsIgnored() {
        final Header[] headers = new Header[] { new BasicHeader("Date", "asdf") };
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
                                   headers, mockResource);
        assertNull(entry.getDate());
    }

    @Test
    public void testValidDateHeaderIsParsed() {
        final long nowMs = System.currentTimeMillis();
        // round down to nearest second to make comparison easier
        final Date date = new Date(nowMs - (nowMs % 1000L));
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils.formatDate(date)) };
        entry = new HttpCacheEntry(new Date(), new Date(), HttpStatus.SC_OK,
                                   headers, mockResource);
        final Date dateHeaderValue = entry.getDate();
        assertNotNull(dateHeaderValue);
        assertEquals(date.getTime(), dateHeaderValue.getTime());
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
}
