/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn.tsccm;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;

/**
 * Manages a pool of {@link OperatedClientConnection client connections} and 
 * is able to service connection requests from multiple execution threads. 
 * Connections are pooled on a per route basis. A request for a route which 
 * already the manager has persistent connections for available in the pool 
 * will be services by leasing a connection from the pool rather than 
 * creating a brand new connection.
 * <p>
 * ThreadSafeClientConnManager maintains a maximum limit of connection on 
 * a per route basis and in total. Per default this implementation will
 * create no more than than 2 concurrent connections per given route 
 * and no more 20 connections in total. For many real-world applications
 * these limits may prove too constraining, especially if they use HTTP
 * as a transport protocol for their services. Connection limits, however,
 * can be adjusted using HTTP parameters.
 * <p>
 * The following parameters can be used to customize the behavior of this 
 * class: 
 * <ul>
 *  <li>{@link org.apache.http.conn.params.ConnManagerPNames#MAX_TOTAL_CONNECTIONS}</li>
 *  <li>{@link org.apache.http.conn.params.ConnManagerPNames#MAX_CONNECTIONS_PER_ROUTE}</li>
 * </ul>
 *
 * @see org.apache.http.conn.params.ConnPerRoute 
 * @see org.apache.http.conn.params.ConnPerRouteBean
 *
 * @since 4.0
 */
public class ThreadSafeClientConnManager implements ClientConnectionManager {

    private final Log log = LogFactory.getLog(getClass());

    /** The schemes supported by this connection manager. */
    protected final SchemeRegistry schemeRegistry; // @ThreadSafe
    
    /** The pool of connections being managed. */
    protected final AbstractConnPool connectionPool;

    /** The operator for opening and updating connections. */
    protected final ClientConnectionOperator connOperator; // DefaultClientConnectionOperator is @ThreadSafe
    
    /**
     * Creates a new thread safe connection manager.
     *
     * @param params    the parameters for this manager.
     * @param schreg    the scheme registry.
     */
    public ThreadSafeClientConnManager(HttpParams params,
                                       SchemeRegistry schreg) {

        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        if (schreg == null) {
            throw new IllegalArgumentException("Scheme registry may not be null");
        }
        this.schemeRegistry = schreg;
        this.connOperator   = createConnectionOperator(schreg);
        this.connectionPool = createConnectionPool(params);

    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    /**
     * Hook for creating the connection pool.
     *
     * @return  the connection pool to use
     */
    protected AbstractConnPool createConnectionPool(final HttpParams params) {
        return new ConnPoolByRoute(connOperator, params);
    }

    /**
     * Hook for creating the connection operator.
     * It is called by the constructor.
     * Derived classes can override this method to change the
     * instantiation of the operator.
     * The default implementation here instantiates
     * {@link DefaultClientConnectionOperator DefaultClientConnectionOperator}.
     *
     * @param schreg    the scheme registry.
     *
     * @return  the connection operator to use
     */
    protected ClientConnectionOperator
        createConnectionOperator(SchemeRegistry schreg) {

        return new DefaultClientConnectionOperator(schreg);// @ThreadSafe
    }

    public SchemeRegistry getSchemeRegistry() {
        return this.schemeRegistry;
    }

    public ClientConnectionRequest requestConnection(
            final HttpRoute route, 
            final Object state) {
        
        final PoolEntryRequest poolRequest = connectionPool.requestPoolEntry(
                route, state);
        
        return new ClientConnectionRequest() {
            
            public void abortRequest() {
                poolRequest.abortRequest();
            }
            
            public ManagedClientConnection getConnection(
                    long timeout, TimeUnit tunit) throws InterruptedException,
                    ConnectionPoolTimeoutException {
                if (route == null) {
                    throw new IllegalArgumentException("Route may not be null.");
                }

                if (log.isDebugEnabled()) {
                    log.debug("ThreadSafeClientConnManager.getConnection: "
                        + route + ", timeout = " + timeout);
                }

                BasicPoolEntry entry = poolRequest.getPoolEntry(timeout, tunit);
                return new BasicPooledConnAdapter(ThreadSafeClientConnManager.this, entry);
            }
            
        };
        
    }

    public void releaseConnection(ManagedClientConnection conn, long validDuration, TimeUnit timeUnit) {

        if (!(conn instanceof BasicPooledConnAdapter)) {
            throw new IllegalArgumentException
                ("Connection class mismatch, " +
                 "connection not obtained from this manager.");
        }
        BasicPooledConnAdapter hca = (BasicPooledConnAdapter) conn;
        if ((hca.getPoolEntry() != null) && (hca.getManager() != this)) {
            throw new IllegalArgumentException
                ("Connection not obtained from this manager.");
        }
        synchronized (hca) {
            BasicPoolEntry entry = (BasicPoolEntry) hca.getPoolEntry();
            if (entry == null) {
                return;
            }
            try {
                // make sure that the response has been read completely
                if (hca.isOpen() && !hca.isMarkedReusable()) {
                    // In MTHCM, there would be a call to
                    // SimpleHttpConnectionManager.finishLastResponse(conn);
                    // Consuming the response is handled outside in 4.0.

                    // make sure this connection will not be re-used
                    // Shut down rather than close, we might have gotten here
                    // because of a shutdown trigger.
                    // Shutdown of the adapter also clears the tracked route.
                    hca.shutdown();
                }
            } catch (IOException iox) {
                if (log.isDebugEnabled())
                    log.debug("Exception shutting down released connection.",
                              iox);
            } finally {
                boolean reusable = hca.isMarkedReusable();
                if (log.isDebugEnabled()) {
                    if (reusable) {
                        log.debug("Released connection is reusable.");
                    } else {
                        log.debug("Released connection is not reusable.");
                    }
                }
                hca.detach();
                connectionPool.freeEntry(entry, reusable, validDuration, timeUnit);
            }
        }
    }

    public void shutdown() {
        log.debug("Shutting down");
        connectionPool.shutdown();
    }

    /**
     * Gets the total number of pooled connections for the given route.
     * This is the total number of connections that have been created and
     * are still in use by this connection manager for the route.
     * This value will not exceed the maximum number of connections per host.
     * 
     * @param route     the route in question
     *
     * @return  the total number of pooled connections for that route
     */
    public int getConnectionsInPool(HttpRoute route) {
        return ((ConnPoolByRoute)connectionPool).getConnectionsInPool(
                route);
    }

    /**
     * Gets the total number of pooled connections.  This is the total number of 
     * connections that have been created and are still in use by this connection 
     * manager.  This value will not exceed the maximum number of connections
     * in total.
     * 
     * @return the total number of pooled connections
     */
    public int getConnectionsInPool() {
        int count;
        connectionPool.poolLock.lock();
        count = connectionPool.numConnections; //@@@
        connectionPool.poolLock.unlock();
        return count;
    }

    public void closeIdleConnections(long idleTimeout, TimeUnit tunit) {
        if (log.isDebugEnabled()) {
            log.debug("Closing connections idle for " + idleTimeout + " " + tunit);
        }
        connectionPool.closeIdleConnections(idleTimeout, tunit);
        connectionPool.deleteClosedConnections();
    }
    
    public void closeExpiredConnections() {
        log.debug("Closing expired connections");
        connectionPool.closeExpiredConnections();
        connectionPool.deleteClosedConnections();
    }

}

