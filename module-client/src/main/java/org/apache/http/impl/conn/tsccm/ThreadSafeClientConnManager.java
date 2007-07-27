/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.params.HttpConnectionManagerParams;
import org.apache.http.params.HttpParams;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;



/**
 * Manages a pool of {@link OperatedClientConnection client connections}.
 * <p>
 * This class is derived from <code>MultiThreadedHttpConnectionManager</code>
 * in HttpClient 3. See there for original authors.
 * </p>
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$ $Date$
 *
 * @since 4.0
 */
public class ThreadSafeClientConnManager
    implements ClientConnectionManager {

    //@@@ LOG must be static for now, it's used in static methods
    private final static Log LOG =
        LogFactory.getLog(ThreadSafeClientConnManager.class);


    /** The schemes supported by this connection manager. */
    protected SchemeRegistry schemeRegistry; 
    
    /** The parameters of this connection manager. */
    private HttpParams params;


    /** The pool of connections being managed. */
    //@@@ temporarily, used in BasicPoolEntry
    /*private*/ AbstractConnPool connectionPool;

    /** The operator for opening and updating connections. */
    /*private*/ ClientConnectionOperator connOperator;

    /** Indicates whether this connection manager is shut down. */
    private volatile boolean isShutDown;
    


    /**
     * Creates a new thread safe connection manager.
     *
     * @param params    the parameters for this manager
     * @param schreg    the scheme registry, or
     *                  <code>null</code> for the default registry
     */
    public ThreadSafeClientConnManager(HttpParams params,
                                       SchemeRegistry schreg) {

        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        this.params = params;
        this.schemeRegistry  = schreg;
        this.connectionPool = new ConnPoolByRoute(this);
        this.connOperator = createConnectionOperator(schreg);
        this.isShutDown = false;

        //@@@ synchronized(BadStaticMaps.ALL_CONNECTION_MANAGERS) {
        //@@@    BadStaticMaps.ALL_CONNECTION_MANAGERS.put(this, null);
        //@@@}
    } // <constructor>


    public SchemeRegistry getSchemeRegistry() {
        return this.schemeRegistry;
    }

    
    // non-javadoc, see interface ClientConnectionManager
    public ManagedClientConnection getConnection(HttpRoute route) {

        while (true) {
            try {
                return getConnection(route, 0);
            } catch (ConnectionPoolTimeoutException e) {
                // we'll go ahead and log this, but it should never happen.
                // Exceptions are only thrown when the timeout occurs and
                // since we have no timeout, it doesn't happen.
                LOG.debug(
                    "Unexpected exception while waiting for connection",
                    e
                );
            }
        }
    }


    // non-javadoc, see interface ClientConnectionManager
    public ManagedClientConnection getConnection(HttpRoute route,
                                                 long timeout)
        throws ConnectionPoolTimeoutException {

        if (route == null) {
            throw new IllegalArgumentException("Route may not be null.");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("ThreadSafeClientConnManager.getConnection: "
                + route + ", timeout = " + timeout);
        }

        final BasicPoolEntry entry =
            connectionPool.getEntry(route, timeout, connOperator);

        return new TSCCMConnAdapter(this, entry);
    }


    /**
     * Hook for creating the connection operator.
     * It is called by the constructor.
     * Derived classes can override this method to change the
     * instantiation of the operator.
     * The default implementation here instantiates
     * {@link DefaultClientConnectionOperator DefaultClientConnectionOperator}.
     *
     * @param schreg    the scheme registry to use, or <code>null</code>
     *
     * @return  the connection operator to use
     */
    protected ClientConnectionOperator
        createConnectionOperator(SchemeRegistry schreg) {

        return new DefaultClientConnectionOperator(schreg);
    }

    
    /**
     * Releases an allocated connection.
     * If another thread is blocked in getConnection() that could use this
     * connection, it will be woken up.
     *
     * @param conn the connection to make available.
     */
    public void releaseConnection(ManagedClientConnection conn) {

        if (!(conn instanceof TSCCMConnAdapter)) {
            throw new IllegalArgumentException
                ("Connection class mismatch, " +
                 "connection not obtained from this manager.");
        }
        TSCCMConnAdapter hca = (TSCCMConnAdapter) conn;
        if ((hca.getPoolEntry() != null) && (hca.getManager() != this)) {
            throw new IllegalArgumentException
                ("Connection not obtained from this manager.");
        }

        try {
            // make sure that the response has been read completely
            if (hca.isOpen() && !hca.isMarkedReusable()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug
                        ("Released connection open but not marked reusable.");
                }
                // In MTHCM, method releasePoolEntry below would call
                // SimpleHttpConnectionManager.finishLastResponse(conn);
                // Consuming the response is handled outside in 4.0.

                // make sure this connection will not be re-used
                // Shut down rather than close, we might have gotten here
                // because of a shutdown trigger.
                // Shutdown of the adapter also clears the tracked route.
                hca.shutdown();
            }
        } catch (IOException iox) {
            //@@@ log as warning? let pass?
            if (LOG.isDebugEnabled())
                LOG.debug("Exception shutting down released connection.",
                          iox);
        } finally {
            BasicPoolEntry entry = (BasicPoolEntry) hca.getPoolEntry();
            hca.detach();
            releasePoolEntry(entry);
        }
    }


    /**
     * Releases an allocated connection by the pool entry.
     *
     * @param entry     the pool entry for the connection to release,
     *                  or <code>null</code>
     */
    private void releasePoolEntry(BasicPoolEntry entry) {

        if (entry == null)
            return;

        connectionPool.freeEntry(entry);
    }



    /* *
     * Shuts down all instances of this class.
     *
     * @deprecated no replacement
     * /
    public static void shutdownAll() {
        //@@@ BadStaticMaps.shutdownAll();
    }
    */


    /**
     * Shuts down the connection manager and releases all resources.
     * All connections associated with this manager will be closed
     * and released. 
     * The connection manager can no longer be used once shut down.
     * Calling this method more than once will have no effect.
     */
    public synchronized void shutdown() {
        synchronized (connectionPool) {
            if (!isShutDown) {
                isShutDown = true;
                connectionPool.shutdown();
            }
        }
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
        return ((ConnPoolByRoute)connectionPool).getConnectionsInPool(route);
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
        synchronized (connectionPool) {
            return connectionPool.numConnections; //@@@
        }
    }


    /**
     * Deletes all free connections that are closed.
     * Only connections currently owned by the connection
     * manager are processed.
     */
    private void deleteClosedConnections() {
        connectionPool.deleteClosedConnections();
    }


    /**
     * Deletes all free connections that are idle or closed.
     */
    public void closeIdleConnections(long idleTimeout) {
        connectionPool.closeIdleConnections(idleTimeout);
        deleteClosedConnections();
    }


    /**
     * Returns {@link HttpParams parameters} associated 
     * with this connection manager.
     */
    public HttpParams getParams() {
        return this.params;
    }

    /**
     * Assigns {@link HttpParams parameters} for this 
     * connection manager.
     * 
     * @see HttpConnectionManagerParams
     */
    public void setParams(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        this.params = params;
    }


    //@@@ still needed?
    static /*default*/ void closeConnection(final OperatedClientConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException ex) {
                LOG.debug("I/O error closing connection", ex);
            }
        }
    }


} // class ThreadSafeClientConnManager

