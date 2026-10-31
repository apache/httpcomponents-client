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
package org.apache.hc.client5.http.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link RequestSupport}.
 */
class TestRequestSupport {

    @Test
    void testPathPrefixExtraction() {
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb")));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/")));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/../aaaa/")));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb/cccc")));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb/")));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa/bbbb?////")));
        Assertions.assertEquals("/aa%2Faa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aa%2faa/bbbb")));
        Assertions.assertEquals("/aa%2Faa/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/a%61%2fa%61/bbbb")));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/")));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "/aaaa")));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(new BasicHttpRequest("GET", "")));
    }

    @Test
    void testPathPrefixExtractionIgnoresQueryAndFragment() {
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bbbb?q=%E4%BD%A0%20x&r=a%2Fb&s=..%2F..&t"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bbbb?/../cccc/dddd/"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bbbb#cccc/dddd"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bbbb?x=1#cccc/dddd"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bbbb#cccc?x=1/2"));
        Assertions.assertEquals("/aaaa/bbbb/", extractPathPrefix("/aaaa/bbbb/?x=1"));
        Assertions.assertEquals("/aaaa/bbbb/", extractPathPrefix("/aaaa/bbbb/#x"));
        Assertions.assertEquals("/", extractPathPrefix("/?x=1/2/3"));
        Assertions.assertEquals("/", extractPathPrefix("?x=1/2/3"));
        Assertions.assertEquals("/aa%2Faa/", extractPathPrefix("/aa%2faa/bbbb?x=%2F"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bb%2Fbb?x=1"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("/aaaa/bbbb?q=a b"));
        Assertions.assertEquals("/aa%3Baa/", extractPathPrefix("/aa;aa/bbbb?q=a b"));
    }

    @Test
    void testPathPrefixExtractionNormalization() {
        Assertions.assertEquals("/a-b.c_d~e/F0/", extractPathPrefix("/a-b.c_d~e/F0/g"));
        Assertions.assertEquals("/...a/b../", extractPathPrefix("/...a/b../c"));
        Assertions.assertEquals("/aaaa/bbbb/", extractPathPrefix("/aaaa/./bbbb/cccc"));
        Assertions.assertEquals("/aaaa/bbbb/", extractPathPrefix("/aaaa//bbbb/cccc"));
        Assertions.assertEquals("/bbbb/", extractPathPrefix("/aaaa/%2E%2E/bbbb/cccc"));
        Assertions.assertEquals("/aaaa%3Bv%3D1/", extractPathPrefix("/aaaa;v=1/bbbb;w=2"));
        Assertions.assertEquals("/aaaa%3Abbbb%40cccc/", extractPathPrefix("/aaaa:bbbb@cccc/dddd"));
        Assertions.assertEquals("/aaaa%20bbbb/", extractPathPrefix("/aaaa%20bbbb/cccc"));
        Assertions.assertEquals("/aaaa/", extractPathPrefix("http://host/aaaa/bbbb?x=1"));
        Assertions.assertEquals("/", extractPathPrefix("*"));
    }

    /**
     * Compares {@link RequestSupport#extractPathPrefix(HttpRequest)} with the implementation that
     * parsed the request path including the query and the fragment.
     */
    @Test
    void testPathPrefixExtractionMatchesFullUriParsing() {
        final String[] tokens = {
                "/", "/", "/", "//", "a", "Bc", "0", "-", "_", "~", ".", "..", "...",
                "%2F", "%2f", "%61", "%2E", "%20", "%3B", "%C3%A9", "%", "%G1",
                ";", "=", ":", "@", "!", "$", "+", ",", "*", "'", "(", " ", "|", "é", "?", "#", "&"
        };
        final Random random = new Random(42);
        for (int n = 0; n < 20000; n++) {
            final StringBuilder buf = new StringBuilder();
            if (random.nextInt(10) > 0) {
                buf.append('/');
            }
            final int count = random.nextInt(12);
            for (int i = 0; i < count; i++) {
                buf.append(tokens[random.nextInt(tokens.length)]);
            }
            final String path = buf.toString();
            if (isValidUri(path) || !isValidUri(path.split("[?#]", 2)[0])) {
                Assertions.assertEquals(extractPathPrefixFromUri(path), extractPathPrefix(path), path);
            }
        }
    }

    private static String extractPathPrefix(final String path) {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.setPath(path);
        return RequestSupport.extractPathPrefix(request);
    }

    private static boolean isValidUri(final String s) {
        try {
            new URI(s);
            return true;
        } catch (final URISyntaxException ex) {
            return false;
        }
    }

    private static String extractPathPrefixFromUri(final String path) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(path);
            uriBuilder.setFragment(null);
            uriBuilder.clearParameters();
            uriBuilder.optimize();
            final List<String> pathSegments = uriBuilder.getPathSegments();

            if (!pathSegments.isEmpty()) {
                pathSegments.remove(pathSegments.size() - 1);
            }
            if (pathSegments.isEmpty()) {
                return "/";
            }
            final StringBuilder buf = new StringBuilder();
            buf.append('/');
            for (final String pathSegment : pathSegments) {
                PercentCodec.encode(buf, pathSegment, StandardCharsets.US_ASCII);
                buf.append('/');
            }
            return buf.toString();
        } catch (final URISyntaxException ex) {
            return path;
        }
    }

}
