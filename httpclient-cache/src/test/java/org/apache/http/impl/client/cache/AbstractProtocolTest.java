package org.apache.http.impl.client.cache;

import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.easymock.IExpectationSetters;
import org.easymock.classextension.EasyMock;
import org.junit.Before;

public abstract class AbstractProtocolTest {

    protected static final int MAX_BYTES = 1024;
    protected static final int MAX_ENTRIES = 100;
    protected int entityLength = 128;
    protected HttpHost host;
    protected HttpEntity body;
    protected HttpClient mockBackend;
    protected HttpCache mockCache;
    protected HttpRequest request;
    protected HttpResponse originResponse;
    protected CacheConfig params;
    protected CachingHttpClient impl;
    protected HttpCache cache;

    public static HttpRequest eqRequest(HttpRequest in) {
        EasyMock.reportMatcher(new RequestEquivalent(in));
        return null;
    }

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com");

        body = HttpTestUtils.makeBody(entityLength);

        request = new BasicHttpRequest("GET", "/foo", HttpVersion.HTTP_1_1);

        originResponse = make200Response();

        cache = new BasicHttpCache(MAX_ENTRIES);
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);
        params = new CacheConfig();
        params.setMaxObjectSizeBytes(MAX_BYTES);
        impl = new CachingHttpClient(mockBackend, cache, new HeapResourceFactory(), params);
    }

    protected void replayMocks() {
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
    }

    protected void verifyMocks() {
        EasyMock.verify(mockBackend);
        EasyMock.verify(mockCache);
    }

    protected HttpResponse make200Response() {
        HttpResponse out = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        out.setHeader("Date", DateUtils.formatDate(new Date()));
        out.setHeader("Server", "MockOrigin/1.0");
        out.setHeader("Content-Length", "128");
        out.setEntity(makeBody(128));
        return out;
    }

    protected HttpEntity makeBody(int nbytes) {
        return HttpTestUtils.makeBody(nbytes);
    }

    protected IExpectationSetters<HttpResponse> backendExpectsAnyRequest() throws Exception {
        HttpResponse resp = mockBackend.execute(EasyMock.isA(HttpHost.class), EasyMock
                .isA(HttpRequest.class), (HttpContext) EasyMock.isNull());
        return EasyMock.expect(resp);
    }

    protected void emptyMockCacheExpectsNoPuts() throws Exception {
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);

        impl = new CachingHttpClient(mockBackend, mockCache, new HeapResourceFactory(), params);

        EasyMock.expect(mockCache.getEntry((String) EasyMock.anyObject())).andReturn(null)
                .anyTimes();

        mockCache.removeEntry(EasyMock.isA(String.class));
        EasyMock.expectLastCall().anyTimes();
    }

    protected void behaveAsNonSharedCache() {
        params.setSharedCache(false);
        impl = new CachingHttpClient(mockBackend, cache, new HeapResourceFactory(), params);
    }

    public AbstractProtocolTest() {
        super();
    }

}