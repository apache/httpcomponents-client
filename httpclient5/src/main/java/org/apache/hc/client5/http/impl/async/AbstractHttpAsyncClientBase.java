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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

abstract class AbstractHttpAsyncClientBase extends CloseableHttpAsyncClient {

    enum Status { READY, RUNNING, TERMINATED }

    final Logger log = LogManager.getLogger(getClass());

    private final AsyncPushConsumerRegistry pushConsumerRegistry;
    private final DefaultConnectingIOReactor ioReactor;
    private final ExecutorService executorService;
    private final AtomicReference<Status> status;

    AbstractHttpAsyncClientBase(
            final DefaultConnectingIOReactor ioReactor,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final ThreadFactory threadFactory) {
        super();
        this.ioReactor = ioReactor;
        this.pushConsumerRegistry = pushConsumerRegistry;
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.status = new AtomicReference<>(Status.READY);
    }

    @Override
    public final void start() {
        if (status.compareAndSet(Status.READY, Status.RUNNING)) {
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    ioReactor.start();
                }
            });
        }
    }

    @Override
    public void register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        pushConsumerRegistry.register(hostname, uriPattern, supplier);
    }

    void ensureRunning() {
        switch (status.get()) {
            case READY:
                throw new IllegalStateException("Client is not running");
            case TERMINATED:
                throw new IllegalStateException("Client has been terminated");
        }
    }

    ConnectionInitiator getConnectionInitiator() {
        return ioReactor;
    }

    @Override
    public final IOReactorStatus getStatus() {
        return ioReactor.getStatus();
    }

    @Override
    public final List<ExceptionEvent> getExceptionLog() {
        return ioReactor.getExceptionLog();
    }

    @Override
    public final void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        ioReactor.awaitShutdown(waitTime);
    }

    @Override
    public final void initiateShutdown() {
        if (log.isDebugEnabled()) {
            log.debug("Initiating shutdown");
        }
        ioReactor.initiateShutdown();
    }

    @Override
    public final void shutdown(final ShutdownType shutdownType) {
        if (log.isDebugEnabled()) {
            log.debug("Shutdown " + shutdownType);
        }
        ioReactor.initiateShutdown();
        ioReactor.shutdown(shutdownType);
    }

    @Override
    public void close() {
        shutdown(ShutdownType.GRACEFUL);
    }

}
