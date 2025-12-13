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
package org.apache.hc.client5.http.websocket.client.impl.connector;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.EndpointParameters;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * Facade that leases an IOSession from the pool and exposes a ProtocolIOSession through AsyncClientEndpoint.
 *
 * @since 5.6
 */
@Internal
public final class WebSocketEndpointConnector {

    private final HttpAsyncRequester requester;
    private final ManagedConnPool<HttpHost, IOSession> connPool;

    public WebSocketEndpointConnector(final HttpAsyncRequester requester, final ManagedConnPool<HttpHost, IOSession> connPool) {
        this.requester = Args.notNull(requester, "requester");
        this.connPool = Args.notNull(connPool, "connPool");
    }

    public final class ProtoEndpoint extends AsyncClientEndpoint {

        private final AtomicReference<PoolEntry<HttpHost, IOSession>> poolEntryRef;

        ProtoEndpoint(final PoolEntry<HttpHost, IOSession> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
        }

        private PoolEntry<HttpHost, IOSession> getPoolEntryOrThrow() {
            final PoolEntry<HttpHost, IOSession> pe = poolEntryRef.get();
            if (pe == null) {
                throw new IllegalStateException("Endpoint has already been released");
            }
            return pe;
        }

        private IOSession getIOSessionOrThrow() {
            final IOSession io = getPoolEntryOrThrow().getConnection();
            if (io == null) {
                throw new IllegalStateException("I/O session is invalid");
            }
            return io;
        }

        /**
         * Expose the ProtocolIOSession for protocol switching.
         */
        public ProtocolIOSession getProtocolIOSession() {
            final IOSession io = getIOSessionOrThrow();
            if (!(io instanceof ProtocolIOSession)) {
                throw new IllegalStateException("Underlying IOSession is not a ProtocolIOSession: " + io);
            }
            return (ProtocolIOSession) io;
        }

        @Override
        public void execute(final AsyncClientExchangeHandler exchangeHandler,
                            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                            final HttpContext context) {
            Args.notNull(exchangeHandler, "Exchange handler");
            final IOSession ioSession = getIOSessionOrThrow();
            ioSession.enqueue(new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, null, context), Command.Priority.NORMAL);
            if (!ioSession.isOpen()) {
                try {
                    exchangeHandler.failed(new org.apache.hc.core5.http.ConnectionClosedException());
                } finally {
                    exchangeHandler.releaseResources();
                }
            }
        }

        @Override
        public boolean isConnected() {
            final PoolEntry<HttpHost, IOSession> pe = poolEntryRef.get();
            final IOSession io = pe != null ? pe.getConnection() : null;
            return io != null && io.isOpen();
        }

        @Override
        public void releaseAndReuse() {
            final PoolEntry<HttpHost, IOSession> pe = poolEntryRef.getAndSet(null);
            if (pe != null) {
                final IOSession io = pe.getConnection();
                connPool.release(pe, io != null && io.isOpen());
            }
        }

        @Override
        public void releaseAndDiscard() {
            final PoolEntry<HttpHost, IOSession> pe = poolEntryRef.getAndSet(null);
            if (pe != null) {
                pe.discardConnection(CloseMode.GRACEFUL);
                connPool.release(pe, false);
            }
        }
    }

    public Future<ProtoEndpoint> connect(final HttpHost host,
                                         final Timeout timeout,
                                         final Object attachment,
                                         final FutureCallback<ProtoEndpoint> callback) {
        Args.notNull(host, "Host");
        Args.notNull(timeout, "Timeout");

        final ComplexFuture<ProtoEndpoint> resultFuture = new ComplexFuture<>(callback);

        final Future<PoolEntry<HttpHost, IOSession>> leaseFuture = connPool.lease(host, null, timeout,
                new FutureCallback<PoolEntry<HttpHost, IOSession>>() {
                    @Override
                    public void completed(final PoolEntry<HttpHost, IOSession> poolEntry) {
                        final ProtoEndpoint endpoint = new ProtoEndpoint(poolEntry);
                        final IOSession ioSession = poolEntry.getConnection();
                        if (ioSession != null && !ioSession.isOpen()) {
                            poolEntry.discardConnection(CloseMode.IMMEDIATE);
                        }
                        if (poolEntry.hasConnection()) {
                            resultFuture.completed(endpoint);
                        } else {
                            final Future<IOSession> future = requester.requestSession(
                                    host, timeout,
                                    new EndpointParameters(host, attachment),
                                    new FutureCallback<IOSession>() {
                                        @Override
                                        public void completed(final IOSession session) {
                                            session.setSocketTimeout(timeout);
                                            poolEntry.assignConnection(session);
                                            resultFuture.completed(endpoint);
                                        }

                                        @Override
                                        public void failed(final Exception cause) {
                                            try {
                                                resultFuture.failed(cause);
                                            } finally {
                                                endpoint.releaseAndDiscard();
                                            }
                                        }

                                        @Override
                                        public void cancelled() {
                                            try {
                                                resultFuture.cancel();
                                            } finally {
                                                endpoint.releaseAndDiscard();
                                            }
                                        }
                                    });
                            resultFuture.setDependency(future);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        resultFuture.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        resultFuture.cancel();
                    }
                });

        resultFuture.setDependency(leaseFuture);
        return resultFuture;
    }
}
