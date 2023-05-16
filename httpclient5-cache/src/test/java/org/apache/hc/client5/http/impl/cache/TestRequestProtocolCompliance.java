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
        assertEquals(1, impl.requestIsFatallyNonCompliant(req).size());
    }

    @Test
    public void testRequestWithWeekETagForPUTOrDELETEIfMatch() throws Exception {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.setHeader("If-Match", "W/\"weak\"");
        assertEquals(1, impl.requestIsFatallyNonCompliant(req).size());
    }

    @Test
    public void testRequestWithWeekETagForPUTOrDELETEIfMatchAllowed() throws Exception {
        final HttpRequest req = new BasicHttpRequest("PUT", "http://example.com/");
        req.setHeader("If-Match", "W/\"weak\"");
        impl = new RequestProtocolCompliance(true);
        assertEquals(Collections.emptyList(), impl.requestIsFatallyNonCompliant(req));
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

}
