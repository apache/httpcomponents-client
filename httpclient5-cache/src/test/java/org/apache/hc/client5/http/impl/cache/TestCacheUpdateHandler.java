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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestCacheUpdateHandler {

    private Instant requestDate;
    private Instant responseDate;

    private CacheUpdateHandler impl;
    private HttpCacheEntry entry;
    private Instant now;
    private Instant oneSecondAgo;
    private Instant twoSecondsAgo;
    private Instant eightSecondsAgo;
    private Instant tenSecondsAgo;
    private HttpResponse response;

    @BeforeEach
    public void setUp() throws Exception {
        requestDate = Instant.now().minusSeconds(1);
        responseDate = Instant.now();

        now = Instant.now();
        oneSecondAgo = now.minusSeconds(1);
        twoSecondsAgo = now.minusSeconds(2);
        eightSecondsAgo = now.minusSeconds(8);
        tenSecondsAgo = now.minusSeconds(10);

        response = new BasicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");

        impl = new CacheUpdateHandler();
    }

    @Test
    public void testUpdateCacheEntryReturnsDifferentEntryInstance()
            throws IOException {
        entry = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry newEntry = impl.updateCacheEntry(null, entry,
                requestDate, responseDate, response);
        assertNotSame(newEntry, entry);
    }

    @Test
    public void testHeadersAreMergedCorrectly() throws IOException {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("ETag", "\"etag\"")};
        entry = HttpTestUtils.makeCacheEntry(headers);
        response.setHeaders();

        final HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, entry,
                Instant.now(), Instant.now(), response);

         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(responseDate)));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("ETag", "\"etag\""));
    }

    @Test
    public void testNewerHeadersReplaceExistingHeaders() throws IOException {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("Cache-Control", "private"),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("Cache-Control", "max-age=0"),};
        entry = HttpTestUtils.makeCacheEntry(headers);

        response.setHeaders(new BasicHeader("Last-Modified", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("Cache-Control", "public"));

        final HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, entry,
                Instant.now(), Instant.now(), response);

         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(requestDate)));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("ETag", "\"etag\""));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Last-Modified", DateUtils.formatStandardDate(responseDate)));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Cache-Control", "public"));
    }

    @Test
    public void testNewHeadersAreAddedByMerge() throws IOException {

        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("ETag", "\"etag\"")};

        entry = HttpTestUtils.makeCacheEntry(headers);
        response.setHeaders(new BasicHeader("Last-Modified", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("Cache-Control", "public"));

        final HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, entry,
                Instant.now(), Instant.now(), response);

         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(requestDate)));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("ETag", "\"etag\""));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Last-Modified", DateUtils.formatStandardDate(responseDate)));
         assertThat(updatedEntry, ContainsHeaderMatcher.contains("Cache-Control", "public"));
    }

    @Test
    public void oldHeadersRetainedIfResponseOlderThanEntry()
            throws Exception {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("ETag", "\"new-etag\"")
        };
        entry = HttpTestUtils.makeCacheEntry(twoSecondsAgo, now, headers);
        response.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        response.setHeader("ETag", "\"old-etag\"");
        final HttpCacheEntry result = impl.updateCacheEntry("A", entry, Instant.now(),
                Instant.now(), response);
         assertThat(result, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(oneSecondAgo)));
         assertThat(result, ContainsHeaderMatcher.contains("ETag", "\"new-etag\""));
    }

    @Test
    public void testUpdatedEntryHasLatestRequestAndResponseDates()
            throws IOException {
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo);
        final HttpCacheEntry updated = impl.updateCacheEntry(null, entry,
                twoSecondsAgo, oneSecondAgo, response);

        assertEquals(twoSecondsAgo, updated.getRequestInstant());
        assertEquals(oneSecondAgo, updated.getResponseInstant());
    }

    @Test
    public void entry1xxWarningsAreRemovedOnUpdate() throws Exception {
        final Header[] headers = {
                new BasicHeader("Warning", "110 fred \"Response is stale\""),
                new BasicHeader("ETag", "\"old\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(eightSecondsAgo))
        };
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo, headers);
        response.setHeader("ETag", "\"new\"");
        response.setHeader("Date", DateUtils.formatStandardDate(twoSecondsAgo));
        final HttpCacheEntry updated = impl.updateCacheEntry(null, entry,
                twoSecondsAgo, oneSecondAgo, response);

        assertEquals(0, updated.getHeaders("Warning").length);
    }

    @Test
    public void entryWithMalformedDateIsStillUpdated() throws Exception {
        final Header[] headers = {
                new BasicHeader("ETag", "\"old\""),
                new BasicHeader("Date", "bad-date")
        };
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo, headers);
        response.setHeader("ETag", "\"new\"");
        response.setHeader("Date", DateUtils.formatStandardDate(twoSecondsAgo));
        final HttpCacheEntry updated = impl.updateCacheEntry(null, entry,
                twoSecondsAgo, oneSecondAgo, response);

        assertEquals("\"new\"", updated.getFirstHeader("ETag").getValue());
    }

    @Test
    public void entryIsStillUpdatedByResponseWithMalformedDate() throws Exception {
        final Header[] headers = {
                new BasicHeader("ETag", "\"old\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo))
        };
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo, headers);
        response.setHeader("ETag", "\"new\"");
        response.setHeader("Date", "bad-date");
        final HttpCacheEntry updated = impl.updateCacheEntry(null, entry, twoSecondsAgo,
                oneSecondAgo, response);

        assertEquals("\"new\"", updated.getFirstHeader("ETag").getValue());
    }

    @Test
    public void cannotUpdateFromANon304OriginResponse() throws Exception {
        entry = HttpTestUtils.makeCacheEntry();
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        try {
            impl.updateCacheEntry("A", entry, Instant.now(), Instant.now(),
                    response);
            fail("should have thrown exception");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testCacheUpdateAddsVariantURIToParentEntry() throws Exception {
        final String parentCacheKey = "parentCacheKey";
        final String variantCacheKey = "variantCacheKey";
        final String existingVariantKey = "existingVariantKey";
        final String newVariantCacheKey = "newVariantCacheKey";
        final String newVariantKey = "newVariantKey";
        final Map<String,String> existingVariants = new HashMap<>();
        existingVariants.put(existingVariantKey, variantCacheKey);
        final HttpCacheEntry parent = HttpTestUtils.makeCacheEntry(existingVariants);
        final HttpCacheEntry variant = HttpTestUtils.makeCacheEntry();

        final HttpCacheEntry result = impl.updateParentCacheEntry(parentCacheKey, parent, variant, newVariantKey, newVariantCacheKey);
        final Map<String,String> resultMap = result.getVariantMap();
        assertEquals(2, resultMap.size());
        assertEquals(variantCacheKey, resultMap.get(existingVariantKey));
        assertEquals(newVariantCacheKey, resultMap.get(newVariantKey));
    }

    @Test
    public void testContentEncodingHeaderIsNotUpdatedByMerge() throws IOException {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Content-Encoding", "identity")};

        entry = HttpTestUtils.makeCacheEntry(headers);
        response.setHeaders(new BasicHeader("Last-Modified", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("Cache-Control", "public"),
                new BasicHeader("Content-Encoding", "gzip"));

        final HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, entry,
                Instant.now(), Instant.now(), response);

        final Header[] updatedHeaders = updatedEntry.getHeaders();
        headersContain(updatedHeaders, "Content-Encoding", "identity");
        headersNotContain(updatedHeaders, "Content-Encoding", "gzip");
    }

    @Test
    public void testContentLengthIsNotAddedWhenTransferEncodingIsPresent() throws IOException {
        final Header[] headers = {
                new BasicHeader("Date", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Transfer-Encoding", "chunked")};

        entry = HttpTestUtils.makeCacheEntry(headers);
        response.setHeaders(new BasicHeader("Last-Modified", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("Cache-Control", "public"),
                new BasicHeader("Content-Length", "0"));

        final HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, entry,
                Instant.now(), Instant.now(), response);

        final Header[] updatedHeaders = updatedEntry.getHeaders();
        headersContain(updatedHeaders, "Transfer-Encoding", "chunked");
        headersNotContain(updatedHeaders, "Content-Length", "0");
    }

    private void headersContain(final Header[] headers, final String name, final String value) {
        for (final Header header : headers) {
            if (header.getName().equals(name)) {
                if (header.getValue().equals(value)) {
                    return;
                }
            }
        }
        fail("Header [" + name + ": " + value + "] not found in headers.");
    }

    private void headersNotContain(final Header[] headers, final String name, final String value) {
        for (final Header header : headers) {
            if (header.getName().equals(name)) {
                if (header.getValue().equals(value)) {
                    fail("Header [" + name + ": " + value + "] found in headers where it should not be");
                }
            }
        }
    }
}
