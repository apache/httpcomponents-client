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
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
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
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Deadline;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A connection manager for a single connection. This connection manager maintains only one active
 * connection. Even though this class is fully thread-safe it ought to be used by one execution
 * thread only, as only one thread a time can lease the connection at a time.
 * <p>
 * This connection manager will make an effort to reuse the connection for subsequent requests
 * with the same {@link HttpRoute route}. It will, however, close the existing connection and
 * open it for the given route, if the route of the persistent connection does not match that
 * of the connection request. If the connection has been already been allocated
 * {@link IllegalStateException} is thrown.
 * </p>
 * <p>
 * This connection manager implementation should be used inside an EJB container instead of
 * {@link PoolingHttpClientConnectionManager}.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class BasicHttpClientConnectionManager implements HttpClientConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(BasicHttpClientConnectionManager.class);

    private static final AtomicLong COUNT = new AtomicLong(0);

    private final HttpClientConnectionOperator connectionOperator;
    private final HttpConnectionFactory<ManagedHttpClientConnection> connFactory;
    private final String id;

    private final ReentrantLock lock;

    private ManagedHttpClientConnection conn;
    private HttpRoute route;
    private Object state;
    private long created;
    private long updated;
    private long expiry;
    private boolean leased;
    private SocketConfig socketConfig;
    private ConnectionConfig connectionConfig;
    private TlsConfig tlsConfig;

    private final AtomicBoolean closed;

    private static Registry<ConnectionSocketFactory> getDefaultRegistry() {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, SSLConnectionSocketFactory.getSocketFactory())
                .build();
    }

    public BasicHttpClientConnectionManager(
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
      this(new DefaultHttpClientConnectionOperator(
              socketFactoryRegistry, schemePortResolver, dnsResolver), connFactory);
    }

    /**
     * @since 4.4
     */
    public BasicHttpClientConnectionManager(
            final HttpClientConnectionOperator httpClientConnectionOperator,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        super();
        this.connectionOperator = Args.notNull(httpClientConnectionOperator, "Connection operator");
        this.connFactory = connFactory != null ? connFactory : ManagedHttpClientConnectionFactory.INSTANCE;
        this.id = String.format("ep-%010d", COUNT.getAndIncrement());
        this.expiry = Long.MAX_VALUE;
        this.socketConfig = SocketConfig.DEFAULT;
        this.connectionConfig = ConnectionConfig.DEFAULT;
        this.tlsConfig = TlsConfig.DEFAULT;
        this.closed = new AtomicBoolean(false);
        this.lock = new ReentrantLock();
    }

    public BasicHttpClientConnectionManager(
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, connFactory, null, null);
    }

    public BasicHttpClientConnectionManager(
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry) {
        this(socketFactoryRegistry, null, null, null);
    }

    public BasicHttpClientConnectionManager() {
        this(getDefaultRegistry(), null, null, null);
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.closed.compareAndSet(false, true)) {
            closeConnection(closeMode);
        }
    }

    HttpRoute getRoute() {
        return route;
    }

    Object getState() {
        return state;
    }

    public SocketConfig getSocketConfig() {
        lock.lock();
        try {
            return socketConfig;
        } finally {
            lock.unlock();
        }
    }

    public void setSocketConfig(final SocketConfig socketConfig) {
        lock.lock();
        try {
            this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @since 5.2
     */
    public ConnectionConfig getConnectionConfig() {
        lock.lock();
        try {
            return connectionConfig;
        } finally {
            lock.unlock();
        }

    }

    /**
     * @since 5.2
     */
    public void setConnectionConfig(final ConnectionConfig connectionConfig) {
        lock.lock();
        try {
            this.connectionConfig = connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @since 5.2
     */
    public TlsConfig getTlsConfig() {
        lock.lock();
        try {
            return tlsConfig;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @since 5.2
     */
    public void setTlsConfig(final TlsConfig tlsConfig) {
        lock.lock();
        try {
            this.tlsConfig = tlsConfig != null ? tlsConfig : TlsConfig.DEFAULT;
        } finally {
            lock.unlock();
        }
    }

    public LeaseRequest lease(final String id, final HttpRoute route, final Object state) {
        return lease(id, route, Timeout.DISABLED, state);
    }

    @Override
    public LeaseRequest lease(final String id, final HttpRoute route, final Timeout requestTimeout, final Object state) {
        return new LeaseRequest() {

            @Override
            public ConnectionEndpoint get(
                    final Timeout timeout) throws InterruptedException, ExecutionException, TimeoutException {
                try {
                    return new InternalConnectionEndpoint(route, getConnection(route, state));
                } catch (final IOException ex) {
                    throw new ExecutionException(ex.getMessage(), ex);
                }
            }

            @Override
            public boolean cancel() {
                return false;
            }

        };
    }

    private void closeConnection(final CloseMode closeMode) {
        lock.lock();
        try {
            if (this.conn != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} Closing connection {}", id, closeMode);
                }
                this.conn.close(closeMode);
                this.conn = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkExpiry() {
        if (this.conn != null && System.currentTimeMillis() >= this.expiry) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Connection expired @ {}", id, Instant.ofEpochMilli(this.expiry));
            }
            closeConnection(CloseMode.GRACEFUL);
        }
    }

    private void validate() {
        if (this.conn != null) {
            final TimeValue timeToLive = connectionConfig.getTimeToLive();
            if (TimeValue.isNonNegative(timeToLive)) {
                final Deadline deadline = Deadline.calculate(created, timeToLive);
                if (deadline.isExpired()) {
                    closeConnection(CloseMode.GRACEFUL);
                }
            }
        }
        if (this.conn != null) {
            final TimeValue timeValue = connectionConfig.getValidateAfterInactivity() != null ?
                    connectionConfig.getValidateAfterInactivity() : TimeValue.ofSeconds(2);
            if (TimeValue.isNonNegative(timeValue)) {
                final Deadline deadline = Deadline.calculate(updated, timeValue);
                if (deadline.isExpired()) {
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
                        closeConnection(CloseMode.GRACEFUL);
                    }
                }
            }
        }
    }

    ManagedHttpClientConnection getConnection(final HttpRoute route, final Object state) throws IOException {
        lock.lock();
        try {
            Asserts.check(!isClosed(), "Connection manager has been shut down");
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Get connection for route {}", id, route);
            }
            Asserts.check(!this.leased, "Connection %s is still allocated", conn);
            if (!Objects.equals(this.route, route) || !Objects.equals(this.state, state)) {
                closeConnection(CloseMode.GRACEFUL);
            }
            this.route = route;
            this.state = state;
            checkExpiry();
            validate();
            if (this.conn == null) {
                this.conn = this.connFactory.createConnection(null);
                this.created = System.currentTimeMillis();
            } else {
                this.conn.activate();
            }
            this.leased = true;
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Using connection {}", id, conn);
            }
            return this.conn;
        } finally {
            lock.unlock();
        }
    }


    private InternalConnectionEndpoint cast(final ConnectionEndpoint endpoint) {
        if (endpoint instanceof InternalConnectionEndpoint) {
            return (InternalConnectionEndpoint) endpoint;
        }
        throw new IllegalStateException("Unexpected endpoint class: " + endpoint.getClass());
    }

    @Override
    public void release(final ConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        lock.lock();
        try {
            Args.notNull(endpoint, "Managed endpoint");
            final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
            final ManagedHttpClientConnection conn = internalEndpoint.detach();
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Releasing connection {}", id, conn);
            }
            if (isClosed()) {
                return;
            }
            try {
                if (keepAlive == null && conn != null) {
                    conn.close(CloseMode.GRACEFUL);
                }
                this.updated = System.currentTimeMillis();
                if (conn != null && conn.isOpen() && conn.isConsistent()) {
                    this.state = state;
                    conn.passivate();
                    if (TimeValue.isPositive(keepAlive)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} Connection can be kept alive for {}", id, keepAlive);
                        }
                        this.expiry = this.updated + keepAlive.toMilliseconds();
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} Connection can be kept alive indefinitely", id);
                        }
                        this.expiry = Long.MAX_VALUE;
                    }
                } else {
                    this.route = null;
                    this.conn = null;
                    this.expiry = Long.MAX_VALUE;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} Connection is not kept alive", id);
                    }
                }
            } finally {
                this.leased = false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void connect(final ConnectionEndpoint endpoint, final TimeValue timeout, final HttpContext context) throws IOException {
        lock.lock();
        try {
            Args.notNull(endpoint, "Endpoint");

            final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
            if (internalEndpoint.isConnected()) {
                return;
            }
            final HttpRoute route = internalEndpoint.getRoute();
            final HttpHost host;
            if (route.getProxyHost() != null) {
                host = route.getProxyHost();
            } else {
                host = route.getTargetHost();
            }
            final Timeout connectTimeout = timeout != null ? Timeout.of(timeout.getDuration(), timeout.getTimeUnit()) : connectionConfig.getConnectTimeout();
            final ManagedHttpClientConnection connection = internalEndpoint.getConnection();
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} connecting endpoint to {} ({})", ConnPoolSupport.getId(endpoint), host, connectTimeout);
            }
            this.connectionOperator.connect(
                    connection,
                    host,
                    route.getLocalSocketAddress(),
                    connectTimeout,
                    socketConfig,
                    tlsConfig,
                    context);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} connected {}", ConnPoolSupport.getId(endpoint), ConnPoolSupport.getId(conn));
            }
            final Timeout socketTimeout = connectionConfig.getSocketTimeout();
            if (socketTimeout != null) {
                connection.setSocketTimeout(socketTimeout);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void upgrade(
            final ConnectionEndpoint endpoint,
            final HttpContext context) throws IOException {
        lock.lock();
        try {
            Args.notNull(endpoint, "Endpoint");
            Args.notNull(route, "HTTP route");
            final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
            this.connectionOperator.upgrade(
                    internalEndpoint.getConnection(),
                    internalEndpoint.getRoute().getTargetHost(),
                    tlsConfig,
                    context);
        } finally {
            lock.unlock();
        }
    }

    public void closeExpired() {
        lock.lock();
        try {
            if (isClosed()) {
                return;
            }
            if (!this.leased) {
                checkExpiry();
            }
        } finally {
            lock.unlock();
        }
    }

    public void closeIdle(final TimeValue idleTime) {
        lock.lock();
        try {
            Args.notNull(idleTime, "Idle time");
            if (isClosed()) {
                return;
            }
            if (!this.leased) {
                long time = idleTime.toMilliseconds();
                if (time < 0) {
                    time = 0;
                }
                final long deadline = System.currentTimeMillis() - time;
                if (this.updated <= deadline) {
                    closeConnection(CloseMode.GRACEFUL);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @see #setValidateAfterInactivity(TimeValue)
     *
     * @since 5.1
     *
     * @deprecated Use {@link #getConnectionConfig()}
     */
    @Deprecated
    public TimeValue getValidateAfterInactivity() {
        return connectionConfig.getValidateAfterInactivity();
    }

    /**
     * Defines period of inactivity after which persistent connections must
     * be re-validated prior to being {@link #lease(String, HttpRoute, Object)} leased} to the consumer.
     * Negative values passed to this method disable connection validation. This check helps
     * detect connections that have become stale (half-closed) while kept inactive in the pool.
     *
     * @since 5.1
     *
     * @deprecated Use {@link #setConnectionConfig(ConnectionConfig)}
     */
    @Deprecated
    public void setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        this.connectionConfig = ConnectionConfig.custom()
                .setValidateAfterInactivity(validateAfterInactivity)
                .build();
    }

    class InternalConnectionEndpoint extends ConnectionEndpoint {

        private final HttpRoute route;
        private final AtomicReference<ManagedHttpClientConnection> connRef;

        public InternalConnectionEndpoint(final HttpRoute route, final ManagedHttpClientConnection conn) {
            this.route = route;
            this.connRef = new AtomicReference<>(conn);
        }

        HttpRoute getRoute() {
            return route;
        }

        ManagedHttpClientConnection getConnection() {
            final ManagedHttpClientConnection conn = this.connRef.get();
            if (conn == null) {
                throw new ConnectionShutdownException();
            }
            return conn;
        }

        ManagedHttpClientConnection getValidatedConnection() {
            final ManagedHttpClientConnection conn = getConnection();
            Asserts.check(conn.isOpen(), "Endpoint is not connected");
            return conn;
        }

        ManagedHttpClientConnection detach() {
            return this.connRef.getAndSet(null);
        }

        @Override
        public boolean isConnected() {
            final ManagedHttpClientConnection conn = getConnection();
            return conn != null && conn.isOpen();
        }

        @Override
        public void close(final CloseMode closeMode) {
            final ManagedHttpClientConnection conn = detach();
            if (conn != null) {
                conn.close(closeMode);
            }
        }

        @Override
        public void close() throws IOException {
            final ManagedHttpClientConnection conn = detach();
            if (conn != null) {
                conn.close();
            }
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            getValidatedConnection().setSocketTimeout(timeout);
        }

        /**
         * @deprecated Use {@link #execute(String, ClassicHttpRequest, RequestExecutor, HttpContext)}
         */
        @Deprecated
        @Override
        public ClassicHttpResponse execute(
                final String exchangeId,
                final ClassicHttpRequest request,
                final HttpRequestExecutor requestExecutor,
                final HttpContext context) throws IOException, HttpException {
            Args.notNull(request, "HTTP request");
            Args.notNull(requestExecutor, "Request executor");
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Executing exchange {}", id, exchangeId);
            }
            return requestExecutor.execute(request, getValidatedConnection(), context);
        }

        /**
         * @since 5.4
         */
        @Override
        public ClassicHttpResponse execute(
                final String exchangeId,
                final ClassicHttpRequest request,
                final RequestExecutor requestExecutor,
                final HttpContext context) throws IOException, HttpException {
            Args.notNull(request, "HTTP request");
            Args.notNull(requestExecutor, "Request executor");
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Executing exchange {}", id, exchangeId);
            }
            return requestExecutor.execute(request, getValidatedConnection(), context);
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
