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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * An asynchronous entity producer that compresses content using raw DEFLATE
 * (RFC 1951) on the fly without full content buffering. It wraps a delegate
 * {@link AsyncEntityProducer} and compresses its output incrementally using
 * a {@link DeflaterOutputStream} with nowrap mode enabled, producing pure
 * DEFLATE stream without ZLIB header or trailer. This class supports streaming
 * compression for requests where the full content length is unknown in advance.
 * <p>
 * The output is chunked, and the "Content-Encoding: deflate" header should be
 * added manually to the request if needed. Use this for modest payload sizes to
 * avoid potential memory issues with very large content.
 * </p>
 *
 * @since 5.6
 */
public final class DeflateCompressingAsyncEntityProducer implements AsyncEntityProducer {

    private final AsyncEntityProducer delegate;
    private final ContentType contentType;
    private DeflaterOutputStream deflateStream;
    private ByteBuffer outputBuffer = ByteBuffer.allocate(8192);
    private boolean started = false;
    private boolean finished = false;

    public DeflateCompressingAsyncEntityProducer(final AsyncEntityProducer delegate) throws IOException {
        this.delegate = Args.notNull(delegate, "delegate");
        this.contentType = ContentType.parse(delegate.getContentType());
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public long getContentLength() {
        return -1; // Unknown due to streaming compression
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return "deflate";
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public int available() {
        return delegate.available() > 0 || outputBuffer.position() > 0 ? 1 : 0;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (finished) {
            channel.endStream();
            return;
        }

        if (!started) {
            deflateStream = new DeflaterOutputStream(new ChannelBoundOutputStream(channel, outputBuffer), new Deflater(Deflater.DEFAULT_COMPRESSION, true));
            started = true;
        }

        delegate.produce(new CompressingDataStreamChannel(channel));

        // If delegate is done, finish deflate
        if (delegate.getContentLength() >= 0 && delegate.available() == 0) {
            deflateStream.finish();
            flushBuffer(channel);
            finished = true;
            channel.endStream();
        }
    }

    private void flushBuffer(final DataStreamChannel channel) throws IOException {
        if (outputBuffer.position() > 0) {
            outputBuffer.flip();
            while (outputBuffer.hasRemaining()) {
                channel.write(outputBuffer);
            }
            outputBuffer.clear();
        }
    }

    @Override
    public void failed(final Exception ex) {
        delegate.failed(ex);
    }

    @Override
    public void releaseResources() {
        delegate.releaseResources();
        if (deflateStream != null) {
            try {
                deflateStream.close();
            } catch (final IOException ignored) {
            }
            deflateStream = null;
        }
        outputBuffer = null;
    }

    private class ChannelBoundOutputStream extends OutputStream {

        private final DataStreamChannel channel;
        private final ByteBuffer buffer;

        public ChannelBoundOutputStream(final DataStreamChannel channel, final ByteBuffer buffer) {
            this.channel = channel;
            this.buffer = buffer;
        }

        @Override
        public void write(final int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            int currentOff = off;
            int currentLen = len;
            while (currentLen > 0) {
                int space = buffer.remaining();
                if (space == 0) {
                    flushBuffer(channel);
                    space = buffer.remaining();
                }
                final int copy = Math.min(currentLen, space);
                buffer.put(b, currentOff, copy);
                currentOff += copy;
                currentLen -= copy;
            }
        }
    }

    private class CompressingDataStreamChannel implements DataStreamChannel {

        private final DataStreamChannel outerChannel;

        public CompressingDataStreamChannel(final DataStreamChannel outerChannel) {
            this.outerChannel = outerChannel;
        }

        @Override
        public void requestOutput() {
            // No-op or propagate if needed
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final byte[] bytes = new byte[src.remaining()];
            src.get(bytes);
            deflateStream.write(bytes);
            deflateStream.flush(); // Flush to produce compressed output incrementally
            flushBuffer(outerChannel);
            return bytes.length;
        }

        @Override
        public void endStream() throws IOException {
            deflateStream.finish();
            flushBuffer(outerChannel);
            finished = true;
            outerChannel.endStream();
        }

        @Override
        public void endStream(final List<? extends Header> trailers) throws IOException {
            endStream();
        }
    }
}