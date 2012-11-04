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

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.GuardedBy;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.LangUtils;

/**
 * A connection manager for a single connection. This connection manager maintains only one active
 * connection. Even though this class is fully thread-safe it ought to be used by one execution
 * thread only, as only one thread a time can lease the connection at a time.
 * <p/>
 * This connection manager will make an effort to reuse the connection for subsequent requests
 * with the same {@link HttpRoute route}. It will, however, close the existing connection and
 * open it for the given route, if the route of the persistent connection does not match that
 * of the connection request. If the connection has been already been allocated
 * {@link IllegalStateException} is thrown.
 * <p/>
 * This connection manager implementation should be used inside an EJB container instead of
 * {@link PoolingHttpClientConnectionManager}.
 *
 * @since 4.3
 */
@ThreadSafe
public class BasicHttpClientConnectionManager implements HttpClientConnectionManager {

    private final Log log = LogFactory.getLog(getClass());

    private final HttpClientConnectionOperator connectionOperator;
    private final HttpConnectionFactory<DefaultClientConnection> connFactory;

    @GuardedBy("this")
    private DefaultClientConnection conn;

    @GuardedBy("this")
    private HttpRoute route;

    @GuardedBy("this")
    private Object state;

    @GuardedBy("this")
    private long updated;

    @GuardedBy("this")
    private long expiry;

    @GuardedBy("this")
    private boolean leased;

    @GuardedBy("this")
    private volatile boolean shutdown;

    public BasicHttpClientConnectionManager(
            final SchemeRegistry schemeRegistry,
            final DnsResolver dnsResolver,
            final HttpConnectionFactory<DefaultClientConnection> connFactory) {
        if (schemeRegistry == null) {
            throw new IllegalArgumentException("Scheme registry may not be null");
        }
        this.connectionOperator = new HttpClientConnectionOperator(schemeRegistry, dnsResolver);
        this.connFactory = connFactory != null ? connFactory : DefaultClientConnectionFactory.INSTANCE;
        this.expiry = Long.MAX_VALUE;
    }

    public BasicHttpClientConnectionManager(final SchemeRegistry schemeRegistry) {
        this(schemeRegistry, null, null);
    }

    public BasicHttpClientConnectionManager() {
        this(SchemeRegistryFactory.createDefault(), null, null);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally { // Make sure we call overridden method even if shutdown barfs
            super.finalize();
        }
    }

    public SchemeRegistry getSchemeRegistry() {
        return this.connectionOperator.getSchemeRegistry();
    }

    HttpRoute getRoute() {
        return route;
    }

    Object getState() {
        return state;
    }

    public final ConnectionRequest requestConnection(
            final HttpRoute route,
            final Object state) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        return new ConnectionRequest() {

            public boolean cancel() {
                // Nothing to abort, since requests are immediate.
                return false;
            }

            public HttpClientConnection get(long timeout, TimeUnit tunit) {
                return BasicHttpClientConnectionManager.this.getConnection(
                        route, state);
            }

        };
    }

    private void closeConnection() {
        if (this.conn != null) {
            this.log.debug("Closing connection");
            try {
                this.conn.close();
            } catch (IOException iox) {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("I/O exception closing connection", iox);
                }
            }
            this.conn = null;
        }
    }

    private void shutdownConnection() {
        if (this.conn != null) {
            this.log.debug("Shutting down connection");
            try {
                this.conn.shutdown();
            } catch (IOException iox) {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("I/O exception shutting down connection", iox);
                }
            }
            this.conn = null;
        }
    }

    private void checkExpiry() {
        if (this.conn != null && System.currentTimeMillis() >= this.expiry) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Connection expired @ " + new Date(this.expiry));
            }
            closeConnection();
        }
    }

    synchronized HttpClientConnection getConnection(final HttpRoute route, final Object state) {
        if (this.shutdown) {
            throw new IllegalStateException("Connection manager has been shut down");
        }
        if (this.log.isDebugEnabled()) {
            this.log.debug("Get connection for route " + route);
        }
        if (this.leased) {
            throw new IllegalStateException("Connection is still allocated");
        }
        if (!LangUtils.equals(this.route, route) || !LangUtils.equals(this.state, state)) {
            closeConnection();
        }
        this.route = route;
        this.state = state;
        checkExpiry();
        if (this.conn == null) {
            this.conn = this.connFactory.create();
        }
        this.leased = true;
        return this.conn;
    }

    public synchronized void releaseConnection(
            final HttpClientConnection conn,
            final Object state,
            long keepalive, final TimeUnit tunit) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection may not be null");
        }
        if (conn != this.conn) {
            throw new IllegalArgumentException("Connection not obtained from this manager");
        }
        if (this.log.isDebugEnabled()) {
            this.log.debug("Releasing connection " + conn);
        }
        if (this.shutdown) {
            shutdownConnection();
            return;
        }
        try {
            this.updated = System.currentTimeMillis();
            if (!this.conn.isOpen()) {
                this.conn = null;
                this.route = null;
                this.conn = null;
                this.expiry = Long.MAX_VALUE;
            } else {
                this.state = state;
                if (this.log.isDebugEnabled()) {
                    String s;
                    if (keepalive > 0) {
                        s = "for " + keepalive + " " + tunit;
                    } else {
                        s = "indefinitely";
                    }
                    this.log.debug("Connection can be kept alive " + s);
                }
                if (keepalive > 0) {
                    this.expiry = this.updated + tunit.toMillis(keepalive);
                } else {
                    this.expiry = Long.MAX_VALUE;
                }
            }
        } finally {
            this.leased = false;
        }
    }

    public void connect(
            final HttpClientConnection conn,
            final HttpHost host,
            final InetAddress local,
            final HttpContext context,
            final HttpParams params) throws IOException {
        if (conn == null) {
            throw new IllegalArgumentException("Connection may not be null");
        }
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        if (conn != this.conn) {
            throw new IllegalArgumentException("Connection not obtained from this manager");
        }
        this.connectionOperator.connect(this.conn, host, local, context, params);
    }

    public void upgrade(
            final HttpClientConnection conn,
            final HttpHost host,
            final HttpContext context,
            final HttpParams params) throws IOException {
        if (conn == null) {
            throw new IllegalArgumentException("Connection may not be null");
        }
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        if (conn != this.conn) {
            throw new IllegalArgumentException("Connection not obtained from this manager");
        }
        this.connectionOperator.upgrade(this.conn, host, context, params);
    }

    public synchronized void closeExpiredConnections() {
        if (this.shutdown) {
            return;
        }
        if (!this.leased) {
            checkExpiry();
        }
    }

    public synchronized void closeIdleConnections(long idletime, TimeUnit tunit) {
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit must not be null.");
        }
        if (this.shutdown) {
            return;
        }
        if (!this.leased) {
            long time = tunit.toMillis(idletime);
            if (time < 0) {
                time = 0;
            }
            long deadline = System.currentTimeMillis() - time;
            if (this.updated <= deadline) {
                closeConnection();
            }
        }
    }

    public synchronized void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        shutdownConnection();
    }

}
