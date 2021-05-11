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
import java.util.Date;
import java.util.concurrent.ExecutionException;
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
import org.apache.hc.core5.util.LangUtils;
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

    private ManagedHttpClientConnection conn;
    private HttpRoute route;
    private Object state;
    private long updated;
    private long expiry;
    private boolean leased;
    private SocketConfig socketConfig;
    private ConnectionConfig connectionConfig;

    private final AtomicBoolean closed;

    private volatile TimeValue validateAfterInactivity;

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
        this.closed = new AtomicBoolean(false);
        this.validateAfterInactivity = TimeValue.ofSeconds(2L);
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

    public synchronized SocketConfig getSocketConfig() {
        return socketConfig;
    }

    public synchronized void setSocketConfig(final SocketConfig socketConfig) {
        this.socketConfig = socketConfig != null ? socketConfig : SocketConfig.DEFAULT;
    }

    /**
     * @since 5.2
     */
    public synchronized ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    /**
     * @since 5.2
     */
    public synchronized void setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
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

    private synchronized void closeConnection(final CloseMode closeMode) {
        if (this.conn != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Closing connection {}", id, closeMode);
            }
            this.conn.close(closeMode);
            this.conn = null;
        }
    }

    private void checkExpiry() {
        if (this.conn != null && System.currentTimeMillis() >= this.expiry) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Connection expired @ {}", id, new Date(this.expiry));
            }
            closeConnection(CloseMode.GRACEFUL);
        }
    }

    private void validate() {
        final TimeValue validateAfterInactivitySnapshot = validateAfterInactivity;
        if (this.conn != null
                && TimeValue.isNonNegative(validateAfterInactivitySnapshot)
                && updated + validateAfterInactivitySnapshot.toMilliseconds() <= System.currentTimeMillis()) {
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

    synchronized ManagedHttpClientConnection getConnection(final HttpRoute route, final Object state) throws IOException {
        Asserts.check(!this.closed.get(), "Connection manager has been shut down");
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Get connection for route {}", id, route);
        }
        Asserts.check(!this.leased, "Connection is still allocated");
        if (!LangUtils.equals(this.route, route) || !LangUtils.equals(this.state, state)) {
            closeConnection(CloseMode.GRACEFUL);
        }
        this.route = route;
        this.state = state;
        checkExpiry();
        validate();
        if (this.conn == null) {
            this.conn = this.connFactory.createConnection(null);
        } else {
            this.conn.activate();
        }
        this.leased = true;
        return this.conn;
    }

    private InternalConnectionEndpoint cast(final ConnectionEndpoint endpoint) {
        if (endpoint instanceof InternalConnectionEndpoint) {
            return (InternalConnectionEndpoint) endpoint;
        }
        throw new IllegalStateException("Unexpected endpoint class: " + endpoint.getClass());
    }

    @Override
    public synchronized void release(final ConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
        Args.notNull(endpoint, "Managed endpoint");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        final ManagedHttpClientConnection conn = internalEndpoint.detach();
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Releasing connection {}", id, conn);
        }
        if (this.closed.get()) {
            return;
        }
        try {
            if (keepAlive == null) {
                this.conn.close(CloseMode.GRACEFUL);
            }
            this.updated = System.currentTimeMillis();
            if (!this.conn.isOpen() && !this.conn.isConsistent()) {
                this.conn = null;
                this.route = null;
                this.conn = null;
                this.expiry = Long.MAX_VALUE;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} Connection is not kept alive", id);
                }
            } else {
                this.state = state;
                if (conn != null) {
                    conn.passivate();
                }
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
            }
        } finally {
            this.leased = false;
        }
    }

    @Override
    public synchronized void connect(final ConnectionEndpoint endpoint, final TimeValue timeout, final HttpContext context) throws IOException {
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
        final ConnectionConfig config = connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
        final TimeValue connectTimeout = timeout != null ? timeout : config.getConnectTimeout();
        final ManagedHttpClientConnection connection = internalEndpoint.getConnection();
        this.connectionOperator.connect(
                connection,
                host,
                route.getLocalSocketAddress(),
                connectTimeout,
                this.socketConfig,
                context);
        final Timeout socketTimeout = config.getSocketTimeout();
        if (socketTimeout != null) {
            connection.setSocketTimeout(socketTimeout);
        }
    }

    @Override
    public synchronized void upgrade(
            final ConnectionEndpoint endpoint,
            final HttpContext context) throws IOException {
        Args.notNull(endpoint, "Endpoint");
        Args.notNull(route, "HTTP route");
        final InternalConnectionEndpoint internalEndpoint = cast(endpoint);
        this.connectionOperator.upgrade(
                internalEndpoint.getConnection(),
                internalEndpoint.getRoute().getTargetHost(),
                context);
    }

    public synchronized void closeExpired() {
        if (this.closed.get()) {
            return;
        }
        if (!this.leased) {
            checkExpiry();
        }
    }

    public synchronized void closeIdle(final TimeValue idleTime) {
        Args.notNull(idleTime, "Idle time");
        if (this.closed.get()) {
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
    }

    /**
     * @see #setValidateAfterInactivity(TimeValue)
     *
     * @since 5.1
     */
    public TimeValue getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    /**
     * Defines period of inactivity after which persistent connections must
     * be re-validated prior to being {@link #lease(String, HttpRoute, Object)} leased} to the consumer.
     * Negative values passed to this method disable connection validation. This check helps
     * detect connections that have become stale (half-closed) while kept inactive in the pool.
     *
     * @since 5.1
     */
    public void setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
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

    }

}
