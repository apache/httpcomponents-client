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

import static org.junit.Assert.*;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Test;

/** 
 * A suite of acceptance tests for compliance with RFC5861, which
 * describes the stale-if-error and stale-while-revalidate
 * Cache-Control extensions.
 */
public class TestRFC5861Compliance extends AbstractProtocolTest {

    /*    
     * "The stale-if-error Cache-Control extension indicates that when an
     * error is encountered, a cached stale response MAY be used to satisfy
     * the request, regardless of other freshness information.When used as a 
     * request Cache-Control extension, its scope of application is the request
     * it appears in; when used as a response Cache-Control extension, its 
     * scope is any request applicable to the cached response in which it 
     * occurs.Its value indicates the upper limit to staleness; when the cached
     * response is more stale than the indicated amount, the cached response
     * SHOULD NOT be used to satisfy the request, absent other information.
     * In this context, an error is any situation that would result in a
     * 500, 502, 503, or 504 HTTP response status code being returned."
     *  
     * http://tools.ietf.org/html/rfc5861
     */
    @Test
    public void testStaleIfErrorInResponseIsTrueReturnsStaleEntryWithWarning()
            throws Exception{
        Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();
        
        HttpTestUtils.assert110WarningFound(result);
    }
    
    @Test
    public void testStaleIfErrorInResponseYieldsToMustRevalidate()
            throws Exception{
        Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, must-revalidate");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();
        
        assertTrue(HttpStatus.SC_OK != result.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testStaleIfErrorInResponseYieldsToProxyRevalidateForSharedCache()
            throws Exception{
        assertTrue(impl.isSharedCache());
        Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, proxy-revalidate");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();
        
        assertTrue(HttpStatus.SC_OK != result.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testStaleIfErrorInResponseNeedNotYieldToProxyRevalidateForPrivateCache()
            throws Exception{
        CacheConfig config = new CacheConfig();
        config.setSharedCache(false);
        impl = new CachingHttpClient(mockBackend, config);
        
        Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60, proxy-revalidate");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();
        
        HttpTestUtils.assert110WarningFound(result);
    }

    @Test
    public void testStaleIfErrorInResponseYieldsToExplicitFreshnessRequest()
            throws Exception{
        Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=60");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","min-fresh=2");
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();
        
        assertTrue(HttpStatus.SC_OK != result.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testStaleIfErrorInRequestIsTrueReturnsStaleEntryWithWarning()
            throws Exception{
        Date tenSecondsAgo = new Date(new Date().getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","public, stale-if-error=60");
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();
        
        HttpTestUtils.assert110WarningFound(result);
    }
    
    @Test
    public void testStaleIfErrorInResponseIsFalseReturnsError()
            throws Exception{
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5, stale-if-error=2");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                result.getStatusLine().getStatusCode());
    }
    
    @Test
    public void testStaleIfErrorInRequestIsFalseReturnsError()
            throws Exception{
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        HttpRequest req1 = HttpTestUtils.makeDefaultRequest();
        HttpResponse resp1 = HttpTestUtils.make200Response(tenSecondsAgo,
                "public, max-age=5");
        
        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = HttpTestUtils.makeDefaultRequest();
        req2.setHeader("Cache-Control","stale-if-error=2");
        HttpResponse resp2 = HttpTestUtils.make500Response();
        
        backendExpectsAnyRequest().andReturn(resp2);
        
        replayMocks();
        impl.execute(host,req1);
        HttpResponse result = impl.execute(host,req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                result.getStatusLine().getStatusCode());
    }
    
    /*
     * When present in an HTTP response, the stale-while-revalidate Cache-
     * Control extension indicates that caches MAY serve the response in
     * which it appears after it becomes stale, up to the indicated number
     * of seconds.
     * 
     * http://tools.ietf.org/html/rfc5861
     */
    @Test
    public void testStaleWhileRevalidateReturnsStaleEntryWithWarning()
        throws Exception {
        
        params.setAsynchronousWorkersMax(1);
        impl = new CachingHttpClient(mockBackend, cache, params);
        
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp1 = HttpTestUtils.make200Response();
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequest().andReturn(resp1).times(1,2);

        HttpRequest req2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(Header h : result.getHeaders("Warning")) {
            for(WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertTrue(warning110Found);
    }
    
    @Test
    public void testStaleWhileRevalidateYieldsToMustRevalidate()
        throws Exception {
        
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        
        params.setAsynchronousWorkersMax(1);
        impl = new CachingHttpClient(mockBackend, cache, params);
        
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, must-revalidate");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, must-revalidate");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        
        backendExpectsAnyRequest().andReturn(resp2);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(Header h : result.getHeaders("Warning")) {
            for(WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertFalse(warning110Found);
    }

    @Test
    public void testStaleWhileRevalidateYieldsToProxyRevalidateForSharedCache()
        throws Exception {
        
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        
        params.setAsynchronousWorkersMax(1);
        params.setSharedCache(true);
        impl = new CachingHttpClient(mockBackend, cache, params);
        
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, proxy-revalidate");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15, proxy-revalidate");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        
        backendExpectsAnyRequest().andReturn(resp2);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(Header h : result.getHeaders("Warning")) {
            for(WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertFalse(warning110Found);
    }

    @Test
    public void testStaleWhileRevalidateYieldsToExplicitFreshnessRequest()
        throws Exception {
        
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        
        params.setAsynchronousWorkersMax(1);
        params.setSharedCache(true);
        impl = new CachingHttpClient(mockBackend, cache, params);
        
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp1 = HttpTestUtils.make200Response();
        resp1.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req2.setHeader("Cache-Control","min-fresh=2");
        HttpResponse resp2 = HttpTestUtils.make200Response();
        resp2.setHeader("Cache-Control", "public, max-age=5, stale-while-revalidate=15");
        resp2.setHeader("ETag","\"etag\"");
        resp2.setHeader("Date", DateUtils.formatDate(now));
        
        backendExpectsAnyRequest().andReturn(resp2);

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning110Found = false;
        for(Header h : result.getHeaders("Warning")) {
            for(WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 110) {
                    warning110Found = true;
                    break;
                }
            }
        }
        assertFalse(warning110Found);
    }
    
}
