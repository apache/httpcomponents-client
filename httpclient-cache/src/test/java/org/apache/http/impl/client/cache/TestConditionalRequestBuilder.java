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
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestConditionalRequestBuilder {

    private ConditionalRequestBuilder impl;
    private HttpRequestWrapper request;
    private HttpCacheEntry entry;

    @Before
    public void setUp() throws Exception {
        impl = new ConditionalRequestBuilder();
        request = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1));
        entry = HttpTestUtils.makeCacheEntry();
    }

    @Test
    public void testBuildConditionalRequestWithLastModified() throws ProtocolException {
        final String theMethod = "GET";
        final String theUri = "/theuri";
        final String lastModified = "this is my last modified date";

        final HttpRequest basicRequest = new BasicHttpRequest(theMethod, theUri);
        basicRequest.addHeader("Accept-Encoding", "gzip");
        final HttpRequestWrapper requestWrapper = HttpRequestWrapper.wrap(basicRequest);

        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", lastModified) };

        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);
        final HttpRequestWrapper newRequest = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        Assert.assertNotSame(basicRequest, newRequest);

        Assert.assertEquals(theMethod, newRequest.getRequestLine().getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestLine().getUri());
        Assert.assertEquals(basicRequest.getRequestLine().getProtocolVersion(), newRequest
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
        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final Date twentySecondsAgo = new Date(now.getTime() - 20 * 1000L);
        final String lmDate = DateUtils.formatDate(twentySecondsAgo);
        final String etag = "\"etag\"";
        final Header[] headers = {
            new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
            new BasicHeader("Last-Modified", lmDate),
            new BasicHeader("ETag", etag)
        };
        final HttpRequest basicRequest = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final HttpRequestWrapper requestWrapper = HttpRequestWrapper.wrap(basicRequest);
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);
        final HttpRequest result = impl.buildConditionalRequest(requestWrapper, cacheEntry);
        Assert.assertEquals(lmDate,
                result.getFirstHeader("If-Modified-Since").getValue());
        Assert.assertEquals(etag,
                result.getFirstHeader("If-None-Match").getValue());
    }

    @Test
    public void testBuildConditionalRequestWithETag() throws ProtocolException {
        final String theMethod = "GET";
        final String theUri = "/theuri";
        final String theETag = "this is my eTag";

        final HttpRequest basicRequest = new BasicHttpRequest(theMethod, theUri);
        basicRequest.addHeader("Accept-Encoding", "gzip");
        final HttpRequestWrapper requestWrapper = HttpRequestWrapper.wrap(basicRequest);

        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", theETag) };

        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);

        final HttpRequest newRequest = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        Assert.assertNotSame(basicRequest, newRequest);

        Assert.assertEquals(theMethod, newRequest.getRequestLine().getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestLine().getUri());
        Assert.assertEquals(basicRequest.getRequestLine().getProtocolVersion(), newRequest
                .getRequestLine().getProtocolVersion());

        Assert.assertEquals(3, newRequest.getAllHeaders().length);

        Assert.assertEquals("Accept-Encoding", newRequest.getAllHeaders()[0].getName());
        Assert.assertEquals("gzip", newRequest.getAllHeaders()[0].getValue());

        Assert.assertEquals("If-None-Match", newRequest.getAllHeaders()[1].getName());
        Assert.assertEquals(theETag, newRequest.getAllHeaders()[1].getValue());
    }

    @Test
    public void testCacheEntryWithMustRevalidateDoesEndToEndRevalidation() throws Exception {
        final HttpRequest basicRequest = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        final HttpRequestWrapper requestWrapper = HttpRequestWrapper.wrap(basicRequest);
        final Date now = new Date();
        final Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final Date nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        final Header[] cacheEntryHeaders = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Cache-Control","max-age=5, must-revalidate") };
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, cacheEntryHeaders);

        final HttpRequest result = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        boolean foundMaxAge0 = false;
        for(final Header h : result.getHeaders("Cache-Control")) {
            for(final HeaderElement elt : h.getElements()) {
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
        final HttpRequest basicRequest = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        final HttpRequestWrapper requestWrapper = HttpRequestWrapper.wrap(basicRequest);
        final Date now = new Date();
        final Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final Date nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        final Header[] cacheEntryHeaders = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Cache-Control","max-age=5, proxy-revalidate") };
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, cacheEntryHeaders);

        final HttpRequest result = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        boolean foundMaxAge0 = false;
        for(final Header h : result.getHeaders("Cache-Control")) {
            for(final HeaderElement elt : h.getElements()) {
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
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals("GET", result.getRequestLine().getMethod());
    }

    @Test
    public void testBuildUnconditionalRequestUsesRequestUri()
        throws Exception {
        final String uri = "/theURI";
        request = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", uri, HttpVersion.HTTP_1_1));
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals(uri, result.getRequestLine().getUri());
    }

    @Test
    public void testBuildUnconditionalRequestUsesHTTP_1_1()
        throws Exception {
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals(HttpVersion.HTTP_1_1, result.getProtocolVersion());
    }

    @Test
    public void testBuildUnconditionalRequestAddsCacheControlNoCache()
        throws Exception {
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        boolean ccNoCacheFound = false;
        for(final Header h : result.getHeaders("Cache-Control")) {
            for(final HeaderElement elt : h.getElements()) {
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
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        boolean ccNoCacheFound = false;
        for(final Header h : result.getHeaders("Pragma")) {
            for(final HeaderElement elt : h.getElements()) {
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
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Range"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfMatch()
        throws Exception {
        request.addHeader("If-Match","\"etag\"");
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Match"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfNoneMatch()
        throws Exception {
        request.addHeader("If-None-Match","\"etag\"");
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-None-Match"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfUnmodifiedSince()
        throws Exception {
        request.addHeader("If-Unmodified-Since", DateUtils.formatDate(new Date()));
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Unmodified-Since"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfModifiedSince()
        throws Exception {
        request.addHeader("If-Modified-Since", DateUtils.formatDate(new Date()));
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertNull(result.getFirstHeader("If-Modified-Since"));
    }

    @Test
    public void testBuildUnconditionalRequestCarriesOtherRequestHeaders()
        throws Exception {
        request.addHeader("User-Agent","MyBrowser/1.0");
        final HttpRequest result = impl.buildUnconditionalRequest(request, entry);
        Assert.assertEquals("MyBrowser/1.0",
                result.getFirstHeader("User-Agent").getValue());
    }

    @Test
    public void testBuildConditionalRequestFromVariants() throws Exception {
        final String etag1 = "\"123\"";
        final String etag2 = "\"456\"";
        final String etag3 = "\"789\"";

        final Map<String,Variant> variantEntries = new HashMap<String,Variant>();
        variantEntries.put(etag1, new Variant("A","B",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag1) })));
        variantEntries.put(etag2, new Variant("C","D",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag2) })));
        variantEntries.put(etag3, new Variant("E","F",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag3) })));

        final HttpRequest conditional = impl.buildConditionalRequestFromVariants(request, variantEntries);

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
