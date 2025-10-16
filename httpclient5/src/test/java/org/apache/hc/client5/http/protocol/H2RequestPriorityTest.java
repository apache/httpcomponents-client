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

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class H2RequestPriorityTest {

    private HttpClientContext h2ctx;

    @BeforeEach
    void setUp() {
        h2ctx = HttpClientContext.create();
        h2ctx.setProtocolVersion(HttpVersion.HTTP_2);
    }

    @Test
    void testH2RequestPriority_noopOnHttp11() throws Exception {
        final HttpClientContext ctx11 = HttpClientContext.create();
        ctx11.setProtocolVersion(HttpVersion.HTTP_1_1);
        ctx11.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.of(0, true))
                .build());

        final BasicHttpRequest request = new BasicHttpRequest("GET", new HttpHost("host"), "/");

        final H2RequestPriority interceptor = H2RequestPriority.INSTANCE;
        interceptor.process(request, null, ctx11);

        Assertions.assertNull(request.getFirstHeader(HttpHeaders.PRIORITY),
                "No Priority header should be added for HTTP/1.1");
    }

    @Test
    void adds_u_only_when_nonDefault_urgency() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        h2ctx.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.of(5, false))
                .build());

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertNotNull(req.getFirstHeader(HttpHeaders.PRIORITY));
        Assertions.assertEquals("u=5", req.getFirstHeader(HttpHeaders.PRIORITY).getValue());
        Assertions.assertEquals(1, req.getHeaders(HttpHeaders.PRIORITY).length);
    }

    @Test
    void adds_i_only_when_incremental_true() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        h2ctx.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.of(3, true))
                .build());

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertEquals("i", req.getFirstHeader(HttpHeaders.PRIORITY).getValue());
    }

    @Test
    void adds_both_with_expected_format_and_order() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        h2ctx.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.of(1, true))
                .build());

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertEquals("u=1, i", req.getFirstHeader(HttpHeaders.PRIORITY).getValue());
    }

    @Test
    void omits_header_when_defaults() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        h2ctx.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.defaults())
                .build());

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertNull(req.getFirstHeader(HttpHeaders.PRIORITY));
    }

    @Test
    void preserves_existing_header() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        req.addHeader(HttpHeaders.PRIORITY, "u=0");
        h2ctx.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.of(5, true))
                .build());

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertEquals("u=0", req.getFirstHeader(HttpHeaders.PRIORITY).getValue(),
                "Existing header must be preserved");
    }

    @Test
    void noop_when_no_configured_priority() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        // No RequestConfig set, or default without h2Priority.

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertNull(req.getFirstHeader(HttpHeaders.PRIORITY));
    }

    @Test
    void respects_case_insensitive_existing_header_name() throws Exception {
        final BasicHttpRequest req = new BasicHttpRequest("GET", new HttpHost("h"), "/");
        req.addHeader("priority", "u=6"); // lower-case, should still be found
        h2ctx.setRequestConfig(RequestConfig.custom()
                .setH2Priority(PriorityValue.of(0, true))
                .build());

        H2RequestPriority.INSTANCE.process(req, null, h2ctx);

        Assertions.assertEquals("u=6", req.getFirstHeader(HttpHeaders.PRIORITY).getValue());
    }
}
