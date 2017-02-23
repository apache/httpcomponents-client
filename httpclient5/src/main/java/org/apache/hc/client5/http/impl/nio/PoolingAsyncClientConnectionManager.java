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

package org.apache.hc.client5.http.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.impl.ComplexFuture;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.utils.Identifiable;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolPolicy;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@code PoolingAsyncClientConnectionManager} maintains a pool of non-blocking
 * {@link org.apache.hc.core5.http.HttpConnection}s and is able to service
 * connection requests from multiple execution threads. Connections are pooled
 * on a per route basis. A request for a route which already the manager has
 * persistent connections for available in the pool will be services by leasing
 * a connection from the pool rather than creating a new connection.
 * <p>
 * {@code PoolingAsyncClientConnectionManager} maintains a maximum limit
 * of connection on a per route basis and in total. Connection limits
 * can be adjusted using {@link ConnPoolControl} methods.
 * <p>
 * Total time to live (TTL) set at construction time defines maximum life span
 * of persistent connections regardless of their expiration setting. No persistent
 * connection will be re-used past its TTL value.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class PoolingAsyncClientConnectionManager implements AsyncClientConnectionManager, ConnPoolControl<HttpRoute> {

    private final Logger log = LogManager.getLogger(getClass());

    private final AsyncClientConnectionOperator connectionOperator;
    private final StrictConnPool<HttpRoute, ManagedAsyncClientConnection> pool;
    private final AtomicBoolean closed;

    private volatile int validateAfterInactivity;

    public PoolingAsyncClientConnectionManager(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final long timeToLive,
            final TimeUnit timeUnit,
            final ConnPoolPolicy policy,
            final ConnPoolListener<HttpRoute> connPoolListener) {
        this.connectionOperator = new AsyncClientConnectionOperator(schemePortResolver, dnsResolver, tlsStrategyLookup);
        this.pool = new StrictConnPool<>(20, 50, timeToLive, timeUnit,
                policy != null ? policy : ConnPoolPolicy.LIFO, connPoolListener);
        this.closed = new AtomicBoolean(false);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.debug("Connection manager is shutting down");
            pool.shutdown();
            log.debug("Connection manager shut down");
        }
    }

    private InternalConnectionEndpoint cast(final AsyncConnectionEndpoint endpoint) {
        if (endpoint instanceof InternalConnectionEndpoint) {
            return (InternalConnectionEndpoint) endpoint;
        } else {
            throw new IllegalStateException("Unexpected endpoint class: " + endpoint.getClass());
        }
    }

    @Override
    public Future<AsyncConnectionEndpoint> lease(
            final HttpRoute route,
            final Object state,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        if (log.isDebugEnabled()) {
            log.debug("Connection request: " + ConnPoolSupport.formatStats(null, route, state, pool));
        }
        final ComplexFuture<AsyncConnectionEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<PoolEntry<HttpRoute, ManagedAsyncClientConnection>> leaseFuture = pool.lease(
                route, state, timeout, timeUnit, new FutureCallback<PoolEntry<HttpRoute, ManagedAsyncClientConnection>>() {

                    void leaseCompleted(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
                        resultFuture.completed(new InternalConnectionEndpoint(poolEntry));
                        if (log.isDebugEnabled()) {
                            log.debug("Connection leased: " + ConnPoolSupport.formatStats(poolEntry.getConnection(), route, state, pool));
                        }
                    }

                    @Override
                    public void completed(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
                        final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                        if (connection != null && validateAfterInactivity > 0 &&
                                poolEntry.getUpdated() + validateAfterInactivity <= System.currentTimeMillis()) {
                            final ProtocolVersion protocolVersion = connection.getProtocolVersion();
                            if (HttpVersion.HTTP_2_0.greaterEquals(protocolVersion)) {
                                connection.submitPriorityCommand(new PingCommand(new BasicPingHandler(new Callback<Boolean>() {

                                    @Override
                                    public void execute(final Boolean result) {
                                        if (result == Boolean.FALSE) {
                                            if (log.isDebugEnabled()) {
                                                log.debug("Connection " + ConnPoolSupport.getId(connection) + " is stale");
                                            }
                                            poolEntry.discardConnection();
                                        }
                                        leaseCompleted(poolEntry);
                                    }

                                })));
                            } else {
                                leaseCompleted(poolEntry);
                            }
                        } else {
                            leaseCompleted(poolEntry);
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

    @Override
    public void release(
            final AsyncConnectionEndpoint endpoint,
            final Object state,
            final long keepAlive, final TimeUnit timeUnit) {
        Args.notNull(endpoint, "Managed endpoint");
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> entry = cast(endpoint).detach();
        if (entry == null) {
            return;
        }
        final ManagedAsyncClientConnection connection = entry.getConnection();
        boolean reusable = connection != null && connection.isOpen();
        try {
            if (reusable) {
                final TimeUnit effectiveUnit = timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS;
                entry.updateConnection(keepAlive, effectiveUnit, state);
                if (log.isDebugEnabled()) {
                    final String s;
                    if (keepAlive > 0) {
                        s = "for " + (double) effectiveUnit.toMillis(keepAlive) / 1000 + " seconds";
                    } else {
                        s = "indefinitely";
                    }
                    log.debug("Connection " + ConnPoolSupport.getId(connection) + " can be kept alive " + s);
                }
            }
        } catch (RuntimeException ex) {
            reusable = false;
            throw ex;
        } finally {
            pool.release(entry, reusable);
            if (log.isDebugEnabled()) {
                log.debug("Connection released: " + ConnPoolSupport.formatStats(
                        connection, entry.getRoute(), entry.getState(), pool));
            }
        }
    }

    @Override
    public Future<AsyncConnectionEndpoint> connect(
            final AsyncConnectionEndpoint endpoint,
            final ConnectionInitiator connectionInitiator,
            final long timeout,
            final TimeUnit timeUnit,
            final HttpContext context,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        Args.notNull(endpoint, "Endpoint");
        Args.notNull(connectionInitiator, "Connection initiator");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final ComplexFuture<AsyncConnectionEndpoint> resultFuture = new ComplexFuture<>(callback);
        if (internalEndpoint.isConnected()) {
            resultFuture.completed(endpoint);
            return resultFuture;
        }
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = internalEndpoint.getPoolEntry();
        final HttpRoute route = poolEntry.getRoute();
        final HttpHost host;
        if (route.getProxyHost() != null) {
            host = route.getProxyHost();
        } else {
            host = route.getTargetHost();
        }
        final InetSocketAddress localAddress = route.getLocalSocketAddress();
        final Future<ManagedAsyncClientConnection> connectFuture = connectionOperator.connect(
                connectionInitiator, host, localAddress, timeout, timeUnit, new FutureCallback<ManagedAsyncClientConnection>() {

                    @Override
                    public void completed(final ManagedAsyncClientConnection connection) {
                        poolEntry.assignConnection(connection);
                        resultFuture.completed(new InternalConnectionEndpoint(poolEntry));
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
        resultFuture.setDependency(connectFuture);
        return resultFuture;
    }

    @Override
    public void upgrade(
            final AsyncConnectionEndpoint endpoint,
            final HttpContext context) {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = internalEndpoint.getValidatedPoolEntry();
        final HttpRoute route = poolEntry.getRoute();
        connectionOperator.upgrade(poolEntry.getConnection(), route.getTargetHost());
    }

    @Override
    public void setMaxTotal(final int max) {
        pool.setMaxTotal(max);
    }

    @Override
    public int getMaxTotal() {
        return pool.getMaxTotal();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        pool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return pool.getDefaultMaxPerRoute();
    }

    @Override
    public void setMaxPerRoute(final HttpRoute route, final int max) {
        pool.setMaxPerRoute(route, max);
    }

    @Override
    public int getMaxPerRoute(final HttpRoute route) {
        return pool.getMaxPerRoute(route);
    }

    @Override
    public void closeIdle(final long idletime, final TimeUnit tunit) {
        pool.closeIdle(idletime, tunit);
    }

    @Override
    public void closeExpired() {
        pool.closeExpired();
    }

    @Override
    public PoolStats getTotalStats() {
        return pool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final HttpRoute route) {
        return pool.getStats(route);
    }

    public int getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    /**
     * Defines period of inactivity in milliseconds after which persistent connections must
     * be re-validated prior to being {@link #lease(HttpRoute, Object, long, TimeUnit,
     * FutureCallback)} leased} to the consumer. Non-positive value passed
     * to this method disables connection validation. This check helps detect connections
     * that have become stale (half-closed) while kept inactive in the pool.
     */
    public void setValidateAfterInactivity(final int ms) {
        validateAfterInactivity = ms;
    }

    class InternalConnectionEndpoint extends AsyncConnectionEndpoint implements Identifiable {

        private final AtomicReference<PoolEntry<HttpRoute, ManagedAsyncClientConnection>> poolEntryRef;

        InternalConnectionEndpoint(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
        }

        @Override
        public String getId() {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            final ManagedAsyncClientConnection connection = poolEntry != null ? poolEntry.getConnection() : null;
            return ConnPoolSupport.getId(connection);
        }

        PoolEntry<HttpRoute, ManagedAsyncClientConnection> getPoolEntry() {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            return poolEntry;
        }

        PoolEntry<HttpRoute, ManagedAsyncClientConnection> getValidatedPoolEntry() {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = getPoolEntry();
            final ManagedAsyncClientConnection connection = poolEntry.getConnection();
            Asserts.check(connection != null && connection.isOpen(), "Endpoint is not connected");
            return poolEntry;
        }

        PoolEntry<HttpRoute, ManagedAsyncClientConnection> detach() {
            return poolEntryRef.getAndSet(null);
        }

        @Override
        public void shutdown() throws IOException {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                poolEntry.discardConnection();
                if (connection != null) {
                    connection.shutdown();
                }
            }
        }

        @Override
        public void close() throws IOException {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                poolEntry.discardConnection();
            }
        }

        @Override
        public boolean isConnected() {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            final ManagedAsyncClientConnection connection = poolEntry.getConnection();
            return connection != null && connection.isOpen();
        }

        @Override
        public void setSocketTimeout(final int timeout) {
            getValidatedPoolEntry().getConnection().setSocketTimeout(timeout);
        }

        @Override
        public void execute(final AsyncClientExchangeHandler exchangeHandler, final HttpContext context) {
            getValidatedPoolEntry().getConnection().submitCommand(new ExecutionCommand(exchangeHandler, context));
        }

    }

}
