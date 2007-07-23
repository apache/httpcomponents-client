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
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

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
import org.apache.http.impl.conn.*; //@@@ specify


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
    private ConnectionPool connectionPool;

    /** The operator for opening and updating connections. */
    private ClientConnectionOperator connOperator;

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
        this.connectionPool = new ConnectionPool();
        this.connOperator = createConnectionOperator(schreg);
        this.isShutDown = false;

        synchronized(BadStaticMaps.ALL_CONNECTION_MANAGERS) {
            BadStaticMaps.ALL_CONNECTION_MANAGERS.put(this, null);
        }
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

        final TrackingPoolEntry entry = doGetConnection(route, timeout);

        return new TSCCMConnAdapter(this, entry);
    }


    /**
     * Obtains a connection within the given timeout.
     *
     * @param route     the route for which to get the connection
     * @param timeout   the timeout, or 0 for no timeout
     *
     * @return  the pool entry for the connection
     *
     * @throws ConnectionPoolTimeoutException   if the timeout expired
     */
    private TrackingPoolEntry doGetConnection(HttpRoute route,
                                              long timeout)
        throws ConnectionPoolTimeoutException {

        TrackingPoolEntry entry = null;

        int maxHostConnections = HttpConnectionManagerParams
            .getMaxConnectionsPerHost(this.params, route);
        int maxTotalConnections = HttpConnectionManagerParams
            .getMaxTotalConnections(this.params);
        
        synchronized (connectionPool) {

            RouteConnPool routePool = connectionPool.getRoutePool(route);
            WaitingThread waitingThread = null;

            boolean useTimeout = (timeout > 0);
            long timeToWait = timeout;
            long startWait = 0;
            long endWait = 0;

            while (entry == null) {

                if (isShutDown) {
                    throw new IllegalStateException
                        ("Connection manager has been shut down.");
                }
                
                // happen to have a free connection with the right specs
                //
                if (routePool.freeConnections.size() > 0) {
                    entry = connectionPool.getFreeConnection(route);

                // have room to make more
                //
                } else if ((routePool.numConnections < maxHostConnections) 
                    && (connectionPool.numConnections < maxTotalConnections)) {

                    entry = createPoolEntry(route);

                // have room to add a connection, and there is at least one
                // free connection that can be liberated to make overall room
                //
                } else if ((routePool.numConnections < maxHostConnections) 
                    && (connectionPool.freeConnections.size() > 0)) {

                    connectionPool.deleteLeastUsedConnection();
                    entry = createPoolEntry(route);

                // otherwise, we have to wait for one of the above conditions
                // to become true
                //
                } else {
                    // TODO: keep track of which routes have waiting
                    // threads, so they avoid being sacrificed before necessary

                    try {
                        
                        if (useTimeout && timeToWait <= 0) {
                            throw new ConnectionPoolTimeoutException("Timeout waiting for connection");
                        }
                        
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unable to get a connection, waiting..., route=" + route);
                        }
                        
                        if (waitingThread == null) {
                            waitingThread = new WaitingThread();
                            waitingThread.pool = routePool;
                            waitingThread.thread = Thread.currentThread();
                        } else {
                            waitingThread.interruptedByConnectionPool = false;
                        }
                                    
                        if (useTimeout) {
                            startWait = System.currentTimeMillis();
                        }
                        
                        routePool.waitingThreads.addLast(waitingThread);
                        connectionPool.waitingThreads.addLast(waitingThread);
                        connectionPool.wait(timeToWait);
                        
                    } catch (InterruptedException e) {
                        if (!waitingThread.interruptedByConnectionPool) {
                            LOG.debug("Interrupted while waiting for connection", e);
                            throw new IllegalThreadStateException(
                                "Interrupted while waiting in ThreadSafeClientConnManager");
                        }
                        // Else, do nothing, we were interrupted by the
                        // connection pool and should now have a connection
                        // waiting for us. Continue in the loop and get it.
                        // Or else we are shutting down, which is also
                        // detected in the loop.
                    } finally {
                        if (!waitingThread.interruptedByConnectionPool) {
                            // Either we timed out, experienced a
                            // "spurious wakeup", or were interrupted by an
                            // external thread.  Regardless we need to 
                            // cleanup for ourselves in the wait queue.
                            routePool.waitingThreads.remove(waitingThread);
                            connectionPool.waitingThreads.remove(waitingThread);
                        }
                        
                        if (useTimeout) {
                            endWait = System.currentTimeMillis();
                            timeToWait -= (endWait - startWait);
                        }
                    }
                }
            }
        }


        return entry;

    } // doGetConnection


    /**
     * Creates a connection to be managed, along with a pool entry.
     *
     * @param route     the route for which to create the connection
     *
     * @return  the pool entry for the new connection
     */
    private TrackingPoolEntry createPoolEntry(HttpRoute route) {

        OperatedClientConnection occ = connOperator.createConnection();
        return connectionPool.createEntry(route, occ);
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
            TrackingPoolEntry entry = (TrackingPoolEntry) hca.getPoolEntry();
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
    //@@@ temporary default visibility, for BadStaticMaps
    void /*default*/ releasePoolEntry(TrackingPoolEntry entry) {

        if (entry == null)
            return;

        connectionPool.freeConnection(entry);
    }



    /**
     * Shuts down all instances of this class.
     *
     * @deprecated no replacement
     */
    public static void shutdownAll() {
        BadStaticMaps.shutdownAll();
    }


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
        synchronized (connectionPool) {
            RouteConnPool routePool = connectionPool.getRoutePool(route);
            return routePool.numConnections;
        }
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
            return connectionPool.numConnections;
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
    
    /**
     * A structured pool of connections.
     * This class keeps track of all connections, using overall lists
     * as well as per-route lists.
     */
    //@@@ temporary package visibility, for BadStaticMaps
    class /*default*/ ConnectionPool implements RefQueueHandler {
        
        /** The list of free connections */
        private LinkedList freeConnections = new LinkedList();

        /** The list of WaitingThreads waiting for a connection */
        private LinkedList waitingThreads = new LinkedList();

        /**
         * References to issued connections.
         * Objects in this set are of class {@link PoolEntryRef PoolEntryRef},
         * and point to the pool entry for the issued connection.
         * GCed connections are detected by the missing pool entries.
         */
        private Set issuedConnections = new HashSet();

        /** A reference queue to track loss of pool entries to GC. */
        //@@@ this should be a pool-specific reference queue
        private ReferenceQueue refQueue = BadStaticMaps.REFERENCE_QUEUE; //@@@

        /** A worker (thread) to track loss of pool entries to GC. */
        private RefQueueWorker refWorker;


        /**
         * Map of route-specific pools.
         * Keys are of class {@link HttpRoute}, and
         * values are of class {@link RouteConnPool}.
         */
        private final Map mapRoutes = new HashMap();

        private IdleConnectionHandler idleConnectionHandler =
            new IdleConnectionHandler();        
        
        /** The number of created connections */
        private int numConnections = 0;


        /**
         * Creates a new connection pool.
         */
        private ConnectionPool() {
            //@@@ currently must be false, otherwise the TSCCM
            //@@@ will not be garbage collected in the unit test...
            boolean conngc = false; //@@@ check parameters to decide
            if (conngc) {
                refQueue = new ReferenceQueue();
                refWorker = new RefQueueWorker(refQueue, this);
                Thread t = new Thread(refWorker); //@@@ use a thread factory
                t.setDaemon(true);
                t.setName("RefQueueWorker@"+ThreadSafeClientConnManager.this);
                t.start();
            }
        }

        /**
         * Cleans up all connection pool resources.
         */
        public synchronized void shutdown() {
            
            // close all free connections
            Iterator iter = freeConnections.iterator();
            while (iter.hasNext()) {
                TrackingPoolEntry entry = (TrackingPoolEntry) iter.next();
                iter.remove();
                closeConnection(entry.getConnection());
            }

            if (refWorker != null)
                refWorker.shutdown();
            // close all connections that have been checked out
            iter = issuedConnections.iterator();
            while (iter.hasNext()) {
                PoolEntryRef per = (PoolEntryRef) iter.next();
                iter.remove();
                TrackingPoolEntry entry = (TrackingPoolEntry) per.get();
                if (entry != null) {
                    closeConnection(entry.getConnection());
                }
            }
            //@@@ while the static map exists, call there to clean it up
            BadStaticMaps.shutdownCheckedOutConnections(this); //@@@
            
            // interrupt all waiting threads
            iter = waitingThreads.iterator();
            while (iter.hasNext()) {
                WaitingThread waiter = (WaitingThread) iter.next();
                iter.remove();
                waiter.interruptedByConnectionPool = true;
                waiter.thread.interrupt();
            }
            
            mapRoutes.clear();
            
            // remove all references to connections
            idleConnectionHandler.removeAll();
        }


        /**
         * Creates a new pool entry for an operated connection.
         * This method assumes that the new connection will be handed
         * out immediately.
         *
         * @param route   the route associated with the new entry
         * @param conn    the underlying connection for the new entry
         *
         * @return the new pool entry
         */
        protected synchronized
            TrackingPoolEntry createEntry(HttpRoute route,
                                          OperatedClientConnection conn) {

            RouteConnPool routePool = getRoutePool(route);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Allocating new connection, route=" + route);
            }
            TrackingPoolEntry entry = new TrackingPoolEntry
                (ThreadSafeClientConnManager.this, conn, route, refQueue);
            numConnections++;
            routePool.numConnections++;
    
            // store a reference to this entry so that it can be cleaned up
            // in the event it is not correctly released
            BadStaticMaps.storeReferenceToConnection(entry, route, this); //@@@
            issuedConnections.add(entry.reference);

            return entry;
        }

        
        // non-javadoc, see interface RefQueueHandler
        public synchronized void handleReference(Reference ref) {

            if (ref instanceof PoolEntryRef) {
                // check if the GCed pool entry was still in use
                //@@@ find a way to detect this without lookup
                //@@@ flag in the PoolEntryRef, to be reset when freed?
                final boolean lost = issuedConnections.remove(ref);
                if (lost) {
                    final HttpRoute route = ((PoolEntryRef)ref).route;

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(
                            "Connection garbage collected. " + route);
                    }

                    handleLostConnection(route);
                }
            }
            //@@@ check if the connection manager was GCed
        }


        /**
         * Handles cleaning up for a lost connection with the given config.
         * Decrements any connection counts and notifies waiting threads,
         * if appropriate.
         * 
         * @param route        the route of the connection that was lost
         */
        //@@@ temporary default visibility, for BadStaticMaps
        synchronized /*default*/
            void handleLostConnection(HttpRoute route) {

            RouteConnPool routePool = getRoutePool(route);
            routePool.numConnections--;
            if (routePool.numConnections < 1)
                mapRoutes.remove(route);

            numConnections--;
            notifyWaitingThread(route);
        }

        /**
         * Get the pool (list) of connections available for the given route.
         *
         * @param route   the configuraton for the connection pool
         * @return a pool (list) of connections available for the given route
         */
        public synchronized
            RouteConnPool getRoutePool(HttpRoute route) {

            // Look for a list of connections for the given config
            RouteConnPool listConnections =
                (RouteConnPool) mapRoutes.get(route);
            if (listConnections == null) {
                // First time for this config
                listConnections = new RouteConnPool();
                listConnections.route = route;
                mapRoutes.put(route, listConnections);
            }

            return listConnections;
        }


        /**
         * If available, get a free connection for a route.
         *
         * @param route         the planned route
         *
         * @return an available connection for the given route
         */
        public synchronized TrackingPoolEntry getFreeConnection(HttpRoute route) {

            TrackingPoolEntry entry = null;

            RouteConnPool routePool = getRoutePool(route);

            if (routePool.freeConnections.size() > 0) {
                entry = (TrackingPoolEntry)
                    routePool.freeConnections.removeLast();
                freeConnections.remove(entry);

                // store a reference to this entry so that it can be cleaned up
                // in the event it is not correctly released
                BadStaticMaps.storeReferenceToConnection(entry, route, this); //@@@
                issuedConnections.add(entry.reference);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Getting free connection, route=" + route);
                }

                // remove the connection from the timeout handler
                idleConnectionHandler.remove(entry.getConnection());
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("There were no free connections to get, route=" 
                    + route);
            }
            return entry;
        }
        
        /**
         * Deletes all closed connections.
         */        
        public synchronized void deleteClosedConnections() {
            
            Iterator iter = freeConnections.iterator();
            
            while (iter.hasNext()) {
                TrackingPoolEntry entry =
                    (TrackingPoolEntry) iter.next();
                if (!entry.getConnection().isOpen()) {
                    iter.remove();
                    deleteConnection(entry);
                }
            }
        }

        /**
         * Closes idle connections.
         * @param idleTimeout
         */
        public synchronized void closeIdleConnections(long idleTimeout) {
            idleConnectionHandler.closeIdleConnections(idleTimeout);
        }
        
        /**
         * Deletes the given connection.
         * This will remove all reference to the connection
         * so that it can be GCed.
         * 
         * <p><b>Note:</b> Does not remove the connection from the
         * freeConnections list.  It
         * is assumed that the caller has already handled this step.</p>
         * 
         * @param entry         the pool entry for the connection to delete
         */
        private synchronized void deleteConnection(TrackingPoolEntry entry) {

            HttpRoute route = entry.getPlannedRoute();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Reclaiming connection, route=" + route);
            }

            closeConnection(entry.getConnection());

            RouteConnPool routePool = getRoutePool(route);
            
            routePool.freeConnections.remove(entry);
            routePool.numConnections--;
            numConnections--;
            if (routePool.numConnections < 1)
                mapRoutes.remove(route);

            // remove the connection from the timeout handler
            idleConnectionHandler.remove(entry.getConnection());
        }

        /**
         * Close and delete an old, unused connection to make room for a new one.
         */
        public synchronized void deleteLeastUsedConnection() {

            TrackingPoolEntry entry =
                (TrackingPoolEntry) freeConnections.removeFirst();

            if (entry != null) {
                deleteConnection(entry);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Attempted to reclaim an unused connection but there were none.");
            }
        }

        /**
         * Notifies a waiting thread that a connection is available, by route.
         *
         * @param route         the route for which to notify
         */
        public synchronized void notifyWaitingThread(HttpRoute route) {
            notifyWaitingThread(getRoutePool(route));
        }


        /**
         * Notifies a waiting thread that a connection is available.
         * This will wake a thread waiting in the specific route pool,
         * if there is one.
         * Otherwise, a thread in the connection pool will be notified.
         * 
         * @param routePool     the pool in which to notify
         */
        public synchronized void notifyWaitingThread(RouteConnPool routePool) {

            //@@@ while this strategy provides for best connection re-use,
            //@@@ is it fair? only do this if the connection is open?
            // Find the thread we are going to notify. We want to ensure that
            // each waiting thread is only interrupted once, so we will remove
            // it from all wait queues before interrupting.
            WaitingThread waitingThread = null;

            if (routePool.waitingThreads.size() > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Notifying thread waiting on pool. " 
                        + routePool.route);
                }
                waitingThread = (WaitingThread) routePool.waitingThreads.removeFirst();
                waitingThreads.remove(waitingThread);
            } else if (waitingThreads.size() > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No-one waiting on route pool, notifying next waiting thread.");
                }
                waitingThread = (WaitingThread) waitingThreads.removeFirst();
                waitingThread.pool.waitingThreads.remove(waitingThread);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Notifying no-one, there are no waiting threads");
            }
                
            if (waitingThread != null) {
                waitingThread.interruptedByConnectionPool = true;
                waitingThread.thread.interrupt();
            }
        }

        /**
         * Marks the given connection as free.
         *
         * @param entry         the pool entry for the connection
         */
        private void freeConnection(TrackingPoolEntry entry) {

            HttpRoute route = entry.getPlannedRoute();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Freeing connection, route=" + route);
            }

            synchronized (this) {

                if (isShutDown) {
                    // the connection manager has been shutdown, release the
                    // connection's resources and get out of here
                    closeConnection(entry.getConnection());
                    return;
                }
                
                RouteConnPool routePool = getRoutePool(route);

                // Put the connection back in the available list
                // and notify a waiter
                routePool.freeConnections.add(entry);
                if (routePool.numConnections == 0) {
                    // for some reason the route pool didn't already exist
                    LOG.error("Route connection pool not found. " + route);
                    routePool.numConnections = 1;
                }

                freeConnections.add(entry);
                // We can remove the reference to this connection as we have
                // control over it again. This also ensures that the connection
                // manager can be GCed.
                BadStaticMaps.removeReferenceToConnection(entry); //@@@
                issuedConnections.remove(entry.reference); //@@@ move above
                if (numConnections == 0) {
                    // for some reason this pool didn't already exist
                    LOG.error("Master connection pool not found. " + route);
                    numConnections = 1;
                }

                // register the connection with the timeout handler
                idleConnectionHandler.add(entry.getConnection());

                notifyWaitingThread(routePool);
            }
        }
    } // class ConnectionPool


    static /*default*/ void closeConnection(final OperatedClientConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException ex) {
                LOG.debug("I/O error closing connection", ex);
            }
        }
    }

    /**
     * A simple struct-like class to combine the connection list and the count
     * of created connections.
     */
    private static class RouteConnPool {

        /** The route this pool is for. */
        public HttpRoute route;

        /** The list of free connections. */
        public LinkedList freeConnections = new LinkedList();

        /** The list of WaitingThreads for this pool. */
        public LinkedList waitingThreads = new LinkedList();

        /** The number of created connections. */
        public int numConnections = 0;
    }


    /**
     * A thread and the pool in which it is waiting.
     */
    private static class WaitingThread {
        /** The thread that is waiting for a connection */
        public Thread thread;
        
        /** The connection pool the thread is waiting for */
        public RouteConnPool pool;
        
        /**
         * Indicates the source of an interruption.
         * Set to <code>true</code> inside
         * {@link ConnectionPool#notifyWaitingThread(RouteConnPool)}
         * and {@link ThreadSafeClientConnManager#shutdown shutdown()}
         * before the thread is interrupted.
         * If not set, the thread was interrupted from the outside.
         */
        public boolean interruptedByConnectionPool = false;
    }


    /**
     * A weak reference to a pool entry.
     * Needed for connection GC.
     * Instances of this class can be kept as static references.
     * If an issued connection is lost to garbage collection,
     * it's pool entry is GC'd and the reference becomes invalid.
     */
    private static class PoolEntryRef extends WeakReference {

        /** The planned route of the entry. */
        private final HttpRoute route;


        /**
         * Creates a new reference to a pool entry.
         *
         * @param entry   the pool entry, must not be <code>null</code>
         * @param queue   the reference queue, or <code>null</code>
         */
        public PoolEntryRef(AbstractPoolEntry entry, ReferenceQueue queue) {
            super(entry, queue);
            if (entry == null) {
                throw new IllegalArgumentException
                    ("Pool entry must not be null.");
            }
            route = ((TrackingPoolEntry)entry).getPlannedRoute();
        }


        /**
         * Indicates whether this reference is still valid.
         *
         * @return <code>true</code> if the pool entry is still referenced,
         *         <code>false</code> otherwise
         */
        public final boolean isValid() {
            //@@@ method currently not used
            //@@@ better sematics: allow explicit invalidation
            return (super.get() != null);
        }


        /**
         * Obtain the planned route for the referenced entry.
         * The planned route is still available, even if the entry is gone.
         *
         * @return      the planned route
         */
        public final HttpRoute getRoute() {
            return this.route;
        }
        
    } // class PoolEntryRef

    
    /**
     * A pool entry representing a connection that tracks it's route.
     * For historical reasons, these entries are sometimes referred to
     * as <i>connections</i> throughout the code.
     */
    //@@@ temporary default visibility, it's needed in BadStaticMaps
    static /*default*/ class TrackingPoolEntry extends AbstractPoolEntry {

        /** The connection manager. */
        private ThreadSafeClientConnManager manager;


        /**
         * A weak reference to <code>this</code> used to detect GC of entries.
         * Of course, pool entries can only be GCed when they are allocated
         * and therefore not referenced with a hard link in the manager.
         */
        private PoolEntryRef reference;

            
        /**
         * Creates a new pool entry.
         *
         * @param tsccm   the connection manager
         * @param occ     the underlying connection for this entry
         * @param route   the planned route for the connection
         * @param queue   the reference queue for tracking GC of this entry,
         *                or <code>null</code>
         */
        private TrackingPoolEntry(ThreadSafeClientConnManager tsccm,
                                  OperatedClientConnection occ,
                                  HttpRoute route,
                                  ReferenceQueue queue) {
            super(occ, route);
            if (tsccm == null) {
                throw new IllegalArgumentException
                    ("Connection manager must not be null.");
            }
            if (route == null) {
                throw new IllegalArgumentException
                    ("Planned route must not be null.");
            }
            this.manager = tsccm;
            this.reference = new PoolEntryRef(this, queue);
        }


        // non-javadoc, see base AbstractPoolEntry
        protected ClientConnectionOperator getOperator() {
            //@@@ if the operator is passed explicitly to the constructor,
            //@@@ this class can be factored out
            return manager.connOperator;
        }


        protected final OperatedClientConnection getConnection() {
            return super.connection;
        }

        protected final HttpRoute getPlannedRoute() {
            return super.plannedRoute;
        }

        protected final WeakReference getWeakRef() {
            return this.reference;
        }

        protected final ThreadSafeClientConnManager getManager() {
            return this.manager;
        }

    } // class TrackingPoolEntry


} // class ThreadSafeClientConnManager

