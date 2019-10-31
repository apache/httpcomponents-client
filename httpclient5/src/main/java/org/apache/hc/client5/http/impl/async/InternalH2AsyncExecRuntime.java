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

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.ComplexCancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class InternalH2AsyncExecRuntime implements AsyncExecRuntime {

    private final Logger log;
    private final H2ConnPool connPool;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    private final AtomicReference<Endpoint> sessionRef;
    private volatile boolean reusable;

    InternalH2AsyncExecRuntime(
            final Logger log,
            final H2ConnPool connPool,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) {
        super();
        this.log = log;
        this.connPool = connPool;
        this.pushHandlerFactory = pushHandlerFactory;
        this.sessionRef = new AtomicReference<>(null);
    }

    @Override
    public boolean isEndpointAcquired() {
        return sessionRef.get() != null;
    }

    @Override
    public Cancellable acquireEndpoint(
            final String id,
            final HttpRoute route,
            final Object object,
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        if (sessionRef.get() == null) {
            final HttpHost target = route.getTargetHost();
            final RequestConfig requestConfig = context.getRequestConfig();
            final Timeout connectTimeout = requestConfig.getConnectTimeout();
            if (log.isDebugEnabled()) {
                log.debug(id + ": acquiring endpoint (" + connectTimeout + ")");
            }
            return Operations.cancellable(connPool.getSession(
                    target,
                    connectTimeout,
                    new FutureCallback<IOSession>() {

                        @Override
                        public void completed(final IOSession ioSession) {
                            sessionRef.set(new Endpoint(target, ioSession));
                            reusable = true;
                            if (log.isDebugEnabled()) {
                                log.debug(id + ": acquired endpoint");
                            }
                            callback.completed(InternalH2AsyncExecRuntime.this);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            callback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            callback.cancelled();
                        }

                    }));
        }
        callback.completed(this);
        return Operations.nonCancellable();
    }

    private void closeEndpoint(final Endpoint endpoint) {
        endpoint.session.close(CloseMode.GRACEFUL);
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": endpoint closed");
        }
    }

    @Override
    public void releaseEndpoint() {
        final Endpoint endpoint = sessionRef.getAndSet(null);
        if (endpoint != null && !reusable) {
            closeEndpoint(endpoint);
        }
    }

    @Override
    public void discardEndpoint() {
        final Endpoint endpoint = sessionRef.getAndSet(null);
        if (endpoint != null) {
            closeEndpoint(endpoint);
        }
    }

    @Override
    public boolean validateConnection() {
        if (reusable) {
            final Endpoint endpoint = sessionRef.get();
            return endpoint != null && endpoint.session.isOpen();
        }
        final Endpoint endpoint = sessionRef.getAndSet(null);
        if (endpoint != null) {
            closeEndpoint(endpoint);
        }
        return false;
    }

    @Override
    public boolean isEndpointConnected() {
        final Endpoint endpoint = sessionRef.get();
        return endpoint != null && endpoint.session.isOpen();
    }


    Endpoint ensureValid() {
        final Endpoint endpoint = sessionRef.get();
        if (endpoint == null) {
            throw new IllegalStateException("I/O session not acquired / already released");
        }
        return endpoint;
    }

    @Override
    public Cancellable connectEndpoint(
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        final Endpoint endpoint = ensureValid();
        if (endpoint.session.isOpen()) {
            callback.completed(this);
            return Operations.nonCancellable();
        }
        final HttpHost target = endpoint.target;
        final RequestConfig requestConfig = context.getRequestConfig();
        final Timeout connectTimeout = requestConfig.getConnectTimeout();
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": connecting endpoint (" + connectTimeout + ")");
        }
        return Operations.cancellable(connPool.getSession(target, connectTimeout,
            new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                sessionRef.set(new Endpoint(target, ioSession));
                reusable = true;
                if (log.isDebugEnabled()) {
                    log.debug(ConnPoolSupport.getId(endpoint) + ": endpoint connected");
                }
                callback.completed(InternalH2AsyncExecRuntime.this);
            }

            @Override
            public void failed(final Exception ex) {
                callback.failed(ex);
            }

            @Override
            public void cancelled() {
                callback.cancelled();
            }

        }));

    }

    @Override
    public void upgradeTls(final HttpClientContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cancellable execute(
            final String id,
            final AsyncClientExchangeHandler exchangeHandler, final HttpClientContext context) {
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final Endpoint endpoint = ensureValid();
        final IOSession session = endpoint.session;
        if (session.isOpen()) {
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": start execution " + id);
            }
            session.enqueue(
                    new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, complexCancellable, context),
                    Command.Priority.NORMAL);
        } else {
            final HttpHost target = endpoint.target;
            final RequestConfig requestConfig = context.getRequestConfig();
            final Timeout connectTimeout = requestConfig.getConnectTimeout();
            connPool.getSession(target, connectTimeout, new FutureCallback<IOSession>() {

                @Override
                public void completed(final IOSession ioSession) {
                    sessionRef.set(new Endpoint(target, ioSession));
                    reusable = true;
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(endpoint) + ": start execution " + id);
                    }
                    session.enqueue(
                            new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, complexCancellable, context),
                            Command.Priority.NORMAL);
                }

                @Override
                public void failed(final Exception ex) {
                    exchangeHandler.failed(ex);
                }

                @Override
                public void cancelled() {
                    exchangeHandler.failed(new InterruptedIOException());
                }

            });
        }
        return complexCancellable;
    }

    @Override
    public void markConnectionReusable(final Object newState, final TimeValue newValidDuration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markConnectionNonReusable() {
        reusable = false;
    }

    static class Endpoint implements Identifiable {

        final HttpHost target;
        final IOSession session;

        Endpoint(final HttpHost target, final IOSession session) {
            this.target = target;
            this.session = session;
        }

        @Override
        public String getId() {
            return session.getId();
        }

    }

    @Override
    public AsyncExecRuntime fork() {
        return new InternalH2AsyncExecRuntime(log, connPool, pushHandlerFactory);
    }

}
