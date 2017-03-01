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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.command.ShutdownType;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

abstract class AbstractHttpAsyncClientBase extends CloseableHttpAsyncClient {

    enum Status { READY, RUNNING, TERMINATED }

    final Logger log = LogManager.getLogger(getClass());

    private final AsyncPushConsumerRegistry pushConsumerRegistry;
    private final DefaultConnectingIOReactor ioReactor;
    private final ExceptionListener exceptionListener;
    private final ExecutorService executorService;
    private final AtomicReference<Status> status;

    public AbstractHttpAsyncClientBase(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final IOReactorConfig reactorConfig,
            final ThreadFactory threadFactory,
            final ThreadFactory workerThreadFactory) throws IOReactorException {
        super();
        this.ioReactor = new DefaultConnectingIOReactor(
                eventHandlerFactory,
                reactorConfig,
                workerThreadFactory,
                new Callback<IOSession>() {

                    @Override
                    public void execute(final IOSession ioSession) {
                        ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
                    }

                });
        this.pushConsumerRegistry = pushConsumerRegistry;
        this.exceptionListener = new ExceptionListener() {
            @Override
            public void onError(final Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        };
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.status = new AtomicReference<>(Status.READY);
    }

    @Override
    public final void start() {
        if (status.compareAndSet(Status.READY, Status.RUNNING)) {
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        ioReactor.execute();
                    } catch (Exception ex) {
                        exceptionListener.onError(ex);
                    }
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
    public final List<ExceptionEvent> getAuditLog() {
        return ioReactor.getAuditLog();
    }

    @Override
    public final void awaitShutdown(final long deadline, final TimeUnit timeUnit) throws InterruptedException {
        ioReactor.awaitShutdown(deadline, timeUnit);
    }

    @Override
    public final void initiateShutdown() {
        ioReactor.initiateShutdown();
    }

    @Override
    public final void shutdown(final long graceTime, final TimeUnit timeUnit) {
        ioReactor.initiateShutdown();
        ioReactor.shutdown(graceTime, timeUnit);
    }

    @Override
    public void close() {
        shutdown(5, TimeUnit.SECONDS);
    }

}
