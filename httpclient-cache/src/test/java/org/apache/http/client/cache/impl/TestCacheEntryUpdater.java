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
package org.apache.http.client.cache.impl;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCacheEntryUpdater {


    private static ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);


    private HttpResponse mockResponse;
    private CacheEntry mockCacheEntry;
    private Date requestDate;
    private Date responseDate;

    private boolean implMocked = false;
    private CacheEntryUpdater impl;

    @Before
    public void setUp() throws Exception {
        mockResponse = EasyMock.createMock(HttpResponse.class);
        mockCacheEntry = EasyMock.createMock(CacheEntry.class);

        requestDate = new Date(System.currentTimeMillis() - 1000);
        responseDate = new Date();

        impl = new CacheEntryUpdater();
    }

    private void replayMocks() {
        EasyMock.replay(mockResponse);
        EasyMock.replay(mockCacheEntry);
        if (implMocked) {
            EasyMock.replay(impl);
        }
    }

    private void verifyMocks() {
        EasyMock.verify(mockResponse);
        EasyMock.verify(mockCacheEntry);
        if (implMocked) {
            EasyMock.verify(impl);
        }
    }

    @Test
    public void testUpdateCacheEntryReturnsDifferentEntryInstance() {

        CacheEntry entry = getEntry(new Header[]{});
        BasicHttpResponse response = new BasicHttpResponse(HTTP_1_1, 200, "OK");

        replayMocks();

        CacheEntry newEntry = impl.updateCacheEntry(entry, requestDate, responseDate, response);

        verifyMocks();

        assertNotSame(newEntry, entry);

    }

    @Test
    public void testHeadersAreMergedCorrectly() {

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(responseDate)),
                new BasicHeader("ETag", "\"etag\"")};

        CacheEntry cacheEntry = getEntry(headers);

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[]{});

        CacheEntry updatedEntry = impl.updateCacheEntry(cacheEntry, new Date(), new Date(), response);

        Assert.assertEquals(2, updatedEntry.getAllHeaders().length);

        headersContain(updatedEntry.getAllHeaders(), "Date", DateUtils.formatDate(responseDate));
        headersContain(updatedEntry.getAllHeaders(), "ETag", "\"etag\"");

    }

    @Test
    public void testNewerHeadersReplaceExistingHeaders() {

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(requestDate)),
                new BasicHeader("Cache-Control", "private"), new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Last-Modified", DateUtils.formatDate(requestDate)),
                new BasicHeader("Cache-Control", "max-age=0"),};
        CacheEntry cacheEntry = getEntry(headers);

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[]{
                new BasicHeader("Last-Modified", DateUtils.formatDate(responseDate)),
                new BasicHeader("Cache-Control", "public"),});

        CacheEntry updatedEntry = impl.updateCacheEntry(cacheEntry, new Date(), new Date(), response);


        Assert.assertEquals(4, updatedEntry.getAllHeaders().length);

        headersContain(updatedEntry.getAllHeaders(), "Date", DateUtils.formatDate(requestDate));
        headersContain(updatedEntry.getAllHeaders(), "ETag", "\"etag\"");
        headersContain(updatedEntry.getAllHeaders(), "Last-Modified", DateUtils
                .formatDate(responseDate));
        headersContain(updatedEntry.getAllHeaders(), "Cache-Control", "public");
    }

    @Test
    public void testNewHeadersAreAddedByMerge() {

        Header[] headers = {
                new BasicHeader("Date", DateUtils.formatDate(requestDate)),
                new BasicHeader("ETag", "\"etag\"")};

        CacheEntry cacheEntry = getEntry(headers);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[]{
                new BasicHeader("Last-Modified", DateUtils.formatDate(responseDate)),
                new BasicHeader("Cache-Control", "public"),});

        CacheEntry updatedEntry = impl.updateCacheEntry(cacheEntry, new Date(), new Date(), response);


        Assert.assertEquals(4, updatedEntry.getAllHeaders().length);

        headersContain(updatedEntry.getAllHeaders(), "Date", DateUtils.formatDate(requestDate));
        headersContain(updatedEntry.getAllHeaders(), "ETag", "\"etag\"");
        headersContain(updatedEntry.getAllHeaders(), "Last-Modified", DateUtils
                .formatDate(responseDate));
        headersContain(updatedEntry.getAllHeaders(), "Cache-Control", "public");

    }

    @Test
    public void testUpdatedEntryHasLatestRequestAndResponseDates() {

        Date now = new Date();

        Date tenSecondsAgo = new Date(now.getTime() - 10000L);
        Date eightSecondsAgo = new Date(now.getTime() - 8000L);

        Date twoSecondsAgo = new Date(now.getTime() - 2000L);
        Date oneSecondAgo = new Date(now.getTime() - 1000L);

        Header[] headers = new Header[]{};

        CacheEntry entry = new CacheEntry(tenSecondsAgo, eightSecondsAgo, HTTP_1_1, headers, new byte[]{}, 200, "OK");

        HttpResponse response = new BasicHttpResponse(HTTP_1_1, 200, "OK");

        CacheEntry updated = impl.updateCacheEntry(entry, twoSecondsAgo, oneSecondAgo, response);

        assertEquals(twoSecondsAgo, updated.getRequestDate());
        assertEquals(oneSecondAgo, updated.getResponseDate());

    }


    // UTILITY

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


    private CacheEntry getEntry(Header[] headers) {
        return getEntry(new Date(), new Date(), headers);
    }

    private CacheEntry getEntry(Date requestDate, Date responseDate, Header[] headers) {
        return new CacheEntry(requestDate, responseDate, HTTP_1_1, headers, new byte[]{}, 200, "OK");
    }
}
