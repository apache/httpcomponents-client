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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
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
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ShutdownType;
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
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@code ClientConnectionPoolManager} maintains a pool of
 * {@link ManagedHttpClientConnection}s and is able to service connection requests
 * from multiple execution threads. Connections are pooled on a per route
 * basis. A request for a route which already the manager has persistent
 * connections for available in the pool will be services by leasing
 * a connection from the pool rather than creating a new connection.
 * <p>
 * {@code ClientConnectionPoolManager} maintains a maximum limit of connection
 * on a per route basis and in total. Connection limits, however, can be adjusted
 * using {@link ConnPoolControl} methods.
 * <p>
 * Total time to live (TTL) set at construction time defines maximum life span
 * of persistent connections regardless of their expiration setting. No persistent
 * connection will be re-used past its TTL value.
 * <p>
 * The handling of stale connections was changed in version 4.4.
 * Previously, the code would check every connection by default before re-using it.
 * The code now only checks the connection if the elapsed time since
 * the last use of the connection exceeds the timeout that has been set.
 * The default timeout is set to 5000ms.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class PoolingHttpClientConnectionManager
    implements HttpClientConnectionManager, ConnPoolControl<HttpRoute> {

    private final Logger log = LogManager.getLogger(getClass());

    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 25;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 5;

    private final HttpClientConnectionOperator connectionOperator;
    private final ManagedConnPool<HttpRoute, ManagedHttpClientConnection> pool;
    private final HttpConnectionFactory<ManagedHttpClientConnection> connFactory;
    private final AtomicBoolean closed;

    private volatile SocketConfig defaultSocketConfig;
    private volatile TimeValue validateAfterInactivity;

    public PoolingHttpClientConnectionManager() {
        this(RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build());
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        this(socketFactoryRegistry, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, TimeValue.NEG_ONE_MILLISECONDS, connFactory);
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
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.log.debug("Connection manager is shutting down");
            this.pool.shutdown(ShutdownType.GRACEFUL);
            this.log.debug("Connection manager shut down");
        }
    }

    private InternalConnectionEndpoint cast(final ConnectionEndpoint endpoint) {
        if (endpoint instanceof InternalConnectionEndpoint) {
            return (InternalConnectionEndpoint) endpoint;
        } else {
            throw new IllegalStateException("Unexpected endpoint class: " + endpoint.getClass());
        }
    }

    public LeaseRequest lease(final HttpRoute route, final Object state) {
        return lease(route, Timeout.DISABLED, state);
    }

    @Override
    public LeaseRequest lease(
            final HttpRoute route,
            final Timeout requestTimeout,
            final Object state) {
        Args.notNull(route, "HTTP route");
        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection request: " + ConnPoolSupport.formatStats(null, route, state, this.pool));
        }
        //TODO: fix me.
        if (log.isWarnEnabled() && Timeout.isPositive(requestTimeout)) {
            log.warn("Connection request timeout is not supported");
        }
        final Future<PoolEntry<HttpRoute, ManagedHttpClientConnection>> leaseFuture = this.pool.lease(route, state, /** requestTimeout, */ null);
        return new LeaseRequest() {

            private volatile ConnectionEndpoint endpoint;

            @Override
            public synchronized ConnectionEndpoint get(
                    final long timeout,
                    final TimeUnit tunit) throws InterruptedException, ExecutionException, TimeoutException {
                if (this.endpoint != null) {
                    return this.endpoint;
                }
                final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry;
                try {
                    poolEntry = leaseFuture.get(timeout, tunit);
                    if (poolEntry == null || leaseFuture.isCancelled()) {
                        throw new InterruptedException();
                    }
                } catch (final TimeoutException ex) {
                    leaseFuture.cancel(true);
                    throw ex;
                }
                try {
                    if (TimeValue.isPositive(validateAfterInactivity)) {
                        final ManagedHttpClientConnection conn = poolEntry.getConnection();
                        if (conn != null
                                && poolEntry.getUpdated() + validateAfterInactivity.toMillis() <= System.currentTimeMillis()) {
                            boolean stale;
                            try {
                                stale = conn.isStale();
                            } catch (final IOException ignore) {
                                stale = true;
                            }
                            if (stale) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Connection " + ConnPoolSupport.getId(conn) + " is stale");
                                }
                                poolEntry.discardConnection(ShutdownType.IMMEDIATE);
                            }
                        }
                    }
                    if (!poolEntry.hasConnection()) {
                        poolEntry.assignConnection(connFactory.createConnection(null));
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Connection leased: " + ConnPoolSupport.formatStats(
                                poolEntry.getConnection(), route, state, pool));
                    }
                    if (leaseFuture.isCancelled()) {
                        pool.release(poolEntry, false);
                    } else {
                        this.endpoint = new InternalConnectionEndpoint(poolEntry);
                    }
                    return this.endpoint;
                } catch (final Exception ex) {
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
        final ManagedHttpClientConnection conn = entry.getConnection();
        if (conn != null && keepAlive == null) {
            conn.shutdown(ShutdownType.GRACEFUL);
        }
        boolean reusable = conn != null && conn.isOpen();
        try {
            if (reusable) {
                entry.updateState(state);
                entry.updateExpiry(keepAlive);
                if (this.log.isDebugEnabled()) {
                    final String s;
                    if (TimeValue.isPositive(keepAlive)) {
                        s = "for " + keepAlive;
                    } else {
                        s = "indefinitely";
                    }
                    this.log.debug("Connection " + ConnPoolSupport.getId(conn) + " can be kept alive " + s);
                }
            }
        } catch (final RuntimeException ex) {
            reusable = false;
            throw ex;
        } finally {
            this.pool.release(entry, reusable);
            if (this.log.isDebugEnabled()) {
                this.log.debug("Connection released: " + ConnPoolSupport.formatStats(
                        conn, entry.getRoute(), entry.getState(), pool));
            }
        }
    }

    @Override
    public void connect(final ConnectionEndpoint endpoint, final TimeValue connectTimeout, final HttpContext context) throws IOException {
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
        this.connectionOperator.connect(
                poolEntry.getConnection(),
                host,
                route.getLocalSocketAddress(),
                connectTimeout,
                defaultSocketConfig != null ? this.defaultSocketConfig : SocketConfig.DEFAULT,
                context);
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
        if (this.log.isDebugEnabled()) {
            this.log.debug("Closing connections idle longer than " + idleTime);
        }
        this.pool.closeIdle(idleTime);
    }

    @Override
    public void closeExpired() {
        this.log.debug("Closing expired connections");
        this.pool.closeExpired();
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

    public SocketConfig getDefaultSocketConfig() {
        return this.defaultSocketConfig;
    }

    public void setDefaultSocketConfig(final SocketConfig defaultSocketConfig) {
        this.defaultSocketConfig = defaultSocketConfig;
    }

    /**
     * @see #setValidateAfterInactivity(TimeValue)
     *
     * @since 4.4
     */
    public TimeValue getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    /**
     * Defines period of inactivity in milliseconds after which persistent connections must
     * be re-validated prior to being {@link #lease(HttpRoute, Object)}  leased} to the consumer.
     * Non-positive value passed to this method disables connection validation. This check helps
     * detect connections that have become stale (half-closed) while kept inactive in the pool.
     *
     * @see #lease(HttpRoute, Object)
     *
     * @since 4.4
     */
    public void setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
    }

    class InternalConnectionEndpoint extends ConnectionEndpoint {

        private final AtomicReference<PoolEntry<HttpRoute, ManagedHttpClientConnection>> poolEntryRef;

        InternalConnectionEndpoint(
                final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry) {
            this.poolEntryRef = new AtomicReference<>(poolEntry);
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
        public void shutdown(final ShutdownType shutdownType) {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                poolEntry.discardConnection(shutdownType);
            }
        }

        @Override
        public void close() throws IOException {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                poolEntry.discardConnection(ShutdownType.GRACEFUL);
            }
        }

        @Override
        public boolean isConnected() {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = getPoolEntry();
            final ManagedHttpClientConnection connection = poolEntry.getConnection();
            return connection != null && connection.isOpen();
        }

        @Override
        public void setSocketTimeout(final int timeout) {
            getValidatedPoolEntry().getConnection().setSocketTimeout(timeout);
        }

        @Override
        public ClassicHttpResponse execute(
                final ClassicHttpRequest request,
                final HttpRequestExecutor requestExecutor,
                final HttpContext context) throws IOException, HttpException {
            Args.notNull(request, "HTTP request");
            Args.notNull(requestExecutor, "Request executor");
            final ManagedHttpClientConnection connection = getValidatedPoolEntry().getConnection();
            return requestExecutor.execute(request, connection, context);
        }

    }

}
