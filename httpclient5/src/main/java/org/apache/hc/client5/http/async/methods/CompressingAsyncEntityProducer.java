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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.entity.compress.ContentCodecRegistry;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Generic {@link AsyncEntityProducer} that compresses the output produced by a
 * delegate using any codec available via {@link ContentCodecRegistry#encoder}.
 *
 * <p>The delegate’s entire payload is buffered once; therefore use this class
 * only for modest payload sizes.</p>
 *
 * @since 5.6
 */
public final class CompressingAsyncEntityProducer implements AsyncEntityProducer {

    private final byte[] compressed;
    private final String codingToken;
    private final ContentType contentType;
    private final AtomicInteger cursor = new AtomicInteger();

    public CompressingAsyncEntityProducer(
            final AsyncEntityProducer delegate,
            final String coding) throws IOException {

        Args.notNull(delegate, "delegate");
        this.codingToken = Args.notBlank(coding, "coding").toLowerCase(Locale.ROOT);
        this.contentType = ContentType.parse(delegate.getContentType());

        final ByteArrayOutputStream rawBuf = new ByteArrayOutputStream();
        delegate.produce(new BufferingChannel(rawBuf));

        final HttpEntity rawEntity = new ByteArrayEntity(rawBuf.toByteArray(), contentType);
        final HttpEntity encodedEnt = encode(rawEntity, codingToken);

        final ByteArrayOutputStream encBuf = new ByteArrayOutputStream();
        encodedEnt.writeTo(encBuf);
        this.compressed = encBuf.toByteArray();
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public long getContentLength() {
        return compressed.length;
    }

    @Override
    public String getContentType() {
        return contentType.toString();
    }

    @Override
    public String getContentEncoding() {
        return codingToken;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public int available() {
        return compressed.length - cursor.get();
    }

    @Override
    public Set<String> getTrailerNames() {
        return new HashSet<>();
    }

    @Override
    public void failed(final Exception ex) {
    }

    @Override
    public void releaseResources() {
    }

    @Override
    public void produce(final DataStreamChannel ch) throws IOException {
        final int pos = cursor.get();
        if (pos >= compressed.length) {
            ch.endStream(Collections.<Header>emptyList());
            return;
        }
        final int chunk = Math.min(8 * 1024, compressed.length - pos);
        ch.write(ByteBuffer.wrap(compressed, pos, chunk));
        cursor.addAndGet(chunk);
    }

    private static HttpEntity encode(final HttpEntity src, final String token) throws IOException {
        final ContentCoding coding = ContentCoding.fromToken(token);
        if (coding == null) {
            throw new IOException("Unknown coding: " + token);
        }
        final java.util.function.UnaryOperator<HttpEntity> op = ContentCodecRegistry.encoder(coding);
        if (op == null) {
            throw new IOException("No encoder registered for " + token);
        }
        return op.apply(src);
    }

    /**
     * Captures bytes pushed by the delegate into a buffer.
     */
    private static final class BufferingChannel implements DataStreamChannel {
        private final ByteArrayOutputStream buf;

        BufferingChannel(final ByteArrayOutputStream buf) {
            this.buf = buf;
        }

        @Override
        public void requestOutput() {
        }

        @Override
        public int write(final ByteBuffer src) {
            final byte[] tmp = new byte[src.remaining()];
            src.get(tmp);
            buf.write(tmp, 0, tmp.length);
            return tmp.length;
        }

        @Override
        public void endStream() {
        }

        @Override
        public void endStream(final List<? extends Header> t) {
        }
    }
}
