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

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Before;
import org.junit.Test;

public class TestRequestProtocolCompliance {

    private RequestProtocolCompliance impl;
    private HttpRequest req;
    private HttpRequest result;
    
    @Before
    public void setUp() {
        req = HttpTestUtils.makeDefaultRequest();
        impl = new RequestProtocolCompliance();
    }
    
    @Test
    public void doesNotModifyACompliantRequest() throws Exception {
       result = impl.makeRequestCompliant(req); 
       assertTrue(HttpTestUtils.equivalent(req, result));
    }
    
    @Test
    public void removesEntityFromTRACERequest() throws Exception {
        HttpEntityEnclosingRequest req = 
            new BasicHttpEntityEnclosingRequest("TRACE", "/", HttpVersion.HTTP_1_1);
        req.setEntity(HttpTestUtils.makeBody(50));
        result = impl.makeRequestCompliant(req);
        if (result instanceof HttpEntityEnclosingRequest) {
            assertNull(((HttpEntityEnclosingRequest)result).getEntity());
        }
    }
    
    @Test
    public void upgrades1_0RequestTo1_1() throws Exception {
        req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        result = impl.makeRequestCompliant(req);
        assertEquals(HttpVersion.HTTP_1_1, result.getProtocolVersion());
    }

    @Test
    public void downgrades1_2RequestTo1_1() throws Exception {
        ProtocolVersion HTTP_1_2 = new ProtocolVersion("HTTP", 1, 2);
        req = new BasicHttpRequest("GET", "/", HTTP_1_2);
        result = impl.makeRequestCompliant(req);
        assertEquals(HttpVersion.HTTP_1_1, result.getProtocolVersion());
    }
    
    @Test
    public void stripsMinFreshFromRequestIfNoCachePresent()
        throws Exception {
        req.setHeader("Cache-Control", "no-cache, min-fresh=10");
        result = impl.makeRequestCompliant(req);
        assertEquals("no-cache",
                result.getFirstHeader("Cache-Control").getValue());
    }

    @Test
    public void stripsMaxFreshFromRequestIfNoCachePresent()
        throws Exception {
        req.setHeader("Cache-Control", "no-cache, max-stale=10");
        result = impl.makeRequestCompliant(req);
        assertEquals("no-cache",
                result.getFirstHeader("Cache-Control").getValue());
    }

    @Test
    public void stripsMaxAgeFromRequestIfNoCachePresent()
        throws Exception {
        req.setHeader("Cache-Control", "no-cache, max-age=10");
        result = impl.makeRequestCompliant(req);
        assertEquals("no-cache",
                result.getFirstHeader("Cache-Control").getValue());
    }
    
    @Test
    public void doesNotStripMinFreshFromRequestWithoutNoCache()
        throws Exception {
        req.setHeader("Cache-Control", "min-fresh=10");
        result = impl.makeRequestCompliant(req);
        assertEquals("min-fresh=10",
                result.getFirstHeader("Cache-Control").getValue());
    }
    
    @Test
    public void correctlyStripsMinFreshFromMiddleIfNoCache()
        throws Exception {
        req.setHeader("Cache-Control", "no-cache,min-fresh=10,no-store");
        result = impl.makeRequestCompliant(req);
        assertEquals("no-cache,no-store",
                result.getFirstHeader("Cache-Control").getValue());
    }

}
