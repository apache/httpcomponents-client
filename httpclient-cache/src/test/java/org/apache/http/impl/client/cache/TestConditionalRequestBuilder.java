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
import java.util.HashMap;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestConditionalRequestBuilder {

    private ConditionalRequestBuilder impl;
    private HttpRequest request;
    private HttpCacheEntry entry;

    @Before
    public void setUp() throws Exception {
        impl = new ConditionalRequestBuilder();
        request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        entry = HttpTestUtils.makeCacheEntry();
    }

    @Test
    public void testBuildConditionalRequestWithLastModified() throws ProtocolException {
        String theMethod = "GET";
        String theUri = "/theuri";
        String lastModified = "this is my last modified date";

        HttpRequest request = new BasicHttpRequest(theMethod, theUri);
        request.addHeader("Accept-Encoding", "gzip");

        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", lastModified) };

        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);
        HttpRequest newRequest = impl.buildConditionalRequest(request, cacheEntry);

        Assert.assertNotSame(request, newRequest);

        Assert.assertEquals(theMethod, newRequest.getRequestLine().getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestLine().getUri());
        Assert.assertEquals(request.getRequestLine().getProtocolVersion(), newRequest
                .getRequestLine().getProtocolVersion());
        Assert.assertEquals(2, newRequest.getAllHeaders().length);

        Assert.assertEquals("Accept-Encoding", newRequest.getAllHeaders()[0].getName());
        Assert.assertEquals("gzip", newRequest.getAllHeaders()[0].getValue());

        Assert.assertEquals("If-Modified-Since", newRequest.getAllHeaders()[1].getName());
        Assert.assertEquals(lastModified, newRequest.getAllHeaders()[1].getValue());
    }

    @Test
    public void testConditionalRequestForEntryWithLastModifiedAndEtagIncludesBothAsValidators()
            throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Date twentySecondsAgo = new Date(now.getTime() - 20 * 1000L);
        final String lmDate = DateUtils.formatDate(twentySecondsAgo);
        final String etag = "\"etag\"";
        Header[] headers = {
            new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
            new BasicHeader("Last-Modified", lmDate),
            new BasicHeader("ETag", etag)
        };
        HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(headers);
        HttpRequest result = impl.buildConditionalRequest(request, entry);
        Assert.assertEquals(lmDate,
                result.getFirstHeader("If-Modified-Since").getValue());
        Assert.assertEquals(etag,
                result.getFirstHeader("If-None-Match").getValue());
    }

    @Test
    public void testBuildConditionalRequestWithETag() throws ProtocolException {
        String theMethod = "GET";
        String theUri = "/theuri";
        String theETag = "this is my eTag";

        HttpRequest request = new BasicHttpRequest(theMethod, theUri);
        request.addHeader("Accept-Encoding", "gzip");

        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", theETag) };

        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);

        HttpRequest newRequest = impl.buildConditionalRequest(request, cacheEntry);

        Assert.assertNotSame(request, newRequest);

        Assert.assertEquals(theMethod, newRequest.getRequestLine().getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestLine().getUri());
        Assert.assertEquals(request.getRequestLine().getProtocolVersion(), newRequest
                .getRequestLine().getProtocolVersion());

        Assert.assertEquals(3, newRequest.getAllHeaders().length);

        Assert.assertEquals("Accept-Encoding", newRequest.getAllHeaders()[0].getName());
        Assert.assertEquals("gzip", newRequest.getAllHeaders()[0].getValue());

        Assert.assertEquals("If-None-Match", newRequest.getAllHeaders()[1].getName());
        Assert.assertEquals(theETag, newRequest.getAllHeaders()[1].getValue());
    }

    @Test
    public void testCacheEntryWithMustRevalidateDoesEndToEndRevalidation() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        Date now = new Date();
        Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Date nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        Header[] cacheEntryHeaders = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Cache-Control","max-age=5, must-revalidate") };
        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, cacheEntryHeaders);

        HttpRequest result = impl.buildConditionalRequest(request, cacheEntry);

        boolean foundMaxAge0 = false;
        for(Header h : result.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("max-age".equalsIgnoreCase(elt.getName())
                    && "0".equals(elt.getValue())) {
                    foundMaxAge0 = true;
                }
            }
        }
        Assert.assertTrue(foundMaxAge0);
    }

    @Test
    public void testCacheEntryWithProxyRevalidateDoesEndToEndRevalidation() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        Date now = new Date();
        Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        Date nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        Header[] cacheEntryHeaders = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Cache-Control","max-age=5, proxy-revalidate") };
        HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, cacheEntryHeaders);

        HttpRequest result = impl.buildConditionalRequest(request, cacheEntry);

        boolean foundMaxAge0 = false;
        for(Header h : result.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("max-age".equalsIgnoreCase(elt.getName())
                    && "0".equals(elt.getValue())) {
                    foundMaxAge0 = true;
                }
            }
        }
        Assert.assertTrue(foundMaxAge0);
    }

    @Test
    public void testBuildUnconditionalRequestUsesGETMethod()
        throws Exception {
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals("GET", result.getRequestLine().getMethod());
    }

    @Test
    public void testBuildUnconditionalRequestUsesRequestUri()
        throws Exception {
        final String uri = "/theURI";
        request = new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1);
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals(uri, result.getRequestLine().getUri());
    }

    @Test
    public void testBuildUnconditionalRequestUsesHTTP_1_1()
        throws Exception {
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals(HttpVersion.HTTP_1_1, result.getProtocolVersion());
    }

    @Test
    public void testBuildUnconditionalRequestAddsCacheControlNoCache()
        throws Exception {
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        boolean ccNoCacheFound = false;
        for(Header h : result.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("no-cache".equals(elt.getName())) {
                    ccNoCacheFound = true;
                }
            }
        }
        Assert.assertTrue(ccNoCacheFound);
    }

    @Test
    public void testBuildUnconditionalRequestAddsPragmaNoCache()
        throws Exception {
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        boolean ccNoCacheFound = false;
        for(Header h : result.getHeaders("Pragma")) {
            for(HeaderElement elt : h.getElements()) {
                if ("no-cache".equals(elt.getName())) {
                    ccNoCacheFound = true;
                }
            }
        }
        Assert.assertTrue(ccNoCacheFound);
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfRange()
        throws Exception {
        request.addHeader("If-Range","\"etag\"");
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Range"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfMatch()
        throws Exception {
        request.addHeader("If-Match","\"etag\"");
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Match"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfNoneMatch()
        throws Exception {
        request.addHeader("If-None-Match","\"etag\"");
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-None-Match"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfUnmodifiedSince()
        throws Exception {
        request.addHeader("If-Unmodified-Since", DateUtils.formatDate(new Date()));
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Unmodified-Since"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfModifiedSince()
        throws Exception {
        request.addHeader("If-Modified-Since", DateUtils.formatDate(new Date()));
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Modified-Since"));
    }

    @Test
    public void testBuildUnconditionalRequestCarriesOtherRequestHeaders()
        throws Exception {
        request.addHeader("User-Agent","MyBrowser/1.0");
        HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals("MyBrowser/1.0",
                result.getFirstHeader("User-Agent").getValue());
    }

    @Test
    public void testBuildConditionalRequestFromVariants() throws Exception {
        String etag1 = "\"123\"";
        String etag2 = "\"456\"";
        String etag3 = "\"789\"";

        Map<String,Variant> variantEntries = new HashMap<String,Variant>();
        variantEntries.put(etag1, new Variant("A","B",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag1) })));
        variantEntries.put(etag2, new Variant("C","D",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag2) })));
        variantEntries.put(etag3, new Variant("E","F",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag3) })));

        HttpRequest conditional = impl.buildConditionalRequestFromVariants(request, variantEntries);

        // seems like a lot of work, but necessary, check for existence and exclusiveness
        String ifNoneMatch = conditional.getFirstHeader(HeaderConstants.IF_NONE_MATCH).getValue();
        Assert.assertTrue(ifNoneMatch.contains(etag1));
        Assert.assertTrue(ifNoneMatch.contains(etag2));
        Assert.assertTrue(ifNoneMatch.contains(etag3));
        ifNoneMatch = ifNoneMatch.replace(etag1, "");
        ifNoneMatch = ifNoneMatch.replace(etag2, "");
        ifNoneMatch = ifNoneMatch.replace(etag3, "");
        ifNoneMatch = ifNoneMatch.replace(",","");
        ifNoneMatch = ifNoneMatch.replace(" ", "");
        Assert.assertEquals(ifNoneMatch, "");
    }

}
