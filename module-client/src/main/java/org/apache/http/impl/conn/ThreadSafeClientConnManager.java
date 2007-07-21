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

package org.apache.http.impl.conn;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * A mapping from Reference to ConnectionSource.
     * Used to reclaim resources when connections are lost
     * to the garbage collector.
     */
    private static final Map REFERENCE_TO_CONNECTION_SOURCE = new HashMap();
    
    /**
     * The reference queue used to track when connections are lost to the
     * garbage collector
     */
    private static final ReferenceQueue REFERENCE_QUEUE = new ReferenceQueue();    

    /**
     * The thread responsible for handling lost connections.
     */
    private static ReferenceQueueThread REFERENCE_QUEUE_THREAD;

    
    /**
     * Holds references to all active instances of this class.
     */    
    private static WeakHashMap ALL_CONNECTION_MANAGERS = new WeakHashMap();


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

        synchronized(ALL_CONNECTION_MANAGERS) {
            ALL_CONNECTION_MANAGERS.put(this, null);
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

        return new HttpConnectionAdapter(entry);
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

        if (!(conn instanceof HttpConnectionAdapter)) {
            throw new IllegalArgumentException
                ("Connection class mismatch, " +
                 "connection not obtained from this manager.");
        }
        HttpConnectionAdapter hca = (HttpConnectionAdapter) conn;
        if ((hca.poolEntry != null) &&
            //@@@ (hca.poolEntry.manager != this) &&
            (hca.connManager != this)) {
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
            TrackingPoolEntry entry = (TrackingPoolEntry) hca.poolEntry;
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
    private void releasePoolEntry(TrackingPoolEntry entry) {

        if (entry == null)
            return;

        connectionPool.freeConnection(entry);
    }



    // ######################################################################
    // ######################################################################
    // ##########               old code below                     ##########
    // ######################################################################
    // ######################################################################


    /**
     * Shuts down and cleans up resources used by all instances of 
     * ThreadSafeClientConnManager. All static resources are released, all threads are 
     * stopped, and {@link #shutdown()} is called on all live instances of 
     * ThreadSafeClientConnManager.
     *
     * @see #shutdown()
     */
    public static void shutdownAll() {

        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
            // shutdown all connection managers
            synchronized (ALL_CONNECTION_MANAGERS) {
                // Don't use an iterator here. Iterators on WeakHashMap can
                // get ConcurrentModificationException on garbage collection.
                ThreadSafeClientConnManager[]
                    connManagers = (ThreadSafeClientConnManager[])
                    ALL_CONNECTION_MANAGERS.keySet().toArray(
                        new ThreadSafeClientConnManager
                            [ALL_CONNECTION_MANAGERS.size()]
                        );

                // The map may shrink after size() is called, or some entry
                // may get GCed while the array is built, so expect null.
                for (int i=0; i<connManagers.length; i++) {
                    if (connManagers[i] != null)
                        connManagers[i].shutdown();
                }
            }
            
            // shutdown static resources
            if (REFERENCE_QUEUE_THREAD != null) {
                REFERENCE_QUEUE_THREAD.shutdown();
                REFERENCE_QUEUE_THREAD = null;
            }
            REFERENCE_TO_CONNECTION_SOURCE.clear();
        }        
    }


    /**
     * Stores a weak reference to the given pool entry.
     * Along with the reference, the route and connection pool are stored.
     * These values will be used to reclaim resources if the connection
     * is lost to the garbage collector.  This method should be called
     * before a connection is handed out by the connection manager.
     * <br/>
     * A static reference to the connection manager will also be stored.
     * To ensure that the connection manager can be GCed,
     * {@link #removeReferenceToConnection removeReferenceToConnection}
     * should be called for all pool entry to which the manager
     * keeps a strong reference.
     * 
     * @param connection        the pool entry to store a reference for
     * @param route             the connection's planned route
     * @param connectionPool    the connection pool that created the entry
     * 
     * @see #removeReferenceToConnection
     */
    private static void storeReferenceToConnection(
        TrackingPoolEntry connection,
        HttpRoute route,
        ConnectionPool connectionPool
    ) {

        ConnectionSource source = new ConnectionSource();
        source.connectionPool = connectionPool;
        source.route = route;

        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {

            // start the reference queue thread if needed
            if (REFERENCE_QUEUE_THREAD == null) {
                REFERENCE_QUEUE_THREAD = new ReferenceQueueThread();
                REFERENCE_QUEUE_THREAD.start();
            }
            
            REFERENCE_TO_CONNECTION_SOURCE.put(
                connection.reference,
                source
            );
        }
    }

    /**
     * Removes the reference being stored for the given connection.
     * This method should be called when the manager again has a
     * direct reference to the pool entry.
     * 
     * @param entry     the pool entry for which to remove the reference
     * 
     * @see #storeReferenceToConnection
     */
    private static void removeReferenceToConnection(TrackingPoolEntry entry) {
        
        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
            REFERENCE_TO_CONNECTION_SOURCE.remove(entry.reference);
        }
    }    


    /**
     * Closes and releases all connections currently checked out of the
     * given connection pool.
     * @param connectionPool the pool for which to shutdown the connections
     */
    private static
    void shutdownCheckedOutConnections(ConnectionPool connectionPool) {

        // keep a list of the connections to be closed
        ArrayList connectionsToClose = new ArrayList();

        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
            
            Iterator referenceIter = REFERENCE_TO_CONNECTION_SOURCE.keySet().iterator();
            while (referenceIter.hasNext()) {
                Reference ref = (Reference) referenceIter.next();
                ConnectionSource source = 
                    (ConnectionSource) REFERENCE_TO_CONNECTION_SOURCE.get(ref);
                if (source.connectionPool == connectionPool) {
                    referenceIter.remove();
                    Object entry = ref.get(); // TrackingPoolEntry
                    if (entry != null) {
                        connectionsToClose.add(entry);
                    }
                }
            }
        }

        // close and release the connections outside of the synchronized block
        // to avoid holding the lock for too long
        for (Iterator i = connectionsToClose.iterator(); i.hasNext();) {
            TrackingPoolEntry entry = (TrackingPoolEntry) i.next();
            closeConnection(entry.connection);
            // remove the reference to the connection manager. this ensures
            // that the we don't accidentally end up here again
            //@@@ connection.setHttpConnectionManager(null);

            entry.manager.releasePoolEntry(entry);
        }
    }


    



    // ------------------------------------------------------- Instance Methods

    /**
     * Shuts down the connection manager and releases all resources.  All connections associated 
     * with this class will be closed and released. 
     * 
     * <p>The connection manager can no longer be used once shutdown.  
     * 
     * <p>Calling this method more than once will have no effect.
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
    private class ConnectionPool {
        
        /** The list of free connections */
        private LinkedList freeConnections = new LinkedList();

        /** The list of WaitingThreads waiting for a connection */
        private LinkedList waitingThreads = new LinkedList();

        /**
         * Map where keys are {@link HttpRoute}s and values are
         * {@link RouteConnPool}s
         */
        private final Map mapRoutes = new HashMap();

        private IdleConnectionHandler idleConnectionHandler = new IdleConnectionHandler();        
        
        /** The number of created connections */
        private int numConnections = 0;

        /**
         * Cleans up all connection pool resources.
         */
        public synchronized void shutdown() {
            
            // close all free connections
            Iterator iter = freeConnections.iterator();
            while (iter.hasNext()) {
                TrackingPoolEntry entry = (TrackingPoolEntry) iter.next();
                iter.remove();
                closeConnection(entry.connection);
            }
            
            // close all connections that have been checked out
            shutdownCheckedOutConnections(this);
            
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
            TrackingPoolEntry entry = new TrackingPoolEntry(conn);
            entry.plannedRoute = route;
            numConnections++;
            routePool.numConnections++;
    
            // store a reference to this entry so that it can be cleaned up
            // in the event it is not correctly released
            storeReferenceToConnection(entry, route, this);
            return entry;
        }


        /**
         * Handles cleaning up for a lost connection with the given config.
         * Decrements any connection counts and notifies waiting threads,
         * if appropriate.
         * 
         * @param config        the route of the connection that was lost
         */
        public synchronized
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
                storeReferenceToConnection(entry, route, this);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Getting free connection, route=" + route);
                }

                // remove the connection from the timeout handler
                idleConnectionHandler.remove(entry.connection);
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
                if (!entry.connection.isOpen()) {
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

            HttpRoute route = entry.plannedRoute;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Reclaiming connection, route=" + route);
            }

            closeConnection(entry.connection);

            RouteConnPool routePool = getRoutePool(route);
            
            routePool.freeConnections.remove(entry);
            routePool.numConnections--;
            numConnections--;
            if (routePool.numConnections < 1)
                mapRoutes.remove(route);

            // remove the connection from the timeout handler
            idleConnectionHandler.remove(entry.connection);
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
         *
         * @see #notifyWaitingThread(RouteConnPool)
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

            HttpRoute route = entry.plannedRoute;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Freeing connection, route=" + route);
            }

            synchronized (this) {

                if (isShutDown) {
                    // the connection manager has been shutdown, release the
                    // connection's resources and get out of here
                    closeConnection(entry.connection);
                    return;
                }
                
                RouteConnPool routePool = getRoutePool(route);

                // Put the connection back in the available list
                // and notify a waiter
                routePool.freeConnections.add(entry);
                if (routePool.numConnections == 0) {
                    // for some reason this pool didn't already exist
                    LOG.error("Route connection pool not found. " + route);
                    routePool.numConnections = 1;
                }

                freeConnections.add(entry);
                // We can remove the reference to this connection as we have
                // control over it again. This also ensures that the connection
                // manager can be GCed.
                removeReferenceToConnection(entry);
                if (numConnections == 0) {
                    // for some reason this pool didn't already exist
                    LOG.error("Route connection pool not found. " + route);
                    numConnections = 1;
                }

                // register the connection with the timeout handler
                idleConnectionHandler.add(entry.connection);

                notifyWaitingThread(routePool);
            }
        }
    } // class ConnectionPool


    private static void closeConnection(final OperatedClientConnection conn) {
        try {
            conn.close();
        } catch (IOException ex) {
            LOG.debug("I/O error closing connection", ex);
        }
    }

    /**
     * A simple struct-like class to combine the objects needed to release
     * a connection's resources when claimed by the garbage collector.
     */
    private static class ConnectionSource {
        
        /** The connection pool that created the connection */
        public ConnectionPool connectionPool;

        /** The connection's planned route. */
        public HttpRoute route;
    }
    
    /**
     * A simple struct-like class to combine the connection list and the count
     * of created connections.
     */
    private static class RouteConnPool {

        /** The route this pool is for */
        public HttpRoute route;
        
        /** The list of free connections */
        public LinkedList freeConnections = new LinkedList();
        
        /** The list of WaitingThreads for this pool. */
        public LinkedList waitingThreads = new LinkedList();

        /** The number of created connections */
        public int numConnections = 0;
    }
    
    /**
     * A simple struct-like class to combine the waiting thread and the connection 
     * pool it is waiting on.
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
     * A thread for listening for HttpConnections reclaimed by the garbage
     * collector.
     */
    private static class ReferenceQueueThread extends Thread {

        private volatile boolean isShutDown = false;
        
        /**
         * Create an instance and make this a daemon thread.
         */
        public ReferenceQueueThread() {
            setDaemon(true);
            setName("ThreadSafeClientConnManager cleanup");
        }

        public void shutdown() {
            this.isShutDown = true;
            this.interrupt();
        }
        
        /**
         * Handles cleaning up for the given connection reference.
         * 
         * @param ref the reference to clean up
         */
        private void handleReference(Reference ref) {
            
            ConnectionSource source = null;
            
            synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
                source = (ConnectionSource) REFERENCE_TO_CONNECTION_SOURCE.remove(ref);
            }
            // only clean up for this reference if it is still associated with 
            // a ConnectionSource
            if (source != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "Connection reclaimed by garbage collector, route=" 
                        + source.route);
                }
                
                source.connectionPool.handleLostConnection(source.route);
            }
        }

        /**
         * Start execution.
         */
        public void run() {
            while (!isShutDown) {
                try {
                    // remove the next reference and process it
                    Reference ref = REFERENCE_QUEUE.remove();
                    if (ref != null) {
                        handleReference(ref);
                    }
                } catch (InterruptedException e) {
                    LOG.debug("ReferenceQueueThread interrupted", e);
                }
            }
        }

    } // class ReferenceQueueThread

    
    /**
     * A pool entry representing a connection that tracks it's route.
     * For historical reasons, these entries are sometimes referred to
     * as <i>connections</i> throughout the code.
     */
    private class TrackingPoolEntry extends AbstractPoolEntry {

        //@@@ move to base class
        /** The route for which this entry gets allocated. */
        private HttpRoute plannedRoute;

        /** The connection manager. */
        private ThreadSafeClientConnManager manager;


        /**
         * A weak reference used to detect GCed pool entries.
         * Of course, pool entries can only be GCed when they are allocated
         * and therefore not referenced with a hard link in the manager.
         */
        private WeakReference reference;

            
        /**
         * Creates a new pool entry.
         *
         * @param occ   the underlying connection for this entry
         */
        private TrackingPoolEntry(OperatedClientConnection occ) {
            super(occ);
            //@@@ pass planned route to the constructor?
            //@@@ or update when the adapter is created?
            this.manager = ThreadSafeClientConnManager.this;
            this.reference = new WeakReference(this, REFERENCE_QUEUE);
        }


        // non-javadoc, see base AbstractPoolEntry
        protected ClientConnectionOperator getOperator() {
            return ThreadSafeClientConnManager.this.connOperator;
        }


    } // class TrackingPoolEntry

    
    /**
     * A connection wrapper and callback handler.
     * All connections given out by the manager are wrappers which
     * can be {@link #detach detach}ed to prevent further use on release.
     */
    private class HttpConnectionAdapter extends AbstractPooledConnAdapter {
        //@@@ HTTPCLIENT-653
        //@@@ this adapter being a nested class prevents proper detaching of
        //@@@ the adapter from the manager, and therefore GC of the manager

        /**
         * Creates a new adapter.
         *
         * @param entry   the pool entry for the connection being wrapped
         */
        protected HttpConnectionAdapter(TrackingPoolEntry entry) {
            super(ThreadSafeClientConnManager.this, entry);
            super.markedReusable = true;
        }
    }

} // class ThreadSafeClientConnManager

