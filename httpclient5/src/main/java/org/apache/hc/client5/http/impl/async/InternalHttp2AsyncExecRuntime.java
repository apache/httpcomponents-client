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
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;

class InternalHttp2AsyncExecRuntime implements AsyncExecRuntime {

    private final Logger log;
    private final H2ConnPool connPool;
    private final AtomicReference<Endpoint> sessionRef;
    private volatile boolean reusable;

    InternalHttp2AsyncExecRuntime(final Logger log, final H2ConnPool connPool) {
        super();
        this.log = log;
        this.connPool = connPool;
        this.sessionRef = new AtomicReference<>(null);
    }

    @Override
    public boolean isConnectionAcquired() {
        return sessionRef.get() != null;
    }

    @Override
    public Cancellable acquireConnection(
            final HttpRoute route,
            final Object object,
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        if (sessionRef.get() == null) {
            final HttpHost target = route.getTargetHost();
            final RequestConfig requestConfig = context.getRequestConfig();
            return Operations.cancellable(connPool.getSession(
                    target,
                    requestConfig.getConnectionTimeout(),
                    new FutureCallback<IOSession>() {

                        @Override
                        public void completed(final IOSession ioSession) {
                            sessionRef.set(new Endpoint(target, ioSession));
                            reusable = true;
                            callback.completed(InternalHttp2AsyncExecRuntime.this);
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
        } else {
            callback.completed(this);
            return Operations.nonCancellable();
        }
    }

    @Override
    public void releaseConnection() {
        final Endpoint endpoint = sessionRef.getAndSet(null);
        if (endpoint != null && !reusable) {
            endpoint.session.shutdown(ShutdownType.GRACEFUL);
        }
    }

    @Override
    public void discardConnection() {
        final Endpoint endpoint = sessionRef.getAndSet(null);
        if (endpoint != null) {
            endpoint.session.shutdown(ShutdownType.GRACEFUL);
        }
    }

    @Override
    public boolean validateConnection() {
        if (reusable) {
            final Endpoint endpoint = sessionRef.get();
            return endpoint != null && !endpoint.session.isClosed();
        } else {
            final Endpoint endpoint = sessionRef.getAndSet(null);
            if (endpoint != null) {
                endpoint.session.shutdown(ShutdownType.GRACEFUL);
            }
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        final Endpoint endpoint = sessionRef.get();
        return endpoint != null && !endpoint.session.isClosed();
    }


    Endpoint ensureValid() {
        final Endpoint endpoint = sessionRef.get();
        if (endpoint == null) {
            throw new IllegalStateException("I/O session not acquired / already released");
        }
        return endpoint;
    }

    @Override
    public Cancellable connect(
            final HttpClientContext context,
            final FutureCallback<AsyncExecRuntime> callback) {
        final Endpoint endpoint = ensureValid();
        if (!endpoint.session.isClosed()) {
            callback.completed(this);
            return Operations.nonCancellable();
        } else {
            final HttpHost target = endpoint.target;
            final RequestConfig requestConfig = context.getRequestConfig();
            return Operations.cancellable(connPool.getSession(target, requestConfig.getConnectionTimeout(), new FutureCallback<IOSession>() {

                @Override
                public void completed(final IOSession ioSession) {
                    sessionRef.set(new Endpoint(target, ioSession));
                    reusable = true;
                    callback.completed(InternalHttp2AsyncExecRuntime.this);
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

    }

    @Override
    public void upgradeTls(final HttpClientContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cancellable execute(final AsyncClientExchangeHandler exchangeHandler, final HttpClientContext context) {
        final ComplexCancellable complexCancellable = new ComplexCancellable();
        final Endpoint endpoint = ensureValid();
        final IOSession session = endpoint.session;
        if (!session.isClosed()) {
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": executing " + ConnPoolSupport.getId(exchangeHandler));
            }
            session.addLast(new ExecutionCommand(exchangeHandler, complexCancellable, context));
        } else {
            final HttpHost target = endpoint.target;
            final RequestConfig requestConfig = context.getRequestConfig();
            connPool.getSession(target, requestConfig.getConnectionTimeout(), new FutureCallback<IOSession>() {

                @Override
                public void completed(final IOSession ioSession) {
                    sessionRef.set(new Endpoint(target, ioSession));
                    reusable = true;
                    if (log.isDebugEnabled()) {
                        log.debug(ConnPoolSupport.getId(endpoint) + ": executing " + ConnPoolSupport.getId(exchangeHandler));
                    }
                    session.addLast(new ExecutionCommand(exchangeHandler, complexCancellable, context));
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

    static class Endpoint {

        final HttpHost target;
        final IOSession session;

        Endpoint(final HttpHost target, final IOSession session) {
            this.target = target;
            this.session = session;
        }
    }

    @Override
    public AsyncExecRuntime fork() {
        return new InternalHttp2AsyncExecRuntime(log, connPool);
    }

}
