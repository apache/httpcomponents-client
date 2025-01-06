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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.impl.nio.ExpandableBuffer;
import org.apache.hc.core5.util.Args;

/**
 * TODO: to be replaced by core functionality
 */
@Internal
abstract class AbstractSharedBuffer extends ExpandableBuffer {

    final ReentrantLock lock;
    final Condition condition;

    volatile boolean endStream;
    volatile boolean aborted;

    public AbstractSharedBuffer(final ReentrantLock lock, final int initialBufferSize) {
        super(initialBufferSize);
        this.lock = Args.notNull(lock, "Lock");
        this.condition = lock.newCondition();
    }

    @Override
    public boolean hasData() {
        lock.lock();
        try {
            return super.hasData();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        lock.lock();
        try {
            return super.capacity();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int length() {
        lock.lock();
        try {
            return super.length();
        } finally {
            lock.unlock();
        }
    }

    public void abort() {
        lock.lock();
        try {
            endStream = true;
            aborted = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        if (aborted) {
            return;
        }
        lock.lock();
        try {
            setInputMode();
            buffer().clear();
            endStream = false;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEndStream() {
        lock.lock();
        try {
            return endStream && !super.hasData();
        } finally {
            lock.unlock();
        }
    }

}
