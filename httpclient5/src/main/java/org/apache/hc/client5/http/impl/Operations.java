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

package org.apache.hc.client5.http.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.core5.concurrent.Cancellable;

/**
 * Common cancellable operations.
 *
 * @since 5.0
 */
public final class Operations {

    private final static Cancellable NOOP_CANCELLABLE = new Cancellable() {

        @Override
        public boolean cancel() {
            return false;
        }

    };

    /**
     * This class represents a {@link Future} in the completed state with a fixed result.
     * The outcome of the future cannot be altered and it cannot be cancelled.
     *
     * @param <T> operation result representation.
     */
    public static class CompletedFuture<T> implements Future<T> {

        private final T result;

        public CompletedFuture(final T result) {
            this.result = result;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return result;
        }

        @Override
        public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return result;
        }
        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

    }

    /**
     * Creates a {@link Cancellable} operation handle for an ongoing process
     * or operation that cannot be cancelled. Attempts to cancel the operation
     * with this handle will have no effect.
     *
     * @return the no-op cancellable operation handle.
     */
    public static Cancellable nonCancellable() {
        return NOOP_CANCELLABLE;
    }

    /**
     * Creates a {@link Cancellable} operation handle for an ongoing process
     * or operation represented by a {@link Future}.
     *
     * @param future the result future
     * @return the cancellable operation handle.
     */
    public static Cancellable cancellable(final Future<?> future) {
        if (future == null) {
            return NOOP_CANCELLABLE;
        }
        if (future instanceof Cancellable) {
            return (Cancellable) future;
        }
        return new Cancellable() {

            @Override
            public boolean cancel() {
                return future.cancel(true);
            }

        };
    }

}
