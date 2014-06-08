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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestBasicHttpCache {

    private BasicHttpCache impl;
    private SimpleHttpCacheStorage backing;

    @Before
    public void setUp() throws Exception {
        backing = new SimpleHttpCacheStorage();
        impl = new BasicHttpCache(new HeapResourceFactory(), backing, CacheConfig.DEFAULT);
    }

    @Test
    public void testDoNotFlushCacheEntriesOnGet() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testDoNotFlushCacheEntriesOnHead() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpHead("/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testDoNotFlushCacheEntriesOnOptions() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpOptions("/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testDoNotFlushCacheEntriesOnTrace() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpTrace("/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testFlushContentLocationEntryIfUnSafeRequest()
            throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpPost("/foo");
        final HttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Content-Location", "/bar");
        resp.setHeader(HeaderConstants.ETAG, "\"etag\"");
        final String key = (new CacheKeyGenerator()).getURI(host, new HttpGet("/bar"));

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(new Date())),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        backing.map.put(key, entry);

        impl.flushInvalidatedCacheEntriesFor(host, req, resp);

        assertNull(backing.map.get(key));
    }

    @Test
    public void testDoNotFlushContentLocationEntryIfSafeRequest()
            throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("/foo");
        final HttpResponse resp = HttpTestUtils.make200Response();
        resp.setHeader("Content-Location", "/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, new HttpGet("/bar"));

        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry(new Header[] {
           new BasicHeader("Date", DateUtils.formatDate(new Date())),
           new BasicHeader("ETag", "\"old-etag\"")
        });

        backing.map.put(key, entry);

        impl.flushInvalidatedCacheEntriesFor(host, req, resp);

        assertEquals(entry, backing.map.get(key));
    }

    @Test
    public void testCanFlushCacheEntriesAtUri() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpDelete("/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, req);
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();

        backing.map.put(key, entry);

        impl.flushCacheEntriesFor(host, req);

        assertNull(backing.map.get(key));
    }

    @Test
    public void testRecognizesComplete200Response()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","128");
        final Resource resource = new HeapResource(bytes);

        assertFalse(impl.isIncompleteResponse(resp, resource));
    }

    @Test
    public void testRecognizesComplete206Response()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","128");
        resp.setHeader("Content-Range","bytes 0-127/255");

        assertFalse(impl.isIncompleteResponse(resp, resource));
    }

    @Test
    public void testRecognizesIncomplete200Response()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        assertTrue(impl.isIncompleteResponse(resp, resource));
    }

    @Test
    public void testIgnoresIncompleteNon200Or206Responses()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_FORBIDDEN, "Forbidden");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        assertFalse(impl.isIncompleteResponse(resp, resource));
    }

    @Test
    public void testResponsesWithoutExplicitContentLengthAreComplete()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setEntity(new ByteArrayEntity(bytes));

        assertFalse(impl.isIncompleteResponse(resp, resource));
    }

    @Test
    public void testResponsesWithUnparseableContentLengthHeaderAreComplete()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setHeader("Content-Length","foo");
        resp.setEntity(new ByteArrayEntity(bytes));

        assertFalse(impl.isIncompleteResponse(resp, resource));
    }

    @Test
    public void testNullResourcesAreComplete()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        resp.setHeader("Content-Length","256");

        assertFalse(impl.isIncompleteResponse(resp, null));
    }

    @Test
    public void testIncompleteResponseErrorProvidesPlainTextErrorMessage()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        final HttpResponse result = impl.generateIncompleteResponseError(resp, resource);
        final Header ctype = result.getFirstHeader("Content-Type");
        assertEquals("text/plain;charset=UTF-8", ctype.getValue());
    }

    @Test
    public void testIncompleteResponseErrorProvidesNonEmptyErrorMessage()
        throws Exception {
        final HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final byte[] bytes = HttpTestUtils.getRandomBytes(128);
        final Resource resource = new HeapResource(bytes);
        resp.setEntity(new ByteArrayEntity(bytes));
        resp.setHeader("Content-Length","256");

        final HttpResponse result = impl.generateIncompleteResponseError(resp, resource);
        final int clen = Integer.parseInt(result.getFirstHeader("Content-Length").getValue());
        assertTrue(clen > 0);
        final HttpEntity body = result.getEntity();
        if (body.getContentLength() < 0) {
            final InputStream is = body.getContent();
            int bytes_read = 0;
            while((is.read()) != -1) {
                bytes_read++;
            }
            is.close();
            assertEquals(clen, bytes_read);
        } else {
            assertTrue(body.getContentLength() == clen);
        }
    }

    @Test
    public void testCacheUpdateAddsVariantURIToParentEntry() throws Exception {
        final String parentCacheKey = "parentCacheKey";
        final String variantCacheKey = "variantCacheKey";
        final String existingVariantKey = "existingVariantKey";
        final String newVariantCacheKey = "newVariantCacheKey";
        final String newVariantKey = "newVariantKey";
        final Map<String,String> existingVariants = new HashMap<String,String>();
        existingVariants.put(existingVariantKey, variantCacheKey);
        final HttpCacheEntry parent = HttpTestUtils.makeCacheEntry(existingVariants);
        final HttpCacheEntry variant = HttpTestUtils.makeCacheEntry();

        final HttpCacheEntry result = impl.doGetUpdatedParentEntry(parentCacheKey, parent, variant, newVariantKey, newVariantCacheKey);
        final Map<String,String> resultMap = result.getVariantMap();
        assertEquals(2, resultMap.size());
        assertEquals(variantCacheKey, resultMap.get(existingVariantKey));
        assertEquals(newVariantCacheKey, resultMap.get(newVariantKey));
    }

    @Test
    public void testStoreInCachePutsNonVariantEntryInPlace() throws Exception {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertFalse(entry.hasVariants());
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req = new HttpGet("http://foo.example.com/bar");
        final String key = (new CacheKeyGenerator()).getURI(host, req);

        impl.storeInCache(host, req, entry);
        assertSame(entry, backing.map.get(key));
    }

    @Test
    public void testTooLargeResponsesAreNotCached() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Date now = new Date();
        final Date requestSent = new Date(now.getTime() - 3 * 1000L);
        final Date responseGenerated = new Date(now.getTime() - 2 * 1000L);
        final Date responseReceived = new Date(now.getTime() - 1 * 1000L);

        final HttpResponse originResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        originResponse.setEntity(HttpTestUtils.makeBody(CacheConfig.DEFAULT_MAX_OBJECT_SIZE_BYTES + 1));
        originResponse.setHeader("Cache-Control","public, max-age=3600");
        originResponse.setHeader("Date", DateUtils.formatDate(responseGenerated));
        originResponse.setHeader("ETag", "\"etag\"");

        final HttpResponse result = impl.cacheAndReturnResponse(host, request, originResponse, requestSent, responseReceived);
        assertEquals(0, backing.map.size());
        assertTrue(HttpTestUtils.semanticallyTransparent(originResponse, result));
    }


    @Test
    public void testSmallEnoughResponsesAreCached() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Date now = new Date();
        final Date requestSent = new Date(now.getTime() - 3 * 1000L);
        final Date responseGenerated = new Date(now.getTime() - 2 * 1000L);
        final Date responseReceived = new Date(now.getTime() - 1 * 1000L);

        final HttpResponse originResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        originResponse.setEntity(HttpTestUtils.makeBody(CacheConfig.DEFAULT_MAX_OBJECT_SIZE_BYTES - 1));
        originResponse.setHeader("Cache-Control","public, max-age=3600");
        originResponse.setHeader("Date", DateUtils.formatDate(responseGenerated));
        originResponse.setHeader("ETag", "\"etag\"");

        final HttpResponse result = impl.cacheAndReturnResponse(host, request, originResponse, requestSent, responseReceived);
        assertEquals(1, backing.map.size());
        assertTrue(backing.map.containsKey((new CacheKeyGenerator()).getURI(host, request)));
        assertTrue(HttpTestUtils.semanticallyTransparent(originResponse, result));
    }

    @Test
    public void testGetCacheEntryReturnsNullOnCacheMiss() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        Assert.assertNull(result);
    }

    @Test
    public void testGetCacheEntryFetchesFromCacheOnCacheHitIfNoVariants() throws Exception {
        final HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        assertFalse(entry.hasVariants());
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final String key = (new CacheKeyGenerator()).getURI(host, request);

        backing.map.put(key,entry);

        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        Assert.assertSame(entry, result);
    }

    @Test
    public void testGetCacheEntryReturnsNullIfNoVariantInCache() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final HttpResponse origResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        origResponse.setEntity(HttpTestUtils.makeBody(128));
        origResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        impl.cacheAndReturnResponse(host, origRequest, origResponse, new Date(), new Date());
        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        assertNull(result);
    }

    @Test
    public void testGetCacheEntryReturnsVariantIfPresentInCache() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");
        request.setHeader("Accept-Encoding","gzip");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final HttpResponse origResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        origResponse.setEntity(HttpTestUtils.makeBody(128));
        origResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        impl.cacheAndReturnResponse(host, origRequest, origResponse, new Date(), new Date());
        final HttpCacheEntry result = impl.getCacheEntry(host, request);
        assertNotNull(result);
    }

    @Test
    public void testGetVariantCacheEntriesReturnsEmptySetOnNoVariants() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Map<String,Variant> variants = impl.getVariantCacheEntriesWithEtags(host, request);

        assertNotNull(variants);
        assertEquals(0, variants.size());
    }

    @Test
    public void testGetVariantCacheEntriesReturnsAllVariants() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new HttpGet("http://foo.example.com/bar");
        req1.setHeader("Accept-Encoding", "gzip");

        final HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Date", DateUtils.formatDate(new Date()));
        resp1.setHeader("Cache-Control", "max-age=3600, public");
        resp1.setHeader("ETag", "\"etag1\"");
        resp1.setHeader("Vary", "Accept-Encoding");
        resp1.setHeader("Content-Encoding","gzip");
        resp1.setHeader("Vary", "Accept-Encoding");

        final HttpRequest req2 = new HttpGet("http://foo.example.com/bar");
        req2.setHeader("Accept-Encoding", "identity");

        final HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Date", DateUtils.formatDate(new Date()));
        resp2.setHeader("Cache-Control", "max-age=3600, public");
        resp2.setHeader("ETag", "\"etag2\"");
        resp2.setHeader("Vary", "Accept-Encoding");
        resp2.setHeader("Content-Encoding","gzip");
        resp2.setHeader("Vary", "Accept-Encoding");

        impl.cacheAndReturnResponse(host, req1, resp1, new Date(), new Date());
        impl.cacheAndReturnResponse(host, req2, resp2, new Date(), new Date());

        final Map<String,Variant> variants = impl.getVariantCacheEntriesWithEtags(host, req1);

        assertNotNull(variants);
        assertEquals(2, variants.size());

    }

    @Test
    public void testOriginalResponseWithNoContentSizeHeaderIsReleased() throws Exception {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest request = new HttpGet("http://foo.example.com/bar");

        final Date now = new Date();
        final Date requestSent = new Date(now.getTime() - 3 * 1000L);
        final Date responseGenerated = new Date(now.getTime() - 2 * 1000L);
        final Date responseReceived = new Date(now.getTime() - 1 * 1000L);

        final HttpResponse originResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        final BasicHttpEntity entity = new BasicHttpEntity();
        final ConsumableInputStream inputStream = new ConsumableInputStream(new ByteArrayInputStream(HttpTestUtils.getRandomBytes(CacheConfig.DEFAULT_MAX_OBJECT_SIZE_BYTES - 1)));
        entity.setContent(inputStream);
        originResponse.setEntity(entity);
        originResponse.setHeader("Cache-Control","public, max-age=3600");
        originResponse.setHeader("Date", DateUtils.formatDate(responseGenerated));
        originResponse.setHeader("ETag", "\"etag\"");

        final HttpResponse result = impl.cacheAndReturnResponse(host, request, originResponse, requestSent, responseReceived);
        IOUtils.consume(result.getEntity());
        assertTrue(inputStream.wasClosed());
    }

    static class DisposableResource implements Resource {

        private static final long serialVersionUID = 1L;

        private final byte[] b;
        private boolean dispoased;

        public DisposableResource(final byte[] b) {
            super();
            this.b = b;
        }

        byte[] getByteArray() {
            return this.b;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (dispoased) {
                throw new IOException("Already dispoased");
            }
            return new ByteArrayInputStream(this.b);
        }

        @Override
        public long length() {
            return this.b.length;
        }

        @Override
        public void dispose() {
            this.dispoased = true;
        }

    }

    @Test
    public void testEntryUpdate() throws Exception {

        final HeapResourceFactory rf = new HeapResourceFactory() {

            @Override
            Resource createResource(final byte[] buf) {
                return new DisposableResource(buf);
            }

        };

        impl = new BasicHttpCache(rf, backing, CacheConfig.DEFAULT);

        final HttpHost host = new HttpHost("foo.example.com");

        final HttpRequest origRequest = new HttpGet("http://foo.example.com/bar");
        origRequest.setHeader("Accept-Encoding","gzip");

        final HttpResponse origResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        origResponse.setEntity(HttpTestUtils.makeBody(128));
        origResponse.setHeader("Date", DateUtils.formatDate(new Date()));
        origResponse.setHeader("Cache-Control", "max-age=3600, public");
        origResponse.setHeader("ETag", "\"etag\"");
        origResponse.setHeader("Vary", "Accept-Encoding");
        origResponse.setHeader("Content-Encoding","gzip");

        final HttpResponse response = impl.cacheAndReturnResponse(
                host, origRequest, origResponse, new Date(), new Date());
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        IOUtils.copyAndClose(entity.getContent(), new ByteArrayOutputStream());
    }

}
