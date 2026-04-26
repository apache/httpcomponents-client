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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;

/**
 * Raw tunnel {@link IOSession} implementation with bounded buffering,
 * capacity flow control and stream-scoped close semantics.
 * <p>
 * Closing this session cancels only the CONNECT stream,
 * not the underlying physical HTTP/2 connection.
 * </p>
 *
 * @since 5.7
 */
final class H2TunnelRawIOSession implements IOSession {

    private static final int INBOUND_BUFFER_LIMIT = 64 * 1024;
    private static final int OUTBOUND_BUFFER_LIMIT = 64 * 1024;

    private final IOSession physicalSession;
    private final String id;
    private final Lock lock;

    private final Deque<Command> commandQueue;
    private final Deque<ByteBuffer> inboundQueue;
    private final Deque<ByteBuffer> outboundQueue;

    private final AtomicReference<IOEventHandler> handlerRef;
    private final AtomicReference<DataStreamChannel> dataChannelRef;
    private final AtomicReference<StreamControl> streamControlRef;

    private CapacityChannel capacityChannel;
    private Timeout socketTimeout;
    private int eventMask;
    private Status status;

    private int inboundBytes;
    private int outboundBytes;
    private int consumedBytesSinceUpdate;

    private boolean capacityInitialized;
    private boolean localEndStreamSent;
    private boolean remoteEndStream;

    private long lastReadTime;
    private long lastWriteTime;
    private long lastEventTime;

    H2TunnelRawIOSession(
            final IOSession physicalSession,
            final Timeout socketTimeout,
            final StreamControl streamControl) {
        this.physicalSession = physicalSession;
        this.id = physicalSession.getId() + "-h2-tunnel";
        this.lock = new ReentrantLock();
        this.commandQueue = new ArrayDeque<>();
        this.inboundQueue = new ArrayDeque<>();
        this.outboundQueue = new ArrayDeque<>();
        this.handlerRef = new AtomicReference<>();
        this.dataChannelRef = new AtomicReference<>();
        this.streamControlRef = new AtomicReference<>(streamControl);

        this.capacityChannel = null;
        this.socketTimeout = socketTimeout;
        this.eventMask = SelectionKey.OP_READ;
        this.status = Status.ACTIVE;

        this.capacityInitialized = false;
        this.localEndStreamSent = false;
        this.remoteEndStream = false;

        final long now = System.currentTimeMillis();
        this.lastReadTime = now;
        this.lastWriteTime = now;
        this.lastEventTime = now;
    }

    void bindStreamControl(final StreamControl streamControl) {
        streamControlRef.compareAndSet(null, streamControl);
    }

    void attachChannel(final DataStreamChannel channel) {
        dataChannelRef.set(channel);
    }

    void updateCapacityChannel(final CapacityChannel capacityChannel) throws IOException {
        int update = 0;
        lock.lock();
        try {
            this.capacityChannel = capacityChannel;
            if (!capacityInitialized) {
                update += Math.max(0, INBOUND_BUFFER_LIMIT - inboundBytes);
                capacityInitialized = true;
            }
            if (consumedBytesSinceUpdate > 0) {
                update += consumedBytesSinceUpdate;
                consumedBytesSinceUpdate = 0;
            }
        } finally {
            lock.unlock();
        }
        if (update > 0) {
            capacityChannel.update(update);
        }
    }

