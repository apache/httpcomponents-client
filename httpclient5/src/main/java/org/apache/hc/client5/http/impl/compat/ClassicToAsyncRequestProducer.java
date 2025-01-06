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
package org.apache.hc.client5.http.impl.compat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

/**
 * TODO: to be replaced by core functionality
 */
@Experimental
@Internal
class ClassicToAsyncRequestProducer implements AsyncRequestProducer {

    private final ClassicHttpRequest request;
    private final int initialBufferSize;
    private final Timeout timeout;
    private final CountDownLatch countDownLatch;
    private final AtomicReference<SharedOutputBuffer> bufferRef;
    private final AtomicReference<Exception> exceptionRef;

    private volatile boolean repeatable;

    public interface IORunnable {

        void execute() throws IOException;

    }

    public ClassicToAsyncRequestProducer(final ClassicHttpRequest request, final int initialBufferSize, final Timeout timeout) {
        this.request = Args.notNull(request, "HTTP request");
        this.initialBufferSize = Args.positive(initialBufferSize, "Initial buffer size");
        this.timeout = timeout;
        this.countDownLatch = new CountDownLatch(1);
        this.bufferRef = new AtomicReference<>();
        this.exceptionRef = new AtomicReference<>();
    }

    public ClassicToAsyncRequestProducer(final ClassicHttpRequest request, final Timeout timeout) {
        this(request, ClassicToAsyncSupport.INITIAL_BUF_SIZE, timeout);
    }

    void propagateException() throws IOException {
        final Exception ex = exceptionRef.getAndSet(null);
        if (ex != null) {
            ClassicToAsyncSupport.rethrow(ex);
        }
    }

    public IORunnable blockWaiting() throws IOException, InterruptedException {
        if (timeout == null) {
            countDownLatch.await();
        } else {
            if (!countDownLatch.await(timeout.getDuration(), timeout.getTimeUnit())) {
                throw new InterruptedIOException("Timeout blocked waiting for output (" + timeout + ")");
            }
        }
        propagateException();
        final SharedOutputBuffer outputBuffer = bufferRef.get();
        return () -> {
            final HttpEntity requestEntity = request.getEntity();
            if (requestEntity != null) {
                try (final InternalOutputStream outputStream = new InternalOutputStream(outputBuffer)) {
                    requestEntity.writeTo(outputStream);
                }
            }
        };
    }

    @Override
    public void sendRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
        final HttpEntity requestEntity = request.getEntity();
        final SharedOutputBuffer buffer = requestEntity != null ? new SharedOutputBuffer(initialBufferSize) : null;
        bufferRef.set(buffer);
        repeatable = requestEntity == null || requestEntity.isRepeatable();
        channel.sendRequest(request, requestEntity, null);
        countDownLatch.countDown();
    }

    @Override
    public boolean isRepeatable() {
        return repeatable;
    }

    @Override
    public int available() {
        final SharedOutputBuffer buffer = bufferRef.get();
        if (buffer != null) {
            return buffer.length();
        }
        return 0;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        final SharedOutputBuffer buffer = bufferRef.get();
        if (buffer != null) {
            buffer.flush(channel);
        }
    }

    @Override
    public void failed(final Exception cause) {
        try {
            exceptionRef.set(cause);
        } finally {
            countDownLatch.countDown();
        }
    }

    @Override
    public void releaseResources() {
    }

    class InternalOutputStream extends OutputStream {

        private final SharedOutputBuffer buffer;

        public InternalOutputStream(final SharedOutputBuffer buffer) {
            Asserts.notNull(buffer, "Shared buffer");
            this.buffer = buffer;
        }

        @Override
        public void close() throws IOException {
            propagateException();
            this.buffer.writeCompleted(timeout);
        }

        @Override
        public void flush() throws IOException {
            propagateException();
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            propagateException();
            this.buffer.write(b, off, len, timeout);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            propagateException();
            if (b == null) {
                return;
            }
            this.buffer.write(b, 0, b.length, timeout);
        }

        @Override
        public void write(final int b) throws IOException {
            propagateException();
            this.buffer.write(b, timeout);
        }

    }

}
