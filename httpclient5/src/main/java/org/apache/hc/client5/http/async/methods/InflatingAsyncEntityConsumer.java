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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * {@code AsyncEntityConsumer} that transparently inflates an HTTP response
 * whose {@code Content-Encoding} header is {@code deflate}.
 *
 * <p>The consumer supports both the “raw” and the “zlib-wrapped” variants
 * that exist in the wild.  If the caller does not specify which one to
 * expect, the first two bytes of the stream are inspected and the correct
 * decoder is chosen automatically.</p>
 *
 * <p>No blocking I/O classes are used; back-pressure from the reactor
 * thread is honoured at all times.</p>
 *
 * @param <T> the result type of the wrapped inner consumer
 * @since 5.6
 */
public final class InflatingAsyncEntityConsumer<T> implements AsyncEntityConsumer<T> {

    private static final int OUT_BUF = 8 * 1024;

    private final AsyncEntityConsumer<T> inner;
    private Inflater inflater;
    private final byte[] out = new byte[OUT_BUF];

    private final Boolean nowrapHint;
    private boolean formatChosen = false;

    private FutureCallback<T> callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);


    /**
     * Auto-detect (default, safest).
     */
    public InflatingAsyncEntityConsumer(final AsyncEntityConsumer<T> inner) {
        this(inner, null);
    }

    /**
     * @param nowrap {@code true} = raw DEFLATE, {@code false} = zlib wrapper,
     *               {@code null} = auto-detect from first two bytes.
     */
    public InflatingAsyncEntityConsumer(final AsyncEntityConsumer<T> inner,
                                        final Boolean nowrap) {
        this.inner = Args.notNull(inner, "inner");
        this.nowrapHint = nowrap;
        this.inflater = new Inflater(nowrap == null || nowrap);
    }


    @Override
    public void streamStart(final EntityDetails details,
                            final FutureCallback<T> resultCallback)
            throws HttpException, IOException {
        final String enc = details.getContentEncoding();
        if (enc != null && !"deflate".equalsIgnoreCase(enc)) {
            throw new HttpException("Unsupported Content-Encoding: " + enc);
        }
        this.callback = resultCallback;
        inner.streamStart(details, resultCallback);
    }

    @Override
    public void updateCapacity(final CapacityChannel ch) throws IOException {
        ch.update(Integer.MAX_VALUE);
        inner.updateCapacity(ch);
    }

    /* -------------------- data pump ---------------------- */

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (inflater == null) {
            return;
        }

        /* auto-detect first two bytes when caller did not give an explicit hint */
        if (nowrapHint == null && !formatChosen && src.remaining() >= 2) {
            src.mark();
            final int b0 = src.get() & 0xFF;
            final int b1 = src.get() & 0xFF;
            src.reset();

            // CMF 0x78 and one of the four valid FLG values ⇒ stream is zlib-wrapped
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
        inflateLoop();
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers)
            throws HttpException, IOException {
        if (inflater != null) {
            inflateLoop();
            inflater.end();
            inflater = null;
        }
        inner.streamEnd(trailers == null ? Collections.emptyList() : trailers);
        completed.set(true);
        if (callback != null) {
            callback.completed(inner.getContent());
        }
    }

    private void inflateLoop() throws IOException {
        try {
            int n;
            while ((n = inflater.inflate(out)) > 0) {
                inner.consume(ByteBuffer.wrap(out, 0, n));
            }
            if (inflater.needsDictionary()) {
                throw new IOException("Deflate dictionary required");
            }
        } catch (final DataFormatException ex) {
            throw new IOException("Corrupt DEFLATE stream", ex);
        }
    }


    @Override
    public T getContent() {
        return inner.getContent();
    }

    @Override
    public void failed(final Exception cause) {
        if (completed.compareAndSet(false, true) && callback != null) {
            callback.failed(cause);
        }
        inner.failed(cause);
    }

    /**
     * Don’t free native inflater until {@code streamEnd}.
     */
    @Override
    public void releaseResources() {
        inflater = null;
        inner.releaseResources();
    }
}
