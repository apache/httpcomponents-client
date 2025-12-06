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
package org.apache.hc.client5.http.websocket.core.util;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Internal;

/**
 * Lock-free fixed-size ByteBuffer pool with a hard capacity limit.
 * Buffers are cleared before reuse. Non-matching capacities are dropped.
 *
 * @since 5.6
 */
@Internal
public final class ByteBufferPool {

    private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pooled = new AtomicInteger(0);

    private final int bufferSize;
    private final int maxCapacity;
    private final boolean direct;

    public ByteBufferPool(final int bufferSize, final int maxCapacity) {
        this(bufferSize, maxCapacity, false);
    }

    public ByteBufferPool(final int bufferSize, final int maxCapacity, final boolean direct) {
        if (bufferSize <= 0 || maxCapacity < 0) {
            throw new IllegalArgumentException("Invalid pool configuration");
        }
        this.bufferSize = bufferSize;
        this.maxCapacity = maxCapacity;
        this.direct = direct;
    }

    /**
     * Acquire a buffer or allocate a new one if the pool is empty.
     */
    public ByteBuffer acquire() {
        final ByteBuffer buf = pool.poll();
        if (buf != null) {
            pooled.decrementAndGet();
            buf.clear();
            return buf;
        }
        return direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
    }

    /**
     * Return a buffer to the pool iff it matches the configured capacity and there is room.
     */
    public void release(final ByteBuffer buffer) {
        if (buffer == null || buffer.capacity() != bufferSize) {
            return;
        }
        buffer.clear();
        for (; ; ) {
            final int n = pooled.get();
            if (n >= maxCapacity) {
                return;
            }
            if (pooled.compareAndSet(n, n + 1)) {
                pool.offer(buffer);
                return;
            }
        }
    }

    /**
     * Drain the pool.
     */
    public void clear() {
        while (pool.poll() != null) { /* drain */ }
        pooled.set(0);
    }

    public int bufferSize() {
        return bufferSize;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    public int pooledCount() {
        return pooled.get();
    }

}
