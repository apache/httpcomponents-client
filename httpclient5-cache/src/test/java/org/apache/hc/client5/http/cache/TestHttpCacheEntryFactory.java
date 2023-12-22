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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.apache.hc.client5.http.HeadersMatcher;
import org.apache.hc.client5.http.impl.cache.ContainsHeaderMatcher;
import org.apache.hc.client5.http.impl.cache.HttpCacheEntryMatcher;
import org.apache.hc.client5.http.impl.cache.HttpTestUtils;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestHttpCacheEntryFactory {

    private Instant requestDate;
    private Instant responseDate;

    private HttpCacheEntry entry;
    private Instant now;
    private Instant oneSecondAgo;
    private Instant twoSecondsAgo;
    private Instant eightSecondsAgo;
    private Instant tenSecondsAgo;
    private HttpHost host;
    private HttpRequest request;
    private HttpResponse response;
    private HttpCacheEntryFactory impl;

    @BeforeEach
    public void setUp() throws Exception {
        requestDate = Instant.now().minusSeconds(1);
        responseDate = Instant.now();

        now = Instant.now();
        oneSecondAgo = now.minusSeconds(1);
        twoSecondsAgo = now.minusSeconds(2);
        eightSecondsAgo = now.minusSeconds(8);
        tenSecondsAgo = now.minusSeconds(10);

        host = new HttpHost("foo.example.com");
        request = new BasicHttpRequest("GET", "/stuff");
        response = new BasicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");

        impl = new HttpCacheEntryFactory();
    }

    @Test
    public void testFilterHopByHopAndConnectionSpecificHeaders() {
        response.setHeaders(
                new BasicHeader(HttpHeaders.CONNECTION, "blah, blah, this, that"),
                new BasicHeader("Blah", "huh?"),
                new BasicHeader("BLAH", "huh?"),
                new BasicHeader("this", "huh?"),
                new BasicHeader("That", "huh?"),
                new BasicHeader("Keep-Alive", "timeout, max=20"),
                new BasicHeader("X-custom", "my stuff"),
                new BasicHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString()),
                new BasicHeader(HttpHeaders.CONTENT_LENGTH, "111"));
        final HeaderGroup filteredHeaders = HttpCacheEntryFactory.filterHopByHopHeaders(response);
        MatcherAssert.assertThat(filteredHeaders.getHeaders(), HeadersMatcher.same(
                new BasicHeader("X-custom", "my stuff"),
                new BasicHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
        ));
    }

    @Test
    public void testHeadersAreMergedCorrectly() {
        entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("ETag", "\"etag\""));

        final HeaderGroup mergedHeaders = impl.mergeHeaders(entry, response);

        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(responseDate)));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("ETag", "\"etag\""));
    }

    @Test
    public void testNewerHeadersReplaceExistingHeaders() {
        entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("Cache-Control", "private"),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("Cache-Control", "max-age=0"));

        response.setHeaders(
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("Cache-Control", "public"));

        final HeaderGroup mergedHeaders = impl.mergeHeaders(entry, response);

        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(requestDate)));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("ETag", "\"etag\""));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Last-Modified", DateUtils.formatStandardDate(responseDate)));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Cache-Control", "public"));
    }

    @Test
    public void testNewHeadersAreAddedByMerge() {
        entry = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Date", DateUtils.formatStandardDate(requestDate)),
                new BasicHeader("ETag", "\"etag\""));
        response.setHeaders(
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(responseDate)),
                new BasicHeader("Cache-Control", "public"));

        final HeaderGroup mergedHeaders = impl.mergeHeaders(entry, response);

        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Date", DateUtils.formatStandardDate(requestDate)));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("ETag", "\"etag\""));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Last-Modified", DateUtils.formatStandardDate(responseDate)));
        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("Cache-Control", "public"));
    }

    @Test
    public void entryWithMalformedDateIsStillUpdated() throws Exception {
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo,
                new BasicHeader("ETag", "\"old\""),
                new BasicHeader("Date", "bad-date"));
        response.setHeader("ETag", "\"new\"");
        response.setHeader("Date", DateUtils.formatStandardDate(twoSecondsAgo));

        final HeaderGroup mergedHeaders = impl.mergeHeaders(entry, response);

        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("ETag", "\"new\""));
    }

    @Test
    public void entryIsStillUpdatedByResponseWithMalformedDate() throws Exception {
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo,
                new BasicHeader("ETag", "\"old\""),
                new BasicHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo)));
        response.setHeader("ETag", "\"new\"");
        response.setHeader("Date", "bad-date");

        final HeaderGroup mergedHeaders = impl.mergeHeaders(entry, response);

        MatcherAssert.assertThat(mergedHeaders, ContainsHeaderMatcher.contains("ETag", "\"new\""));
    }

    @Test
    public void testUpdateCacheEntryReturnsDifferentEntryInstance() {
        entry = HttpTestUtils.makeCacheEntry();
        final HttpCacheEntry newEntry = impl.createUpdated(requestDate, responseDate, host, request, response, entry);
        Assertions.assertNotSame(newEntry, entry);
    }

    @Test
    public void testCreateRootVariantEntry() {
        request.setHeaders(
                new BasicHeader("Keep-Alive", "timeout, max=20"),
                new BasicHeader("X-custom", "my stuff"),
                new BasicHeader(HttpHeaders.ACCEPT, "stuff"),
                new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de")
        );
        response.setHeaders(
                new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "identity"),
                new BasicHeader(HttpHeaders.CONNECTION, "Keep-Alive, Blah"),
                new BasicHeader("Blah", "huh?"),
                new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(twoSecondsAgo)),
                new BasicHeader(HttpHeaders.VARY, "Stuff"),
                new BasicHeader(HttpHeaders.ETAG, "\"some-etag\""),
                new BasicHeader("X-custom", "my stuff")
        );

        final HttpCacheEntry newEntry = impl.create(tenSecondsAgo, oneSecondAgo, host, request, response, HttpTestUtils.makeRandomResource(1024));

        final Set<String> variants = new HashSet<>();
        variants.add("variant1");
        variants.add("variant2");
        variants.add("variant3");

        final HttpCacheEntry newRoot = impl.createRoot(newEntry, variants);

        MatcherAssert.assertThat(newRoot, HttpCacheEntryMatcher.equivalent(
                HttpTestUtils.makeCacheEntry(
                        tenSecondsAgo,
                        oneSecondAgo,
                        Method.GET,
                        "http://foo.example.com:80/stuff",
                        new Header[]{
                                new BasicHeader("X-custom", "my stuff"),
                                new BasicHeader(HttpHeaders.ACCEPT, "stuff"),
                                new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de")
                        },
                        HttpStatus.SC_NOT_MODIFIED,
                        new Header[]{
                                new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(twoSecondsAgo)),
                                new BasicHeader(HttpHeaders.VARY, "Stuff"),
                                new BasicHeader(HttpHeaders.ETAG, "\"some-etag\""),
                                new BasicHeader("X-custom", "my stuff")
                        },
                        variants
                        )));

        Assertions.assertTrue(newRoot.hasVariants());
        Assertions.assertNull(newRoot.getResource());
    }

    @Test
    public void testCreateResourceEntry() {
        request.setHeaders(
                new BasicHeader("Keep-Alive", "timeout, max=20"),
                new BasicHeader("X-custom", "my stuff"),
                new BasicHeader(HttpHeaders.ACCEPT, "stuff"),
                new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de"),
                new BasicHeader(HttpHeaders.AUTHORIZATION, "Super secret")
        );
        response.setHeaders(
                new BasicHeader(HttpHeaders.TRANSFER_ENCODING, "identity"),
                new BasicHeader(HttpHeaders.CONNECTION, "Keep-Alive, Blah"),
                new BasicHeader("Blah", "huh?"),
                new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(twoSecondsAgo)),
                new BasicHeader(HttpHeaders.ETAG, "\"some-etag\""),
                new BasicHeader("X-custom", "my stuff")
        );

        final Resource resource = HttpTestUtils.makeRandomResource(128);
        final HttpCacheEntry newEntry = impl.create(tenSecondsAgo, oneSecondAgo, host, request, response, resource);

        MatcherAssert.assertThat(newEntry, HttpCacheEntryMatcher.equivalent(
                HttpTestUtils.makeCacheEntry(
                        tenSecondsAgo,
                        oneSecondAgo,
                        Method.GET,
                        "http://foo.example.com:80/stuff",
                        new Header[]{
                                new BasicHeader("X-custom", "my stuff"),
                                new BasicHeader(HttpHeaders.ACCEPT, "stuff"),
                                new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de")
                        },
                        HttpStatus.SC_NOT_MODIFIED,
                        new Header[]{
                                new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(twoSecondsAgo)),
                                new BasicHeader(HttpHeaders.ETAG, "\"some-etag\""),
                                new BasicHeader("X-custom", "my stuff")
                        },
                        resource
                )));

        Assertions.assertFalse(newEntry.hasVariants());
    }

    @Test
    public void testCreateUpdatedResourceEntry() {
        final Resource resource = HttpTestUtils.makeRandomResource(128);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(
                tenSecondsAgo,
                twoSecondsAgo,
                Method.GET,
                "/stuff",
                new Header[]{
                        new BasicHeader("X-custom", "my stuff"),
                        new BasicHeader(HttpHeaders.ACCEPT, "stuff"),
                        new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de")
                },
                HttpStatus.SC_NOT_MODIFIED,
                new Header[]{
                        new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(twoSecondsAgo)),
                        new BasicHeader(HttpHeaders.ETAG, "\"some-etag\""),
                        new BasicHeader("X-custom", "my stuff"),
                        new BasicHeader("Cache-Control", "max-age=0")
                },
                resource
        );

        response.setHeaders(
                new BasicHeader(HttpHeaders.ETAG, "\"some-new-etag\""),
                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("Cache-Control", "public")
        );

        request.setHeaders(
                new BasicHeader("X-custom", "my other stuff"),
                new BasicHeader(HttpHeaders.ACCEPT, "stuff, other-stuff"),
                new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de")
        );

        final HttpCacheEntry updatedEntry = impl.createUpdated(tenSecondsAgo, oneSecondAgo, host, request, response, entry);

        MatcherAssert.assertThat(updatedEntry, HttpCacheEntryMatcher.equivalent(
                HttpTestUtils.makeCacheEntry(
                        tenSecondsAgo,
                        oneSecondAgo,
                        Method.GET,
                        "http://foo.example.com:80/stuff",
                        new Header[]{
                                new BasicHeader("X-custom", "my other stuff"),
                                new BasicHeader(HttpHeaders.ACCEPT, "stuff, other-stuff"),
                                new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en, de")
                        },
                        HttpStatus.SC_NOT_MODIFIED,
                        new Header[]{
                                new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(twoSecondsAgo)),
                                new BasicHeader("X-custom", "my stuff"),
                                new BasicHeader(HttpHeaders.ETAG, "\"some-new-etag\""),
                                new BasicHeader("Last-Modified", DateUtils.formatStandardDate(oneSecondAgo)),
                                new BasicHeader("Cache-Control", "public")
                        },
                        resource
                )));

        Assertions.assertFalse(updatedEntry.hasVariants());
    }

    @Test
    public void testUpdateNotModifiedIfResponseOlder() {
        entry = HttpTestUtils.makeCacheEntry(twoSecondsAgo, now,
                new BasicHeader("Date", DateUtils.formatStandardDate(oneSecondAgo)),
                new BasicHeader("ETag", "\"new-etag\""));
        response.setHeader("Date", DateUtils.formatStandardDate(tenSecondsAgo));
        response.setHeader("ETag", "\"old-etag\"");

        final HttpCacheEntry newEntry = impl.createUpdated(Instant.now(), Instant.now(), host, request, response, entry);

        Assertions.assertSame(newEntry, entry);
    }

    @Test
    public void testUpdateHasLatestRequestAndResponseDates() {
        entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo);
        final HttpCacheEntry updated = impl.createUpdated(twoSecondsAgo, oneSecondAgo, host, request, response, entry);

        Assertions.assertEquals(twoSecondsAgo, updated.getRequestInstant());
        Assertions.assertEquals(oneSecondAgo, updated.getResponseInstant());
    }

    @Test
    public void cannotUpdateFromANon304OriginResponse() throws Exception {
        entry = HttpTestUtils.makeCacheEntry();
        response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                impl.createUpdated(Instant.now(), Instant.now(), host, request, response, entry));
    }

}
