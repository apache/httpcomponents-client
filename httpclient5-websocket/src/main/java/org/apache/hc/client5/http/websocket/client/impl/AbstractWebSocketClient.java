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
package org.apache.hc.client5.http.websocket.client.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWebSocketClient extends CloseableWebSocketClient {

    enum Status { READY, RUNNING, TERMINATED }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractWebSocketClient.class);

    private final HttpAsyncRequester requester;
    private final ExecutorService executorService;
    private final AtomicReference<Status> status;

    AbstractWebSocketClient(final HttpAsyncRequester requester, final ThreadFactory threadFactory) {
        super();
        this.requester = Args.notNull(requester, "requester");
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.status = new AtomicReference<>(Status.READY);
    }

    @Override
    public final void start() {
        if (status.compareAndSet(Status.READY, Status.RUNNING)) {
            executorService.execute(requester::start);
        }
    }

    boolean isRunning() {
        return status.get() == Status.RUNNING;
    }

    @Override
    public final IOReactorStatus getStatus() {
        return requester.getStatus();
    }

    @Override
    public final void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        requester.awaitShutdown(waitTime);
    }

    @Override
    public final void initiateShutdown() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initiating shutdown");
        }
        requester.initiateShutdown();
    }

    void internalClose(final CloseMode closeMode) {
    }

    @Override
    public final void close(final CloseMode closeMode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutdown {}", closeMode);
        }
        requester.initiateShutdown();
        requester.close(closeMode != null ? closeMode : CloseMode.IMMEDIATE);
        executorService.shutdownNow();
        internalClose(closeMode);
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }
}
