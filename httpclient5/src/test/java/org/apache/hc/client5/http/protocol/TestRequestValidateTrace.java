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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestRequestValidateTrace {

    private RequestValidateTrace interceptor;
    private HttpRequest request;
    private HttpContext context;

    @BeforeEach
    void setUp() {
        interceptor = new RequestValidateTrace();
        context = new BasicHttpContext();
    }

    @Test
    void testTraceRequestWithoutSensitiveHeaders() throws HttpException, IOException {
        request = new BasicHttpRequest("TRACE", "/");
        interceptor.process(request, null, context);
        assertNull(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testTraceRequestWithSensitiveHeaders() {
        request = new BasicHttpRequest("TRACE", "/");
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        assertThrows(ProtocolException.class, () -> interceptor.process(request, null, context));
    }

    @Test
    void testTraceRequestWithBody() {
        request = new BasicHttpRequest("TRACE", "/");
        final EntityDetails entity = new BasicEntityDetails(10, null);
        assertThrows(ProtocolException.class, () -> interceptor.process(request, entity, context));
    }

    @Test
    void testNonTraceRequest() throws HttpException, IOException {
        request = new BasicHttpRequest("GET", "/");
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        interceptor.process(request, null, context);
        assertNotNull(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testTraceRequestWithCookieHeader() {
        request = new BasicHttpRequest("TRACE", "/");
        request.setHeader(HttpHeaders.COOKIE, "someCookie=someValue");
        assertThrows(ProtocolException.class, () -> interceptor.process(request, null, context));
    }
}
