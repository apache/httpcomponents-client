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

import org.apache.hc.client5.http.cookie.CookieStore;
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
    private final CookieStore partition;
    private final URI source;
    private final UseAsDictionary directive;
    private final Instant storedAt;
    private final Instant validUntil;
    private final int maxSize;
    private final ByteArrayOutputStream buffer;

    private boolean discarded;

    /**
     * Creates a capturing decorator for a single response whose {@code Use-As-Dictionary}
     * offer has already been parsed. The arguments are validated eagerly: the references must
     * be non-null and {@code maxSize} must be positive.
     *
     * @param downstream the body consumer to which every chunk is forwarded unchanged.
     * @param store the store that receives the captured dictionary on a complete, supported delivery.
     * @param partition the cookie storage partition associated with the exchange.
     * @param source the request {@link URI} the dictionary is keyed by.
     * @param directive the parsed {@code Use-As-Dictionary} offer supplying the match pattern,
     *   destination, identifier and type; a dictionary is stored only when the offer is
     *   {@link UseAsDictionary#isSupported() supported}.
     * @param storedAt the instant recorded as the dictionary's creation time.
     * @param validUntil the instant past which the stored dictionary is no longer valid.
     * @param maxSize the capture ceiling in bytes; once the buffer would exceed it capture is
     *   abandoned for the rest of the exchange and no dictionary is stored.
     */
    DictionaryCapturingAsyncDataConsumer(
            final AsyncDataConsumer downstream,
            final CompressionDictionaryStore store,
            final CookieStore partition,
            final URI source,
            final UseAsDictionary directive,
            final Instant storedAt,
            final Instant validUntil,
            final int maxSize) {
        this.downstream = Args.notNull(downstream, "Downstream data consumer");
        this.store = Args.notNull(store, "Dictionary store");
        this.partition = Args.notNull(partition, "Cookie partition");
        this.source = Args.notNull(source, "Source");
        this.directive = Args.notNull(directive, "Directive");
        this.storedAt = Args.notNull(storedAt, "Stored at");
        this.validUntil = Args.notNull(validUntil, "Valid until");
        this.maxSize = Args.positive(maxSize, "Maximum dictionary size");
        this.buffer = new ByteArrayOutputStream(Math.min(maxSize, 8192));
    }

    /**
     * Propagates the capacity update unchanged; this decorator imposes no flow control of its
     * own and leaves back-pressure entirely to the downstream consumer.
     *
     * @param capacityChannel the channel through which the downstream consumer signals the
     *   capacity it is prepared to accept.
     * @throws IOException if the downstream consumer fails while handling the capacity update.
     */
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
     *
     * @param src the received bytes; its position advanced by {@code downstream} marks the
     *   range that was accepted and is therefore captured.
     * @throws IOException if the downstream consumer fails to accept the chunk.
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
     * Signals stream end to the downstream consumer, then registers the captured body as a
     * compression dictionary when capture completed intact and the directive is supported.
     * A downstream failure prevents the response from being stored as a dictionary.
     *
     * @param trailers the trailing headers, forwarded unchanged to the downstream consumer.
     * @throws HttpException if the downstream consumer rejects the end of stream.
     * @throws IOException in case of an I/O error while signalling stream end downstream.
     */
    @Override
    public void streamEnd(
            final List<? extends Header> trailers)
            throws HttpException, IOException {

        downstream.streamEnd(trailers);

        if (!discarded && directive.isSupported()) {
            store.add(partition, new CompressionDictionary(
                    buffer.toByteArray(),
                    source,
                    directive.getMatch(),
                    directive.getMatchDest(),
                    directive.getId(),
                    directive.getType(),
                    storedAt,
                    validUntil));
        }
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
