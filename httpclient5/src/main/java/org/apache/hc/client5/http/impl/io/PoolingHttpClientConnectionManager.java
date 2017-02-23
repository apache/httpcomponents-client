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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolPolicy;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
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

    private final StrictConnPool<HttpRoute, ManagedHttpClientConnection> pool;
    private final HttpConnectionFactory<ManagedHttpClientConnection> connFactory;
    private final HttpClientConnectionOperator connectionOperator;
    private final AtomicBoolean closed;

    private volatile SocketConfig defaultSocketConfig;
    private volatile int validateAfterInactivity;

    private static Registry<ConnectionSocketFactory> getDefaultRegistry() {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();
    }

    public PoolingHttpClientConnectionManager() {
        this(getDefaultRegistry());
    }

    public PoolingHttpClientConnectionManager(
            final long timeToLive, final TimeUnit tunit) {
        this(getDefaultRegistry(), null, null ,null, ConnPoolPolicy.LIFO, null, timeToLive, tunit);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        this(socketFactoryRegistry, null, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final DnsResolver dnsResolver) {
        this(socketFactoryRegistry, null, dnsResolver);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, connFactory, null);
    }

    public PoolingHttpClientConnectionManager(
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(getDefaultRegistry(), connFactory, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory,
            final DnsResolver dnsResolver) {
        this(socketFactoryRegistry, connFactory, null, dnsResolver, ConnPoolPolicy.LIFO, null, -1, TimeUnit.MILLISECONDS);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final ConnPoolPolicy connPoolPolicy,
            final ConnPoolListener<HttpRoute> connPoolListener,
            final long timeToLive, final TimeUnit tunit) {
        this(new DefaultHttpClientConnectionOperator(socketFactoryRegistry, schemePortResolver, dnsResolver),
            connFactory, connPoolPolicy, connPoolListener, timeToLive, tunit);
    }

    public PoolingHttpClientConnectionManager(
            final HttpClientConnectionOperator httpClientConnectionOperator,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory,
            final ConnPoolPolicy connPoolPolicy,
            final ConnPoolListener<HttpRoute> connPoolListener,
            final long timeToLive, final TimeUnit tunit) {
        super();
        this.connectionOperator = Args.notNull(httpClientConnectionOperator, "Connection operator");
        this.connFactory = connFactory != null ? connFactory : ManagedHttpClientConnectionFactory.INSTANCE;
        this.pool = new StrictConnPool<>(
                DEFAULT_MAX_CONNECTIONS_PER_ROUTE, DEFAULT_MAX_TOTAL_CONNECTIONS, timeToLive, tunit, connPoolPolicy, connPoolListener);
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Visible for test.
     */
    PoolingHttpClientConnectionManager(
            final StrictConnPool<HttpRoute, ManagedHttpClientConnection> pool,
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        super();
        this.connectionOperator = new DefaultHttpClientConnectionOperator(
                socketFactoryRegistry, schemePortResolver, dnsResolver);
        this.connFactory = ManagedHttpClientConnectionFactory.INSTANCE;
        this.pool = pool;
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
            this.pool.shutdown();
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

    @Override
    public LeaseRequest lease(
            final HttpRoute route,
            final Object state) {
        Args.notNull(route, "HTTP route");
        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection request: " + ConnPoolSupport.formatStats(null, route, state, this.pool));
        }
        final Future<PoolEntry<HttpRoute, ManagedHttpClientConnection>> leaseFuture = this.pool.lease(route, state, null);
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
                    if (validateAfterInactivity > 0) {
                        final ManagedHttpClientConnection conn = poolEntry.getConnection();
                        if (conn != null
                                && poolEntry.getUpdated() + validateAfterInactivity <= System.currentTimeMillis()) {
                            boolean stale;
                            try {
                                stale = conn.isStale();
                            } catch (IOException ignore) {
                                stale = true;
                            }
                            if (stale) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Connection " + ConnPoolSupport.getId(conn) + " is stale");
                                }
                                poolEntry.discardConnection();
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
                } catch (Exception ex) {
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
    public void release(
            final ConnectionEndpoint endpoint,
            final Object state,
            final long keepAlive, final TimeUnit timeUnit) {
        Args.notNull(endpoint, "Managed endpoint");
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = cast(endpoint).detach();
        if (entry == null) {
            return;
        }
        final ManagedHttpClientConnection conn = entry.getConnection();
        boolean reusable = conn != null && conn.isOpen();
        try {
            if (reusable) {
                final TimeUnit effectiveUnit = timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS;
                entry.updateConnection(keepAlive, effectiveUnit, state);
                if (this.log.isDebugEnabled()) {
                    final String s;
                    if (keepAlive > 0) {
                        s = "for " + (double) effectiveUnit.toMillis(keepAlive) / 1000 + " seconds";
                    } else {
                        s = "indefinitely";
                    }
                    this.log.debug("Connection " + ConnPoolSupport.getId(conn) + " can be kept alive " + s);
                }
            }
        } catch (RuntimeException ex) {
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
    public void connect(
            final ConnectionEndpoint endpoint,
            final long connectTimeout,
            final TimeUnit timeUnit,
            final HttpContext context) throws IOException {
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
                (int) (timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS).toMillis(connectTimeout),
                this.defaultSocketConfig != null ? this.defaultSocketConfig : SocketConfig.DEFAULT,
                context);
    }

    @Override
    public void upgrade(
            final ConnectionEndpoint endpoint,
            final HttpContext context) throws IOException {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = internalEndpoint.getValidatedPoolEntry();
        final HttpRoute route = poolEntry.getRoute();
        this.connectionOperator.upgrade(poolEntry.getConnection(), route.getTargetHost(), context);
    }

    @Override
    public void closeIdle(final long idleTimeout, final TimeUnit tunit) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Closing connections idle longer than " + idleTimeout + " " + tunit);
        }
        this.pool.closeIdle(idleTimeout, tunit);
    }

    @Override
    public void closeExpired() {
        this.log.debug("Closing expired connections");
        this.pool.closeExpired();
    }

    protected void enumAvailable(final Callback<PoolEntry<HttpRoute, ManagedHttpClientConnection>> callback) {
        this.pool.enumAvailable(callback);
    }

    protected void enumLeased(final Callback<PoolEntry<HttpRoute, ManagedHttpClientConnection>> callback) {
        this.pool.enumLeased(callback);
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
     * @since 4.4
     */
    public Set<HttpRoute> getRoutes() {
        return this.pool.getRoutes();
    }

    public SocketConfig getDefaultSocketConfig() {
        return this.defaultSocketConfig;
    }

    public void setDefaultSocketConfig(final SocketConfig defaultSocketConfig) {
        this.defaultSocketConfig = defaultSocketConfig;
    }

    /**
     * @see #setValidateAfterInactivity(int)
     *
     * @since 4.4
     */
    public int getValidateAfterInactivity() {
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
    public void setValidateAfterInactivity(final int ms) {
        validateAfterInactivity = ms;
    }

    static class ConfigData {

        private final Map<HttpHost, SocketConfig> socketConfigMap;
        private volatile SocketConfig defaultSocketConfig;

        ConfigData() {
            super();
            this.socketConfigMap = new ConcurrentHashMap<>();
        }

        public SocketConfig getDefaultSocketConfig() {
            return this.defaultSocketConfig;
        }

        public void setDefaultSocketConfig(final SocketConfig defaultSocketConfig) {
            this.defaultSocketConfig = defaultSocketConfig;
        }

        public SocketConfig getSocketConfig(final HttpHost host) {
            return this.socketConfigMap.get(host);
        }

        public void setSocketConfig(final HttpHost host, final SocketConfig socketConfig) {
            this.socketConfigMap.put(host, socketConfig);
        }

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
        public void shutdown() throws IOException {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                final HttpClientConnection connection = poolEntry.getConnection();
                poolEntry.discardConnection();
                if (connection != null) {
                    connection.shutdown();
                }
            }
        }

        @Override
        public void close() throws IOException {
            final PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry = poolEntryRef.get();
            if (poolEntry != null) {
                poolEntry.discardConnection();
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
            return requestExecutor.execute(request, getValidatedPoolEntry().getConnection(), context);
        }

    }

}
