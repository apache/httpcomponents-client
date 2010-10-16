package org.apache.http.impl.client.cache;


import java.util.HashSet;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.message.BasicHttpRequest;
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

        originResponse = HttpTestUtils.make200Response();

        params = new CacheConfig();
        params.setMaxCacheEntries(MAX_ENTRIES);
        params.setMaxObjectSizeBytes(MAX_BYTES);
        cache = new BasicHttpCache(params);
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);
        impl = new CachingHttpClient(mockBackend, cache, params);
    }

    protected void replayMocks() {
        EasyMock.replay(mockBackend);
        EasyMock.replay(mockCache);
    }

    protected void verifyMocks() {
        EasyMock.verify(mockBackend);
        EasyMock.verify(mockCache);
    }

    protected IExpectationSetters<HttpResponse> backendExpectsAnyRequest() throws Exception {
        HttpResponse resp = mockBackend.execute(EasyMock.isA(HttpHost.class), EasyMock
                .isA(HttpRequest.class), (HttpContext) EasyMock.isNull());
        return EasyMock.expect(resp);
    }

    protected void emptyMockCacheExpectsNoPuts() throws Exception {
        mockBackend = EasyMock.createMock(HttpClient.class);
        mockCache = EasyMock.createMock(HttpCache.class);

        impl = new CachingHttpClient(mockBackend, mockCache, params);

        EasyMock.expect(mockCache.getCacheEntry(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class)))
            .andReturn(null).anyTimes();
        EasyMock.expect(mockCache.getVariantCacheEntries(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class)))
            .andReturn(new HashSet<HttpCacheEntry>()).anyTimes();

        mockCache.flushCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();

        mockCache.flushInvalidatedCacheEntriesFor(EasyMock.isA(HttpHost.class), EasyMock.isA(HttpRequest.class));
        EasyMock.expectLastCall().anyTimes();
    }

    protected void behaveAsNonSharedCache() {
        params.setSharedCache(false);
        impl = new CachingHttpClient(mockBackend, cache, params);
    }

    public AbstractProtocolTest() {
        super();
    }

}