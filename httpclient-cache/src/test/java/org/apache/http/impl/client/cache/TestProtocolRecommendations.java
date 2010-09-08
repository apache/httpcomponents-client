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

import java.io.IOException;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Test;

/*
 * This test class captures functionality required to achieve unconditional
 * compliance with the HTTP/1.1 spec, i.e. all the SHOULD, SHOULD NOT,
 * RECOMMENDED, and NOT RECOMMENDED behaviors.
 */
public class TestProtocolRecommendations extends AbstractProtocolTest {

    /* "identity: The default (identity) encoding; the use of no
     * transformation whatsoever. This content-coding is used only in the
     * Accept-Encoding header, and SHOULD NOT be used in the
     * Content-Encoding header."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.5
     */
    @Test
    public void testIdentityCodingIsNotUsedInContentEncodingHeader()
        throws Exception {
        originResponse.setHeader("Content-Encoding", "identity");
        backendExpectsAnyRequest().andReturn(originResponse);
        replayMocks();
        HttpResponse result = impl.execute(host, request);
        verifyMocks();
        boolean foundIdentity = false;
        for(Header h : result.getHeaders("Content-Encoding")) {
            for(HeaderElement elt : h.getElements()) {
                if ("identity".equalsIgnoreCase(elt.getName())) {
                    foundIdentity = true;
                }
            }
        }
        assertFalse(foundIdentity);
    }

    /*
     * "For this reason, a cache SHOULD NOT return a stale response if the
     * client explicitly requests a first-hand or fresh one, unless it is
     * impossible to comply for technical or policy reasons."
     */
    private HttpRequest requestToPopulateStaleCacheEntry() throws Exception {
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp1 = make200Response();
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        resp1.setHeader("Cache-Control","public,max-age=5");
        resp1.setHeader("Etag","\"etag\"");

        backendExpectsAnyRequest().andReturn(resp1);
        return req1;
    }

    private void testDoesNotReturnStaleResponseOnError(HttpRequest req2)
    throws Exception, IOException {
        HttpRequest req1 = requestToPopulateStaleCacheEntry();

        backendExpectsAnyRequest().andThrow(new IOException());

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = null;
        try {
            result = impl.execute(host, req2);
        } catch (IOException acceptable) {
        }
        verifyMocks();

        if (result != null) {
            assertFalse(result.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        }
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFirstHandOneWithCacheControl()
            throws Exception {
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","no-cache");
        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFirstHandOneWithPragma()
            throws Exception {
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req.setHeader("Pragma","no-cache");
        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFreshWithMaxAge()
            throws Exception {
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","max-age=0");
        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlySpecifiesLargerMaxAge()
            throws Exception {
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","max-age=20");
        testDoesNotReturnStaleResponseOnError(req);
    }


    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFreshWithMinFresh()
    throws Exception {
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","min-fresh=2");

        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testDoesNotReturnStaleResponseIfClientExplicitlyRequestsFreshWithMaxStale()
    throws Exception {
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req.setHeader("Cache-Control","max-stale=2");

        testDoesNotReturnStaleResponseOnError(req);
    }

    @Test
    public void testMayReturnStaleResponseIfClientExplicitlySpecifiesAcceptableMaxStale()
            throws Exception {
        HttpRequest req1 = requestToPopulateStaleCacheEntry();
        HttpRequest req2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        req2.setHeader("Cache-Control","max-stale=20");

        backendExpectsAnyRequest().andThrow(new IOException());

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        assertNotNull(result.getFirstHeader("Warning"));
    }

    /*
     * "A correct cache MUST respond to a request with the most up-to-date
     * response held by the cache that is appropriate to the request
     * (see sections 13.2.5, 13.2.6, and 13.12) which meets one of the
     * following conditions:
     *
     * 1. It has been checked for equivalence with what the origin server
     * would have returned by revalidating the response with the
     * origin server (section 13.3);
     *
     * 2. It is "fresh enough" (see section 13.2). In the default case,
     * this means it meets the least restrictive freshness requirement
     * of the client, origin server, and cache (see section 14.9); if
     * the origin server so specifies, it is the freshness requirement
     * of the origin server alone.
     *
     * If a stored response is not "fresh enough" by the most
     * restrictive freshness requirement of both the client and the
     * origin server, in carefully considered circumstances the cache
     * MAY still return the response with the appropriate Warning
     * header (see section 13.1.5 and 14.46), unless such a response
     * is prohibited (e.g., by a "no-store" cache-directive, or by a
     * "no-cache" cache-request-directive; see section 14.9).
     *
     * 3. It is an appropriate 304 (Not Modified), 305 (Proxy Redirect),
     * or error (4xx or 5xx) response message.
     *
     * If the cache can not communicate with the origin server, then a
     * correct cache SHOULD respond as above if the response can be
     * correctly served from the cache..."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.1.1
     */
    @Test
    public void testReturnsCachedResponsesAppropriatelyWhenNoOriginCommunication()
        throws Exception {
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpResponse resp1 = make200Response();
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        resp1.setHeader("Cache-Control", "public, max-age=5");
        resp1.setHeader("ETag","\"etag\"");
        resp1.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));

        backendExpectsAnyRequest().andReturn(resp1);

        HttpRequest req2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);

        backendExpectsAnyRequest().andThrow(new IOException()).anyTimes();

        replayMocks();
        impl.execute(host, req1);
        HttpResponse result = impl.execute(host, req2);
        verifyMocks();

        assertEquals(HttpStatus.SC_OK, result.getStatusLine().getStatusCode());
        boolean warning111Found = false;
        for(Header h : result.getHeaders("Warning")) {
            for(WarningValue wv : WarningValue.getWarningValues(h)) {
                if (wv.getWarnCode() == 111) {
                    warning111Found = true;
                    break;
                }
            }
        }
        assertTrue(warning111Found);
    }

    /*
     * "If a cache receives a response (either an entire response, or a
     * 304 (Not Modified) response) that it would normally forward to the
     * requesting client, and the received response is no longer fresh,
     * the cache SHOULD forward it to the requesting client without adding
     * a new Warning (but without removing any existing Warning headers).
     * A cache SHOULD NOT attempt to revalidate a response simply because
     * that response became stale in transit; this might lead to an
     * infinite loop."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.1.1
     */
    @Test
    public void testDoesNotAddNewWarningHeaderIfResponseArrivesStale()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        originResponse.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        originResponse.setHeader("Cache-Control","public, max-age=5");
        originResponse.setHeader("ETag","\"etag\"");

        backendExpectsAnyRequest().andReturn(originResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, request);
        verifyMocks();

        assertNull(result.getFirstHeader("Warning"));
    }

    @Test
    public void testForwardsExistingWarningHeadersOnResponseThatArrivesStale()
        throws Exception {
        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        originResponse.setHeader("Date", DateUtils.formatDate(tenSecondsAgo));
        originResponse.setHeader("Cache-Control","public, max-age=5");
        originResponse.setHeader("ETag","\"etag\"");
        originResponse.addHeader("Age","10");
        final String warning = "110 fred \"Response is stale\"";
        originResponse.addHeader("Warning",warning);

        backendExpectsAnyRequest().andReturn(originResponse);

        replayMocks();
        HttpResponse result = impl.execute(host, request);
        verifyMocks();

        assertEquals(warning, result.getFirstHeader("Warning").getValue());
    }
}
