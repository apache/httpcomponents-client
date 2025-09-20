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
package org.apache.hc.client5.http.impl.io;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.pool.DisposalCallback;

@Internal
final class OffLockDisposalCallback<T extends ModalCloseable> implements DisposalCallback<T> {

    private final DisposalCallback<T> delegate;
    private final Queue<T> gracefulQueue = new ConcurrentLinkedQueue<>();

    OffLockDisposalCallback(final DisposalCallback<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(final T closeable, final CloseMode mode) {
        if (mode == CloseMode.IMMEDIATE) {
            delegate.execute(closeable, CloseMode.IMMEDIATE);
        } else {
            gracefulQueue.offer(closeable);
        }
    }

    void drain() {
        for (T c; (c = gracefulQueue.poll()) != null; ) {
            delegate.execute(c, CloseMode.GRACEFUL);
        }
    }
}