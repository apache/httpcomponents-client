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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.apache.hc.client5.http.impl.RequestCopier;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.Before;
import org.junit.Test;

public class TestRequestProtocolCompliance {

    private RequestProtocolCompliance impl;

    @Before
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
        assertEquals(Arrays.asList(), impl.requestIsFatallyNonCompliant(req));
    }

    @Test
    public void testRequestContainsNoCacheDirectiveWithFieldName() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Cache-Control", "no-cache=false");
        assertEquals(1, impl.requestIsFatallyNonCompliant(req).size());
    }

    @Test
    public void doesNotModifyACompliantRequest() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertTrue(HttpTestUtils.equivalent(req, wrapper));
    }

    @Test
    public void upgrades1_0RequestTo1_1() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setVersion(HttpVersion.HTTP_1_0);
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals(HttpVersion.HTTP_1_1, wrapper.getVersion());
    }

    @Test
    public void downgrades1_2RequestTo1_1() throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setVersion(new ProtocolVersion("HTTP", 1, 2));
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals(HttpVersion.HTTP_1_1, wrapper.getVersion());
    }

    @Test
    public void stripsMinFreshFromRequestIfNoCachePresent()
        throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Cache-Control", "no-cache, min-fresh=10");
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals("no-cache",
                wrapper.getFirstHeader("Cache-Control").getValue());
    }

    @Test
    public void stripsMaxFreshFromRequestIfNoCachePresent()
        throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Cache-Control", "no-cache, max-stale=10");
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals("no-cache",
                wrapper.getFirstHeader("Cache-Control").getValue());
    }

    @Test
    public void stripsMaxAgeFromRequestIfNoCachePresent()
        throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Cache-Control", "no-cache, max-age=10");
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals("no-cache",
                wrapper.getFirstHeader("Cache-Control").getValue());
    }

    @Test
    public void doesNotStripMinFreshFromRequestWithoutNoCache()
        throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Cache-Control", "min-fresh=10");
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals("min-fresh=10",
                wrapper.getFirstHeader("Cache-Control").getValue());
    }

    @Test
    public void correctlyStripsMinFreshFromMiddleIfNoCache()
        throws Exception {
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        req.setHeader("Cache-Control", "no-cache,min-fresh=10,no-store");
        final HttpRequest wrapper = RequestCopier.INSTANCE.copy(req);
        impl.makeRequestCompliant(wrapper);
        assertEquals("no-cache,no-store",
                wrapper.getFirstHeader("Cache-Control").getValue());
    }

}
