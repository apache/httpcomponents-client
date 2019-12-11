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

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 25;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private final ManagedConnPool<HttpRoute, ManagedAsyncClientConnection> pool;
    private final AsyncClientConnectionOperator connectionOperator;
    private final AtomicBoolean closed;

    private volatile TimeValue validateAfterInactivity;

    public PoolingAsyncClientConnectionManager() {
        this(RegistryBuilder.<TlsStrategy>create()
                .register("https", DefaultClientTlsStrategy.getDefault())
                .build());
    }

    public PoolingAsyncClientConnectionManager(final Lookup<TlsStrategy> tlsStrategyLookup) {
        this(tlsStrategyLookup, PoolConcurrencyPolicy.STRICT, TimeValue.NEG_ONE_MILLISECOND);
    }

    public PoolingAsyncClientConnectionManager(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final TimeValue timeToLive) {
        this(tlsStrategyLookup, poolConcurrencyPolicy, PoolReusePolicy.LIFO, timeToLive);
    }

    public PoolingAsyncClientConnectionManager(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive) {
        this(tlsStrategyLookup, poolConcurrencyPolicy, poolReusePolicy, timeToLive, null, null);
    }

    public PoolingAsyncClientConnectionManager(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        this(new DefaultAsyncClientConnectionOperator(tlsStrategyLookup, schemePortResolver, dnsResolver),
                poolConcurrencyPolicy, poolReusePolicy, timeToLive);
    }

    @Internal
    protected PoolingAsyncClientConnectionManager(
            final AsyncClientConnectionOperator connectionOperator,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive) {
        this.connectionOperator = Args.notNull(connectionOperator, "Connection operator");
        switch (poolConcurrencyPolicy != null ? poolConcurrencyPolicy : PoolConcurrencyPolicy.STRICT) {
            case STRICT:
                this.pool = new StrictConnPool<>(
                        DEFAULT_MAX_CONNECTIONS_PER_ROUTE,
                        DEFAULT_MAX_TOTAL_CONNECTIONS,
                        timeToLive,
                        poolReusePolicy,
                        null);
                break;
            case LAX:
                this.pool = new LaxConnPool<>(
                        DEFAULT_MAX_CONNECTIONS_PER_ROUTE,
                        timeToLive,
                        poolReusePolicy,
                        null);
                break;
            default:
                throw new IllegalArgumentException("Unexpected PoolConcurrencyPolicy value: " + poolConcurrencyPolicy);
        }
        this.closed = new AtomicBoolean(false);
    }

    @Internal
    protected PoolingAsyncClientConnectionManager(
            final ManagedConnPool<HttpRoute, ManagedAsyncClientConnection> pool,
            final AsyncClientConnectionOperator connectionOperator) {
        this.connectionOperator = Args.notNull(connectionOperator, "Connection operator");
        this.pool = Args.notNull(pool, "Connection pool");
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.closed.compareAndSet(false, true)) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Shutdown connection pool " + closeMode);
            }
            this.pool.close(closeMode);
            this.log.debug("Connection pool shut down");
        }
    }

    private InternalConnectionEndpoint cast(final AsyncConnectionEndpoint endpoint) {
        if (endpoint instanceof InternalConnectionEndpoint) {
            return (InternalConnectionEndpoint) endpoint;
        }
        throw new IllegalStateException("Unexpected endpoint class: " + endpoint.getClass());
    }

    @Override
    public Future<AsyncConnectionEndpoint> lease(
            final String id,
            final HttpRoute route,
            final Object state,
            final Timeout requestTimeout,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        if (log.isDebugEnabled()) {
            log.debug(id + ": endpoint lease request (" + requestTimeout + ") " +
                    ConnPoolSupport.formatStats(route, state, pool));
        }
        final ComplexFuture<AsyncConnectionEndpoint> resultFuture = new ComplexFuture<>(callback);
        final Future<PoolEntry<HttpRoute, ManagedAsyncClientConnection>> leaseFuture = pool.lease(
                route, state, requestTimeout, new FutureCallback<PoolEntry<HttpRoute, ManagedAsyncClientConnection>>() {

                    void leaseCompleted(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
                        final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                        if (connection != null) {
                            connection.activate();
                        }
                        if (log.isDebugEnabled()) {
                            log.debug(id + ": endpoint leased " + ConnPoolSupport.formatStats(route, state, pool));
                        }
                        final AsyncConnectionEndpoint endpoint = new InternalConnectionEndpoint(poolEntry);
                        if (log.isDebugEnabled()) {
                            log.debug(id + ": acquired " + ConnPoolSupport.getId(endpoint));
                        }
                        resultFuture.completed(endpoint);
                    }

                    @Override
                    public void completed(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
                        final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                        final TimeValue timeValue = PoolingAsyncClientConnectionManager.this.validateAfterInactivity;
                        if (TimeValue.isPositive(timeValue) && connection != null &&
                                poolEntry.getUpdated() + timeValue.toMilliseconds() <= System.currentTimeMillis()) {
                            final ProtocolVersion protocolVersion = connection.getProtocolVersion();
                            if (HttpVersion.HTTP_2_0.greaterEquals(protocolVersion)) {
                                connection.submitCommand(new PingCommand(new BasicPingHandler(new Callback<Boolean>() {

                                    @Override
                                    public void execute(final Boolean result) {
                                        if (result == Boolean.FALSE) {
                                            if (log.isDebugEnabled()) {
                                                log.debug(id + ": connection " + ConnPoolSupport.getId(connection) + " is stale");
                                            }
                                            poolEntry.discardConnection(CloseMode.IMMEDIATE);
                                        }
                                        leaseCompleted(poolEntry);
                                    }

                                })), Command.Priority.IMMEDIATE);
                            } else {
                                if (!connection.isOpen()) {
                                    if (log.isDebugEnabled()) {
                                        log.debug(id + ": connection " + ConnPoolSupport.getId(connection) + " is closed");
                                    }
                                    poolEntry.discardConnection(CloseMode.IMMEDIATE);
                                }
                                leaseCompleted(poolEntry);
                            }
                        } else {
                            leaseCompleted(poolEntry);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        if (log.isDebugEnabled()) {
                            log.debug(id + ": endpoint lease failed");
                        }
                        resultFuture.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        if (log.isDebugEnabled()) {
                            log.debug(id + ": endpoint lease cancelled");
                        }
                        resultFuture.cancel();
                    }

                });

        resultFuture.setDependency(leaseFuture);
        return resultFuture;
    }

    @Override
    public void release(final AsyncConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        Args.notNull(endpoint, "Managed endpoint");
        Args.notNull(keepAlive, "Keep-alive time");
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> entry = cast(endpoint).detach();
        if (entry == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": releasing endpoint");
        }
        final ManagedAsyncClientConnection connection = entry.getConnection();
        boolean reusable = connection != null && connection.isOpen();
        try {
            if (reusable) {
                entry.updateState(state);
                entry.updateExpiry(keepAlive);
                connection.passivate();
                if (log.isDebugEnabled()) {
                    final String s;
                    if (TimeValue.isPositive(keepAlive)) {
                        s = "for " + keepAlive;
                    } else {
                        s = "indefinitely";
                    }
                    log.debug(ConnPoolSupport.getId(endpoint) + ": connection " + ConnPoolSupport.getId(connection) +
                            " can be kept alive " + s);
                }
            }
        } catch (final RuntimeException ex) {
            reusable = false;
            throw ex;
        } finally {
            pool.release(entry, reusable);
            if (log.isDebugEnabled()) {
                log.debug(ConnPoolSupport.getId(endpoint) + ": connection released " +
                        ConnPoolSupport.formatStats(entry.getRoute(), entry.getState(), pool));
            }
        }
    }

    @Override
    public Future<AsyncConnectionEndpoint> connect(
            final AsyncConnectionEndpoint endpoint,
            final ConnectionInitiator connectionInitiator,
            final Timeout connectTimeout,
            final Object attachment,
            final HttpContext context,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        Args.notNull(endpoint, "Endpoint");
        Args.notNull(connectionInitiator, "Connection initiator");
        Args.notNull(connectTimeout, "Timeout");
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
        if (this.log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(endpoint) + ": connecting endpoint to " + host + " (" + connectTimeout + ")");
        }
        final Future<ManagedAsyncClientConnection> connectFuture = connectionOperator.connect(
                connectionInitiator, host, localAddress, connectTimeout, attachment, new FutureCallback<ManagedAsyncClientConnection>() {

                    @Override
                    public void completed(final ManagedAsyncClientConnection connection) {
                        try {
                            if (log.isDebugEnabled()) {
                                log.debug(ConnPoolSupport.getId(endpoint) + ": connected " + ConnPoolSupport.getId(connection));
                            }
                            poolEntry.assignConnection(connection);
                            resultFuture.completed(internalEndpoint);
                        } catch (final RuntimeException ex) {
                            resultFuture.failed(ex);
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
        resultFuture.setDependency(connectFuture);
        return resultFuture;
    }

    @Override
    public void upgrade(
            final AsyncConnectionEndpoint endpoint,
            final Object attachment,
            final HttpContext context) {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = internalEndpoint.getValidatedPoolEntry();
        final HttpRoute route = poolEntry.getRoute();
        final ManagedAsyncClientConnection connection = poolEntry.getConnection();
        connectionOperator.upgrade(poolEntry.getConnection(), route.getTargetHost(), attachment);
        if (log.isDebugEnabled()) {
            log.debug(ConnPoolSupport.getId(internalEndpoint) + ": upgraded " + ConnPoolSupport.getId(connection));
        }
    }

    @Override
    public Set<HttpRoute> getRoutes() {
        return pool.getRoutes();
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
    public void closeIdle(final TimeValue idletime) {
        pool.closeIdle(idletime);
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

    public TimeValue getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    /**
     * Defines period of inactivity after which persistent connections must
     * be re-validated prior to being {@link #lease(String, HttpRoute, Object, Timeout,
     * FutureCallback)} leased} to the consumer. Non-positive value passed
     * to this method disables connection validation. This check helps detect connections
     * that have become stale (half-closed) while kept inactive in the pool.
     */
    public void setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
    }

    private static final AtomicLong COUNT = new AtomicLong(0);

    class InternalConnectionEndpoint extends AsyncConnectionEndpoint implements Identifiable {

        private final AtomicReference<PoolEntry<HttpRoute, ManagedAsyncClientConnection>> poolEntryRef;
        private final String id;

        InternalConnectionEndpoint(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
            this.id = String.format("ep-%08X", COUNT.getAndIncrement());
        }

        @Override
        public String getId() {
            return id;
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
        public void close(final CloseMode closeMode) {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                if (log.isDebugEnabled()) {
                    log.debug(id + ": close " + closeMode);
                }
                poolEntry.discardConnection(closeMode);
            }
        }

        @Override
        public boolean isConnected() {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry == null) {
                return false;
            }
            final ManagedAsyncClientConnection connection = poolEntry.getConnection();
            if (connection == null) {
                return false;
            }
            if (!connection.isOpen()) {
                poolEntry.discardConnection(CloseMode.IMMEDIATE);
                return false;
            }
            return true;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            getValidatedPoolEntry().getConnection().setSocketTimeout(timeout);
        }

        @Override
        public void execute(
                final String exchangeId,
                final AsyncClientExchangeHandler exchangeHandler,
                final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                final HttpContext context) {
            final ManagedAsyncClientConnection connection = getValidatedPoolEntry().getConnection();
            if (log.isDebugEnabled()) {
                log.debug(id + ": executing exchange " + exchangeId + " over " + ConnPoolSupport.getId(connection));
            }
            connection.submitCommand(
                    new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, context),
                    Command.Priority.NORMAL);
        }

    }

}
