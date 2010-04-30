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
package org.apache.http.client.cache.impl;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.cache.impl.CacheEntry;
import org.apache.http.client.cache.impl.URIExtractor;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestURIExtractor {

    private static final BasicHttpRequest REQUEST_FULL_EPISODES = new BasicHttpRequest("GET",
            "/full_episodes");
    private static final BasicHttpRequest REQUEST_ROOT = new BasicHttpRequest("GET", "/");

    URIExtractor extractor;
    private HttpHost host;
    private CacheEntry mockEntry;
    private HttpRequest mockRequest;

    @Before
    public void setUp() throws Exception {
        host = new HttpHost("foo.example.com");
        mockEntry = EasyMock.createMock(CacheEntry.class);
        mockRequest = EasyMock.createMock(HttpRequest.class);
        extractor = new URIExtractor();
    }

    private void replayMocks() {
        EasyMock.replay(mockEntry);
        EasyMock.replay(mockRequest);
    }

    private void verifyMocks() {
        EasyMock.verify(mockEntry);
        EasyMock.verify(mockRequest);
    }

    @Test
    public void testGetURIWithDefaultPortAndScheme() {
        Assert.assertEquals("http://www.comcast.net/", extractor.getURI(new HttpHost(
                "www.comcast.net"), REQUEST_ROOT));

        Assert.assertEquals("http://www.fancast.com/full_episodes", extractor.getURI(new HttpHost(
                "www.fancast.com"), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentScheme() {
        Assert.assertEquals("https://www.comcast.net/", extractor.getURI(new HttpHost(
                "www.comcast.net", -1, "https"), REQUEST_ROOT));

        Assert.assertEquals("myhttp://www.fancast.com/full_episodes", extractor.getURI(
                new HttpHost("www.fancast.com", -1, "myhttp"), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentPort() {
        Assert.assertEquals("http://www.comcast.net:8080/", extractor.getURI(new HttpHost(
                "www.comcast.net", 8080), REQUEST_ROOT));

        Assert.assertEquals("http://www.fancast.com:9999/full_episodes", extractor.getURI(
                new HttpHost("www.fancast.com", 9999), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentPortAndScheme() {
        Assert.assertEquals("https://www.comcast.net:8080/", extractor.getURI(new HttpHost(
                "www.comcast.net", 8080, "https"), REQUEST_ROOT));

        Assert.assertEquals("myhttp://www.fancast.com:9999/full_episodes", extractor.getURI(
                new HttpHost("www.fancast.com", 9999, "myhttp"), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithQueryParameters() {
        Assert.assertEquals("http://www.comcast.net/?foo=bar", extractor.getURI(new HttpHost(
                "www.comcast.net", -1, "http"), new BasicHttpRequest("GET", "/?foo=bar")));
        Assert.assertEquals("http://www.fancast.com/full_episodes?foo=bar", extractor.getURI(
                new HttpHost("www.fancast.com", -1, "http"), new BasicHttpRequest("GET",
                        "/full_episodes?foo=bar")));
    }

    @Test
    public void testGetVariantURIWithNoVaryHeaderReturnsNormalURI() {
        final String theURI = "theURI";
        Header[] noHdrs = new Header[0];
        org.easymock.EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(noHdrs);
        extractor = new URIExtractor() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };

        replayMocks();
        String result = extractor.getVariantURI(host, mockRequest, mockEntry);
        verifyMocks();
        Assert.assertSame(theURI, result);
    }

    @Test
    public void testGetVariantURIWithSingleValueVaryHeaderPrepends() {
        final String theURI = "theURI";
        Header[] varyHeaders = { new BasicHeader("Vary", "Accept-Encoding") };
        Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip") };

        extractor = new URIExtractor() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        org.easymock.EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        org.easymock.EasyMock.expect(mockRequest.getHeaders("Accept-Encoding")).andReturn(
                encHeaders);
        replayMocks();

        String result = extractor.getVariantURI(host, mockRequest, mockEntry);

        verifyMocks();
        Assert.assertEquals("{Accept-Encoding=gzip}" + theURI, result);
    }

    @Test
    public void testGetVariantURIWithMissingRequestHeader() {
        final String theURI = "theURI";
        Header[] noHeaders = new Header[0];
        Header[] varyHeaders = { new BasicHeader("Vary", "Accept-Encoding") };
        extractor = new URIExtractor() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        org.easymock.EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        org.easymock.EasyMock.expect(mockRequest.getHeaders("Accept-Encoding"))
                .andReturn(noHeaders);
        replayMocks();

        String result = extractor.getVariantURI(host, mockRequest, mockEntry);

        verifyMocks();
        Assert.assertEquals("{Accept-Encoding=}" + theURI, result);
    }

    @Test
    public void testGetVariantURIAlphabetizesWithMultipleVaryingHeaders() {
        final String theURI = "theURI";
        Header[] varyHeaders = { new BasicHeader("Vary", "User-Agent, Accept-Encoding") };
        Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip") };
        Header[] uaHeaders = { new BasicHeader("User-Agent", "browser") };
        extractor = new URIExtractor() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        org.easymock.EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        org.easymock.EasyMock.expect(mockRequest.getHeaders("Accept-Encoding")).andReturn(
                encHeaders);
        org.easymock.EasyMock.expect(mockRequest.getHeaders("User-Agent")).andReturn(uaHeaders);
        replayMocks();

        String result = extractor.getVariantURI(host, mockRequest, mockEntry);

        verifyMocks();
        Assert.assertEquals("{Accept-Encoding=gzip&User-Agent=browser}" + theURI, result);
    }

    @Test
    public void testGetVariantURIHandlesMultipleVaryHeaders() {
        final String theURI = "theURI";
        Header[] varyHeaders = { new BasicHeader("Vary", "User-Agent"),
                new BasicHeader("Vary", "Accept-Encoding") };
        Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip") };
        Header[] uaHeaders = { new BasicHeader("User-Agent", "browser") };
        extractor = new URIExtractor() {
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        EasyMock.expect(mockRequest.getHeaders("Accept-Encoding")).andReturn(encHeaders);
        EasyMock.expect(mockRequest.getHeaders("User-Agent")).andReturn(uaHeaders);
        replayMocks();

        String result = extractor.getVariantURI(host, mockRequest, mockEntry);

        verifyMocks();
        Assert.assertEquals("{Accept-Encoding=gzip&User-Agent=browser}" + theURI, result);
    }

    @Test
    public void testGetVariantURIHandlesMultipleLineRequestHeaders() {
        final String theURI = "theURI";
        Header[] varyHeaders = { new BasicHeader("Vary", "User-Agent, Accept-Encoding") };
        Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip"),
                new BasicHeader("Accept-Encoding", "deflate") };
        Header[] uaHeaders = { new BasicHeader("User-Agent", "browser") };
        extractor = new URIExtractor() {
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        EasyMock.expect(mockRequest.getHeaders("Accept-Encoding")).andReturn(encHeaders);
        EasyMock.expect(mockRequest.getHeaders("User-Agent")).andReturn(uaHeaders);
        replayMocks();

        String result = extractor.getVariantURI(host, mockRequest, mockEntry);

        verifyMocks();
        Assert
                .assertEquals("{Accept-Encoding=gzip%2C+deflate&User-Agent=browser}" + theURI,
                        result);
    }
}
