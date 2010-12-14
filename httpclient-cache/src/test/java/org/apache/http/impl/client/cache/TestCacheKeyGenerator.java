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

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCacheKeyGenerator {

    private static final BasicHttpRequest REQUEST_FULL_EPISODES = new BasicHttpRequest("GET",
            "/full_episodes");
    private static final BasicHttpRequest REQUEST_ROOT = new BasicHttpRequest("GET", "/");

    CacheKeyGenerator extractor;
    private HttpHost host;
    private HttpCacheEntry mockEntry;
    private HttpRequest mockRequest;

    @Before
    public void setUp() throws Exception {
        host = new HttpHost("foo.example.com");
        mockEntry = EasyMock.createMock(HttpCacheEntry.class);
        mockRequest = EasyMock.createMock(HttpRequest.class);
        extractor = new CacheKeyGenerator();
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
    public void testExtractsUriFromAbsoluteUriInRequest() {
        HttpHost host = new HttpHost("bar.example.com");
        HttpRequest req = new HttpGet("http://foo.example.com/");
        Assert.assertEquals("http://foo.example.com:80/", extractor.getURI(host, req));
    }

    @Test
    public void testGetURIWithDefaultPortAndScheme() {
        Assert.assertEquals("http://www.comcast.net:80/", extractor.getURI(new HttpHost(
                "www.comcast.net"), REQUEST_ROOT));

        Assert.assertEquals("http://www.fancast.com:80/full_episodes", extractor.getURI(new HttpHost(
                "www.fancast.com"), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentScheme() {
        Assert.assertEquals("https://www.comcast.net:443/", extractor.getURI(new HttpHost(
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
        Assert.assertEquals("http://www.comcast.net:80/?foo=bar", extractor.getURI(new HttpHost(
                "www.comcast.net", -1, "http"), new BasicHttpRequest("GET", "/?foo=bar")));
        Assert.assertEquals("http://www.fancast.com:80/full_episodes?foo=bar", extractor.getURI(
                new HttpHost("www.fancast.com", -1, "http"), new BasicHttpRequest("GET",
                        "/full_episodes?foo=bar")));
    }

    @Test
    public void testGetVariantURIWithNoVaryHeaderReturnsNormalURI() {
        final String theURI = "theURI";
        org.easymock.EasyMock.expect(mockEntry.hasVariants()).andReturn(false);
        extractor = new CacheKeyGenerator() {
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

        extractor = new CacheKeyGenerator() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.hasVariants()).andReturn(true).anyTimes();
        EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        EasyMock.expect(mockRequest.getHeaders("Accept-Encoding")).andReturn(
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
        extractor = new CacheKeyGenerator() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.hasVariants()).andReturn(true).anyTimes();
        EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        EasyMock.expect(mockRequest.getHeaders("Accept-Encoding"))
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
        extractor = new CacheKeyGenerator() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.hasVariants()).andReturn(true).anyTimes();
        EasyMock.expect(mockEntry.getHeaders("Vary")).andReturn(varyHeaders);
        EasyMock.expect(mockRequest.getHeaders("Accept-Encoding")).andReturn(
                encHeaders);
        EasyMock.expect(mockRequest.getHeaders("User-Agent")).andReturn(uaHeaders);
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
        extractor = new CacheKeyGenerator() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.hasVariants()).andReturn(true).anyTimes();
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
        extractor = new CacheKeyGenerator() {
            @Override
            public String getURI(HttpHost h, HttpRequest req) {
                Assert.assertSame(host, h);
                Assert.assertSame(mockRequest, req);
                return theURI;
            }
        };
        EasyMock.expect(mockEntry.hasVariants()).andReturn(true).anyTimes();
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

    /*
     * "When comparing two URIs to decide if they match or not, a client
     * SHOULD use a case-sensitive octet-by-octet comparison of the entire
     * URIs, with these exceptions:
     * - A port that is empty or not given is equivalent to the default
     * port for that URI-reference;
     * - Comparisons of host names MUST be case-insensitive;
     * - Comparisons of scheme names MUST be case-insensitive;
     * - An empty abs_path is equivalent to an abs_path of "/".
     * Characters other than those in the 'reserved' and 'unsafe' sets
     * (see RFC 2396 [42]) are equivalent to their '"%" HEX HEX' encoding."
     *
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.2.3
     */
    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttp() {
        HttpHost host1 = new HttpHost("foo.example.com:");
        HttpHost host2 = new HttpHost("foo.example.com:80");
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Assert.assertEquals(extractor.getURI(host1, req), extractor.getURI(host2, req));
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttps() {
        HttpHost host1 = new HttpHost("foo.example.com", -1, "https");
        HttpHost host2 = new HttpHost("foo.example.com", 443, "https");
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final String uri1 = extractor.getURI(host1, req);
        final String uri2 = extractor.getURI(host2, req);
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttpsAbsoluteURI() {
        HttpHost host = new HttpHost("foo.example.com", -1, "https");
        HttpGet get1 = new HttpGet("https://bar.example.com:/");
        HttpGet get2 = new HttpGet("https://bar.example.com:443/");
        final String uri1 = extractor.getURI(host, get1);
        final String uri2 = extractor.getURI(host, get2);
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testNotProvidedPortEquivalentToDefaultPortForHttpsAbsoluteURI() {
        HttpHost host = new HttpHost("foo.example.com", -1, "https");
        HttpGet get1 = new HttpGet("https://bar.example.com/");
        HttpGet get2 = new HttpGet("https://bar.example.com:443/");
        final String uri1 = extractor.getURI(host, get1);
        final String uri2 = extractor.getURI(host, get2);
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testNotProvidedPortEquivalentToDefaultPortForHttp() {
        HttpHost host1 = new HttpHost("foo.example.com");
        HttpHost host2 = new HttpHost("foo.example.com:80");
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Assert.assertEquals(extractor.getURI(host1, req), extractor.getURI(host2, req));
    }

    @Test
    public void testHostNameComparisonsAreCaseInsensitive() {
        HttpHost host1 = new HttpHost("foo.example.com");
        HttpHost host2 = new HttpHost("FOO.EXAMPLE.COM");
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Assert.assertEquals(extractor.getURI(host1, req), extractor.getURI(host2, req));
    }

    @Test
    public void testSchemeNameComparisonsAreCaseInsensitive() {
        HttpHost host1 = new HttpHost("foo.example.com", -1, "http");
        HttpHost host2 = new HttpHost("foo.example.com", -1, "HTTP");
        HttpRequest req = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Assert.assertEquals(extractor.getURI(host1, req), extractor.getURI(host2, req));
    }

    @Test
    public void testEmptyAbsPathIsEquivalentToSlash() {
        HttpHost host = new HttpHost("foo.example.com");
        HttpRequest req1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        HttpRequest req2 = new HttpGet("http://foo.example.com");
        Assert.assertEquals(extractor.getURI(host, req1), extractor.getURI(host, req2));
    }

    @Test
    public void testEquivalentPathEncodingsAreEquivalent() {
        HttpHost host = new HttpHost("foo.example.com");
        HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html", HttpVersion.HTTP_1_1);
        HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/home.html", HttpVersion.HTTP_1_1);
        Assert.assertEquals(extractor.getURI(host, req1), extractor.getURI(host, req2));
    }

    @Test
    public void testEquivalentExtraPathEncodingsAreEquivalent() {
        HttpHost host = new HttpHost("foo.example.com");
        HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html", HttpVersion.HTTP_1_1);
        HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith%2Fhome.html", HttpVersion.HTTP_1_1);
        Assert.assertEquals(extractor.getURI(host, req1), extractor.getURI(host, req2));
    }
}
