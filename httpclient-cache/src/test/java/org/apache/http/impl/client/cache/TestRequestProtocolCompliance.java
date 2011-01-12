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
