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

import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * This class maintains a background thread to enforce an eviction policy for expired / idle
 * persistent connections kept alive in the connection pool.
 *
 * @since 4.4
 */
public final class IdleConnectionEvictor {

    private final ThreadFactory threadFactory;
    private final Thread thread;

    public IdleConnectionEvictor(final ConnPoolControl<?> connectionManager, final ThreadFactory threadFactory,
                                 final TimeValue sleepTime, final TimeValue maxIdleTime) {
        Args.notNull(connectionManager, "Connection manager");
        this.threadFactory = threadFactory != null ? threadFactory : new DefaultThreadFactory("idle-connection-evictor", true);
        final TimeValue localSleepTime = sleepTime != null ? sleepTime : TimeValue.ofSeconds(5);
        this.thread = this.threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(localSleepTime.toMillis());
                        connectionManager.closeExpired();
                        if (maxIdleTime != null) {
                            connectionManager.closeIdle(maxIdleTime);
                        }
                    }
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (final Exception ex) {
                }

            }
        });
    }

    public IdleConnectionEvictor(final ConnPoolControl<?> connectionManager, final TimeValue sleepTime, final TimeValue maxIdleTime) {
        this(connectionManager, null, sleepTime, maxIdleTime);
    }

    public IdleConnectionEvictor(final ConnPoolControl<?> connectionManager, final TimeValue maxIdleTime) {
        this(connectionManager, null, maxIdleTime, maxIdleTime);
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
