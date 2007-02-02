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

package org.apache.http.conn.impl;

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
import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HostConfiguration;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.params.HttpConnectionManagerParams;
import org.apache.http.impl.params.DefaultHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;


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
 * @version   $Revision$ $Date$
 *
 * @since 4.0
 */
public class ThreadSafeClientConnManager
    implements ClientConnectionManager {


    private static final Log LOG =
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


    /** The parameters of this connection manager. */
    private HttpParams params = new DefaultHttpParams(); 


    /** The pool of connections being managed. */
    private ConnectionPool connectionPool;

    /** The operator for opening and updating connections. */
    private ClientConnectionOperator connectionOperator;

    /** Indicates whether this connection manager is shut down. */
    private boolean isShutDown;
    


    /**
     * Creates a new thread safe connection manager.
     *
     * @param params    the parameters for this manager
     */
    public ThreadSafeClientConnManager(HttpParams params) {

        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        this.params = params;
        this.connectionPool = new ConnectionPool();
        this.connectionOperator = createConnectionOperator();
        this.isShutDown = false;

        synchronized(ALL_CONNECTION_MANAGERS) {
            ALL_CONNECTION_MANAGERS.put(this, null);
        }
    } // <constructor>



    // non-javadoc, see interface ClientConnectionManager
    public ManagedClientConnection getConnection(HostConfiguration route) {

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
    public ManagedClientConnection getConnection(HostConfiguration route,
                                                 long timeout)
        throws ConnectionPoolTimeoutException {

        if (route == null) {
            throw new IllegalArgumentException("hostConfiguration is null");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("ThreadSafeClientConnManager.getConnection:  route = "
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
    private TrackingPoolEntry doGetConnection(HostConfiguration route,
                                              long timeout)
        throws ConnectionPoolTimeoutException {

        TrackingPoolEntry entry = null;

        int maxHostConnections = HttpConnectionManagerParams
            .getMaxConnectionsPerHost(this.params, route);
        int maxTotalConnections = HttpConnectionManagerParams
            .getMaxTotalConnections(this.params);
        
        synchronized (connectionPool) {

            // we used to clone the hostconfig here, but it is now immutable:
            //route = new HostConfiguration(route);
            HostConnectionPool hostPool = connectionPool.getHostPool(route);
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
                if (hostPool.freeConnections.size() > 0) {
                    entry = connectionPool.getFreeConnection(route);

                // have room to make more
                //
                } else if ((hostPool.numConnections < maxHostConnections) 
                    && (connectionPool.numConnections < maxTotalConnections)) {

                    entry = createPoolEntry(route);

                // have room to add host connection, and there is at least one
                // free connection that can be liberated to make overall room
                //
                } else if ((hostPool.numConnections < maxHostConnections) 
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
                            LOG.debug("Unable to get a connection, waiting..., hostConfig=" + route);
                        }
                        
                        if (waitingThread == null) {
                            waitingThread = new WaitingThread();
                            waitingThread.hostConnectionPool = hostPool;
                            waitingThread.thread = Thread.currentThread();
                        }
                                    
                        if (useTimeout) {
                            startWait = System.currentTimeMillis();
                        }
                        
                        hostPool.waitingThreads.addLast(waitingThread);
                        connectionPool.waitingThreads.addLast(waitingThread);
                        connectionPool.wait(timeToWait);
                        
                        // we have not been interrupted so we need to remove ourselves from the 
                        // wait queue
                        hostPool.waitingThreads.remove(waitingThread);
                        connectionPool.waitingThreads.remove(waitingThread);
                    } catch (InterruptedException e) {
                        // do nothing
                    } finally {
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
    private TrackingPoolEntry createPoolEntry(HostConfiguration route) {

        OperatedClientConnection occ = createOperatedConnection();
        return connectionPool.createEntry(route, occ);
    }


    /**
     * Hook for creating an operated connection to be managed.
     * Derived classes can override this method to change the
     * instantiation of operated connections.
     * The default implementation here instantiates
     * {@link DefaultClientConnection DefaultClientConnection}.
     *
     * @return  a new connection to be managed
     */
    protected OperatedClientConnection createOperatedConnection() {
        return new DefaultClientConnection();
    }


    /**
     * Hook for creating the connection operator.
     * It is called by the constructor.
     * Derived classes can override this method to change the
     * instantiation of the operator.
     * The default implementation here instantiates
     * {@link DefaultClientConnectionOperator DefaultClientConnectionOperator}.
     *
     * @return  the connection operator to use
     */
    protected ClientConnectionOperator createConnectionOperator() {
        return new DefaultClientConnectionOperator();
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
        if ((hca.poolEntry != null) && (hca.poolEntry.manager != this)) {
            throw new IllegalArgumentException
                ("Connection not obtained from this manager.");
        }

        TrackingPoolEntry entry = hca.poolEntry;
        hca.detach();
        releasePoolEntry(entry);
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

        // make sure that the response has been read completely
        System.out.println("@@@ should consume response and free connection");
        //@@@ SimpleHttpConnectionManager.finishLastResponse(conn);
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
                Iterator connIter = ALL_CONNECTION_MANAGERS.keySet().iterator();
                while (connIter.hasNext()) {
                    ThreadSafeClientConnManager connManager = 
                        (ThreadSafeClientConnManager) connIter.next();
                    connIter.remove();
                    connManager.shutdown();
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
     * @param hostConfiguration the connection's route
     * @param connectionPool    the connection pool that created the entry
     * 
     * @see #removeReferenceToConnection
     */
    private static void storeReferenceToConnection(
        TrackingPoolEntry connection,
        HostConfiguration hostConfiguration,
        ConnectionPool connectionPool
    ) {

        ConnectionSource source = new ConnectionSource();
        source.connectionPool = connectionPool;
        source.hostConfiguration = hostConfiguration;

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
     * Gets the total number of pooled connections for the given host configuration.  This 
     * is the total number of connections that have been created and are still in use 
     * by this connection manager for the host configuration.  This value will
     * not exceed the maximum number of connections per host.
     * 
     * @param hostConfiguration The host configuration
     * @return The total number of pooled connections
     */
    public int getConnectionsInPool(HostConfiguration hostConfiguration) {
        synchronized (connectionPool) {
            HostConnectionPool hostPool = connectionPool.getHostPool(hostConfiguration);
            return hostPool.numConnections;
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
     * @since 3.0
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
         * Map where keys are {@link HostConfiguration}s and values are
         * {@link HostConnectionPool}s
         */
        private final Map mapHosts = new HashMap();

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
                waiter.thread.interrupt();
            }
            
            // clear out map hosts
            mapHosts.clear();
            
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
            TrackingPoolEntry createEntry(HostConfiguration route,
                                          OperatedClientConnection conn) {

            HostConnectionPool hostPool = getHostPool(route);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Allocating new connection, hostConfiguration=" + route);
            }
            TrackingPoolEntry entry = new TrackingPoolEntry(conn);
            entry.plannedRoute = route;
            numConnections++;
            hostPool.numConnections++;
    
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
         * @param config the host configuration of the connection that was lost
         */
        public synchronized
            void handleLostConnection(HostConfiguration config) {

            HostConnectionPool hostPool = getHostPool(config);
            hostPool.numConnections--;
            if (hostPool.numConnections < 1)
                mapHosts.remove(config);

            numConnections--;
            notifyWaitingThread(config);
        }

        /**
         * Get the pool (list) of connections available for the given route.
         *
         * @param route   the configuraton for the connection pool
         * @return a pool (list) of connections available for the given route
         */
        public synchronized
            HostConnectionPool getHostPool(HostConfiguration route) {

            // Look for a list of connections for the given config
            HostConnectionPool listConnections =
                (HostConnectionPool) mapHosts.get(route);
            if (listConnections == null) {
                // First time for this config
                listConnections = new HostConnectionPool();
                listConnections.hostConfiguration = route;
                mapHosts.put(route, listConnections);
            }

            return listConnections;
        }


        /**
         * If available, get a free connection for this host
         *
         * @param hostConfiguration the configuraton for the connection pool
         * @return an available connection for the given config
         */
        public synchronized TrackingPoolEntry getFreeConnection(HostConfiguration hostConfiguration) {

            TrackingPoolEntry entry = null;

            HostConnectionPool hostPool = getHostPool(hostConfiguration);

            if (hostPool.freeConnections.size() > 0) {
                entry = (TrackingPoolEntry) hostPool.freeConnections.removeLast();
                freeConnections.remove(entry);
                // store a reference to this entry so that it can be cleaned up
                // in the event it is not correctly released
                storeReferenceToConnection(entry, hostConfiguration, this);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Getting free connection, hostConfig=" + hostConfiguration);
                }

                // remove the connection from the timeout handler
                idleConnectionHandler.remove(entry.connection);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("There were no free connections to get, hostConfig=" 
                    + hostConfiguration);
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

            HostConfiguration route = entry.route;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Reclaiming connection, hostConfig=" + route);
            }

            closeConnection(entry.connection);

            HostConnectionPool hostPool = getHostPool(route);
            
            hostPool.freeConnections.remove(entry);
            hostPool.numConnections--;
            numConnections--;
            if (hostPool.numConnections < 1)
                mapHosts.remove(route);

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
         * Notifies a waiting thread that a connection for the given configuration is 
         * available.
         * @param configuration the host config to use for notifying
         * @see #notifyWaitingThread(HostConnectionPool)
         */
        public synchronized void notifyWaitingThread(HostConfiguration configuration) {
            notifyWaitingThread(getHostPool(configuration));
        }

        /**
         * Notifies a waiting thread that a connection for the given configuration is 
         * available.  This will wake a thread waiting in this host pool or if there is not
         * one a thread in the connection pool will be notified.
         * 
         * @param hostPool the host pool to use for notifying
         */
        public synchronized void notifyWaitingThread(HostConnectionPool hostPool) {

            // find the thread we are going to notify, we want to ensure that each
            // waiting thread is only interrupted once so we will remove it from 
            // all wait queues before interrupting it
            WaitingThread waitingThread = null;
                
            if (hostPool.waitingThreads.size() > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Notifying thread waiting on host pool, hostConfig=" 
                        + hostPool.hostConfiguration);
                }                
                waitingThread = (WaitingThread) hostPool.waitingThreads.removeFirst();
                waitingThreads.remove(waitingThread);
            } else if (waitingThreads.size() > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No-one waiting on host pool, notifying next waiting thread.");
                }
                waitingThread = (WaitingThread) waitingThreads.removeFirst();
                waitingThread.hostConnectionPool.waitingThreads.remove(waitingThread);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Notifying no-one, there are no waiting threads");
            }
                
            if (waitingThread != null) {
                waitingThread.thread.interrupt();
            }
        }

        /**
         * Marks the given connection as free.
         *
         * @param entry         the pool entry for the connection
         */
        private void freeConnection(TrackingPoolEntry entry) {

            HostConfiguration route = entry.plannedRoute;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Freeing connection, hostConfig=" + route);
            }

            synchronized (this) {

                if (isShutDown) {
                    // the connection manager has been shutdown, release the
                    // connection's resources and get out of here
                    closeConnection(entry.connection);
                    return;
                }
                
                HostConnectionPool hostPool = getHostPool(route);

                // Put the connection back in the available list
                // and notify a waiter
                hostPool.freeConnections.add(entry);
                if (hostPool.numConnections == 0) {
                    // for some reason this connection pool didn't already exist
                    LOG.error("Host connection pool not found, hostConfig=" 
                              + route);
                    hostPool.numConnections = 1;
                }

                freeConnections.add(entry);
                // We can remove the reference to this connection as we have
                // control over it again. This also ensures that the connection
                // manager can be GCed.
                removeReferenceToConnection(entry);
                if (numConnections == 0) {
                    // for some reason this connection pool didn't already exist
                    LOG.error("Host connection pool not found, hostConfig=" 
                              + route);
                    numConnections = 1;
                }

                // register the connection with the timeout handler
                idleConnectionHandler.add(entry.connection);

                notifyWaitingThread(hostPool);
            }
        }
    }


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

        /** The connection's host configuration */
        public HostConfiguration hostConfiguration;
    }
    
    /**
     * A simple struct-like class to combine the connection list and the count
     * of created connections.
     */
    private static class HostConnectionPool {
        /** The hostConfig this pool is for */
        public HostConfiguration hostConfiguration;
        
        /** The list of free connections */
        public LinkedList freeConnections = new LinkedList();
        
        /** The list of WaitingThreads for this host */
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
        public HostConnectionPool hostConnectionPool;
    }

    /**
     * A thread for listening for HttpConnections reclaimed by the garbage
     * collector.
     */
    private static class ReferenceQueueThread extends Thread {

        private boolean isShutDown = false;
        
        /**
         * Create an instance and make this a daemon thread.
         */
        public ReferenceQueueThread() {
            setDaemon(true);
            setName("ThreadSafeClientConnManager cleanup");
        }

        public void shutdown() {
            this.isShutDown = true;
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
                        "Connection reclaimed by garbage collector, hostConfig=" 
                        + source.hostConfiguration);
                }
                
                source.connectionPool.handleLostConnection(source.hostConfiguration);
            }
        }

        /**
         * Start execution.
         */
        public void run() {
            while (!isShutDown) {
                try {
                    // remove the next reference and process it, a timeout 
                    // is used so that the thread does not block indefinitely 
                    // and therefore keep the thread from shutting down
                    Reference ref = REFERENCE_QUEUE.remove(1000);
                    if (ref != null) {
                        handleReference(ref);
                    }
                } catch (InterruptedException e) {
                    LOG.debug("ReferenceQueueThread interrupted", e);
                }
            }
        }

    }

    
    /**
     * A pool entry representing a connection that tracks it's route.
     * For historical reasons, these entries are sometimes referred to
     * as <i>connections</i> throughout the code.
     */
    private class TrackingPoolEntry {

        /** The underlying connection being pooled or used. */
        private OperatedClientConnection connection;

        /** The route for which this entry gets allocated. */
        private HostConfiguration plannedRoute;

        /** The host configuration part of the tracked route. */
        private HostConfiguration route;

        /** The tunnel created flag part of the tracked route. */
        private boolean tunnelled;

        /** The layered flag part of the tracked route. */
        private boolean layered;

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
            this.connection = occ;
            this.route = null;
            this.tunnelled = false;
            this.manager = ThreadSafeClientConnManager.this;
            this.reference = new WeakReference(this, REFERENCE_QUEUE);
        }


        /**
         * Opens the underlying connection.
         *
         * @param route         the route along which to open the connection
         * @param context       the context for opening the connection
         * @param params        the parameters for opening the connection
         *
         * @throws IOException  in case of a problem
         */
        private void open(HostConfiguration route,
                          HttpContext context, HttpParams params)
            throws IOException {

            if (route == null) {
                throw new IllegalArgumentException
                    ("HostConfiguration must not be null.");
            }
            //@@@ is context allowed to be null? depends on operator?
            if (params == null) {
                throw new IllegalArgumentException
                    ("Parameters must not be null.");
            }

            // - collect the arguments
            // - call the operator
            // - update the tracking data
            // In this order, we can be sure that only a successful
            // opening of the connection will be tracked.

            final HttpHost target = route.getHost();
            if (target == null) {
                throw new IllegalStateException
                    ("Target host must not be null.");
            }
            final HttpHost proxy = route.getProxyHost();

            if (LOG.isDebugEnabled()) {
                if (proxy == null) {
                    LOG.debug("Open connection to " + target);
                } else {
                    LOG.debug("Open connection to " + target +
                              " via proxy " + proxy);
                }
            }

            ThreadSafeClientConnManager.this.connectionOperator.openConnection
                (this.connection,
                 (proxy != null) ? proxy : target,
                 context, params);

            this.route = route;

        } // open


        /**
         * Tracks tunnelling of the connection.
         * The tunnel has to be established outside by sending a CONNECT
         * request to the proxy.
         *
         * @param secure    <code>true</code> if the tunnel should be
         *                  considered secure, <code>false</code> otherwise
         * @param params    the parameters for tunnelling the connection
         *
         * @throws IOException  in case of a problem
         */
        private void tunnelCreated(boolean secure, HttpParams params)
            throws IOException {

            if (params == null) {
                throw new IllegalArgumentException
                    ("Parameters must not be null.");
            }

            if (route.getProxyHost() == null) {
                throw new IllegalStateException("No proxy in route.");
            }
            if (tunnelled) {
                throw new IllegalStateException
                    ("Connection is already tunnelled.");
            }

            this.connection.update(null, route.getHost(),
                                   secure, params);
            tunnelled = true;

        } // tunnelCreated


        /**
         * Layers a protocol on top of an established tunnel.
         *
         * @param context   the context for layering
         * @param params    the parameters for layering
         *
         * @throws IOException  in case of a problem
         */
        private void layerProtocol(HttpContext context, HttpParams params)
            throws IOException {

            //@@@ is context allowed to be null? depends on operator?
            if (params == null) {
                throw new IllegalArgumentException
                    ("Parameters must not be null.");
            }

            if (!this.tunnelled) {
                throw new IllegalStateException
                    ("Protocol layering without a tunnel not supported.");
            }
            if (this.layered) {
                throw new IllegalStateException
                    ("Protocol already layered.");
            }

            // - collect the arguments
            // - call the operator
            // - update the tracking data
            // In this order, we can be sure that only a successful
            // layering on top of the connection will be tracked.

            final HttpHost target = route.getHost();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Layer protocol on connection to " + target);
            }

            ThreadSafeClientConnManager.this.connectionOperator
                .updateSecureConnection(this.connection, target,
                                        context, params);

            this.layered = true;

        } // layerProtocol


        /**
         * Tracks close or shutdown of the connection.
         * There is no distinction between the two, the route is dropped
         * in both cases. This method should be called regardless of
         * whether the close or shutdown succeeds or triggers an error.
         */
        private void closing() {
            route     = null;
            tunnelled = false;
        }

    } // class TrackingPoolEntry

    
    /**
     * A connection wrapper and callback handler.
     * All connections given out by the manager are wrappers which
     * can be {@link #detach detach}ed to prevent further use on release.
     */
    private class HttpConnectionAdapter
        extends AbstractClientConnectionAdapter {

        /** The wrapped pool entry. */
        private TrackingPoolEntry poolEntry;


        /**
         * Creates a new adapter.
         *
         * @param entry   the pool entry for the connection being wrapped
         */
        protected HttpConnectionAdapter(TrackingPoolEntry entry) {
            super(entry.connection);
            poolEntry = entry;
        }


        /**
         * Asserts that this adapter is still attached.
         *
         * @throws IllegalStateException
         *      if it is {@link #detach detach}ed
         */
        protected final void assertAttached() {
            if (poolEntry == null) {
                throw new IllegalStateException("Adapter is detached.");
            }
        }

        /**
         * Tests if the wrapped connection is still available.
         * @return boolean
         */
        protected boolean hasConnection() {
            return wrappedConnection != null;
        }

        /**
         * Detaches this adapter from the wrapped connection.
         * This adapter becomes useless.
         */
        private void detach() {
            this.wrappedConnection = null;
            this.poolEntry = null;
        }


        // non-javadoc, see interface ManagedHttpConnection
        public void open(HostConfiguration route,
                         HttpContext context, HttpParams params)
            throws IOException {

            assertAttached();
            poolEntry.open(route, context, params);
        }


        // non-javadoc, see interface ManagedHttpConnection
        public void tunnelCreated(boolean secure, HttpParams params)
            throws IOException {

            assertAttached();
            poolEntry.tunnelCreated(secure, params);
        }


        // non-javadoc, see interface ManagedHttpConnection
        public void layerProtocol(HttpContext context, HttpParams params)
            throws IOException {

            assertAttached();
            poolEntry.layerProtocol(context, params);
        }



        // non-javadoc, see interface HttpConnection        
        public void close() throws IOException {
            if (poolEntry != null)
                poolEntry.closing();

            if (hasConnection()) {
                wrappedConnection.close();
            } else {
                // do nothing
            }
        }

        // non-javadoc, see interface HttpConnection        
        public void shutdown() throws IOException {
            if (poolEntry != null)
                poolEntry.closing();

            if (hasConnection()) {
                wrappedConnection.shutdown();
            } else {
                // do nothing
            }
        }





/*
        //===========================
        // HttpHostConnection methods
        //===========================
        public void setHttpConnectionManager(final HttpConnectionManager httpConnectionManager) {
            if (hasConnection()) {
                wrappedConnection.setHttpConnectionManager(httpConnectionManager);
            } else {
                // do nothing
            }
        }

        public boolean isLocked() {
            if (hasConnection()) {
                return wrappedConnection.isLocked();
            } else {
                return false;
            }
        }

        public void setLocked(boolean locked) {
            if (hasConnection()) {
                wrappedConnection.setLocked(locked);
            } else {
                // do nothing
            }
        }

        public void releaseConnection() {
            HttpHostConnection conn = this.wrappedConnection;
            if (conn != null && !conn.isLocked()) {
                this.wrappedConnection = null;
                conn.releaseConnection();
            } else {
                // do nothing
            }
        }

        public HostConfiguration getHostConfiguration() {
            if (hasConnection()) {
                return wrappedConnection.getHostConfiguration();
            } else {
                return null;
            }
        }

        public void open(final HttpParams params) throws IOException {
            if (hasConnection()) {
                wrappedConnection.open(params);
            } else {
                // do nothing
            }
        }

        public void tunnelCreated(final HttpParams params) throws IOException {
            if (hasConnection()) {
                wrappedConnection.tunnelCreated(params);
            } else {
                // do nothing
            }
        }

        public void setSocketTimeout(int timeout) throws SocketException {
            if (hasConnection()) {
                wrappedConnection.setSocketTimeout(timeout);
            } else {
                // do nothing
            }
        }

        public HttpResponse getLastResponse() {
            if (hasConnection()) {
                return wrappedConnection.getLastResponse();
            } else {
                return null;
            }
        }

        public void setLastResponse(final HttpResponse response) {
            if (hasConnection()) {
                wrappedConnection.setLastResponse(response);
            } else {
                // do nothing
            }
        }
*/        
    } // class HttpConnectionAdapter

}

