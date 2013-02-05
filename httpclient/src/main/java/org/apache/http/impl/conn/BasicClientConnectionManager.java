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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpClientConnection;
import org.apache.http.annotation.GuardedBy;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;

/**
 * A connection manager for a single connection. This connection manager maintains only one active
 * connection at a time. Even though this class is thread-safe it ought to be used by one execution
 * thread only.
 * <p/>
 * BasicClientConnManager will make an effort to reuse the connection for subsequent requests
 * with the same {@link HttpRoute route}. It will, however, close the existing connection and
 * open it for the given route, if the route of the persistent connection does not match that
 * of the connection request. If the connection has been already been allocated
 * {@link IllegalStateException} is thrown.
 * <p/>
 * This connection manager implementation can be used inside a EJB container instead of
 * {@link PoolingClientConnectionManager}.
 *
 * @since 4.2
 */
@ThreadSafe
public class BasicClientConnectionManager implements ClientConnectionManager {

    private final Log log = LogFactory.getLog(getClass());

    private static final AtomicLong COUNTER = new AtomicLong();

    /** The message to be logged on multiple allocation. */
    public final static String MISUSE_MESSAGE =
    "Invalid use of BasicClientConnManager: connection still allocated.\n" +
    "Make sure to release the connection before allocating another one.";

    /** The schemes supported by this connection manager. */
    private final SchemeRegistry schemeRegistry;

    /** The operator for opening and updating connections. */
    private final ClientConnectionOperator connOperator;

    /** The one and only entry in this pool. */
    @GuardedBy("this")
    private HttpPoolEntry poolEntry;

    /** The currently issued managed connection, if any. */
    @GuardedBy("this")
    private ManagedClientConnectionImpl conn;

    /** Indicates whether this connection manager is shut down. */
    @GuardedBy("this")
    private volatile boolean shutdown;

    /**
     * Creates a new simple connection manager.
     *
     * @param schreg    the scheme registry
     */
    public BasicClientConnectionManager(final SchemeRegistry schreg) {
        if (schreg == null) {
            throw new IllegalArgumentException("Scheme registry may not be null");
        }
        this.schemeRegistry = schreg;
        this.connOperator = createConnectionOperator(schreg);
    }

    public BasicClientConnectionManager() {
        this(SchemeRegistryFactory.createDefault());
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
        return this.schemeRegistry;
    }

    protected ClientConnectionOperator createConnectionOperator(final SchemeRegistry schreg) {
        return new DefaultClientConnectionOperator(schreg);
    }

    public final ClientConnectionRequest requestConnection(
            final HttpRoute route,
            final Object state) {

        return new ClientConnectionRequest() {

            public void abortRequest() {
                // Nothing to abort, since requests are immediate.
            }

            public ManagedClientConnection getConnection(
                    long timeout, TimeUnit tunit) {
                return BasicClientConnectionManager.this.getConnection(
                        route, state);
            }

        };
    }

    private void assertNotShutdown() {
        if (this.shutdown) {
            throw new IllegalStateException("Connection manager has been shut down");
        }
    }

    ManagedClientConnection getConnection(final HttpRoute route, final Object state) {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null.");
        }
        synchronized (this) {
            assertNotShutdown();
            if (this.log.isDebugEnabled()) {
                this.log.debug("Get connection for route " + route);
            }
            if (this.conn != null) {
                throw new IllegalStateException(MISUSE_MESSAGE);
            }
            if (this.poolEntry != null && !this.poolEntry.getPlannedRoute().equals(route)) {
                this.poolEntry.close();
                this.poolEntry = null;
            }
            if (this.poolEntry == null) {
                String id = Long.toString(COUNTER.getAndIncrement());
                OperatedClientConnection conn = this.connOperator.createConnection();
                this.poolEntry = new HttpPoolEntry(this.log, id, route, conn, 0, TimeUnit.MILLISECONDS);
            }
            long now = System.currentTimeMillis();
            if (this.poolEntry.isExpired(now)) {
                this.poolEntry.close();
                this.poolEntry.getTracker().reset();
            }
            this.conn = new ManagedClientConnectionImpl(this, this.connOperator, this.poolEntry);
            return this.conn;
        }
    }

    private void shutdownConnection(final HttpClientConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException iox) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("I/O exception shutting down connection", iox);
            }
        }
    }
    
    public void releaseConnection(final ManagedClientConnection conn, long keepalive, TimeUnit tunit) {
        if (!(conn instanceof ManagedClientConnectionImpl)) {
            throw new IllegalArgumentException("Connection class mismatch, " +
                 "connection not obtained from this manager");
        }
        ManagedClientConnectionImpl managedConn = (ManagedClientConnectionImpl) conn;
        synchronized (managedConn) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Releasing connection " + conn);
            }
            if (managedConn.getPoolEntry() == null) {
                return; // already released
            }
            ClientConnectionManager manager = managedConn.getManager();
            if (manager != null && manager != this) {
                throw new IllegalStateException("Connection not obtained from this manager");
            }
            synchronized (this) {
                if (this.shutdown) {
                    shutdownConnection(managedConn);
                    return;
                }
                try {
                    if (managedConn.isOpen() && !managedConn.isMarkedReusable()) {
                        shutdownConnection(managedConn);
                    }
                    if (managedConn.isMarkedReusable()) {
                        this.poolEntry.updateExpiry(keepalive, tunit != null ? tunit : TimeUnit.MILLISECONDS);
                        if (this.log.isDebugEnabled()) {
                            String s;
                            if (keepalive > 0) {
                                s = "for " + keepalive + " " + tunit;
                            } else {
                                s = "indefinitely";
                            }
                            this.log.debug("Connection can be kept alive " + s);
                        }
                    }
                } finally {
                    managedConn.detach();
                    this.conn = null;
                    if (this.poolEntry.isClosed()) {
                        this.poolEntry = null;
                    }
                }
            }
        }
    }

    public void closeExpiredConnections() {
        synchronized (this) {
            assertNotShutdown();
            long now = System.currentTimeMillis();
            if (this.poolEntry != null && this.poolEntry.isExpired(now)) {
                this.poolEntry.close();
                this.poolEntry.getTracker().reset();
            }
        }
    }

    public void closeIdleConnections(long idletime, TimeUnit tunit) {
        if (tunit == null) {
            throw new IllegalArgumentException("Time unit must not be null.");
        }
        synchronized (this) {
            assertNotShutdown();
            long time = tunit.toMillis(idletime);
            if (time < 0) {
                time = 0;
            }
            long deadline = System.currentTimeMillis() - time;
            if (this.poolEntry != null && this.poolEntry.getUpdated() <= deadline) {
                this.poolEntry.close();
                this.poolEntry.getTracker().reset();
            }
        }
    }

    public void shutdown() {
        synchronized (this) {
            this.shutdown = true;
            try {
                if (this.poolEntry != null) {
                    this.poolEntry.close();
                }
            } finally {
                this.poolEntry = null;
                this.conn = null;
            }
        }
    }

}
