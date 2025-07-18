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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.zip.Deflater;

import org.apache.hc.client5.http.impl.async.ContentCompressionAsyncExec;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InflatingAsyncDataConsumerTest {

    /**
     * Collects bytes and decodes to UTF-8 once at the end.
     */
    private static final class ByteCollector implements AsyncEntityConsumer<String> {

        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        private FutureCallback<String> callback;

        @Override
        public void streamStart(final EntityDetails ed, final FutureCallback<String> cb) {
            this.callback = cb;
        }

        @Override
        public void updateCapacity(final CapacityChannel c) {
        }

        @Override
        public void consume(final ByteBuffer src) {
            final byte[] tmp = new byte[src.remaining()];
            src.get(tmp);
            buf.write(tmp, 0, tmp.length);
        }

        @Override
        public void streamEnd(final List<? extends Header> t) {
            if (callback != null) {
                callback.completed(getContent());
            }
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

    private static String ORIGINAL;

    @BeforeAll
    static void buildText() {
        final String seed = "Hello ✈ DEF-in-late 🎈! ";
        final StringBuilder sb = new StringBuilder(seed.length() * 4000);
        for (int i = 0; i < 4000; i++) {
            sb.append(seed);
        }
        ORIGINAL = sb.toString();
    }

    private static byte[] compress(final boolean nowrap) {
        final Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, nowrap);
        final byte[] src = ORIGINAL.getBytes(StandardCharsets.UTF_8);
        def.setInput(src);
        def.finish();

        final byte[] buf = new byte[4096];
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while (!def.finished()) {
            final int n = def.deflate(buf);
            out.write(buf, 0, n);
        }
        def.end();
        return out.toByteArray();
    }

    @Test
    void inflateRawAndZlib() throws Exception {

        for (final boolean nowrap : new boolean[]{true, false}) {

            final byte[] compressed = compress(nowrap);

            final ByteCollector inner = new ByteCollector();
            final InflatingAsyncDataConsumer inflating =
                    new InflatingAsyncDataConsumer(inner, Boolean.valueOf(nowrap));

            final CountDownLatch done = new CountDownLatch(1);
            final FutureCallback<String> cb = new FutureCallback<String>() {
                @Override
                public void completed(final String result) {
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

            /* minimal EntityDetails stub */
            final EntityDetails details = new EntityDetails() {
                @Override
                public long getContentLength() {
                    return compressed.length;
                }

                @Override
                public String getContentType() {
                    return "text/plain";
                }

                @Override
                public String getContentEncoding() {
                    return "deflate";
                }

                @Override
                public boolean isChunked() {
                    return false;
                }

                @Override
                public Set<String> getTrailerNames() {
                    return new HashSet<>();
                }
            };
            inner.streamStart(details, cb);

            for (int off = 0; off < compressed.length; off += 1024) {
                final int n = Math.min(1024, compressed.length - off);
                inflating.consume(ByteBuffer.wrap(compressed, off, n));
            }
            inflating.streamEnd(Collections.emptyList());

            assertTrue(done.await(2, TimeUnit.SECONDS), "callback timeout");
            assertEquals(ORIGINAL, inner.getContent(),
                    (nowrap ? "raw" : "zlib") + " inflate mismatch");
        }
    }

    @Test
    void unknownEncodingMapFlag() throws Exception {
        final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> map = new LinkedHashMap<>();
        map.put("deflate", d -> new InflatingAsyncDataConsumer(d, null));
        final ContentCompressionAsyncExec exec = new ContentCompressionAsyncExec(map, false);
        assertNotNull(exec);
    }
}
