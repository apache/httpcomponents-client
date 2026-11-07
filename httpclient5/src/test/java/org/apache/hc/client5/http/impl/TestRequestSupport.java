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

import org.apache.hc.core5.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link RequestSupport}.
 */
class TestRequestSupport {

    @Test
    void testPathPrefixExtraction() {
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("/aaaa/bbbb"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("/aaaa/"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("/aaaa/../aaaa/"));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix("/aaaa/bbbb/cccc"));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix("/aaaa/bbbb/"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("/aaaa/bbbb/.."));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("/aaaa/bbbb?////"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("/aaaa//bbbb"));
        Assertions.assertEquals("/aa%2Faa/", RequestSupport.extractPathPrefix("/aa%2faa/bbbb"));
        Assertions.assertEquals("/aa%2Faa/", RequestSupport.extractPathPrefix("/a%61%2fa%61/bbbb"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("aaaa/bbbb"));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix("aaaa"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("aaaa/bbbb"));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix("/"));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix("/aaaa"));
        Assertions.assertEquals("/", RequestSupport.extractPathPrefix(""));
    }

    @Test
    void testPathPrefixExtractionDots() {
        Assertions.assertEquals("/aaaa/b.b/", RequestSupport.extractPathPrefix("/aaaa/b.b/cccc"));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix("/aaaa/bbbb/cc.cc"));
        Assertions.assertEquals("/a.a/b..b/", RequestSupport.extractPathPrefix("/a.a/b..b/c."));
        Assertions.assertEquals("/aaaa/.bbbb/", RequestSupport.extractPathPrefix("/aaaa/.bbbb/cccc"));
        Assertions.assertEquals("/aaaa/.../", RequestSupport.extractPathPrefix("/aaaa/.../cccc"));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix("/aaaa/./bbbb/cccc"));
        Assertions.assertEquals("/aaaa/bbbb/", RequestSupport.extractPathPrefix("/aaaa/bbbb/."));
        Assertions.assertEquals("/cccc/", RequestSupport.extractPathPrefix("/aaaa/%2E%2E/cccc/dddd"));
        Assertions.assertEquals("/aaaa/", RequestSupport.extractPathPrefix("../aaaa/bbbb"));
        Assertions.assertEquals("/foo.bar/", RequestSupport.extractPathPrefix("foo.bar/baz"));
    }

    @Test
    void testPathPrefixExtractor() {
        final Supplier<String> pathPrefixExtractor = RequestSupport.pathPrefixSupplier("/aaaa/bbbb");
        final String pathPrefix = pathPrefixExtractor.get();
        Assertions.assertEquals("/aaaa/", pathPrefix);
        Assertions.assertSame(pathPrefix, pathPrefixExtractor.get());
        Assertions.assertSame(pathPrefix, pathPrefixExtractor.get());
    }

}
