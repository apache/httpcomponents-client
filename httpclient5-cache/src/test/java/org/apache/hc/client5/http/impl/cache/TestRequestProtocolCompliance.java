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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestRequestProtocolCompliance {

    private RequestProtocolCompliance impl;

    @BeforeEach
    public void setUp() {
        impl = new RequestProtocolCompliance(false);
    }

    @Test
    public void testRequestWithWeakETagAndRange() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Range", "bytes=0-499");
        req.setHeader("If-Range", "W/\"weak\"");
        assertEquals(1, impl.requestIsFatallyNonCompliant(req, false).size());
    }

    @Test
    public void testRequestWithWeekETagForPUTOrDELETEIfMatch() throws Exception {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.setHeader("If-Match", "W/\"weak\"");
        assertEquals(1, impl.requestIsFatallyNonCompliant(req, false).size());
    }

    @Test
    public void testRequestWithWeekETagForPUTOrDELETEIfMatchAllowed() throws Exception {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.setHeader("If-Match", "W/\"weak\"");
        impl = new RequestProtocolCompliance(true);
        assertEquals(Collections.emptyList(), impl.requestIsFatallyNonCompliant(req, false));
    }

    @Test
    public void doesNotModifyACompliantRequest() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        final HttpRequest wrapper = BasicRequestBuilder.copy(req).build();
        impl.makeRequestCompliant(wrapper);
        assertTrue(HttpTestUtils.equivalent(req, wrapper));
    }

    @Test
    public void upgrades1_0RequestTo1_1() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setVersion(HttpVersion.HTTP_1_0);
        final HttpRequest wrapper = BasicRequestBuilder.copy(req).build();
        impl.makeRequestCompliant(wrapper);
        assertEquals(HttpVersion.HTTP_1_1, wrapper.getVersion());
    }

    @Test
    public void downgrades1_2RequestTo1_1() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setVersion(new ProtocolVersion("HTTP", 1, 2));
        final HttpRequest wrapper = BasicRequestBuilder.copy(req).build();
        impl.makeRequestCompliant(wrapper);
        assertEquals(HttpVersion.HTTP_1_1, wrapper.getVersion());
    }

    @Test
    public void testRequestWithMultipleIfMatchHeaders() {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.addHeader(HttpHeaders.IF_MATCH, "W/\"weak1\"");
        req.addHeader(HttpHeaders.IF_MATCH, "W/\"weak2\"");
        assertEquals(1, impl.requestIsFatallyNonCompliant(req, false).size());
    }

    @Test
    public void testRequestWithMultipleIfNoneMatchHeaders() {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.addHeader(HttpHeaders.IF_NONE_MATCH, "W/\"weak1\"");
        req.addHeader(HttpHeaders.IF_NONE_MATCH, "W/\"weak2\"");
        assertEquals(1, impl.requestIsFatallyNonCompliant(req, false).size());
    }

    @Test
    public void testRequestWithPreconditionFailed() {
        final HttpRequest req = new BasicHttpRequest("GET", "http://example.com/");
        req.addHeader(HttpHeaders.IF_MATCH, "W/\"weak1\"");
        req.addHeader(HttpHeaders.RANGE, "1");
        req.addHeader(HttpHeaders.IF_RANGE, "W/\"weak2\""); // ETag doesn't match with If-Match ETag
        // This will cause the precondition If-Match to fail because the ETags are different
        final List<RequestProtocolError> requestProtocolErrors = impl.requestIsFatallyNonCompliant(req, false);
        assertTrue(requestProtocolErrors.contains(RequestProtocolError.WEAK_ETAG_AND_RANGE_ERROR));
    }

    @Test
    public void testRequestWithValidIfRangeDate() {
        final HttpRequest req = new BasicHttpRequest("GET", "http://example.com/");
        req.addHeader(HttpHeaders.RANGE, "bytes=0-499");
        req.addHeader(HttpHeaders.LAST_MODIFIED, "Wed, 21 Oct 2023 07:28:00 GMT");
        req.addHeader(HttpHeaders.IF_RANGE, "Wed, 21 Oct 2023 07:28:00 GMT");
        assertTrue(impl.requestIsFatallyNonCompliant(req, false).isEmpty());
    }

    @Test
    public void testRequestWithInvalidDateFormat() {
        final HttpRequest req = new BasicHttpRequest("GET", "http://example.com/");
        req.addHeader(HttpHeaders.RANGE, "bytes=0-499");
        req.addHeader(HttpHeaders.LAST_MODIFIED, "Wed, 21 Oct 2023 07:28:00 GMT");
        req.addHeader(HttpHeaders.IF_RANGE, "20/10/2023");
        assertTrue(impl.requestIsFatallyNonCompliant(req, false).isEmpty());
    }

    @Test
    public void testRequestWithMissingIfRangeDate() {
        final HttpRequest req = new BasicHttpRequest("GET", "http://example.com/");
        req.addHeader(HttpHeaders.RANGE, "bytes=0-499");
        req.addHeader(HttpHeaders.LAST_MODIFIED, "Wed, 21 Oct 2023 07:28:00 GMT");
        assertTrue(impl.requestIsFatallyNonCompliant(req, false).isEmpty());
    }

    @Test
    public void testRequestWithWeakETagAndRangeAndDAte() {
        // Setup request with GET method, Range header, If-Range header starting with "W/",
        // and a Last-Modified date that doesn't match the If-Range date
        final HttpRequest req = new BasicHttpRequest("GET", "http://example.com/");
        req.addHeader(HttpHeaders.RANGE, "bytes=0-499");
        req.addHeader(HttpHeaders.LAST_MODIFIED, "Fri, 20 Oct 2023 07:28:00 GMT");
        req.addHeader(HttpHeaders.IF_RANGE, "Wed, 18 Oct 2023 07:28:00 GMT");


        // Use your implementation to check the request
        final List<RequestProtocolError> errors = impl.requestIsFatallyNonCompliant(req, false);

        // Assert that the WEAK_ETAG_AND_RANGE_ERROR is in the list of errors
        assertTrue(errors.contains(RequestProtocolError.WEAK_ETAG_AND_RANGE_ERROR));
    }

    @Test
    public void testRequestWithWeekETagForPUTOrDELETEIfMatchWithStart() {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.setHeader(HttpHeaders.IF_MATCH, "*");
        assertEquals(0, impl.requestIsFatallyNonCompliant(req, false).size());
    }

    @Test
    public void testRequestOkETagForPUTOrDELETEIfMatch() {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.setHeader(HttpHeaders.IF_MATCH, "1234");
        assertEquals(0, impl.requestIsFatallyNonCompliant(req, false).size());
    }

}
