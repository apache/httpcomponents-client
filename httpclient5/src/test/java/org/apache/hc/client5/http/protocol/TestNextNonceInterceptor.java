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

import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestNextNonceInterceptor {

    private static final String AUTHENTICATION_INFO_HEADER = "Authentication-Info";

    private NextNonceInterceptor interceptor;
    private HttpClientContext context;

    @BeforeEach
    void setUp() {
        interceptor = new NextNonceInterceptor();
        context = HttpClientContext.create();
    }

    @Test
    void testNoAuthenticationInfoHeader() {
        final HttpResponse response = new BasicHttpResponse(200);

        interceptor.process(response, null, context);

        Assertions.assertNull(context.getNextNonce(),
                "Context should not contain nextnonce when the header is missing");
    }

    @Test
    void testAuthenticationInfoHeaderWithoutNextNonce() {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(new BasicHeader(AUTHENTICATION_INFO_HEADER, "auth-param=value"));

        interceptor.process(response, null, context);

        Assertions.assertNull(context.getNextNonce(),
                "Context should not contain nextnonce when it is missing in the header value");
    }

    @Test
    void testAuthenticationInfoHeaderWithNextNonce() {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(new BasicHeader(AUTHENTICATION_INFO_HEADER, "nextnonce=\"10024b2308596a55d02699c0a0400fb4\",qop=auth,rspauth=\"0386df3cb9effdf08c9e00ab955827f3\",cnonce=\"21558090\",nc=00000001"));

        interceptor.process(response, null, context);

        Assertions.assertEquals("10024b2308596a55d02699c0a0400fb4", context.getNextNonce(),
                "Context should contain the correct nextnonce value when it is present in the header");
    }

    @Test
    void testMultipleAuthenticationInfoHeaders() {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(new BasicHeader(AUTHENTICATION_INFO_HEADER, "auth-param=value"));  // First header without nextnonce
        response.addHeader(new BasicHeader(AUTHENTICATION_INFO_HEADER, "nextnonce=\"10024b2308596a55d02699c0a0400fb4\",qop=auth,rspauth=\"0386df3cb9effdf08c9e00ab955827f3\",cnonce=\"21558090\",nc=00000001"));  // Second header with nextnonce

        interceptor.process(response, null, context);

        // Since only the first header is processed, `auth-nextnonce` should not be set in the context
        Assertions.assertNull(context.getNextNonce(),
                "Context should not contain nextnonce if it's not in the first Authentication-Info header");
    }

    @Test
    void testAuthenticationInfoHeaderWithEmptyNextNonce() {
        final HttpResponse response = new BasicHttpResponse(200);
        response.addHeader(new BasicHeader(AUTHENTICATION_INFO_HEADER, "nextnonce=\"\",qop=auth,rspauth=\"0386df3cb9effdf08c9e00ab955827f3\",cnonce=\"21558090\",nc=00000001"));

        interceptor.process(response, null, context);

        Assertions.assertNull(context.getNextNonce(),
                "Context should not contain nextnonce if it is empty in the Authentication-Info header");
    }

}
