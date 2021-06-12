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

import java.io.IOException;
import java.util.Date;
import java.util.Random;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * We are a conditionally-compliant HTTP/1.1 client with a cache. However, a lot
 * of the rules for proxies apply to us, as far as proper operation of the
 * requests that pass through us. Generally speaking, we want to make sure that
 * any response returned from our HttpClient.execute() methods is conditionally
 * compliant with the rules for an HTTP/1.1 server, and that any requests we
 * pass downstream to the backend HttpClient are are conditionally compliant
 * with the rules for an HTTP/1.1 client.
 *
 * There are some cases where strictly behaving as a compliant caching proxy
 * would result in strange behavior, since we're attached as part of a client
 * and are expected to be a drop-in replacement. The test cases captured here
 * document the places where we differ from the HTTP RFC.
 */
@SuppressWarnings("boxing") // test code
@RunWith(MockitoJUnitRunner.class)
public class TestProtocolDeviations {

    private static final int MAX_BYTES = 1024;
    private static final int MAX_ENTRIES = 100;

    HttpHost host;
    HttpRoute route;
    @Mock
    HttpEntity mockEntity;
    @Mock
    ExecRuntime mockEndpoint;
    @Mock
    ExecChain mockExecChain;
    @Mock
    HttpCache mockCache;
    ClassicHttpRequest request;
    HttpCacheContext context;
    ClassicHttpResponse originResponse;

    ExecChainHandler impl;

    @Before
    public void setUp() {
        host = new HttpHost("foo.example.com", 80);

        route = new HttpRoute(host);

        request = new BasicClassicHttpRequest("GET", "/foo");

        context = HttpCacheContext.create();

        originResponse = make200Response();

        final CacheConfig config = CacheConfig.custom()
                .setMaxCacheEntries(MAX_ENTRIES)
                .setMaxObjectSize(MAX_BYTES)
                .build();

        final HttpCache cache = new BasicHttpCache(config);
        impl = createCachingExecChain(cache, config);
    }

    private ClassicHttpResponse execute(final ClassicHttpRequest request) throws IOException, HttpException {
        return impl.execute(request,
                new ExecChain.Scope("test", route, request, mockEndpoint, context),
                mockExecChain);
    }

    protected ExecChainHandler createCachingExecChain(final HttpCache cache, final CacheConfig config) {
        return new CachingExec(cache, null, config);
    }

    private ClassicHttpResponse make200Response() {
        final ClassicHttpResponse out = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        out.setHeader("Date", DateUtils.formatDate(new Date()));
        out.setHeader("Server", "MockOrigin/1.0");
        out.setEntity(makeBody(128));
        return out;
    }

    private HttpEntity makeBody(final int nbytes) {
        final byte[] bytes = new byte[nbytes];
        new Random().nextBytes(bytes);
        return new ByteArrayEntity(bytes, null);
    }

    /*
     * "10.2.7 206 Partial Content ... The request MUST have included a Range
     * header field (section 14.35) indicating the desired range, and MAY have
     * included an If-Range header field (section 14.27) to make the request
     * conditional."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.7
     */
    @Test
    public void testPartialContentIsNotReturnedToAClientThatDidNotAskForIt() throws Exception {

        // tester's note: I don't know what the cache will *do* in
        // this situation, but it better not just pass the response
        // on.
        request.removeHeaders("Range");
        originResponse = new BasicClassicHttpResponse(HttpStatus.SC_PARTIAL_CONTENT, "Partial Content");
        originResponse.setHeader("Content-Range", "bytes 0-499/1234");
        originResponse.setEntity(makeBody(500));

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        try {
            final HttpResponse result = execute(request);
            Assert.assertTrue(HttpStatus.SC_PARTIAL_CONTENT != result.getCode());
        } catch (final ClientProtocolException acceptableBehavior) {
            // this is probably ok
        }
    }

    /*
     * "10.4.2 401 Unauthorized ... The response MUST include a WWW-Authenticate
     * header field (section 14.47) containing a challenge applicable to the
     * requested resource."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.2
     */
    @Test
    public void testPassesOnOrigin401ResponseWithoutWWWAuthenticateHeader() throws Exception {

        originResponse = new BasicClassicHttpResponse(401, "Unauthorized");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final HttpResponse result = execute(request);
        Assert.assertSame(originResponse, result);
    }

    /*
     * "10.4.6 405 Method Not Allowed ... The response MUST include an Allow
     * header containing a list of valid methods for the requested resource.
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.2
     */
    @Test
    public void testPassesOnOrigin405WithoutAllowHeader() throws Exception {
        originResponse = new BasicClassicHttpResponse(405, "Method Not Allowed");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final HttpResponse result = execute(request);
        Assert.assertSame(originResponse, result);
    }

    /*
     * "10.4.8 407 Proxy Authentication Required ... The proxy MUST return a
     * Proxy-Authenticate header field (section 14.33) containing a challenge
     * applicable to the proxy for the requested resource."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.8
     */
    @Test
    public void testPassesOnOrigin407WithoutAProxyAuthenticateHeader() throws Exception {
        originResponse = new BasicClassicHttpResponse(407, "Proxy Authentication Required");

        Mockito.when(mockExecChain.proceed(Mockito.any(), Mockito.any())).thenReturn(originResponse);

        final HttpResponse result = execute(request);
        Assert.assertSame(originResponse, result);
    }

}
