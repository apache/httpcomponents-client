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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.MessageSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestConditionalRequestBuilder {

    private ConditionalRequestBuilder impl;
    private HttpHost host;
    private HttpRoute route;
    private RoutedHttpRequest request;

    @Before
    public void setUp() throws Exception {
        impl = new ConditionalRequestBuilder();
        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = RoutedHttpRequest.adapt(new BasicClassicHttpRequest("GET", "/"), route);
    }

    @Test
    public void testBuildConditionalRequestWithLastModified() throws ProtocolException {
        final String theMethod = "GET";
        final String theUri = "/theuri";
        final String lastModified = "this is my last modified date";

        final ClassicHttpRequest basicRequest = new BasicClassicHttpRequest(theMethod, theUri);
        basicRequest.addHeader("Accept-Encoding", "gzip");
        final RoutedHttpRequest requestWrapper = RoutedHttpRequest.adapt(basicRequest, route);

        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", lastModified) };

        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);
        final RoutedHttpRequest newRequest = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        Assert.assertNotSame(basicRequest, newRequest);

        Assert.assertEquals(theMethod, newRequest.getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestUri());
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
        final ClassicHttpRequest basicRequest = new BasicClassicHttpRequest("GET", "/");
        final RoutedHttpRequest requestWrapper = RoutedHttpRequest.adapt(basicRequest, route);
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);
        final ClassicHttpRequest result = impl.buildConditionalRequest(requestWrapper, cacheEntry);
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

        final ClassicHttpRequest basicRequest = new BasicClassicHttpRequest(theMethod, theUri);
        basicRequest.addHeader("Accept-Encoding", "gzip");
        final RoutedHttpRequest requestWrapper = RoutedHttpRequest.adapt(basicRequest, route);

        final Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", theETag) };

        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(headers);

        final ClassicHttpRequest newRequest = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        Assert.assertNotSame(basicRequest, newRequest);

        Assert.assertEquals(theMethod, newRequest.getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestUri());

        Assert.assertEquals(3, newRequest.getAllHeaders().length);

        Assert.assertEquals("Accept-Encoding", newRequest.getAllHeaders()[0].getName());
        Assert.assertEquals("gzip", newRequest.getAllHeaders()[0].getValue());

        Assert.assertEquals("If-None-Match", newRequest.getAllHeaders()[1].getName());
        Assert.assertEquals(theETag, newRequest.getAllHeaders()[1].getValue());
    }

    @Test
    public void testCacheEntryWithMustRevalidateDoesEndToEndRevalidation() throws Exception {
        final ClassicHttpRequest basicRequest = new BasicClassicHttpRequest("GET","/");
        final RoutedHttpRequest requestWrapper = RoutedHttpRequest.adapt(basicRequest, route);
        final Date now = new Date();
        final Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final Date nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        final Header[] cacheEntryHeaders = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Cache-Control","max-age=5, must-revalidate") };
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, cacheEntryHeaders);

        final ClassicHttpRequest result = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        boolean foundMaxAge0 = false;

        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("max-age".equalsIgnoreCase(elt.getName()) && "0".equals(elt.getValue())) {
                foundMaxAge0 = true;
            }
        }
        Assert.assertTrue(foundMaxAge0);
    }

    @Test
    public void testCacheEntryWithProxyRevalidateDoesEndToEndRevalidation() throws Exception {
        final ClassicHttpRequest basicRequest = new BasicClassicHttpRequest("GET", "/");
        final RoutedHttpRequest requestWrapper = RoutedHttpRequest.adapt(basicRequest, route);
        final Date now = new Date();
        final Date elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        final Date nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);

        final Header[] cacheEntryHeaders = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(tenSecondsAgo)),
                new BasicHeader("ETag", "\"etag\""),
                new BasicHeader("Cache-Control","max-age=5, proxy-revalidate") };
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(elevenSecondsAgo, nineSecondsAgo, cacheEntryHeaders);

        final ClassicHttpRequest result = impl.buildConditionalRequest(requestWrapper, cacheEntry);

        boolean foundMaxAge0 = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("max-age".equalsIgnoreCase(elt.getName()) && "0".equals(elt.getValue())) {
                foundMaxAge0 = true;
            }
        }
        Assert.assertTrue(foundMaxAge0);
    }

    @Test
    public void testBuildUnconditionalRequestUsesGETMethod()
        throws Exception {
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertEquals("GET", result.getMethod());
    }

    @Test
    public void testBuildUnconditionalRequestUsesRequestUri()
        throws Exception {
        final String uri = "/theURI";
        request = RoutedHttpRequest.adapt(new BasicClassicHttpRequest("GET", uri), route);
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertEquals(uri, result.getRequestUri());
    }

    @Test
    public void testBuildUnconditionalRequestUsesHTTP_1_1()
        throws Exception {
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertEquals(HttpVersion.HTTP_1_1, result.getVersion());
    }

    @Test
    public void testBuildUnconditionalRequestAddsCacheControlNoCache()
        throws Exception {
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        boolean ccNoCacheFound = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("no-cache".equals(elt.getName())) {
                ccNoCacheFound = true;
            }
        }
        Assert.assertTrue(ccNoCacheFound);
    }

    @Test
    public void testBuildUnconditionalRequestAddsPragmaNoCache()
        throws Exception {
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        boolean ccNoCacheFound = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(result, HeaderConstants.PRAGMA);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("no-cache".equals(elt.getName())) {
                ccNoCacheFound = true;
            }
        }
        Assert.assertTrue(ccNoCacheFound);
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfRange()
        throws Exception {
        request.addHeader("If-Range","\"etag\"");
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertNull(result.getFirstHeader("If-Range"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfMatch()
        throws Exception {
        request.addHeader("If-Match","\"etag\"");
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertNull(result.getFirstHeader("If-Match"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfNoneMatch()
        throws Exception {
        request.addHeader("If-None-Match","\"etag\"");
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertNull(result.getFirstHeader("If-None-Match"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfUnmodifiedSince()
        throws Exception {
        request.addHeader("If-Unmodified-Since", DateUtils.formatDate(new Date()));
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertNull(result.getFirstHeader("If-Unmodified-Since"));
    }

    @Test
    public void testBuildUnconditionalRequestDoesNotUseIfModifiedSince()
        throws Exception {
        request.addHeader("If-Modified-Since", DateUtils.formatDate(new Date()));
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertNull(result.getFirstHeader("If-Modified-Since"));
    }

    @Test
    public void testBuildUnconditionalRequestCarriesOtherRequestHeaders()
        throws Exception {
        request.addHeader("User-Agent","MyBrowser/1.0");
        final ClassicHttpRequest result = impl.buildUnconditionalRequest(request);
        Assert.assertEquals("MyBrowser/1.0",
                result.getFirstHeader("User-Agent").getValue());
    }

    @Test
    public void testBuildConditionalRequestFromVariants() throws Exception {
        final String etag1 = "\"123\"";
        final String etag2 = "\"456\"";
        final String etag3 = "\"789\"";

        final Map<String,Variant> variantEntries = new HashMap<>();
        variantEntries.put(etag1, new Variant("A","B",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag1) })));
        variantEntries.put(etag2, new Variant("C","D",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag2) })));
        variantEntries.put(etag3, new Variant("E","F",HttpTestUtils.makeCacheEntry(new Header[] { new BasicHeader("ETag", etag3) })));

        final ClassicHttpRequest conditional = impl.buildConditionalRequestFromVariants(request, variantEntries);

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
