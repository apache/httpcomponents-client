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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHeaderIterator;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"boxing","static-access"}) // this is test code
public class TestCacheKeyGenerator {

    private CacheKeyGenerator extractor;

    @BeforeEach
    public void setUp() throws Exception {
        extractor = CacheKeyGenerator.INSTANCE;
    }

    @Test
    public void testGetRequestUri() {
        Assertions.assertEquals("http://foo.example.com/stuff?huh",
                CacheKeyGenerator.getRequestUri(
                        new HttpHost("bar.example.com"),
                        new HttpGet("http://foo.example.com/stuff?huh")));

        Assertions.assertEquals("http://bar.example.com/stuff?huh",
                CacheKeyGenerator.getRequestUri(
                        new HttpHost("bar.example.com"),
                        new HttpGet("/stuff?huh")));

        Assertions.assertEquals("http://foo.example.com:8888/stuff?huh",
                CacheKeyGenerator.getRequestUri(
                        new HttpHost("bar.example.com", 8080),
                        new HttpGet("http://foo.example.com:8888/stuff?huh")));

        Assertions.assertEquals("https://bar.example.com:8443/stuff?huh",
                CacheKeyGenerator.getRequestUri(
                        new HttpHost("https", "bar.example.com", 8443),
                        new HttpGet("/stuff?huh")));

        Assertions.assertEquals("http://foo.example.com/",
                CacheKeyGenerator.getRequestUri(
                        new HttpHost("bar.example.com"),
                        new HttpGet("http://foo.example.com")));

        Assertions.assertEquals("http://bar.example.com/stuff?huh",
                CacheKeyGenerator.getRequestUri(
                        new HttpHost("bar.example.com"),
                        new HttpGet("stuff?huh")));
    }

