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


package org.apache.hc.client5.http.protocol;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TestRequestIfRange {
    @Mock
    private HttpRequest request;

    @Mock
    private EntityDetails entity;

    @Mock
    private HttpContext context;

    private RequestIfRange requestIfRange;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        requestIfRange = new RequestIfRange();
    }

    @Test
    void testNoIfRangeHeader() throws Exception {
        when(request.getFirstHeader(HttpHeaders.IF_RANGE)).thenReturn(null);
        requestIfRange.process(request, entity, context);
        // No exception should be thrown
    }

    @Test
    void testIfRangeWithoutRangeHeader() {
        when(request.getFirstHeader(HttpHeaders.IF_RANGE)).thenReturn(mock(Header.class));
        when(request.containsHeader(HttpHeaders.RANGE)).thenReturn(false);

        assertThrows(ProtocolException.class, () -> requestIfRange.process(request, entity, context));
    }

    @Test
    void testWeakETagInIfRange() {
        when(request.getFirstHeader(HttpHeaders.IF_RANGE)).thenReturn(mock(Header.class));
        when(request.containsHeader(HttpHeaders.RANGE)).thenReturn(true);
        when(request.getFirstHeader(HttpHeaders.ETAG)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.ETAG).getValue()).thenReturn("W/\"weak-etag\"");

        assertThrows(ProtocolException.class, () -> requestIfRange.process(request, entity, context));
    }


    @Test
    void testDateHeaderWithStrongValidator() throws Exception {
        when(request.getFirstHeader(HttpHeaders.IF_RANGE)).thenReturn(mock(Header.class));
        when(request.containsHeader(HttpHeaders.RANGE)).thenReturn(true);
        when(request.getFirstHeader(HttpHeaders.DATE)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.DATE).getValue()).thenReturn("Tue, 15 Nov 2022 08:12:31 GMT");
        when(request.getFirstHeader(HttpHeaders.LAST_MODIFIED)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.LAST_MODIFIED).getValue()).thenReturn("Tue, 15 Nov 2022 08:12:30 GMT");

        requestIfRange.process(request, entity, context);
        // No exception should be thrown
    }

    @Test
    void testSmallDifferenceWithETagPresent() {
        when(request.getFirstHeader(HttpHeaders.IF_RANGE)).thenReturn(mock(Header.class));
        when(request.containsHeader(HttpHeaders.RANGE)).thenReturn(true);
        when(request.getFirstHeader(HttpHeaders.DATE)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.DATE).getValue()).thenReturn("Tue, 15 Nov 2022 08:12:30 GMT"); // Same as Last-Modified
        when(request.getFirstHeader(HttpHeaders.LAST_MODIFIED)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.LAST_MODIFIED).getValue()).thenReturn("Tue, 15 Nov 2022 08:12:30 GMT");
        final Header mockETagHeader = mock(Header.class);
        when(mockETagHeader.getValue()).thenReturn("\"some-value\""); // Mocking a weak ETag value
        when(request.getFirstHeader(HttpHeaders.ETAG)).thenReturn(mockETagHeader);

        assertThrows(ProtocolException.class, () -> requestIfRange.process(request, entity, context));
    }

    @Test
    void testSmallDifferenceWithETagAbsent() throws Exception {
        when(request.getFirstHeader(HttpHeaders.IF_RANGE)).thenReturn(mock(Header.class));
        when(request.containsHeader(HttpHeaders.RANGE)).thenReturn(true);
        when(request.getFirstHeader(HttpHeaders.DATE)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.DATE).getValue()).thenReturn("Tue, 15 Nov 2022 08:12:31 GMT");
        when(request.getFirstHeader(HttpHeaders.LAST_MODIFIED)).thenReturn(mock(Header.class, RETURNS_DEEP_STUBS));
        when(request.getFirstHeader(HttpHeaders.LAST_MODIFIED).getValue()).thenReturn("Tue, 15 Nov 2022 08:12:30 GMT");
        when(request.getFirstHeader(HttpHeaders.ETAG)).thenReturn(null);

        requestIfRange.process(request, entity, context);
        // No exception should be thrown
    }

}