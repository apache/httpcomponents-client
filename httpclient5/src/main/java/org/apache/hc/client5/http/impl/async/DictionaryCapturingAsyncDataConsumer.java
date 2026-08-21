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
package org.apache.hc.client5.http.impl.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncDataConsumer} decorator that captures a response body so it can be
 * registered as a compression dictionary while the body is delivered to the wrapped
 * consumer unchanged.
 * <p>
 * When the origin marks a response with a {@code Use-As-Dictionary} directive, that
 * response may be reused as a shared dictionary to decode future Dictionary-Compressed
 * Brotli ({@code dcb}) or Dictionary-Compressed Zstandard ({@code dcz}) responses, as
 * defined by Compression Dictionary Transport. This decorator sits between the message pipeline and the actual
 * body consumer: every chunk is forwarded downstream first, then a copy of the bytes the
 * downstream consumer accepted is accumulated into an in-memory buffer. On stream end the
 * buffered bytes are handed to the {@link CompressionDictionaryStore} as a new
 * {@link CompressionDictionary}, keyed by the request {@link URI} and the match pattern
 * and identifier carried by the directive.
 * <p>
 * Capture is best-effort and bounded. If the accumulated body would exceed {@code maxSize},
 * or if the exchange is aborted before it completes, the buffer is discarded and no
 * dictionary is stored; the downstream consumer is unaffected in either case. A dictionary
 * is only stored when the directive is {@link UseAsDictionary#isSupported() supported}.
 * <p>
 * Instances are not thread-safe and, like any {@link AsyncDataConsumer}, expect their
 * callbacks to be invoked by a single I/O thread for the lifetime of one message exchange.
 */
final class DictionaryCapturingAsyncDataConsumer implements AsyncDataConsumer {

    private final AsyncDataConsumer downstream;
    private final CompressionDictionaryStore store;
    private final URI source;
    private final UseAsDictionary directive;
    private final Instant storedAt;
    private final Instant validUntil;
    private final int maxSize;
    private final ByteArrayOutputStream buffer;

    private boolean discarded;

    DictionaryCapturingAsyncDataConsumer(
            final AsyncDataConsumer downstream,
            final CompressionDictionaryStore store,
            final URI source,
            final UseAsDictionary directive,
            final Instant storedAt,
            final Instant validUntil,
            final int maxSize) {
        this.downstream = Args.notNull(downstream, "Downstream data consumer");
        this.store = Args.notNull(store, "Dictionary store");
        this.source = Args.notNull(source, "Source");
        this.directive = Args.notNull(directive, "Directive");
        this.storedAt = Args.notNull(storedAt, "Stored at");
        this.validUntil = Args.notNull(validUntil, "Valid until");
        this.maxSize = Args.positive(maxSize, "Maximum dictionary size");
        this.buffer = new ByteArrayOutputStream(Math.min(maxSize, 8192));
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        downstream.updateCapacity(capacityChannel);
    }

    /**
     * Forwards the chunk downstream and, unless capture has been discarded, appends a copy
     * of the bytes the downstream consumer actually accepted to the capture buffer. Only the
     * range from the entry position to the position left by {@code downstream} is captured,
     * so partially consumed buffers are handled correctly. Capture is abandoned for the rest
     * of the exchange once the buffered size would exceed {@code maxSize}.
     */
    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final int start = src.position();

        downstream.consume(src);

        if (!discarded) {
            final int consumed = src.position() - start;
            if (consumed > 0) {
                if (buffer.size() + consumed > maxSize) {
                    discarded = true;
                    buffer.reset();
                    return;
                }

                final ByteBuffer copy = src.duplicate();
                copy.position(start);
                copy.limit(start + consumed);

                final byte[] bytes = new byte[consumed];
                copy.get(bytes);
                buffer.write(bytes, 0, bytes.length);
            }
        }
    }

    /**
     * Registers the captured body as a compression dictionary when capture completed intact
     * and the directive is supported, then signals stream end to the downstream consumer.
     * The downstream consumer is always notified, whether or not a dictionary was stored.
     */
    @Override
    public void streamEnd(
            final List<? extends Header> trailers)
            throws HttpException, IOException {

        if (!discarded && directive.isSupported()) {
            store.add(new CompressionDictionary(
                    buffer.toByteArray(),
                    source,
                    directive.getMatch(),
                    directive.getMatchDest(),
                    directive.getId(),
                    directive.getType(),
                    storedAt,
                    validUntil));
        }

        downstream.streamEnd(trailers);
    }

    /**
     * Discards any captured bytes and releases the downstream consumer. Marking capture as
     * discarded ensures no dictionary is stored for an exchange torn down before completion.
     */
    @Override
    public void releaseResources() {
        discarded = true;
        buffer.reset();
        downstream.releaseResources();
    }
}