    @Test
    public void testNormalizeRequestUri() throws URISyntaxException {
        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheKeyGenerator.normalize(URI.create("//bar.example.com/stuff?huh")));

        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheKeyGenerator.normalize(URI.create("http://bar.example.com/stuff?huh")));

        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheKeyGenerator.normalize(URI.create("http://bar.example.com/stuff?huh#there")));

        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheKeyGenerator.normalize(URI.create("HTTP://BAR.example.com/p1/p2/../../stuff?huh")));
    }

    @Test
    public void testExtractsUriFromAbsoluteUriInRequest() {
        final HttpHost host = new HttpHost("bar.example.com");
        final HttpRequest req = new HttpGet("http://foo.example.com/");
        Assertions.assertEquals("http://foo.example.com:80/", extractor.generateKey(host, req));
    }

    @Test
    public void testGetURIWithDefaultPortAndScheme() {
        Assertions.assertEquals("http://www.comcast.net:80/", extractor.generateKey(
                new HttpHost("www.comcast.net"),
                new BasicHttpRequest("GET", "/")));

        Assertions.assertEquals("http://www.fancast.com:80/full_episodes", extractor.generateKey(
                new HttpHost("www.fancast.com"),
                new BasicHttpRequest("GET", "/full_episodes")));
    }

    @Test
    public void testGetURIWithDifferentScheme() {
        Assertions.assertEquals("https://www.comcast.net:443/", extractor.generateKey(
                new HttpHost("https", "www.comcast.net", -1),
                new BasicHttpRequest("GET", "/")));

        Assertions.assertEquals("myhttp://www.fancast.com/full_episodes", extractor.generateKey(
                new HttpHost("myhttp", "www.fancast.com", -1),
                new BasicHttpRequest("GET", "/full_episodes")));
    }

    @Test
    public void testGetURIWithDifferentPort() {
        Assertions.assertEquals("http://www.comcast.net:8080/", extractor.generateKey(
                new HttpHost("www.comcast.net", 8080),
                new BasicHttpRequest("GET", "/")));

        Assertions.assertEquals("http://www.fancast.com:9999/full_episodes", extractor.generateKey(
                new HttpHost("www.fancast.com", 9999),
                new BasicHttpRequest("GET", "/full_episodes")));
    }

    @Test
    public void testGetURIWithDifferentPortAndScheme() {
        Assertions.assertEquals("https://www.comcast.net:8080/", extractor.generateKey(
                new HttpHost("https", "www.comcast.net", 8080),
                new BasicHttpRequest("GET", "/")));

        Assertions.assertEquals("myhttp://www.fancast.com:9999/full_episodes", extractor.generateKey(
                new HttpHost("myhttp", "www.fancast.com", 9999),
                new BasicHttpRequest("GET", "/full_episodes")));
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttp() {
        final HttpHost host1 = new HttpHost("foo.example.com:");
        final HttpHost host2 = new HttpHost("foo.example.com:80");
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assertions.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttps() {
        final HttpHost host1 = new HttpHost("https", "foo.example.com", -1);
        final HttpHost host2 = new HttpHost("https", "foo.example.com", 443);
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        final String uri1 = extractor.generateKey(host1, req);
        final String uri2 = extractor.generateKey(host2, req);
        Assertions.assertEquals(uri1, uri2);
    }

    @Test
    public void testEmptyPortEquivalentToDefaultPortForHttpsAbsoluteURI() {
        final HttpHost host = new HttpHost("https", "foo.example.com", -1);
        final HttpGet get1 = new HttpGet("https://bar.example.com:/");
        final HttpGet get2 = new HttpGet("https://bar.example.com:443/");
        final String uri1 = extractor.generateKey(host, get1);
        final String uri2 = extractor.generateKey(host, get2);
        Assertions.assertEquals(uri1, uri2);
    }

    @Test
    public void testNotProvidedPortEquivalentToDefaultPortForHttpsAbsoluteURI() {
        final HttpHost host = new HttpHost("https", "foo.example.com", -1);
        final HttpGet get1 = new HttpGet("https://bar.example.com/");
        final HttpGet get2 = new HttpGet("https://bar.example.com:443/");
        final String uri1 = extractor.generateKey(host, get1);
        final String uri2 = extractor.generateKey(host, get2);
        Assertions.assertEquals(uri1, uri2);
    }

    @Test
    public void testNotProvidedPortEquivalentToDefaultPortForHttp() {
        final HttpHost host1 = new HttpHost("foo.example.com");
        final HttpHost host2 = new HttpHost("foo.example.com:80");
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assertions.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testHostNameComparisonsAreCaseInsensitive() {
        final HttpHost host1 = new HttpHost("foo.example.com");
        final HttpHost host2 = new HttpHost("FOO.EXAMPLE.COM");
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assertions.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testSchemeNameComparisonsAreCaseInsensitive() {
        final HttpHost host1 = new HttpHost("http", "foo.example.com", -1);
        final HttpHost host2 = new HttpHost("HTTP", "foo.example.com", -1);
        final HttpRequest req = new BasicHttpRequest("GET", "/");
        Assertions.assertEquals(extractor.generateKey(host1, req), extractor.generateKey(host2, req));
    }

    @Test
    public void testEmptyAbsPathIsEquivalentToSlash() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/");
        final HttpRequest req2 = new HttpGet("http://foo.example.com");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testExtraDotSegmentsAreIgnored() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/");
        final HttpRequest req2 = new HttpGet("http://foo.example.com/./");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testExtraDotDotSegmentsAreIgnored() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/");
        final HttpRequest req2 = new HttpGet("http://foo.example.com/.././../");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testIntermidateDotDotSegementsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/../home.html");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testIntermidateEncodedDotDotSegementsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/../home.html");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testIntermidateDotSegementsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/./home.html");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testEquivalentPathEncodingsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/home.html");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testEquivalentExtraPathEncodingsAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/home.html");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testEquivalentExtraPathEncodingsWithPercentAreEquivalent() {
        final HttpHost host = new HttpHost("foo.example.com");
        final HttpRequest req1 = new BasicHttpRequest("GET", "/~smith/home%20folder.html");
        final HttpRequest req2 = new BasicHttpRequest("GET", "/%7Esmith/home%20folder.html");
        Assertions.assertEquals(extractor.generateKey(host, req1), extractor.generateKey(host, req2));
    }

    @Test
    public void testGetURIWithQueryParameters() {
        Assertions.assertEquals("http://www.comcast.net:80/?foo=bar", extractor.generateKey(
                new HttpHost("http", "www.comcast.net", -1), new BasicHttpRequest("GET", "/?foo=bar")));
        Assertions.assertEquals("http://www.fancast.com:80/full_episodes?foo=bar", extractor.generateKey(
                new HttpHost("http", "www.fancast.com", -1), new BasicHttpRequest("GET",
                        "/full_episodes?foo=bar")));
    }

    private static Iterator<Header> headers(final Header... headers) {
        return new BasicHeaderIterator(headers, null);
    }

    @Test
    public void testNormalizeHeaderElements() {
        final List<String> tokens = new ArrayList<>();
        CacheKeyGenerator.normalizeElements(headers(
                new BasicHeader("Accept-Encoding", "gzip,zip,deflate")
        ), tokens::add);
        Assertions.assertEquals(Arrays.asList("deflate", "gzip", "zip"), tokens);

        tokens.clear();
        CacheKeyGenerator.normalizeElements(headers(
                new BasicHeader("Accept-Encoding", "  gZip  , Zip,  ,  ,  deflate  ")
        ), tokens::add);
        Assertions.assertEquals(Arrays.asList("deflate", "gzip", "zip"), tokens);

        tokens.clear();
        CacheKeyGenerator.normalizeElements(headers(
                new BasicHeader("Accept-Encoding", "gZip,Zip,,"),
                new BasicHeader("Accept-Encoding", "   gZip,Zip,,,"),
                new BasicHeader("Accept-Encoding", "gZip,  ,,,deflate")
        ), tokens::add);
        Assertions.assertEquals(Arrays.asList("deflate", "gzip", "zip"), tokens);

        tokens.clear();
        CacheKeyGenerator.normalizeElements(headers(
                new BasicHeader("Cookie", "name1 = value1 ;   p1 = v1 ; P2   = \"v2\""),
                new BasicHeader("Cookie", "name3;;;"),
                new BasicHeader("Cookie", "   name2 = \" value 2 \"   ; ; ; ,,,")
        ), tokens::add);
        Assertions.assertEquals(Arrays.asList("name1=value1;p1=v1;p2=v2", "name2=\" value 2 \"", "name3"), tokens);
    }

    @Test
    public void testGetVariantKey() {
        final HttpRequest request = BasicRequestBuilder.get("/blah")
                .addHeader(HttpHeaders.USER_AGENT, "some-agent")
                .addHeader(HttpHeaders.ACCEPT_ENCODING, "gzip,zip")
                .addHeader(HttpHeaders.ACCEPT_ENCODING, "deflate")
                .build();

        Assertions.assertEquals("{user-agent=some-agent}",
                extractor.generateVariantKey(request, Collections.singletonList(HttpHeaders.USER_AGENT)));
        Assertions.assertEquals("{accept-encoding=deflate,gzip,zip}",
                extractor.generateVariantKey(request, Collections.singletonList(HttpHeaders.ACCEPT_ENCODING)));
        Assertions.assertEquals("{accept-encoding=deflate,gzip,zip&user-agent=some-agent}",
                extractor.generateVariantKey(request, Arrays.asList(HttpHeaders.USER_AGENT, HttpHeaders.ACCEPT_ENCODING)));
    }

    @Test
    public void testGetVariantKeyInputNormalization() {
        final HttpRequest request = BasicRequestBuilder.get("/blah")
                .addHeader(HttpHeaders.USER_AGENT, "Some-Agent")
                .addHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, ZIP,,")
                .addHeader(HttpHeaders.ACCEPT_ENCODING, "deflate")
                .build();

        Assertions.assertEquals("{user-agent=some-agent}",
                extractor.generateVariantKey(request, Collections.singletonList(HttpHeaders.USER_AGENT)));
        Assertions.assertEquals("{accept-encoding=deflate,gzip,zip}",
                extractor.generateVariantKey(request, Collections.singletonList(HttpHeaders.ACCEPT_ENCODING)));
        Assertions.assertEquals("{accept-encoding=deflate,gzip,zip&user-agent=some-agent}",
                extractor.generateVariantKey(request, Arrays.asList(HttpHeaders.USER_AGENT, HttpHeaders.ACCEPT_ENCODING)));
        Assertions.assertEquals("{accept-encoding=deflate,gzip,zip&user-agent=some-agent}",
                extractor.generateVariantKey(request, Arrays.asList(HttpHeaders.USER_AGENT, HttpHeaders.ACCEPT_ENCODING, "USER-AGENT", HttpHeaders.ACCEPT_ENCODING)));
    }

    @Test
    public void testGetVariantKeyInputNormalizationReservedChars() {
        final HttpRequest request = BasicRequestBuilder.get("/blah")
                .addHeader(HttpHeaders.USER_AGENT, "*===some-agent===*")
                .build();

        Assertions.assertEquals("{user-agent=%2A%3D%3D%3Dsome-agent%3D%3D%3D%2A}",
                extractor.generateVariantKey(request, Collections.singletonList(HttpHeaders.USER_AGENT)));
    }

    @Test
    public void testGetVariantKeyInputNoMatchingHeaders() {
        final HttpRequest request = BasicRequestBuilder.get("/blah")
                .build();

        Assertions.assertEquals("{accept-encoding=&user-agent=}",
                extractor.generateVariantKey(request, Arrays.asList(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.USER_AGENT)));
    }

    @Test
    public void testGetVariantKeyFromCachedResponse() {
        final HttpRequest request = BasicRequestBuilder.get("/blah")
                .addHeader("User-Agent", "agent1")
                .addHeader("Accept-Encoding", "text/plain")
                .build();

        final HttpCacheEntry entry1 = HttpTestUtils.makeCacheEntry();
        Assertions.assertNull(extractor.generateVariantKey(request, entry1));

        final HttpCacheEntry entry2 = HttpTestUtils.makeCacheEntry(
                new BasicHeader("Vary", "User-Agent, Accept-Encoding")
        );
        Assertions.assertEquals("{accept-encoding=text%2Fplain&user-agent=agent1}", extractor.generateVariantKey(request, entry2));
    }

}
