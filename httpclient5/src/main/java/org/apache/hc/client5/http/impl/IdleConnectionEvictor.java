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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;

/**
 * This class maintains a background thread to enforce an eviction policy for expired / idle
 * persistent connections kept alive in the connection pool.
 *
 * @since 4.4
 */
public final class IdleConnectionEvictor {

    private final ThreadFactory threadFactory;
    private final Thread thread;
    private final long sleepTimeMs;
    private final long maxIdleTimeMs;

    private volatile Exception exception;

    public IdleConnectionEvictor(
            final ConnPoolControl<?> connectionManager,
            final ThreadFactory threadFactory,
            final long sleepTime, final TimeUnit sleepTimeUnit,
            final long maxIdleTime, final TimeUnit maxIdleTimeUnit) {
        Args.notNull(connectionManager, "Connection manager");
        this.threadFactory = threadFactory != null ? threadFactory : new DefaultThreadFactory("idle-connection-evictor", true);
        this.sleepTimeMs = sleepTimeUnit != null ? sleepTimeUnit.toMillis(sleepTime) : sleepTime;
        this.maxIdleTimeMs = maxIdleTimeUnit != null ? maxIdleTimeUnit.toMillis(maxIdleTime) : maxIdleTime;
        this.thread = this.threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(sleepTimeMs);
                        connectionManager.closeExpired();
                        if (maxIdleTimeMs > 0) {
                            connectionManager.closeIdle(maxIdleTimeMs, TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (final Exception ex) {
                    exception = ex;
                }

            }
        });
    }

    public IdleConnectionEvictor(
            final ConnPoolControl<?> connectionManager,
            final long sleepTime, final TimeUnit sleepTimeUnit,
            final long maxIdleTime, final TimeUnit maxIdleTimeUnit) {
        this(connectionManager, null, sleepTime, sleepTimeUnit, maxIdleTime, maxIdleTimeUnit);
    }

    public IdleConnectionEvictor(
            final ConnPoolControl<?> connectionManager,
            final long maxIdleTime, final TimeUnit maxIdleTimeUnit) {
        this(connectionManager, null,
                maxIdleTime > 0 ? maxIdleTime : 5, maxIdleTimeUnit != null ? maxIdleTimeUnit : TimeUnit.SECONDS,
                maxIdleTime, maxIdleTimeUnit);
    }

    public void start() {
        thread.start();
    }

    public void shutdown() {
        thread.interrupt();
    }

    public boolean isRunning() {
        return thread.isAlive();
    }

    public void awaitTermination(final long time, final TimeUnit tunit) throws InterruptedException {
        thread.join((tunit != null ? tunit : TimeUnit.MILLISECONDS).toMillis(time));
    }

}
