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

import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestCacheStatusHeaderGenerator {

    private final CacheStatusHeaderGenerator generator = CacheStatusHeaderGenerator.INSTANCE;

    private String value(final CacheStatus status) {
        final Header header = generator.generate(status);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("Cache-Status", header.getName());
        return header.getValue();
    }

    @Test
    void testHit() {
        final CacheStatus status = new CacheStatus();
        status.hit();
        Assertions.assertEquals("Apache-HttpClient; hit", value(status));
    }

    @Test
    void testForwardMiss() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.MISS);
        Assertions.assertEquals("Apache-HttpClient; fwd=miss", value(status));
    }

    @Test
    void testForwardRequest() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.REQUEST);
        Assertions.assertEquals("Apache-HttpClient; fwd=request", value(status));
    }

    @Test
    void testForwardStaleWithUpstreamStatus() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.STALE);
        status.forwardStatus(304);
        Assertions.assertEquals("Apache-HttpClient; fwd=stale; fwd-status=304", value(status));
    }

    @Test
    void testForwardUriMiss() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.URI_MISS);
        Assertions.assertEquals("Apache-HttpClient; fwd=uri-miss", value(status));
    }

    @Test
    void testForwardVaryMissWithUpstreamStatus() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.VARY_MISS);
        status.forwardStatus(304);
        Assertions.assertEquals("Apache-HttpClient; fwd=vary-miss; fwd-status=304", value(status));
    }

    @Test
    void testForwardBypass() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.BYPASS);
        Assertions.assertEquals("Apache-HttpClient; fwd=bypass", value(status));
    }

    @Test
    void testSuppressedYieldsNoHeader() {
        final CacheStatus status = new CacheStatus();
        status.forward(CacheStatus.ForwardReason.MISS);
        status.suppress();
        Assertions.assertNull(generator.generate(status));
    }

    @Test
    void testUnrecordedYieldsNoHeader() {
        Assertions.assertNull(generator.generate(new CacheStatus()));
    }

    @Test
    void testNullYieldsNoHeader() {
        Assertions.assertNull(generator.generate(null));
    }

}
