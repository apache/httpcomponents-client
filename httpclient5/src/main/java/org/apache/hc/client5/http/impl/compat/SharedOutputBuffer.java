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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.support.classic.ContentOutputBuffer;
import org.apache.hc.core5.util.Timeout;

/**
 * TODO: to be replaced by core functionality
 */
@Internal
final class SharedOutputBuffer extends AbstractSharedBuffer implements ContentOutputBuffer {

    private final AtomicBoolean endStreamPropagated;
    private volatile DataStreamChannel dataStreamChannel;
    private volatile boolean hasCapacity;

    public SharedOutputBuffer(final ReentrantLock lock, final int initialBufferSize) {
        super(lock, initialBufferSize);
        this.hasCapacity = false;
        this.endStreamPropagated = new AtomicBoolean();
    }

    public SharedOutputBuffer(final int bufferSize) {
        this(new ReentrantLock(), bufferSize);
    }

    public void flush(final DataStreamChannel channel) throws IOException {
        lock.lock();
        try {
            dataStreamChannel = channel;
            hasCapacity = true;
            setOutputMode();
            if (buffer().hasRemaining()) {
                dataStreamChannel.write(buffer());
            }
            if (!buffer().hasRemaining() && endStream) {
                propagateEndStream();
            }
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void ensureNotAborted() throws InterruptedIOException {
        if (aborted) {
            throw new InterruptedIOException("Operation aborted");
        }
    }

    /**
     * @since 5.4
     */
    public void write(final byte[] b, final int off, final int len, final Timeout timeout) throws IOException {
        final ByteBuffer src = ByteBuffer.wrap(b, off, len);
        lock.lock();
        try {
            ensureNotAborted();
            setInputMode();
            while (src.hasRemaining()) {
                // always buffer small chunks
                if (src.remaining() < 1024 && buffer().remaining() > src.remaining()) {
                    buffer().put(src);
                } else {
                    if (buffer().position() > 0 || dataStreamChannel == null) {
                        waitFlush(timeout);
                    }
                    if (buffer().position() == 0 && dataStreamChannel != null) {
                        final int bytesWritten = dataStreamChannel.write(src);
                        if (bytesWritten == 0) {
                            hasCapacity = false;
                            waitFlush(timeout);
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        write(b, off, len, null);
    }

    /**
     * @since 5.4
     */
    public void write(final int b, final Timeout timeout) throws IOException {
        lock.lock();
        try {
            ensureNotAborted();
            setInputMode();
            if (!buffer().hasRemaining()) {
                waitFlush(timeout);
            }
            buffer().put((byte)b);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void write(final int b) throws IOException {
        write(b, null);
    }

    /**
     * @since 5.4
     */
    public void writeCompleted(final Timeout timeout) throws IOException {
        if (endStream) {
            return;
        }
        lock.lock();
        try {
            if (!endStream) {
                endStream = true;
                if (dataStreamChannel != null) {
                    setOutputMode();
                    if (buffer().hasRemaining()) {
                        dataStreamChannel.requestOutput();
                        waitEndStream(timeout);
                    } else {
                        propagateEndStream();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeCompleted() throws IOException {
        writeCompleted(null);
    }

    private void waitFlush(final Timeout timeout) throws InterruptedIOException {
        if (dataStreamChannel != null) {
            dataStreamChannel.requestOutput();
        }
        setOutputMode();
        while (buffer().hasRemaining() || !hasCapacity) {
            ensureNotAborted();
            waitForSignal(timeout);
        }
        setInputMode();
    }

    private void waitEndStream(final Timeout timeout) throws InterruptedIOException {
        if (dataStreamChannel != null) {
            dataStreamChannel.requestOutput();
        }
        while (!endStreamPropagated.get() && !aborted) {
            waitForSignal(timeout);
        }
    }

    private void waitForSignal(final Timeout timeout) throws InterruptedIOException {
        try {
            if (timeout == null) {
                condition.await();
            } else {
                if (!condition.await(timeout.getDuration(), timeout.getTimeUnit())) {
                    aborted = true;
                    throw new InterruptedIOException("Timeout blocked waiting for output (" + timeout + ")");
                }
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException(ex.getMessage());
        }
    }

    private void propagateEndStream() throws IOException {
        if (endStreamPropagated.compareAndSet(false, true)) {
            dataStreamChannel.endStream();
        }
    }

}
