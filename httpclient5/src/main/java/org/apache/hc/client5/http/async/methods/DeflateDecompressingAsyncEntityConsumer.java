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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * An asynchronous entity consumer that decompresses raw DEFLATE (RFC 1951) content incrementally
 * without buffering the full compressed input. It wraps an inner {@link AsyncEntityConsumer} to
 * process decompressed data, supporting arbitrary result types (e.g., String, byte[], or custom).
 * Decompressed chunks are passed to the inner consumer as they are produced, using a fixed-size
 * buffer to minimize memory usage. Suitable for modest-sized payloads.
 *
 * @param <T> the type of the result produced by the inner consumer
 * @since 5.6
 */
public class DeflateDecompressingAsyncEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private final AsyncEntityConsumer<T> innerConsumer;
    private Inflater inflater;
    private final byte[] decompressBuffer = new byte[8192];
    private List<Header> trailers = new ArrayList<>();
    private FutureCallback<T> callback;

    /**
     * Constructs a new DEFLATE decompressing consumer wrapping the specified inner consumer.
     *
     * @param innerConsumer the consumer to process decompressed data
     * @throws IllegalArgumentException if innerConsumer is null
     */
    public DeflateDecompressingAsyncEntityConsumer(final AsyncEntityConsumer<T> innerConsumer) {
        this.innerConsumer = Args.notNull(innerConsumer, "innerConsumer");
    }

    @Override
    public void streamStart(final EntityDetails entityDetails, final FutureCallback<T> resultCallback) throws HttpException, IOException {
        final String encoding = entityDetails.getContentEncoding();
        if (!"deflate".equalsIgnoreCase(encoding)) {
            throw new HttpException("Unsupported content coding: " + encoding + ". Only DEFLATE is supported.");
        }
        inflater = new Inflater(false); // nowrap: false for ZLIB-wrapped DEFLATE (common in HTTP)
        this.callback = resultCallback;
        innerConsumer.streamStart(entityDetails, resultCallback);
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE); // Always ready for more data
        innerConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final byte[] input = new byte[src.remaining()];
        src.get(input);
        inflater.setInput(input);

        while (!inflater.needsInput()) {
            final int bytesInflated;
            try {
                bytesInflated = inflater.inflate(decompressBuffer);
            } catch (final DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
            if (bytesInflated == 0) {
                if (inflater.finished()) {
                    break;
                } else if (inflater.needsDictionary()) {
                    throw new IOException("Deflate dictionary needed");
                }
            }
            innerConsumer.consume(ByteBuffer.wrap(decompressBuffer, 0, bytesInflated));
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (trailers != null) {
            this.trailers.addAll(trailers);
        }
        // Ensure final inflate if any remaining
        while (!inflater.finished()) {
            final int bytesInflated;
            try {
                bytesInflated = inflater.inflate(decompressBuffer);
            } catch (final DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
            if (bytesInflated == 0) {
                break;
            }
            innerConsumer.consume(ByteBuffer.wrap(decompressBuffer, 0, bytesInflated));
        }
        if (!inflater.finished()) {
            throw new IOException("Incomplete Deflate stream");
        }
        innerConsumer.streamEnd(trailers);
        if (callback != null) {
            callback.completed(innerConsumer.getContent());
        }
    }

    @Override
    public void failed(final Exception cause) {
        innerConsumer.failed(cause);
    }

    @Override
    public void releaseResources() {
        if (inflater != null) {
            inflater.end();
            inflater = null;
        }
        innerConsumer.releaseResources();
    }

    @Override
    public T getContent() {
        return innerConsumer.getContent();
    }
}