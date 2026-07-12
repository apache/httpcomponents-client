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

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CacheSupport}.
 */
class TestCacheSupport {

    @Test
    void testRequestUriRaw() throws Exception {
        Assertions.assertEquals("http://foo.example.com/stuff?huh",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com"),
                        BasicRequestBuilder.get("http://foo.example.com/stuff?huh").build()));

        Assertions.assertEquals("http://bar.example.com/stuff?huh",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com"),
                        BasicRequestBuilder.get("/stuff?huh").build()));

        Assertions.assertEquals("http://foo.example.com:8888/stuff?huh",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com", 8080),
                        BasicRequestBuilder.get("http://foo.example.com:8888/stuff?huh").build()));

        Assertions.assertEquals("https://bar.example.com:8443/stuff?huh",
                CacheSupport.requestUriRaw(
                        new HttpHost("https", "bar.example.com", 8443),
                        BasicRequestBuilder.get("/stuff?huh").build()));

        Assertions.assertEquals("http://foo.example.com/",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com"),
                        BasicRequestBuilder.get("http://foo.example.com").build()));

        Assertions.assertEquals("http://bar.example.com/stuff?huh",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com"),
                        BasicRequestBuilder.get("stuff?huh").build()));

        Assertions.assertEquals("http://bar.example.com/stuff",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com"),
                        BasicRequestBuilder.get("stuff#huh").build()));

        Assertions.assertEquals("http://bar.example.com/stuff",
                CacheSupport.requestUriRaw(
                        new HttpHost("bar.example.com"),
                        BasicRequestBuilder.get("stuff").build()));
    }

    @Test
    void testNormalizeRequestUri() throws URISyntaxException {
        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheSupport.normalize(URI.create("//bar.example.com/stuff?huh")));

        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheSupport.normalize(URI.create("http://bar.example.com/stuff?huh")));

        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheSupport.normalize(URI.create("http://bar.example.com/stuff?huh#there")));

        Assertions.assertEquals(URI.create("http://bar.example.com:80/stuff?huh"),
                CacheSupport.normalize(URI.create("HTTP://BAR.example.com/p1/p2/../../stuff?huh")));
    }

    @Test
    void testParseDeltaSeconds() {
        Assertions.assertEquals(1234L, CacheSupport.deltaSeconds("1234"));
        Assertions.assertEquals(0L, CacheSupport.deltaSeconds("0"));
        Assertions.assertEquals(-1L, CacheSupport.deltaSeconds("-1"));
        Assertions.assertEquals(-1L, CacheSupport.deltaSeconds("-100"));
        Assertions.assertEquals(-1L, CacheSupport.deltaSeconds(""));
        Assertions.assertEquals(-1L, CacheSupport.deltaSeconds(null));
        Assertions.assertEquals(0L, CacheSupport.deltaSeconds("huh?"));
        Assertions.assertEquals(2147483648L, CacheSupport.deltaSeconds("2147483648"));
        Assertions.assertEquals(2147483648L, CacheSupport.deltaSeconds("2147483649"));
        Assertions.assertEquals(2147483648L, CacheSupport.deltaSeconds("214748364712"));
    }

}
