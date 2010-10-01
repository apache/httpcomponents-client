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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;

public class TestCacheEntryUpdater {

    private Date requestDate;
    private Date responseDate;

    private CacheEntryUpdater impl;

    @Before
    public void setUp() throws Exception {
        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();

        impl = new CacheEntryUpdater();
    }

    @Test
    public void testUpdateCacheEntryReturnsDifferentEntryInstance() throws IOException {

        HttpCacheEntry entry =HttpTestUtils.makeCacheEntry();
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED, "");

        HttpCacheEntry newEntry = impl.updateCacheEntry(null, entry, requestDate, responseDate, response);

        assertNotSame(newEntry, entry);
    }

    @Test
    public void testHeadersAreMergedCorrectly() throws IOException {

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(responseDate)),
                new BasicHeader("ETag", "\"etag\"")};

        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[]{});

        HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, cacheEntry, new Date(), new Date(), response);

        Assert.assertEquals(2, updatedEntry.getAllHeaders().length);

        headersContain(updatedEntry.getAllHeaders(), "Date", DateUtils.formatDate(responseDate));
        headersContain(updatedEntry.getAllHeaders(), "ETag", "\"etag\"");

    }

    @Test
    public void testNewerHeadersReplaceExistingHeaders() throws IOException {

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(requestDate)),
                new BasicHeader("Cache-Control", "private"), new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Last-Modified", DateUtils.formatDate(requestDate)),
                new BasicHeader("Cache-Control", "max-age=0"),};
        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[]{
                new BasicHeader("Last-Modified", DateUtils.formatDate(responseDate)),
                new BasicHeader("Cache-Control", "public"),});

        HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, cacheEntry, new Date(), new Date(), response);


        Assert.assertEquals(4, updatedEntry.getAllHeaders().length);

        headersContain(updatedEntry.getAllHeaders(), "Date", DateUtils.formatDate(requestDate));
        headersContain(updatedEntry.getAllHeaders(), "ETag", "\"etag\"");
        headersContain(updatedEntry.getAllHeaders(), "Last-Modified", DateUtils
                .formatDate(responseDate));
        headersContain(updatedEntry.getAllHeaders(), "Cache-Control", "public");
    }

    @Test
    public void testNewHeadersAreAddedByMerge() throws IOException {

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(requestDate)),
                new BasicHeader("ETag", "\"etag\"")};

        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[]{
                new BasicHeader("Last-Modified", DateUtils.formatDate(responseDate)),
                new BasicHeader("Cache-Control", "public"),});

        HttpCacheEntry updatedEntry = impl.updateCacheEntry(null, cacheEntry, new Date(), new Date(), response);


        Assert.assertEquals(4, updatedEntry.getAllHeaders().length);

        headersContain(updatedEntry.getAllHeaders(), "Date", DateUtils.formatDate(requestDate));
        headersContain(updatedEntry.getAllHeaders(), "ETag", "\"etag\"");
        headersContain(updatedEntry.getAllHeaders(), "Last-Modified", DateUtils
                .formatDate(responseDate));
        headersContain(updatedEntry.getAllHeaders(), "Cache-Control", "public");

    }

    @Test
    public void testUpdatedEntryHasLatestRequestAndResponseDates() throws IOException {

        Date now = new Date();

        Date tenSecondsAgo = new Date(now.getTime() - 10000L);
        Date eightSecondsAgo = new Date(now.getTime() - 8000L);

        Date twoSecondsAgo = new Date(now.getTime() - 2000L);
        Date oneSecondAgo = new Date(now.getTime() - 1000L);

        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(tenSecondsAgo, eightSecondsAgo);

        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED, "");

        HttpCacheEntry updated = impl.updateCacheEntry(null, entry, twoSecondsAgo, oneSecondAgo, response);

        assertEquals(twoSecondsAgo, updated.getRequestDate());
        assertEquals(oneSecondAgo, updated.getResponseDate());

    }

    private void headersContain(Header[] headers, String name, String value) {
        for (Header header : headers) {
            if (header.getName().equals(name)) {
                if (header.getValue().equals(value)) {
                    return;
                }
            }
        }
        Assert.fail("Header [" + name + ": " + value + "] not found in headers.");
    }

}
