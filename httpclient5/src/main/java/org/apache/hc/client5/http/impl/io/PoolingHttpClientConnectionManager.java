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
package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code ClientConnectionPoolManager} maintains a pool of
 * {@link ManagedHttpClientConnection}s and is able to service connection requests
 * from multiple execution threads. Connections are pooled on a per route
 * basis. A request for a route which already the manager has persistent
 * connections for available in the pool will be serviced by leasing
 * a connection from the pool rather than creating a new connection.
 * <p>
 * {@code ClientConnectionPoolManager} maintains a maximum limit of connection
 * on a per route basis and in total. Connection limits, however, can be adjusted
 * using {@link ConnPoolControl} methods.
 * <p>
 * Total time to live (TTL) set at construction time defines maximum life span
 * of persistent connections regardless of their expiration setting. No persistent
 * connection will be re-used past its TTL value.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class PoolingHttpClientConnectionManager
    implements HttpClientConnectionManager, ConnPoolControl<HttpRoute> {

    private static final Logger LOG = LoggerFactory.getLogger(PoolingHttpClientConnectionManager.class);

    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 25;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private final HttpClientConnectionOperator connectionOperator;
    private final ManagedConnPool<HttpRoute, ManagedHttpClientConnection> pool;
    private final HttpConnectionFactory<ManagedHttpClientConnection> connFactory;
    private final AtomicBoolean closed;

    private volatile Resolver<HttpRoute, SocketConfig> socketConfigResolver;
    private volatile Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver;

    public PoolingHttpClientConnectionManager() {
        this(RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, SSLConnectionSocketFactory.getSocketFactory())
                .build());
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        this(socketFactoryRegistry, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, TimeValue.NEG_ONE_MILLISECOND, connFactory);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final TimeValue timeToLive,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, poolConcurrencyPolicy, PoolReusePolicy.LIFO, timeToLive, connFactory);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive) {
        this(socketFactoryRegistry, poolConcurrencyPolicy, poolReusePolicy, timeToLive, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, poolConcurrencyPolicy, poolReusePolicy, timeToLive, null, null, connFactory);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(new DefaultHttpClientConnectionOperator(socketFactoryRegistry, schemePortResolver, dnsResolver),
                poolConcurrencyPolicy,
                poolReusePolicy,
                timeToLive,
                connFactory);
    }

    @Internal
    protected PoolingHttpClientConnectionManager(
            final HttpClientConnectionOperator httpClientConnectionOperator,
            final PoolConcurrencyPolicy poolConcurrencyPolicy,
            final PoolReusePolicy poolReusePolicy,
            final TimeValue timeToLive,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        super();
        this.connectionOperator = Args.notNull(httpClientConnectionOperator, "Connection operator");
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
        this.connFactory = connFactory != null ? connFactory : ManagedHttpClientConnectionFactory.INSTANCE;
        this.closed = new AtomicBoolean(false);
    }

    @Internal
    protected PoolingHttpClientConnectionManager(
            final HttpClientConnectionOperator httpClientConnectionOperator,
            final ManagedConnPool<HttpRoute, ManagedHttpClientConnection> pool,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        super();
        this.connectionOperator = Args.notNull(httpClientConnectionOperator, "Connection operator");
        this.pool = Args.notNull(pool, "Connection pool");
        this.connFactory = connFactory != null ? connFactory : ManagedHttpClientConnectionFactory.INSTANCE;
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

    private InternalConnectionEndpoint cast(final ConnectionEndpoint endpoint) {
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

    private SocketConfig resolveSocketConfig(final HttpRoute route) {
        final Resolver<HttpRoute, SocketConfig> resolver = this.socketConfigResolver;
        final SocketConfig socketConfig = resolver != null ? resolver.resolve(route) : null;
        return socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
    }

    private TimeValue resolveValidateAfterInactivity(final ConnectionConfig connectionConfig) {
        final TimeValue timeValue = connectionConfig.getValidateAfterInactivity();
        return timeValue != null ? timeValue : TimeValue.ofSeconds(2);
    }

    public LeaseRequest lease(final String id, final HttpRoute route, final Object state) {
        return lease(id, route, Timeout.DISABLED, state);
    }

    @Override
    public LeaseRequest lease(
            final String id,
            final HttpRoute route,
            final Timeout requestTimeout,
            final Object state) {
        Args.notNull(route, "HTTP route");
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} endpoint lease request ({}) {}", id, requestTimeout, ConnPoolSupport.formatStats(route, state, pool));
        }
        final Future<PoolEntry<HttpRoute, ManagedHttpClientConnection>> leaseFuture = this.pool.lease(route, state, requestTimeout, null);
        return new LeaseRequest() {

            private volatile ConnectionEndpoint endpoint;

            @Override
            public synchronized ConnectionEndpoint get(
                    final Timeout timeout) throws InterruptedException, ExecutionException, TimeoutException {
                Args.notNull(timeout, "Operation timeout");
                if (this.endpoint != null) {
                    return this.endpoint;
                }
                final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry;
                try {
                    poolEntry = leaseFuture.get(timeout.getDuration(), timeout.getTimeUnit());
                } catch (final TimeoutException ex) {
                    leaseFuture.cancel(true);
                    throw ex;
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} endpoint leased {}", id, ConnPoolSupport.formatStats(route, state, pool));
                }
                final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
                final TimeValue timeValue = resolveValidateAfterInactivity(connectionConfig);
                try {
                    if (TimeValue.isNonNegative(timeValue)) {
                        final ManagedHttpClientConnection conn = poolEntry.getConnection();
                        if (conn != null
                                && poolEntry.getUpdated() + timeValue.toMilliseconds() <= System.currentTimeMillis()) {
                            boolean stale;
                            try {
                                stale = conn.isStale();
                            } catch (final IOException ignore) {
                                stale = true;
                            }
                            if (stale) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("{} connection {} is stale", id, ConnPoolSupport.getId(conn));
                                }
                                poolEntry.discardConnection(CloseMode.IMMEDIATE);
                            }
                        }
                    }
                    final ManagedHttpClientConnection conn = poolEntry.getConnection();
                    if (conn != null) {
                        conn.activate();
                    } else {
                        poolEntry.assignConnection(connFactory.createConnection(null));
                    }
                    this.endpoint = new InternalConnectionEndpoint(poolEntry);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} acquired {}", id, ConnPoolSupport.getId(endpoint));
                    }
                    return this.endpoint;
                } catch (final Exception ex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} endpoint lease failed", id);
                    }
                    pool.release(poolEntry, false);
                    throw new ExecutionException(ex.getMessage(), ex);
                }
            }

            @Override
            public boolean cancel() {
                return leaseFuture.cancel(true);
            }

        };

    }

    @Override
    public void release(final ConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        Args.notNull(endpoint, "Managed endpoint");
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = cast(endpoint).detach();
        if (entry == null) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} releasing endpoint", ConnPoolSupport.getId(endpoint));
        }
        final ManagedHttpClientConnection conn = entry.getConnection();
        if (conn != null && keepAlive == null) {
            conn.close(CloseMode.GRACEFUL);
        }
        boolean reusable = conn != null && conn.isOpen() && conn.isConsistent();
        try {
            if (reusable) {
                entry.updateState(state);
                entry.updateExpiry(keepAlive);
                conn.passivate();
                if (LOG.isDebugEnabled()) {
                    final String s;
                    if (TimeValue.isPositive(keepAlive)) {
                        s = "for " + keepAlive;
                    } else {
                        s = "indefinitely";
                    }
                    LOG.debug("{} connection {} can be kept alive {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.getId(conn), s);
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} connection is not kept alive", ConnPoolSupport.getId(endpoint));
                }
            }
        } catch (final RuntimeException ex) {
            reusable = false;
            throw ex;
        } finally {
            this.pool.release(entry, reusable);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} connection released {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.formatStats(entry.getRoute(), entry.getState(), pool));
            }
        }
    }

    @Override
    public void connect(final ConnectionEndpoint endpoint, final TimeValue timeout, final HttpContext context) throws IOException {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        if (internalEndpoint.isConnected()) {
            return;
        }
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = internalEndpoint.getPoolEntry();
        if (!poolEntry.hasConnection()) {
            poolEntry.assignConnection(connFactory.createConnection(null));
        }
        final HttpRoute route = poolEntry.getRoute();
        final HttpHost host;
        if (route.getProxyHost() != null) {
            host = route.getProxyHost();
        } else {
            host = route.getTargetHost();
        }
        final SocketConfig socketConfig = resolveSocketConfig(route);
        final ConnectionConfig connectionConfig = resolveConnectionConfig(route);
        final TimeValue connectTimeout = timeout != null ? timeout : connectionConfig.getConnectTimeout();
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} connecting endpoint to {} ({})", ConnPoolSupport.getId(endpoint), host, connectTimeout);
        }
        final ManagedHttpClientConnection conn = poolEntry.getConnection();
        this.connectionOperator.connect(
                conn,
                host,
                route.getLocalSocketAddress(),
                timeout,
                socketConfig,
                context);
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} connected {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.getId(conn));
        }
        final Timeout socketTimeout = connectionConfig.getSocketTimeout();
        if (socketTimeout != null) {
            conn.setSocketTimeout(socketTimeout);
        }
    }

    @Override
    public void upgrade(final ConnectionEndpoint endpoint, final HttpContext context) throws IOException {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = internalEndpoint.getValidatedPoolEntry();
        final HttpRoute route = poolEntry.getRoute();
        this.connectionOperator.upgrade(poolEntry.getConnection(), route.getTargetHost(), context);
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        Args.notNull(idleTime, "Idle time");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing connections idle longer than {}", idleTime);
        }
        this.pool.closeIdle(idleTime);
    }

    @Override
    public void closeExpired() {
        LOG.debug("Closing expired connections");
        this.pool.closeExpired();
    }

    @Override
    public Set<HttpRoute> getRoutes() {
        return this.pool.getRoutes();
    }

    @Override
    public int getMaxTotal() {
        return this.pool.getMaxTotal();
    }

    @Override
    public void setMaxTotal(final int max) {
        this.pool.setMaxTotal(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return this.pool.getDefaultMaxPerRoute();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        this.pool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getMaxPerRoute(final HttpRoute route) {
        return this.pool.getMaxPerRoute(route);
    }

    @Override
    public void setMaxPerRoute(final HttpRoute route, final int max) {
        this.pool.setMaxPerRoute(route, max);
    }

    @Override
    public PoolStats getTotalStats() {
        return this.pool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final HttpRoute route) {
        return this.pool.getStats(route);
    }

    /**
     * Sets the same {@link SocketConfig} for all routes
     */
    public void setDefaultSocketConfig(final SocketConfig config) {
        this.socketConfigResolver = (route) -> config;
    }

    /**
     * Sets {@link Resolver} of {@link SocketConfig} on a per route basis.
     *
     * @since 5.2
     */
    public void setSocketConfigResolver(final Resolver<HttpRoute, SocketConfig> socketConfigResolver) {
        this.socketConfigResolver = socketConfigResolver;
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
     * @deprecated Use custom {@link #setConnectionConfigResolver(Resolver)}
     */
    @Deprecated
    public SocketConfig getDefaultSocketConfig() {
        return SocketConfig.DEFAULT;
    }

    /**
     * @since 4.4
     *
     * @deprecated Use {@link #setConnectionConfigResolver(Resolver)}.
     */
    @Deprecated
    public TimeValue getValidateAfterInactivity() {
        return ConnectionConfig.DEFAULT.getValidateAfterInactivity();
    }

    /**
     * Defines period of inactivity after which persistent connections must
     * be re-validated prior to being {@link #lease(String, HttpRoute, Object)} leased} to the consumer.
     * Negative values passed to this method disable connection validation. This check helps
     * detect connections that have become stale (half-closed) while kept inactive in the pool.
     *
     * @since 4.4
     *
     * @deprecated Use {@link #setConnectionConfigResolver(Resolver)}.
     */
    @Deprecated
    public void setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        setDefaultConnectionConfig(ConnectionConfig.custom()
                .setValidateAfterInactivity(validateAfterInactivity)
                .build());
    }

    private static final AtomicLong COUNT = new AtomicLong(0);

    class InternalConnectionEndpoint extends ConnectionEndpoint implements Identifiable {

        private final AtomicReference<PoolEntry<HttpRoute, ManagedHttpClientConnection>> poolEntryRef;
        private final String id;

        InternalConnectionEndpoint(
                final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
            this.id = String.format("ep-%010d", COUNT.getAndIncrement());
        }

        @Override
        public String getId() {
            return id;
        }

        PoolEntry<HttpRoute, ManagedHttpClientConnection> getPoolEntry() {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            return poolEntry;
        }

        PoolEntry<HttpRoute, ManagedHttpClientConnection> getValidatedPoolEntry() {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = getPoolEntry();
            final ManagedHttpClientConnection connection = poolEntry.getConnection();
            Asserts.check(connection != null && connection.isOpen(), "Endpoint is not connected");
            return poolEntry;
        }

        PoolEntry<HttpRoute, ManagedHttpClientConnection> detach() {
            return poolEntryRef.getAndSet(null);
        }

        @Override
        public void close(final CloseMode closeMode) {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                poolEntry.discardConnection(closeMode);
            }
        }

        @Override
        public void close() throws IOException {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                poolEntry.discardConnection(CloseMode.GRACEFUL);
            }
        }

        @Override
        public boolean isConnected() {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = getPoolEntry();
            final ManagedHttpClientConnection connection = poolEntry.getConnection();
            return connection != null && connection.isOpen();
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            getValidatedPoolEntry().getConnection().setSocketTimeout(timeout);
        }

        @Override
        public ClassicHttpResponse execute(
                final String exchangeId,
                final ClassicHttpRequest request,
                final HttpRequestExecutor requestExecutor,
                final HttpContext context) throws IOException, HttpException {
            Args.notNull(request, "HTTP request");
            Args.notNull(requestExecutor, "Request executor");
            final ManagedHttpClientConnection connection = getValidatedPoolEntry().getConnection();
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} executing exchange {} over {}", id, exchangeId, ConnPoolSupport.getId(connection));
            }
            return requestExecutor.execute(request, connection, context);
        }

    }

}
