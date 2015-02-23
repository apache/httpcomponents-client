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
package org.apache.http.client.cache;

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

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;

public class TestHttpCacheEntry {

    private Date now;
    private Date elevenSecondsAgo;
    private Date nineSecondsAgo;
    private Resource mockResource;
    private StatusLine statusLine;
    private HttpCacheEntry entry;

    @Before
    public void setUp() {
        now = new Date();
        elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);
        statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        mockResource = mock(Resource.class);
    }

    private HttpCacheEntry makeEntry(final Header[] headers) {
        return new HttpCacheEntry(elevenSecondsAgo, nineSecondsAgo,
                statusLine, headers, mockResource, HeaderConstants.GET_METHOD);
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
            new HttpCacheEntry(null, new Date(), statusLine,
                    new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
            fail("Should have thrown exception");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void mustProvideResponseDate() {
        try {
            new HttpCacheEntry(new Date(), null, statusLine,
                    new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
            fail("Should have thrown exception");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void mustProvideStatusLine() {
        try {
            new HttpCacheEntry(new Date(), new Date(), null,
                    new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
            fail("Should have thrown exception");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void mustProvideResponseHeaders() {
        try {
            new HttpCacheEntry(new Date(), new Date(), statusLine,
                    null, mockResource, HeaderConstants.GET_METHOD);
            fail("Should have thrown exception");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void canRetrieveOriginalStatusLine() {
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertSame(statusLine, entry.getStatusLine());
    }

    @Test
    public void protocolVersionComesFromOriginalStatusLine() {
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertSame(statusLine.getProtocolVersion(),
                entry.getProtocolVersion());
    }

    @Test
    public void reasonPhraseComesFromOriginalStatusLine() {
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertSame(statusLine.getReasonPhrase(), entry.getReasonPhrase());
    }

    @Test
    public void statusCodeComesFromOriginalStatusLine() {
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertEquals(statusLine.getStatusCode(), entry.getStatusCode());
    }

    @Test
    public void canGetOriginalRequestDate() {
        final Date requestDate = new Date();
        entry = new HttpCacheEntry(requestDate, new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertSame(requestDate, entry.getRequestDate());
    }

    @Test
    public void canGetOriginalResponseDate() {
        final Date responseDate = new Date();
        entry = new HttpCacheEntry(new Date(), responseDate, statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertSame(responseDate, entry.getResponseDate());
    }

    @Test
    public void canGetOriginalResource() {
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertSame(mockResource, entry.getResource());
    }

    @Test
    public void canGetOriginalHeaders() {
        final Header[] headers = {
                new BasicHeader("Server", "MockServer/1.0"),
                new BasicHeader("Date", DateUtils.formatDate(now))
        };
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                headers, mockResource, HeaderConstants.GET_METHOD);
        final Header[] result = entry.getAllHeaders();
        assertEquals(headers.length, result.length);
        for(int i=0; i<headers.length; i++) {
            assertEquals(headers[i], result[i]);
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void canConstructWithoutVariants() {
        new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
    }

    @SuppressWarnings("unused")
    @Test
    public void canProvideVariantMap() {
        new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource,
                new HashMap<String,String>(), HeaderConstants.GET_METHOD);
    }

    @Test
    public void canRetrieveOriginalVariantMap() {
        final Map<String,String> variantMap = new HashMap<String,String>();
        variantMap.put("A","B");
        variantMap.put("C","D");
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource,
                variantMap, HeaderConstants.GET_METHOD);
        final Map<String,String> result = entry.getVariantMap();
        assertEquals(2, result.size());
        assertEquals("B", result.get("A"));
        assertEquals("D", result.get("C"));
    }

    @Test
    public void retrievedVariantMapIsNotModifiable() {
        final Map<String,String> variantMap = new HashMap<String,String>();
        variantMap.put("A","B");
        variantMap.put("C","D");
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource,
                variantMap, HeaderConstants.GET_METHOD);
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
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                new Header[]{}, mockResource, HeaderConstants.GET_METHOD);
        assertNotNull(entry.toString());
        assertFalse("".equals(entry.toString()));
    }

    @Test
    public void testMissingDateHeaderIsIgnored() {
        final Header[] headers = new Header[] {};
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                                   headers, mockResource, HeaderConstants.GET_METHOD);
        assertNull(entry.getDate());
    }

    @Test
    public void testMalformedDateHeaderIsIgnored() {
        final Header[] headers = new Header[] { new BasicHeader("Date", "asdf") };
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                                   headers, mockResource, HeaderConstants.GET_METHOD);
        assertNull(entry.getDate());
    }

    @Test
    public void testValidDateHeaderIsParsed() {
        final long nowMs = System.currentTimeMillis();
        // round down to nearest second to make comparison easier
        final Date date = new Date(nowMs - (nowMs % 1000L));
        final Header[] headers = new Header[] { new BasicHeader("Date", DateUtils.formatDate(date)) };
        entry = new HttpCacheEntry(new Date(), new Date(), statusLine,
                                   headers, mockResource, HeaderConstants.GET_METHOD);
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
