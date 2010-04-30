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

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.impl.CacheEntry;
import org.apache.http.client.cache.impl.CacheEntryUpdater;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCacheEntryUpdater {

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
    public void testUpdateCacheEntry() {
        mockImplMethods("mergeHeaders");
        mockCacheEntry.setRequestDate(requestDate);
        mockCacheEntry.setResponseDate(responseDate);
        impl.mergeHeaders(mockCacheEntry, mockResponse);

        replayMocks();

        impl.updateCacheEntry(mockCacheEntry, requestDate, responseDate, mockResponse);

        verifyMocks();
    }

    @Test
    public void testExistingHeadersNotInResponseDontChange() {

        CacheEntry cacheEntry = new CacheEntry();
        cacheEntry.setResponseHeaders(new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(responseDate)),
                new BasicHeader("ETag", "eTag") });

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[] {});

        impl.mergeHeaders(cacheEntry, response);

        Assert.assertEquals(2, cacheEntry.getAllHeaders().length);

        headersContain(cacheEntry.getAllHeaders(), "Date", DateUtils.formatDate(responseDate));
        headersContain(cacheEntry.getAllHeaders(), "ETag", "eTag");

    }

    @Test
    public void testNewerHeadersReplaceExistingHeaders() {
        CacheEntry cacheEntry = new CacheEntry();
        cacheEntry.setResponseHeaders(new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(requestDate)),
                new BasicHeader("Cache-Control", "private"), new BasicHeader("ETag", "eTag"),
                new BasicHeader("Last-Modified", DateUtils.formatDate(requestDate)),
                new BasicHeader("Cache-Control", "max-age=0"), });

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[] {
                new BasicHeader("Last-Modified", DateUtils.formatDate(responseDate)),
                new BasicHeader("Cache-Control", "public"), });

        impl.mergeHeaders(cacheEntry, response);

        Assert.assertEquals(4, cacheEntry.getAllHeaders().length);

        headersContain(cacheEntry.getAllHeaders(), "Date", DateUtils.formatDate(requestDate));
        headersContain(cacheEntry.getAllHeaders(), "ETag", "eTag");
        headersContain(cacheEntry.getAllHeaders(), "Last-Modified", DateUtils
                .formatDate(responseDate));
        headersContain(cacheEntry.getAllHeaders(), "Cache-Control", "public");
    }

    @Test
    public void testNewHeadersAreAddedByMerge() {

        CacheEntry cacheEntry = new CacheEntry();
        cacheEntry.setResponseHeaders(new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(requestDate)),
                new BasicHeader("ETag", "eTag"), });

        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion(
                "http", 1, 1), HttpStatus.SC_NOT_MODIFIED, ""));
        response.setHeaders(new Header[] {
                new BasicHeader("Last-Modified", DateUtils.formatDate(responseDate)),
                new BasicHeader("Cache-Control", "public"), });

        impl.mergeHeaders(cacheEntry, response);

        Assert.assertEquals(4, cacheEntry.getAllHeaders().length);

        headersContain(cacheEntry.getAllHeaders(), "Date", DateUtils.formatDate(requestDate));
        headersContain(cacheEntry.getAllHeaders(), "ETag", "eTag");
        headersContain(cacheEntry.getAllHeaders(), "Last-Modified", DateUtils
                .formatDate(responseDate));
        headersContain(cacheEntry.getAllHeaders(), "Cache-Control", "public");

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

    private void mockImplMethods(String... methods) {
        implMocked = true;
        impl = EasyMock.createMockBuilder(CacheEntryUpdater.class).addMockedMethods(methods)
                .createMock();
    }

}
