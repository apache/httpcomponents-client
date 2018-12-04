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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHeaderIterator;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"boxing","static-access"}) // this is test code
public class TestCacheKeyGenerator {

    private static final BasicHttpRequest REQUEST_FULL_EPISODES = new BasicHttpRequest("GET",
            "/full_episodes");
    private static final BasicHttpRequest REQUEST_ROOT = new BasicHttpRequest("GET", "/");

    private CacheKeyGenerator extractor;
    private HttpHost defaultHost;
    private HttpCacheEntry mockEntry;
    private HttpRequest mockRequest;

    @Before
    public void setUp() throws Exception {
        defaultHost = new HttpHost("foo.example.com");
        mockEntry = mock(HttpCacheEntry.class);
        mockRequest = mock(HttpRequest.class);
        extractor = CacheKeyGenerator.INSTANCE;
    }

    @Test
    public void testExtractsUriFromAbsoluteUriInRequest() {
        final HttpHost host = new HttpHost("bar.example.com");
        final HttpRequest req = new HttpGet("http://foo.example.com/");
        Assert.assertEquals("http://foo.example.com:80/", extractor.generateKey(host, req));
    }

    @Test
    public void testGetURIWithDefaultPortAndScheme() {
        Assert.assertEquals("http://www.comcast.net:80/", extractor.generateKey(new HttpHost(
                "www.comcast.net"), REQUEST_ROOT));

        Assert.assertEquals("http://www.fancast.com:80/full_episodes", extractor.generateKey(new HttpHost(
                "www.fancast.com"), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentScheme() {
        Assert.assertEquals("https://www.comcast.net:443/", extractor.generateKey(
                new HttpHost("https", "www.comcast.net", -1), REQUEST_ROOT));

        Assert.assertEquals("myhttp://www.fancast.com/full_episodes", extractor.generateKey(
                new HttpHost("myhttp", "www.fancast.com", -1), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentPort() {
        Assert.assertEquals("http://www.comcast.net:8080/", extractor.generateKey(new HttpHost(
                "www.comcast.net", 8080), REQUEST_ROOT));

        Assert.assertEquals("http://www.fancast.com:9999/full_episodes", extractor.generateKey(
                new HttpHost("www.fancast.com", 9999), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithDifferentPortAndScheme() {
        Assert.assertEquals("https://www.comcast.net:8080/", extractor.generateKey(
                new HttpHost("https", "www.comcast.net", 8080), REQUEST_ROOT));

        Assert.assertEquals("myhttp://www.fancast.com:9999/full_episodes", extractor.generateKey(
                new HttpHost("myhttp", "www.fancast.com", 9999), REQUEST_FULL_EPISODES));
    }

    @Test
    public void testGetURIWithQueryParameters() {
        Assert.assertEquals("http://www.comcast.net:80/?foo=bar", extractor.generateKey(
                new HttpHost("http", "www.comcast.net", -1), new BasicHttpRequest("GET", "/?foo=bar")));
        Assert.assertEquals("http://www.fancast.com:80/full_episodes?foo=bar", extractor.generateKey(
                new HttpHost("http", "www.fancast.com", -1), new BasicHttpRequest("GET",
                        "/full_episodes?foo=bar")));
    }

    @Test
    public void testGetVariantURIWithNoVaryHeaderReturnsNormalURI() {
        final String theURI = "theURI";
        when(mockEntry.hasVariants()).thenReturn(false);
        extractor = new CacheKeyGenerator() {
            @Override
            public String generateKey(final HttpHost h, final HttpRequest request) {
                Assert.assertSame(defaultHost, h);
                Assert.assertSame(mockRequest, request);
                return theURI;
            }
        };

        final String result = extractor.generateKey(defaultHost, mockRequest, mockEntry);
        verify(mockEntry).hasVariants();
        Assert.assertSame(theURI, result);
    }

    @Test
    public void testGetVariantURIWithSingleValueVaryHeaderPrepends() {
        final String theURI = "theURI";
        final Header[] varyHeaders = { new BasicHeader("Vary", "Accept-Encoding") };
        final Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip") };

        extractor = new CacheKeyGenerator() {
            @Override
            public String generateKey(final HttpHost h, final HttpRequest request) {
                Assert.assertSame(defaultHost, h);
                Assert.assertSame(mockRequest, request);
                return theURI;
            }
        };
        when(mockEntry.hasVariants()).thenReturn(true);
        when(mockEntry.headerIterator("Vary")).thenReturn(new BasicHeaderIterator(varyHeaders, "Vary"));
        when(mockRequest.getHeaders("Accept-Encoding")).thenReturn(encHeaders);

        final String result = extractor.generateKey(defaultHost, mockRequest, mockEntry);

        verify(mockEntry).hasVariants();
        verify(mockEntry).headerIterator("Vary");
        verify(mockRequest).getHeaders("Accept-Encoding");
        Assert.assertEquals("{Accept-Encoding=gzip}" + theURI, result);
    }

    @Test
    public void testGetVariantURIWithMissingRequestHeader() {
        final String theURI = "theURI";
        final Header[] noHeaders = new Header[0];
        final Header[] varyHeaders = { new BasicHeader("Vary", "Accept-Encoding") };
        extractor = new CacheKeyGenerator() {
            @Override
            public String generateKey(final HttpHost h, final HttpRequest request) {
                Assert.assertSame(defaultHost, h);
                Assert.assertSame(mockRequest, request);
                return theURI;
            }
        };
        when(mockEntry.hasVariants()).thenReturn(true);
        when(mockEntry.headerIterator("Vary")).thenReturn(new BasicHeaderIterator(varyHeaders, "Vary"));
        when(mockRequest.getHeaders("Accept-Encoding"))
                .thenReturn(noHeaders);

        final String result = extractor.generateKey(defaultHost, mockRequest, mockEntry);

        verify(mockEntry).hasVariants();
        verify(mockEntry).headerIterator("Vary");
        verify(mockRequest).getHeaders("Accept-Encoding");
        Assert.assertEquals("{Accept-Encoding=}" + theURI, result);
    }

    @Test
    public void testGetVariantURIAlphabetizesWithMultipleVaryingHeaders() {
        final String theURI = "theURI";
        final Header[] varyHeaders = { new BasicHeader("Vary", "User-Agent, Accept-Encoding") };
        final Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip") };
        final Header[] uaHeaders = { new BasicHeader("User-Agent", "browser") };
        extractor = new CacheKeyGenerator() {
            @Override
            public String generateKey(final HttpHost h, final HttpRequest request) {
                Assert.assertSame(defaultHost, h);
                Assert.assertSame(mockRequest, request);
                return theURI;
            }
        };
        when(mockEntry.hasVariants()).thenReturn(true);
        when(mockEntry.headerIterator("Vary")).thenReturn(new BasicHeaderIterator(varyHeaders, "Vary"));
        when(mockRequest.getHeaders("Accept-Encoding")).thenReturn(encHeaders);
        when(mockRequest.getHeaders("User-Agent")).thenReturn(uaHeaders);

        final String result = extractor.generateKey(defaultHost, mockRequest, mockEntry);

        verify(mockEntry).hasVariants();
        verify(mockEntry).headerIterator("Vary");
        verify(mockRequest).getHeaders("Accept-Encoding");
        verify(mockRequest).getHeaders("User-Agent");
        Assert.assertEquals("{Accept-Encoding=gzip&User-Agent=browser}" + theURI, result);
    }

    @Test
    public void testGetVariantURIHandlesMultipleVaryHeaders() {
        final String theURI = "theURI";
        final Header[] varyHeaders = { new BasicHeader("Vary", "User-Agent"),
                new BasicHeader("Vary", "Accept-Encoding") };
        final Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip") };
        final Header[] uaHeaders = { new BasicHeader("User-Agent", "browser") };
        extractor = new CacheKeyGenerator() {
            @Override
            public String generateKey(final HttpHost h, final HttpRequest request) {
                Assert.assertSame(defaultHost, h);
                Assert.assertSame(mockRequest, request);
                return theURI;
            }
        };
        when(mockEntry.hasVariants()).thenReturn(true);
        when(mockEntry.headerIterator("Vary")).thenReturn(new BasicHeaderIterator(varyHeaders, "Vary"));
        when(mockRequest.getHeaders("Accept-Encoding")).thenReturn(encHeaders);
        when(mockRequest.getHeaders("User-Agent")).thenReturn(uaHeaders);

        final String result = extractor.generateKey(defaultHost, mockRequest, mockEntry);

        verify(mockEntry).hasVariants();
        verify(mockEntry).headerIterator("Vary");
        verify(mockRequest).getHeaders("Accept-Encoding");
        verify(mockRequest).getHeaders("User-Agent");
        Assert.assertEquals("{Accept-Encoding=gzip&User-Agent=browser}" + theURI, result);
    }

    @Test
    public void testGetVariantURIHandlesMultipleLineRequestHeaders() {
        final String theURI = "theURI";
        final Header[] varyHeaders = { new BasicHeader("Vary", "User-Agent, Accept-Encoding") };
        final Header[] encHeaders = { new BasicHeader("Accept-Encoding", "gzip"),
                new BasicHeader("Accept-Encoding", "deflate") };
        final Header[] uaHeaders = { new BasicHeader("User-Agent", "browser") };
        extractor = new CacheKeyGenerator() {
            @Override
            public String generateKey(final HttpHost h, final HttpRequest request) {
                Assert.assertSame(defaultHost, h);
                Assert.assertSame(mockRequest, request);
                return theURI;
            }
        };
        when(mockEntry.hasVariants()).thenReturn(true);
        when(mockEntry.headerIterator("Vary")).thenReturn(new BasicHeaderIterator(varyHeaders, "Vary"));
        when(mockRequest.getHeaders("Accept-Encoding")).thenReturn(encHeaders);
        when(mockRequest.getHeaders("User-Agent")).thenReturn(uaHeaders);

        final String result = extractor.generateKey(defaultHost, mockRequest, mockEntry);

        verify(mockEntry).hasVariants();
        verify(mockEntry).headerIterator("Vary");
        verify(mockRequest).getHeaders("Accept-Encoding");
        verify(mockRequest).getHeaders("User-Agent");
        Assert.assertEquals("{Accept-Encoding=gzip%2C+deflate&User-Agent=browser}" + theURI, result);
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
        final HttpHost host1 = new HttpHost("foo.example.com:");
        final HttpHost host2 = new HttpHost("foo.example.com:80");
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assert.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttps() {
        final HttpHost host1 = new HttpHost("https", "foo.example.com", -1);
        final HttpHost host2 = new HttpHost("https", "foo.example.com", 443);
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        final String uri1 = extractor.generateKey(host1, req);
        final String uri2 = extractor.generateKey(host2, req);
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttpsAbsoluteURI() {
        final HttpHost host = new HttpHost("https", "foo.example.com", -1);
        final HttpGet get1 = new HttpGet("https://bar.example.com:/");
        final HttpGet get2 = new HttpGet("https://bar.example.com:443/");
        final String uri1 = extractor.generateKey(host, get1);
        final String uri2 = extractor.generateKey(host, get2);
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testNotProvidedPortEquivalentToDefaultPortForHttpsAbsoluteURI() {
        final HttpHost host = new HttpHost("https", "foo.example.com", -1);
        final HttpGet get1 = new HttpGet("https://bar.example.com/");
        final HttpGet get2 = new HttpGet("https://bar.example.com:443/");
        final String uri1 = extractor.generateKey(host, get1);
        final String uri2 = extractor.generateKey(host, get2);
        Assert.assertEquals(uri1, uri2);
    }

    @Test
    public void testNotProvidedPortEquivalentToDefaultPortForHttp() {
        final HttpHost host1 = new HttpHost("foo.example.com");
        final HttpHost host2 = new HttpHost("foo.example.com:80");
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assert.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testHostNameComparisonsAreCaseInsensitive() {
        final HttpHost host1 = new HttpHost("foo.example.com");
        final HttpHost host2 = new HttpHost("FOO.EXAMPLE.COM");
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assert.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testSchemeNameComparisonsAreCaseInsensitive() {
        final HttpHost host1 = new HttpHost("http", "foo.example.com", -1);
        final HttpHost host2 = new HttpHost("HTTP", "foo.example.com", -1);
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assert.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testEmptyAbsPathIsEquivalentToSlash() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/");
        final HttpRequest req2 = new HttpGet("http://foo.example.com");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testExtraDotSegmentsAreIgnored() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/");
        final HttpRequest req2 = new HttpGet("http://foo.example.com/./");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testExtraDotDotSegmentsAreIgnored() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/");
        final HttpRequest req2 = new HttpGet("http://foo.example.com/.././../");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testIntermidateDotDotSegementsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/../home.html");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testIntermidateEncodedDotDotSegementsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith%2F../home.html");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testIntermidateDotSegementsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/./home.html");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testEquivalentPathEncodingsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/home.html");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testEquivalentExtraPathEncodingsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith%2Fhome.html");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testEquivalentExtraPathEncodingsWithPercentAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home%20folder.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith%2Fhome%20folder.html");
        Assert.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }
}
