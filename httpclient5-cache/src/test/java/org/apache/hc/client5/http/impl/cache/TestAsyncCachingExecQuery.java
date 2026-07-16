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
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestAsyncCachingExecQuery {

    private AsyncCachingExec impl;

    @BeforeEach
    void setUp() {
        final HttpAsyncCache cache = new BasicHttpAsyncCache(
                HeapResourceFactory.INSTANCE, new SimpleHttpAsyncCacheStorage());
        impl = new AsyncCachingExec(cache, null, CacheConfig.DEFAULT);
    }

    @Test
    void testPrepareRequestBuffersQueryContentFromRepeatableProducer() throws Exception {
        final AsyncEntityProducer producer = new BasicAsyncEntityProducer(
                "select a".getBytes(StandardCharsets.UTF_8), ContentType.TEXT_PLAIN);

        final SimpleHttpRequest converted = impl.prepareRequest(
                new BasicHttpRequest(Method.QUERY, "/stuff"), producer);

        Assertions.assertNotNull(converted);
        Assertions.assertNotNull(converted.getBody());
        Assertions.assertEquals("select a",
                new String(converted.getBody().getBodyBytes(), StandardCharsets.UTF_8));
        Assertions.assertEquals(ContentType.TEXT_PLAIN.getMimeType(),
                converted.getBody().getContentType().getMimeType());
    }

    @Test
    void testPrepareRequestBypassesNonQueryRequestsWithContent() throws Exception {
        final AsyncEntityProducer producer = new BasicAsyncEntityProducer(
                "stuff".getBytes(StandardCharsets.UTF_8), ContentType.TEXT_PLAIN);

        Assertions.assertNull(impl.prepareRequest(new BasicHttpRequest(Method.POST, "/stuff"), producer));
    }

    @Test
    void testPrepareRequestBypassesQueryWithNonRepeatableProducer() throws Exception {
        final StallingEntityProducer producer = new StallingEntityProducer(false);

        Assertions.assertNull(impl.prepareRequest(new BasicHttpRequest(Method.QUERY, "/stuff"), producer));
    }

    @Test
    void testPrepareRequestBypassesAndResetsStallingRepeatableProducer() throws Exception {
        final StallingEntityProducer producer = new StallingEntityProducer(true);

        Assertions.assertNull(impl.prepareRequest(new BasicHttpRequest(Method.QUERY, "/stuff"), producer));
        Assertions.assertTrue(producer.released,
                "a partially drained producer must be reset so that it can still be used");
    }

    static class StallingEntityProducer implements AsyncEntityProducer {

        private final boolean repeatable;

        boolean released;

        StallingEntityProducer(final boolean repeatable) {
            this.repeatable = repeatable;
        }

        @Override
        public boolean isRepeatable() {
            return repeatable;
        }

        @Override
        public void failed(final Exception cause) {
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public String getContentType() {
            return ContentType.TEXT_PLAIN.toString();
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public boolean isChunked() {
            return true;
        }

        @Override
        public Set<String> getTrailerNames() {
            return null;
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
        }

        @Override
        public void releaseResources() {
            released = true;
        }

    }

}
