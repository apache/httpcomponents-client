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

import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.hc.client5.http.HeaderMatcher;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CacheControlGeneratorTest {

    private final CacheControlHeaderGenerator generator = CacheControlHeaderGenerator.INSTANCE;

    @Test
    public void testGenerateRequestCacheControlHeader() {
        assertThat(generator.generate(
                        RequestCacheControl.builder()
                                .setMaxAge(12)
                                .setMaxStale(23)
                                .setMinFresh(34)
                                .setNoCache(true)
                                .setNoStore(true)
                                .setOnlyIfCached(true)
                                .setStaleIfError(56)
                                .build()),
                HeaderMatcher.same("Cache-Control", "max-age=12, max-stale=23, " +
                        "min-fresh=34, no-cache, no-store, only-if-cached, stale-if-error=56"));
        assertThat(generator.generate(
                        RequestCacheControl.builder()
                                .setMaxAge(12)
                                .setNoCache(true)
                                .setMinFresh(34)
                                .setMaxStale(23)
                                .setNoStore(true)
                                .setStaleIfError(56)
                                .setOnlyIfCached(true)
                                .build()),
                HeaderMatcher.same("Cache-Control", "max-age=12, max-stale=23, " +
                        "min-fresh=34, no-cache, no-store, only-if-cached, stale-if-error=56"));
        assertThat(generator.generate(
                        RequestCacheControl.builder()
                                .setMaxAge(0)
                                .build()),
                HeaderMatcher.same("Cache-Control", "max-age=0"));
        assertThat(generator.generate(
                        RequestCacheControl.builder()
                                .setMaxAge(-1)
                                .setMinFresh(10)
                                .build()),
                HeaderMatcher.same("Cache-Control", "min-fresh=10"));
    }

    @Test
    public void testGenerateRequestCacheControlHeaderNoDirectives() {
        final RequestCacheControl cacheControl = RequestCacheControl.builder()
                .build();
        Assertions.assertNull(generator.generate(cacheControl));
    }

}
