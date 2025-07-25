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
package org.apache.hc.client5.http.async.methods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GzipAsyncEntityConsumerTest {

    private static String ORIGINAL;

    @BeforeAll
    static void build() {
        final String seed = "inflate me 🎉 ";
        final StringBuilder sb = new StringBuilder(seed.length() * 3000);
        for (int i = 0; i < 3000; i++) sb.append(seed);
        ORIGINAL = sb.toString();
    }

    private static byte[] gzip(final byte[] data) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final GZIPOutputStream gos = new GZIPOutputStream(out);
        gos.write(data);
        gos.close();
        return out.toByteArray();
    }

    private static class BytesCollector implements AsyncEntityConsumer<String> {
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

        @Override
        public void streamStart(final EntityDetails ed, final FutureCallback<String> cb) {
        }

        @Override
        public void updateCapacity(final CapacityChannel c) {
        }

        @Override
        public void consume(final ByteBuffer src) {
            final byte[] b = new byte[src.remaining()];
            src.get(b);
            buf.write(b, 0, b.length);
        }

        @Override
        public void streamEnd(final List<? extends Header> t) {
        }

        @Override
        public void failed(final Exception cause) {
            throw new RuntimeException(cause);
        }

        @Override
        public void releaseResources() {
        }

        @Override
        public String getContent() {
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fullInflate() throws Exception {

        final byte[] wire = gzip(ORIGINAL.getBytes(StandardCharsets.UTF_8));

        final BytesCollector inner = new BytesCollector();
        final GzipAsyncEntityConsumer<String> inflating =
                new GzipAsyncEntityConsumer<String>(inner);

        final CountDownLatch done = new CountDownLatch(1);
        final FutureCallback<String> cb = new FutureCallback<String>() {
            @Override
            public void completed(final String r) {
                done.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                fail(ex);
            }

            @Override
            public void cancelled() {
                fail("cancelled");
            }
        };

        inflating.streamStart(new EntityDetails() {
            @Override
            public long getContentLength() {
                return wire.length;
            }

            @Override
            public String getContentType() {
                return "text/plain";
            }

            @Override
            public String getContentEncoding() {
                return "gzip";
            }

            @Override
            public boolean isChunked() {
                return false;
            }

            @Override
            public Set<String> getTrailerNames() {
                return new HashSet<>();
            }
        }, cb);

        for (int off = 0; off < wire.length; off += 1000) {
            final int n = Math.min(1000, wire.length - off);
            inflating.consume(ByteBuffer.wrap(wire, off, n));
        }
        inflating.streamEnd(Collections.emptyList());

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(ORIGINAL, inner.getContent());
    }
}
