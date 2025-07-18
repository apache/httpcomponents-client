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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * <p>Streaming, non-blocking {@link AsyncDataConsumer} that transparently
 * inflates a response whose {@code Content-Encoding} is {@code deflate}.
 * The decoded bytes are pushed straight to the wrapped downstream consumer
 * while honouring reactor back-pressure.</p>
 *
 * <p>The implementation understands both formats that exist “in the wild”: the
 * raw DEFLATE stream (RFC 1951) and the zlib-wrapped variant (RFC 1950).
 * If the caller does not specify which one to expect, the first two bytes of
 * the stream are inspected and the proper decoder is chosen automatically.</p>
 *
 * <p>No {@code InputStream}/{@code OutputStream} buffering is used; memory
 * footprint is bounded and suitable for very large payloads.</p>
 *
 * @since 5.6
 */
public final class InflatingAsyncDataConsumer implements AsyncDataConsumer {

    private final AsyncDataConsumer downstream;
    private final Boolean nowrapHint;
    private Inflater inflater;
    private boolean formatChosen;
    private final byte[] out = new byte[8 * 1024];
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public InflatingAsyncDataConsumer(
            final AsyncDataConsumer downstream, final Boolean nowrapHint) {
        this.downstream = downstream;
        this.nowrapHint = nowrapHint;
        this.inflater = new Inflater(nowrapHint == null || nowrapHint);
    }

    @Override
    public void updateCapacity(final CapacityChannel ch) throws IOException {
        downstream.updateCapacity(ch);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (closed.get()) {
            return;
        }

        if (nowrapHint == null && !formatChosen && src.remaining() >= 2) {
            src.mark();
            final int b0 = src.get() & 0xFF;
            final int b1 = src.get() & 0xFF;
            src.reset();
            final boolean zlib = b0 == 0x78 &&
                    (b1 == 0x01 || b1 == 0x5E || b1 == 0x9C || b1 == 0xDA);
            if (zlib) {
                inflater.end();
                inflater = new Inflater(false);
            }
            formatChosen = true;
        }

        final byte[] in = new byte[src.remaining()];
        src.get(in);
        inflater.setInput(in);

        try {
            int n;
            while ((n = inflater.inflate(out)) > 0) {
                downstream.consume(ByteBuffer.wrap(out, 0, n));
            }
            if (inflater.needsDictionary()) {
                throw new IOException("Deflate dictionary required");
            }
        } catch (final DataFormatException ex) {
            throw new IOException("Corrupt DEFLATE stream", ex);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers)
            throws HttpException, IOException {
        if (closed.compareAndSet(false, true)) {
            inflater.end();
            downstream.streamEnd(trailers);
        }
    }

    @Override
    public void releaseResources() {
        inflater = null;
        downstream.releaseResources();
    }
}
