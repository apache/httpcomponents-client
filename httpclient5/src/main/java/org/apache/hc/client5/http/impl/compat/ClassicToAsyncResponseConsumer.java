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
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.support.ClassicResponseBuilder;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;

/**
 * TODO: to be replaced by core functionality
 */
@Experimental
@Internal
class ClassicToAsyncResponseConsumer implements AsyncResponseConsumer<Void> {

    static class ResponseData {

        final HttpResponse head;
        final EntityDetails entityDetails;

        ResponseData(final HttpResponse head,
                     final EntityDetails entityDetails) {
            this.head = head;
            this.entityDetails = entityDetails;
        }

    }

    private final int initialBufferSize;
    private final Timeout timeout;
    private final CountDownLatch countDownLatch;
    private final AtomicReference<ResponseData> responseRef;
    private final AtomicReference<FutureCallback<Void>> callbackRef;
    private final AtomicReference<SharedInputBuffer> bufferRef;
    private final AtomicReference<Exception> exceptionRef;

    public ClassicToAsyncResponseConsumer(final int initialBufferSize, final Timeout timeout) {
        this.initialBufferSize = Args.positive(initialBufferSize, "Initial buffer size");
        this.timeout = timeout;
        this.countDownLatch = new CountDownLatch(1);
        this.responseRef = new AtomicReference<>();
        this.callbackRef = new AtomicReference<>();
        this.bufferRef = new AtomicReference<>();
        this.exceptionRef = new AtomicReference<>();
    }

    public ClassicToAsyncResponseConsumer(final Timeout timeout) {
        this(ClassicToAsyncSupport.INITIAL_BUF_SIZE, timeout);
    }

    void propagateException() throws IOException {
        final Exception ex = exceptionRef.getAndSet(null);
        if (ex != null) {
            ClassicToAsyncSupport.rethrow(ex);
        }
    }

    void fireComplete() throws IOException {
        final FutureCallback<Void> callback = callbackRef.getAndSet(null);
        if (callback != null) {
            callback.completed(null);
        }
    }

    public ClassicHttpResponse blockWaiting() throws IOException, InterruptedException {
        if (timeout == null) {
            countDownLatch.await();
        } else {
            if (!countDownLatch.await(timeout.getDuration(), timeout.getTimeUnit())) {
                throw new InterruptedIOException("Timeout blocked waiting for input (" + timeout + ")");
            }
        }
        propagateException();
        final ResponseData r = responseRef.getAndSet(null);
        Asserts.notNull(r, "HTTP response is missing");
        final SharedInputBuffer inputBuffer = bufferRef.get();
        return ClassicResponseBuilder.create(r.head.getCode())
                .setHeaders(r.head.getHeaders())
                .setVersion(r.head.getVersion())
                .setEntity(r.entityDetails != null ?
                        new IncomingHttpEntity(new InternalInputStream(inputBuffer), r.entityDetails) :
                        null)
                .build();
    }

    @Override
    public void consumeResponse(final HttpResponse asyncResponse,
                                final EntityDetails entityDetails,
                                final HttpContext context,
                                final FutureCallback<Void> resultCallback) throws HttpException, IOException {
        callbackRef.set(resultCallback);
        final ResponseData responseData = new ResponseData(asyncResponse, entityDetails);
        responseRef.set(responseData);
        if (entityDetails != null) {
            bufferRef.set(new SharedInputBuffer(initialBufferSize));
        } else {
            fireComplete();
        }
        countDownLatch.countDown();
    }

    @Override
    public void informationResponse(final HttpResponse response,
                                    final HttpContext context) throws HttpException, IOException {
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final SharedInputBuffer buffer = bufferRef.get();
        if (buffer != null) {
            buffer.updateCapacity(capacityChannel);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final SharedInputBuffer buffer = bufferRef.get();
        if (buffer != null) {
            buffer.fill(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final SharedInputBuffer buffer = bufferRef.get();
        if (buffer != null) {
            buffer.markEndStream();
        }
    }

    @Override
    public final void failed(final Exception cause) {
        try {
            exceptionRef.set(cause);
        } finally {
            countDownLatch.countDown();
        }
    }

    @Override
    public void releaseResources() {
    }

    class InternalInputStream extends InputStream {

        private final SharedInputBuffer buffer;

        InternalInputStream(final SharedInputBuffer buffer) {
            super();
            Args.notNull(buffer, "Input buffer");
            this.buffer = buffer;
        }

        @Override
        public int available() throws IOException {
            propagateException();
            return this.buffer.length();
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            propagateException();
            if (len == 0) {
                return 0;
            }
            final int bytesRead = this.buffer.read(b, off, len, timeout);
            if (bytesRead == -1) {
                fireComplete();
            }
            return bytesRead;
        }

        @Override
        public int read(final byte[] b) throws IOException {
            propagateException();
            if (b == null) {
                return 0;
            }
            final int bytesRead = this.buffer.read(b, 0, b.length, timeout);
            if (bytesRead == -1) {
                fireComplete();
            }
            return bytesRead;
        }

        @Override
        public int read() throws IOException {
            propagateException();
            final int b = this.buffer.read(timeout);
            if (b == -1) {
                fireComplete();
            }
            return b;
        }

        @Override
        public void close() throws IOException {
            // read and discard the remainder of the message
            final byte[] tmp = new byte[1024];
            do {
                /* empty */
            } while (read(tmp) >= 0);
            super.close();
        }

    }

    static class IncomingHttpEntity implements HttpEntity {

        private final InputStream content;
        private final EntityDetails entityDetails;

        IncomingHttpEntity(final InputStream content, final EntityDetails entityDetails) {
            this.content = content;
            this.entityDetails = entityDetails;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isChunked() {
            return entityDetails.isChunked();
        }

        @Override
        public long getContentLength() {
            return entityDetails.getContentLength();
        }

        @Override
        public String getContentType() {
            return entityDetails.getContentType();
        }

        @Override
        public String getContentEncoding() {
            return entityDetails.getContentEncoding();
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return content;
        }

        @Override
        public boolean isStreaming() {
            return content != null;
        }

        @Override
        public void writeTo(final OutputStream outStream) throws IOException {
            AbstractHttpEntity.writeTo(this, outStream);
        }

        @Override
        public Supplier<List<? extends Header>> getTrailers() {
            return null;
        }

        @Override
        public Set<String> getTrailerNames() {
            return Collections.emptySet();
        }

        @Override
        public void close() throws IOException {
            Closer.close(content);
        }

        @Override
        public String toString() {
            return entityDetails.toString();
        }

    }

}