    void onRemoteStreamEnd() {
        lock.lock();
        try {
            remoteEndStream = true;
            if (status == Status.ACTIVE) {
                status = Status.CLOSING;
            }
            if (localEndStreamSent) {
                status = Status.CLOSED;
            }
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    void requestOutput() {
        final DataStreamChannel dataChannel = dataChannelRef.get();
        if (dataChannel != null) {
            dataChannel.requestOutput();
        }
    }

    int available() {
        lock.lock();
        try {
            if (outboundBytes > 0) {
                return outboundBytes;
            }
            if (!localEndStreamSent && status == Status.CLOSING) {
                return 1;
            }
            if (!commandQueue.isEmpty() || (eventMask & SelectionKey.OP_WRITE) != 0) {
                return 1;
            }
            return 0;
        } finally {
            lock.unlock();
        }
    }

    void appendInput(final ByteBuffer src) throws IOException {
        if (src == null || !src.hasRemaining()) {
            return;
        }
        lock.lock();
        try {
            if (status == Status.CLOSED) {
                return;
            }
            final int remaining = src.remaining();
            final int freeSpace = INBOUND_BUFFER_LIMIT - inboundBytes;
            if (remaining > freeSpace) {
                throw new IOException("Tunnel inbound buffer overflow");
            }
            final byte[] data = new byte[remaining];
            src.get(data);
            inboundQueue.addLast(ByteBuffer.wrap(data));
            inboundBytes += data.length;
            final long now = System.currentTimeMillis();
            lastReadTime = now;
            lastEventTime = now;
        } finally {
            lock.unlock();
        }
    }

    void discardInbound(final int bytes) throws IOException {
        if (bytes <= 0) {
            return;
        }
        int remaining = bytes;
        int update = 0;
        CapacityChannel currentCapacityChannel = null;

        lock.lock();
        try {
            while (remaining > 0) {
                final ByteBuffer buffer = inboundQueue.peekFirst();
                if (buffer == null) {
                    break;
                }
                final int chunk = Math.min(remaining, buffer.remaining());
                if (chunk <= 0) {
                    break;
                }
                buffer.position(buffer.position() + chunk);
                remaining -= chunk;
                inboundBytes -= chunk;
                if (!buffer.hasRemaining()) {
                    inboundQueue.pollFirst();
                }
                consumedBytesSinceUpdate += chunk;
            }
            if (capacityChannel != null && consumedBytesSinceUpdate > 0) {
                currentCapacityChannel = capacityChannel;
                update = consumedBytesSinceUpdate;
                consumedBytesSinceUpdate = 0;
            }
        } finally {
            lock.unlock();
        }

        if (currentCapacityChannel != null && update > 0) {
            currentCapacityChannel.update(update);
        }
    }

    void flushOutput() throws IOException {
        final DataStreamChannel dataChannel = dataChannelRef.get();
        if (dataChannel == null) {
            return;
        }

        boolean sendEndStream = false;

        lock.lock();
        try {
            for (; ; ) {
                final ByteBuffer buffer = outboundQueue.peekFirst();
                if (buffer == null) {
                    break;
                }
                final int bytesWritten = dataChannel.write(buffer);
                if (bytesWritten <= 0) {
                    break;
                }
                outboundBytes -= bytesWritten;
                if (!buffer.hasRemaining()) {
                    outboundQueue.pollFirst();
                }
                final long now = System.currentTimeMillis();
                lastWriteTime = now;
                lastEventTime = now;
            }
            if (!localEndStreamSent && status == Status.CLOSING && outboundQueue.isEmpty()) {
                localEndStreamSent = true;
                sendEndStream = true;
            }
        } finally {
            lock.unlock();
        }

        if (sendEndStream) {
            try {
                dataChannel.endStream(null);
            } finally {
                lock.lock();
                try {
                    if (remoteEndStream) {
                        status = Status.CLOSED;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private void cancelPendingCommands() {
        for (final Command command : commandQueue) {
            command.cancel();
        }
        commandQueue.clear();
    }

    private void cancelStream() {
        final StreamControl streamControl = streamControlRef.get();
        if (streamControl != null) {
            streamControl.cancel();
        }
    }

    @Override
    public IOEventHandler getHandler() {
        return handlerRef.get();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        handlerRef.set(handler);
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        if (command == null) {
            return;
        }
        lock.lock();
        try {
            if (status != Status.ACTIVE) {
                command.cancel();
                return;
            }
            if (priority == Command.Priority.IMMEDIATE) {
                commandQueue.addFirst(command);
            } else {
                commandQueue.addLast(command);
            }
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
        requestOutput();
    }

    @Override
    public boolean hasCommands() {
        lock.lock();
        try {
            return !commandQueue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Command poll() {
        lock.lock();
        try {
            return commandQueue.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ByteChannel channel() {
        return this;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return physicalSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return physicalSession.getLocalAddress();
    }

    @Override
    public int getEventMask() {
        lock.lock();
        try {
            return eventMask;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setEventMask(final int ops) {
        final boolean wantOutput = (ops & SelectionKey.OP_WRITE) != 0;
        lock.lock();
        try {
            eventMask = ops;
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
        if (wantOutput) {
            requestOutput();
        }
    }

    @Override
    public void setEvent(final int op) {
        final boolean wantOutput = (op & SelectionKey.OP_WRITE) != 0;
        lock.lock();
        try {
            eventMask |= op;
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
        if (wantOutput) {
            requestOutput();
        }
    }

    @Override
    public void clearEvent(final int op) {
        lock.lock();
        try {
            eventMask &= ~op;
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        boolean cancel = false;

        lock.lock();
        try {
            if (status == Status.CLOSED) {
                return;
            }
            if (closeMode == CloseMode.IMMEDIATE) {
                status = Status.CLOSED;
                localEndStreamSent = true;
                cancelPendingCommands();
                inboundQueue.clear();
                inboundBytes = 0;
                outboundQueue.clear();
                outboundBytes = 0;
                consumedBytesSinceUpdate = 0;
                cancel = true;
            } else {
                status = Status.CLOSING;
                if (dataChannelRef.get() == null && outboundBytes == 0) {
                    status = Status.CLOSED;
                    localEndStreamSent = true;
                    cancel = true;
                }
            }
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }

        if (cancel) {
            cancelStream();
        } else {
            requestOutput();
        }
    }

    @Override
    public Status getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Timeout getSocketTimeout() {
        lock.lock();
        try {
            return socketTimeout;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        lock.lock();
        try {
            socketTimeout = timeout;
            lastEventTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getLastReadTime() {
        lock.lock();
        try {
            return lastReadTime;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getLastWriteTime() {
        lock.lock();
        try {
            return lastWriteTime;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getLastEventTime() {
        lock.lock();
        try {
            return lastEventTime;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateReadTime() {
        final long now = System.currentTimeMillis();
        lock.lock();
        try {
            lastReadTime = now;
            lastEventTime = now;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateWriteTime() {
        final long now = System.currentTimeMillis();
        lock.lock();
        try {
            lastWriteTime = now;
            lastEventTime = now;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        int total = 0;
        int update = 0;
        CapacityChannel currentCapacityChannel = null;

        lock.lock();
        try {
            if (inboundQueue.isEmpty()) {
                return remoteEndStream || status == Status.CLOSED ? -1 : 0;
            }
            while (dst.hasRemaining()) {
                final ByteBuffer buffer = inboundQueue.peekFirst();
                if (buffer == null) {
                    break;
                }
                final int chunk = Math.min(dst.remaining(), buffer.remaining());
                if (chunk <= 0) {
                    break;
                }

                if (buffer.hasArray()) {
                    final int pos = buffer.position();
                    dst.put(buffer.array(), buffer.arrayOffset() + pos, chunk);
                    buffer.position(pos + chunk);
                } else {
                    for (int i = 0; i < chunk; i++) {
                        dst.put(buffer.get());
                    }
                }

                total += chunk;
                inboundBytes -= chunk;
                if (!buffer.hasRemaining()) {
                    inboundQueue.pollFirst();
                }
            }

            if (total > 0) {
                consumedBytesSinceUpdate += total;
                final long now = System.currentTimeMillis();
                lastReadTime = now;
                lastEventTime = now;
                if (capacityChannel != null && consumedBytesSinceUpdate > 0) {
                    currentCapacityChannel = capacityChannel;
                    update = consumedBytesSinceUpdate;
                    consumedBytesSinceUpdate = 0;
                }
            }
        } finally {
            lock.unlock();
        }

        if (currentCapacityChannel != null && update > 0) {
            currentCapacityChannel.update(update);
        }
        return total;
    }

    @Override
    public int write(final ByteBuffer src) {
        if (src == null || !src.hasRemaining()) {
            return 0;
        }
        int bytesAccepted = 0;

        lock.lock();
        try {
            if (status != Status.ACTIVE) {
                return 0;
            }
            final int freeSpace = OUTBOUND_BUFFER_LIMIT - outboundBytes;
            if (freeSpace <= 0) {
                return 0;
            }
            bytesAccepted = Math.min(src.remaining(), freeSpace);
            if (bytesAccepted <= 0) {
                return 0;
            }

            final byte[] data = new byte[bytesAccepted];
            src.get(data);

            outboundQueue.addLast(ByteBuffer.wrap(data));
            outboundBytes += bytesAccepted;

            final long now = System.currentTimeMillis();
            lastWriteTime = now;
            lastEventTime = now;
        } finally {
            lock.unlock();
        }

        requestOutput();
        return bytesAccepted;
    }

    @Override
    public boolean isOpen() {
        lock.lock();
        try {
            return status != Status.CLOSED && physicalSession.isOpen();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPendingCommandCount() {
        lock.lock();
        try {
            return commandQueue.size();
        } finally {
            lock.unlock();
        }
    }
}
