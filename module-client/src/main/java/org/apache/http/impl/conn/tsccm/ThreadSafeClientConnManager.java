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
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.SchemeRegistry;
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

    private final Log LOG =
        LogFactory.getLog(ThreadSafeClientConnManager.class);


    /** The schemes supported by this connection manager. */
    protected SchemeRegistry schemeRegistry; 
    
    /** The parameters of this connection manager. */
    protected HttpParams params;

    /** The pool of connections being managed. */
    protected final AbstractConnPool connectionPool;

    /** The operator for opening and updating connections. */
    protected ClientConnectionOperator connOperator;
    


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
        this.params         = params;
        this.schemeRegistry = schreg;
        this.connectionPool = createConnectionPool();
        this.connOperator   = createConnectionOperator(schreg);

    } // <constructor>


    /**
     * Hook for creating the connection pool.
     *
     * @return  the connection pool to use
     */
    protected AbstractConnPool createConnectionPool() {

        return new ConnPoolByRoute(this);
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


    // non-javadoc, see interface ClientConnectionManager
    public SchemeRegistry getSchemeRegistry() {
        return this.schemeRegistry;
    }

    
    // non-javadoc, see interface ClientConnectionManager
    public ManagedClientConnection getConnection(HttpRoute route)
        throws InterruptedException {

        while (true) {
            try {
                return getConnection(route, 0, null);
            } catch (ConnectionPoolTimeoutException e) {
                // We'll go ahead and log this, but it should never happen.
                // These exceptions are only thrown when the timeout occurs
                // and since we have no timeout, it doesn't happen.
                LOG.debug
                    ("Unexpected exception while waiting for connection.", e);
                //@@@ throw RuntimeException or Error to indicate the problem?
            }
        }
    }


    // non-javadoc, see interface ClientConnectionManager
    public ManagedClientConnection getConnection(HttpRoute route,
                                                 long timeout,
                                                 TimeUnit tunit)
        throws ConnectionPoolTimeoutException, InterruptedException {

        if (route == null) {
            throw new IllegalArgumentException("Route may not be null.");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("ThreadSafeClientConnManager.getConnection: "
                + route + ", timeout = " + timeout);
        }

        final BasicPoolEntry entry =
            connectionPool.getEntry(route, timeout, tunit, connOperator);

        return new BasicPooledConnAdapter(this, entry);
    }

    
    // non-javadoc, see interface ClientConnectionManager
    public void releaseConnection(ManagedClientConnection conn) {

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

        try {
            // make sure that the response has been read completely
            if (hca.isOpen() && !hca.isMarkedReusable()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug
                        ("Released connection open but not marked reusable.");
                }
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
            //@@@ log as warning? let pass?
            if (LOG.isDebugEnabled())
                LOG.debug("Exception shutting down released connection.",
                          iox);
        } finally {
            BasicPoolEntry entry = (BasicPoolEntry) hca.getPoolEntry();
            hca.detach();
            if (entry != null) // is it worth to bother with this check? @@@
                connectionPool.freeEntry(entry);
        }
    }


    // non-javadoc, see interface ClientConnectionManager
    public void shutdown() {
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


    // non-javadoc, see interface ClientConnectionManager
    public void closeIdleConnections(long idleTimeout, TimeUnit tunit) {
        // combine these two in a single call?
        connectionPool.closeIdleConnections(idleTimeout, tunit);
        connectionPool.deleteClosedConnections();
    }


    // non-javadoc, see interface ClientConnectionManager
    public HttpParams getParams() {
        return this.params;
    }


    /* *
     * Assigns {@link HttpParams parameters} for this 
     * connection manager.
     * /
    //@@@ this is basically a no-op unless we pass the params to the pool
    public void setParams(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        this.params = params;
    }
    */


} // class ThreadSafeClientConnManager

