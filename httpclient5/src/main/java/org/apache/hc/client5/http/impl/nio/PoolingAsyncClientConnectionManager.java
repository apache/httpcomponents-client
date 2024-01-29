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

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.EndpointInfo;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.impl.PrefixedIncrementingId;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
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
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Deadline;
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

    private static final Logger LOG = LoggerFactory.getLogger(PoolingAsyncClientConnectionManager.class);

    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 25;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private final ManagedConnPool<HttpRoute, ManagedAsyncClientConnection> pool;
    private final AsyncClientConnectionOperator connectionOperator;
    private final AtomicBoolean closed;

    private volatile Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver;
    private volatile Resolver<HttpHost, TlsConfig> tlsConfigResolver;

    public PoolingAsyncClientConnectionManager() {
        this(RegistryBuilder.<TlsStrategy>create()
                .register(URIScheme.HTTPS.getId(), DefaultClientTlsStrategy.createDefault())
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
                this.pool = new StrictConnPool<HttpRoute, ManagedAsyncClientConnection>(
                        DEFAULT_MAX_CONNECTIONS_PER_ROUTE,
                        DEFAULT_MAX_TOTAL_CONNECTIONS,
                        timeToLive,
                        poolReusePolicy,
                        null) {

                    @Override
                    public void closeExpired() {
                        enumAvailable(e -> closeIfExpired(e));
                    }

                };
                break;
            case LAX:
                this.pool = new LaxConnPool<HttpRoute, ManagedAsyncClientConnection>(
                        DEFAULT_MAX_CONNECTIONS_PER_ROUTE,
                        timeToLive,
                        poolReusePolicy,
                        null) {

                    @Override
                    public void closeExpired() {
                        enumAvailable(e -> closeIfExpired(e));
                    }

                };
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("Shutdown connection pool {}", closeMode);
            }
            this.pool.close(closeMode);
            LOG.debug("Connection pool shut down");
        }
    }

    private InternalConnectionEndpoint cast(final AsyncConnectionEndpoint endpoint) {
        if (endpoint instanceof InternalConnectionEndpoint) {
            return (InternalConnectionEndpoint) endpoint;
        }
        throw new IllegalStateException("Unexpected endpoint class: " + endpoint.getClass());
    }

    private ConnectionConfig resolveConnectionConfig(final HttpRoute route) {
        final Resolver<HttpRoute, ConnectionConfig> resolver = this.connectionConfigResolver;
        final ConnectionConfig connectionConfig = resolver != null ? resolver.resolve(route) : null;
        return connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
    }

    private TlsConfig resolveTlsConfig(final HttpHost host) {
        final Resolver<HttpHost, TlsConfig> resolver = this.tlsConfigResolver;
        TlsConfig tlsConfig = resolver != null ? resolver.resolve(host) : null;
        if (tlsConfig == null) {
            tlsConfig = TlsConfig.DEFAULT;
        }
        if (URIScheme.HTTP.same(host.getSchemeName())
                && tlsConfig.getHttpVersionPolicy() == HttpVersionPolicy.NEGOTIATE) {
            // Plain HTTP does not support protocol negotiation.
            // Fall back to HTTP/1.1
            tlsConfig = TlsConfig.copy(tlsConfig)
                    .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                    .build();
        }
        return tlsConfig;
    }

    @Override
    public Future<AsyncConnectionEndpoint> lease(
            final String id,
            final HttpRoute route,
            final Object state,
            final Timeout requestTimeout,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} endpoint lease request ({}) {}", id, requestTimeout, ConnPoolSupport.formatStats(route, state, pool));
        }
        return new Future<AsyncConnectionEndpoint>() {

            final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
            final BasicFuture<AsyncConnectionEndpoint> resultFuture = new BasicFuture<>(callback);

            final Future<PoolEntry<HttpRoute, ManagedAsyncClientConnection>> leaseFuture = pool.lease(
                    route,
                    state,
                    requestTimeout, new FutureCallback<PoolEntry<HttpRoute, ManagedAsyncClientConnection>>() {

                        @Override
                        public void completed(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
                            if (poolEntry.hasConnection()) {
                                final TimeValue timeToLive = connectionConfig.getTimeToLive();
                                if (TimeValue.isNonNegative(timeToLive)) {
                                    if (timeToLive.getDuration() == 0
                                            || Deadline.calculate(poolEntry.getCreated(), timeToLive).isExpired()) {
                                        poolEntry.discardConnection(CloseMode.GRACEFUL);
                                    }
                                }
                            }
                            if (poolEntry.hasConnection()) {
                                final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                                final TimeValue timeValue = connectionConfig.getValidateAfterInactivity();
                                if (connection.isOpen() && TimeValue.isNonNegative(timeValue)) {
                                    if (timeValue.getDuration() == 0
                                            || Deadline.calculate(poolEntry.getUpdated(), timeValue).isExpired()) {
                                        final ProtocolVersion protocolVersion = connection.getProtocolVersion();
                                        if (protocolVersion != null && protocolVersion.greaterEquals(HttpVersion.HTTP_2_0)) {
                                            connection.submitCommand(new PingCommand(new BasicPingHandler(result -> {
                                                if (result == null || !result)  {
                                                    if (LOG.isDebugEnabled()) {
                                                        LOG.debug("{} connection {} is stale", id, ConnPoolSupport.getId(connection));
                                                    }
                                                    poolEntry.discardConnection(CloseMode.GRACEFUL);
                                                }
                                                leaseCompleted(poolEntry);
                                            })), Command.Priority.IMMEDIATE);
                                            return;
                                        }
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("{} connection {} is closed", id, ConnPoolSupport.getId(connection));
                                        }
                                        poolEntry.discardConnection(CloseMode.IMMEDIATE);
                                    }
                                }
                            }
                            leaseCompleted(poolEntry);
                        }

                        void leaseCompleted(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
                            final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                            if (connection != null) {
                                connection.activate();
                            }
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} endpoint leased {}", id, ConnPoolSupport.formatStats(route, state, pool));
                            }
                            final AsyncConnectionEndpoint endpoint = new InternalConnectionEndpoint(poolEntry);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} acquired {}", id, ConnPoolSupport.getId(endpoint));
                            }
                            resultFuture.completed(endpoint);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} endpoint lease failed", id);
                            }
                            resultFuture.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} endpoint lease cancelled", id);
                            }
                            resultFuture.cancel();
                        }

                    });

            @Override
            public AsyncConnectionEndpoint get() throws InterruptedException, ExecutionException {
                return resultFuture.get();
            }

            @Override
            public AsyncConnectionEndpoint get(
                    final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return resultFuture.get(timeout, unit);
            }

            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                return leaseFuture.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isDone() {
                return resultFuture.isDone();
            }

            @Override
            public boolean isCancelled() {
                return resultFuture.isCancelled();
            }

        };
    }

    @Override
    public void release(final AsyncConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        Args.notNull(endpoint, "Managed endpoint");
        Args.notNull(keepAlive, "Keep-alive time");
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> entry = cast(endpoint).detach();
        if (entry == null) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} releasing endpoint", ConnPoolSupport.getId(endpoint));
        }
        final ManagedAsyncClientConnection connection = entry.getConnection();
        boolean reusable = connection != null && connection.isOpen();
        try {
            if (reusable) {
                entry.updateState(state);
                entry.updateExpiry(keepAlive);
                connection.passivate();
                if (LOG.isDebugEnabled()) {
                    final String s;
                    if (TimeValue.isPositive(keepAlive)) {
                        s = "for " + keepAlive;
                    } else {
                        s = "indefinitely";
                    }
                    LOG.debug("{} connection {} can be kept alive {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.getId(connection), s);
                }
            }
        } catch (final RuntimeException ex) {
            reusable = false;
            throw ex;
        } finally {
            pool.release(entry, reusable);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} connection released {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.formatStats(entry.getRoute(), entry.getState(), pool));
            }
        }
    }

    @Override
    public Future<AsyncConnectionEndpoint> connect(
            final AsyncConnectionEndpoint endpoint,
            final ConnectionInitiator connectionInitiator,
            final Timeout timeout,
            final Object attachment,
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
        final HttpHost firstHop = route.getProxyHost() != null ? route.getProxyHost(): route.getTargetHost();
        final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
        final Timeout connectTimeout = timeout != null ? timeout : connectionConfig.getConnectTimeout();

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} connecting endpoint to {} ({})", ConnPoolSupport.getId(endpoint), firstHop, connectTimeout);
        }
        final Future<ManagedAsyncClientConnection> connectFuture = connectionOperator.connect(
                connectionInitiator,
                firstHop,
                route.getTargetName(),
                route.getLocalSocketAddress(),
                connectTimeout,
                route.isTunnelled() ? null : resolveTlsConfig(route.getTargetHost()),
                context,
                new FutureCallback<ManagedAsyncClientConnection>() {

                    @Override
                    public void completed(final ManagedAsyncClientConnection connection) {
                        try {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} connected {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.getId(connection));
                            }
                            final Timeout socketTimeout = connectionConfig.getSocketTimeout();
                            if (socketTimeout != null) {
                                connection.setSocketTimeout(socketTimeout);
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
            final HttpContext context,
            final FutureCallback<AsyncConnectionEndpoint> callback) {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = internalEndpoint.getValidatedPoolEntry();
        final HttpRoute route = poolEntry.getRoute();
        final HttpHost target = route.getTargetHost();
        connectionOperator.upgrade(
                poolEntry.getConnection(),
                target,
                route.getTargetName(),
                attachment != null ? attachment : resolveTlsConfig(target),
                context,
                new CallbackContribution<ManagedAsyncClientConnection>(callback) {

                    @Override
                    public void completed(final ManagedAsyncClientConnection connection) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} upgraded {}", ConnPoolSupport.getId(internalEndpoint), ConnPoolSupport.getId(connection));
                        }
                        final TlsDetails tlsDetails = connection.getTlsDetails();
                        if (tlsDetails != null && ApplicationProtocol.HTTP_2.id.equals(tlsDetails.getApplicationProtocol())) {
                            connection.switchProtocol(ApplicationProtocol.HTTP_2.id, new CallbackContribution<ProtocolIOSession>(callback) {

                                @Override
                                public void completed(final ProtocolIOSession protocolIOSession) {
                                    if (callback != null) {
                                        callback.completed(endpoint);
                                    }
                                }

                            });
                        } else {
                            if (callback != null) {
                                callback.completed(endpoint);
                            }
                        }
                    }
                });
    }

    @Override
    public void upgrade(final AsyncConnectionEndpoint endpoint, final Object attachment, final HttpContext context) {
        upgrade(endpoint, attachment, context, null);
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

    /**
     * Sets the same {@link ConnectionConfig} for all routes
     *
     * @since 5.2
     */
    public void setDefaultConnectionConfig(final ConnectionConfig config) {
        this.connectionConfigResolver = (route) -> config;
    }

    /**
     * Sets {@link Resolver} of {@link ConnectionConfig} on a per route basis.
     *
     * @since 5.2
     */
    public void setConnectionConfigResolver(final Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver) {
        this.connectionConfigResolver = connectionConfigResolver;
    }

    /**
     * Sets the same {@link ConnectionConfig} for all hosts
     *
     * @since 5.2
     */
    public void setDefaultTlsConfig(final TlsConfig config) {
        this.tlsConfigResolver = (host) -> config;
    }

    /**
     * Sets {@link Resolver} of {@link TlsConfig} on a per host basis.
     *
     * @since 5.2
     */
    public void setTlsConfigResolver(final Resolver<HttpHost, TlsConfig> tlsConfigResolver) {
        this.tlsConfigResolver = tlsConfigResolver;
    }

    void closeIfExpired(final PoolEntry<HttpRoute, ManagedAsyncClientConnection > entry) {
        final long now = System.currentTimeMillis();
        if (entry.getExpiryDeadline().isBefore(now)) {
            entry.discardConnection(CloseMode.GRACEFUL);
        } else {
            final ConnectionConfig connectionConfig = resolveConnectionConfig(entry.getRoute());
            final TimeValue timeToLive = connectionConfig.getTimeToLive();
            if (timeToLive != null && Deadline.calculate(entry.getCreated(), timeToLive).isBefore(now)) {
                entry.discardConnection(CloseMode.GRACEFUL);
            }
        }
    }

    /**
     * @deprecated Use custom {@link #setConnectionConfigResolver(Resolver)}
     */
    @Deprecated
    public TimeValue getValidateAfterInactivity() {
        return ConnectionConfig.DEFAULT.getValidateAfterInactivity();
    }

    /**
     * Defines period of inactivity after which persistent connections must
     * be re-validated prior to being {@link #lease(String, HttpRoute, Object, Timeout,
     * FutureCallback)} leased} to the consumer. Negative values passed
     * to this method disable connection validation. This check helps detect connections
     * that have become stale (half-closed) while kept inactive in the pool.
     *
     * @deprecated Use {@link #setConnectionConfigResolver(Resolver)}.
     */
    @Deprecated
    public void setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        setDefaultConnectionConfig(ConnectionConfig.custom()
                .setValidateAfterInactivity(validateAfterInactivity)
                .build());
    }

    private static final PrefixedIncrementingId INCREMENTING_ID = new PrefixedIncrementingId("ep-");

    static class InternalConnectionEndpoint extends AsyncConnectionEndpoint implements Identifiable {

        private final AtomicReference<PoolEntry<HttpRoute, ManagedAsyncClientConnection>> poolEntryRef;
        private final String id;

        InternalConnectionEndpoint(final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
            this.id = INCREMENTING_ID.getNextId();
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
            if (poolEntry.getConnection() == null) {
                throw new ConnectionShutdownException();
            }
            return poolEntry;
        }

        PoolEntry<HttpRoute, ManagedAsyncClientConnection> detach() {
            return poolEntryRef.getAndSet(null);
        }

        @Override
        public void close(final CloseMode closeMode) {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} close {}", id, closeMode);
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} executing exchange {} over {}", id, exchangeId, ConnPoolSupport.getId(connection));
            }
            context.setProtocolVersion(connection.getProtocolVersion());
            connection.submitCommand(
                    new RequestExecutionCommand(exchangeHandler, pushHandlerFactory, context),
                    Command.Priority.NORMAL);
        }

        @Override
        public EndpointInfo getInfo() {
            final PoolEntry<HttpRoute, ManagedAsyncClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                final ManagedAsyncClientConnection connection = poolEntry.getConnection();
                if (connection != null && connection.isOpen()) {
                    final TlsDetails tlsDetails = connection.getTlsDetails();
                    return new EndpointInfo(connection.getProtocolVersion(), tlsDetails != null ? tlsDetails.getSSLSession() : null);
                }
            }
            return null;
        }

    }

    /**
     * Method that can be called to determine whether the connection manager has been shut down and
     * is closed or not.
     *
     * @return {@code true} if the connection manager has been shut down and is closed, otherwise
     * return {@code false}.
     */
    boolean isClosed() {
        return this.closed.get();
    }

}
