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

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.util.Args;

public final class ComplexFuture<T> extends BasicFuture<T> {

    private final AtomicReference<Cancellable> dependencyRef;

    public ComplexFuture(final FutureCallback<T> callback) {
        super(callback);
        this.dependencyRef = new AtomicReference<>(null);
    }

    public void setDependency(final Cancellable dependency) {
        Args.notNull(dependency, "dependency");
        if (isDone()) {
            dependency.cancel();
        } else {
            dependencyRef.set(dependency);
        }
    }

    public void setDependency(final Future<?> dependency) {
        Args.notNull(dependency, "dependency");
        setDependency(new Cancellable() {

            @Override
            public boolean cancel() {
                return dependency.cancel(true);
            }

        });
    }

    public void clearDependency() {
        dependencyRef.set(null);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        final boolean cancelled = super.cancel(mayInterruptIfRunning);
        final Cancellable dependency = dependencyRef.getAndSet(null);
        if (dependency != null) {
            dependency.cancel();
        }
        return cancelled;
    }

}
