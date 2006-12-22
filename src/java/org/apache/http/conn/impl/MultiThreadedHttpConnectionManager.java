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
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HostConfiguration;
import org.apache.http.conn.HttpConnectionManager;
import org.apache.http.conn.HttpHostConnection;
import org.apache.http.conn.params.HttpConnectionManagerParams;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.params.HttpParams;

/**
 * Manages a set of HttpConnections for various HostConfigurations.
 *
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author Eric Johnson
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author Carl A. Dunham
 *
 * @since 2.0
 */
public class MultiThreadedHttpConnectionManager implements HttpConnectionManager {

    // -------------------------------------------------------- Class Variables

    /** Log object for this class. */
    private static final Log LOG = LogFactory.getLog(MultiThreadedHttpConnectionManager.class);

    /**
     * A mapping from Reference to ConnectionSource.  Used to reclaim resources when connections
     * are lost to the garbage collector.
     */
    private static final Map REFERENCE_TO_CONNECTION_SOURCE = new HashMap();
    
    /**
     * The reference queue used to track when HttpConnections are lost to the
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
    

    // ---------------------------------------------------------- Class Methods

    /**
     * Shuts down and cleans up resources used by all instances of 
     * MultiThreadedHttpConnectionManager. All static resources are released, all threads are 
     * stopped, and {@link #shutdown()} is called on all live instances of 
     * MultiThreadedHttpConnectionManager.
     *
     * @see #shutdown()
     */
    public static void shutdownAll() {

        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
            // shutdown all connection managers
            synchronized (ALL_CONNECTION_MANAGERS) {
                Iterator connIter = ALL_CONNECTION_MANAGERS.keySet().iterator();
                while (connIter.hasNext()) {
                    MultiThreadedHttpConnectionManager connManager = 
                        (MultiThreadedHttpConnectionManager) connIter.next();
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
     * Stores the reference to the given connection along with the host config and connection pool.  
     * These values will be used to reclaim resources if the connection is lost to the garbage 
     * collector.  This method should be called before a connection is released from the connection 
     * manager.
     * 
     * <p>A static reference to the connection manager will also be stored.  To ensure that
     * the connection manager can be GCed {@link #removeReferenceToConnection(HttpHostConnection)}
     * should be called for all connections that the connection manager is storing a reference
     * to.</p>
     * 
     * @param connection the connection to create a reference for
     * @param hostConfiguration the connection's host config
     * @param connectionPool the connection pool that created the connection
     * 
     * @see #removeReferenceToConnection(HttpHostConnection)
     */
    private static void storeReferenceToConnection(
        HttpConnectionWithReference connection,
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
     * Closes and releases all connections currently checked out of the given connection pool.
     * @param connectionPool the connection pool to shutdown the connections for
     */
    private static void shutdownCheckedOutConnections(ConnectionPool connectionPool) {

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
                    HttpHostConnection connection = (HttpHostConnection) ref.get();
                    if (connection != null) {
                        connectionsToClose.add(connection);
                    }
                }
            }
        }

        // close and release the connections outside of the synchronized block to
        // avoid holding the lock for too long
        for (Iterator i = connectionsToClose.iterator(); i.hasNext();) {
            HttpHostConnection connection = (HttpHostConnection) i.next();
            closeConnection(connection);
            // remove the reference to the connection manager. this ensures
            // that the we don't accidentally end up here again
            connection.setHttpConnectionManager(null);
            connection.releaseConnection();
        }
    }
    
    /**
     * Removes the reference being stored for the given connection.  This method should be called
     * when the connection manager again has a direct reference to the connection.
     * 
     * @param connection the connection to remove the reference for
     * 
     * @see #storeReferenceToConnection(HttpHostConnection, HostConfiguration, ConnectionPool)
     */
    private static void removeReferenceToConnection(HttpConnectionWithReference connection) {
        
        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
            REFERENCE_TO_CONNECTION_SOURCE.remove(connection.reference);
        }
    }    
    

    // ----------------------------------------------------- Instance Variables

    /**
     * Collection of parameters associated with this connection manager.
     */
    private HttpParams params = new DefaultHttpParams(); 

    /** Connection Pool */
    private ConnectionPool connectionPool;

    private boolean shutdown = false;
    

    // ----------------------------------------------------------- Constructors

    /**
     * No-args constructor
     */
    public MultiThreadedHttpConnectionManager() {
        this.connectionPool = new ConnectionPool();
        synchronized(ALL_CONNECTION_MANAGERS) {
            ALL_CONNECTION_MANAGERS.put(this, null);
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
            if (!shutdown) {
                shutdown = true;
                connectionPool.shutdown();
            }
        }
    }
    
    /**
     * @see HttpConnectionManager#getConnection(HostConfiguration)
     */
    public HttpHostConnection getConnection(HostConfiguration hostConfiguration) {

        while (true) {
            try {
                return getConnection(hostConfiguration, 0);
            } catch (ConnectionPoolTimeoutException e) {
                // we'll go ahead and log this, but it should never happen. HttpExceptions
                // are only thrown when the timeout occurs and since we have no timeout
                // it should never happen.
                LOG.debug(
                    "Unexpected exception while waiting for connection",
                    e
                );
            }
        }
    }

    /**
     * Gets a connection or waits if one is not available.  A connection is
     * available if one exists that is not being used or if fewer than
     * maxHostConnections have been created in the connectionPool, and fewer
     * than maxTotalConnections have been created in all connectionPools.
     *
     * @param hostConfiguration The host configuration specifying the connection
     *        details.
     * @param timeout the number of milliseconds to wait for a connection, 0 to
     * wait indefinitely
     *
     * @return HttpHostConnection an available connection
     *
     * @throws HttpException if a connection does not become available in
     * 'timeout' milliseconds
     * 
     * @since 3.0
     */
    public HttpHostConnection getConnection(HostConfiguration hostConfiguration, 
        long timeout) throws ConnectionPoolTimeoutException {

        if (hostConfiguration == null) {
            throw new IllegalArgumentException("hostConfiguration is null");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("HttpConnectionManager.getConnection:  config = "
                + hostConfiguration + ", timeout = " + timeout);
        }

        final HttpHostConnection conn = doGetConnection(hostConfiguration, timeout);

        // wrap the connection in an adapter so we can ensure it is used 
        // only once
        return new HttpConnectionAdapter(conn);
    }

    private HttpHostConnection doGetConnection(HostConfiguration hostConfiguration, 
        long timeout) throws ConnectionPoolTimeoutException {

        HttpHostConnection connection = null;

        int maxHostConnections = HttpConnectionManagerParams
            .getMaxConnectionsPerHost(this.params, hostConfiguration);
        int maxTotalConnections = HttpConnectionManagerParams
            .getMaxTotalConnections(this.params);
        
        synchronized (connectionPool) {

            // we used to clone the hostconfig here, but it is now immutable:
            //hostConfiguration = new HostConfiguration(hostConfiguration);
            HostConnectionPool hostPool = connectionPool.getHostPool(hostConfiguration);
            WaitingThread waitingThread = null;

            boolean useTimeout = (timeout > 0);
            long timeToWait = timeout;
            long startWait = 0;
            long endWait = 0;

            while (connection == null) {

                if (shutdown) {
                    throw new IllegalStateException("Connection factory has been shutdown.");
                }
                
                // happen to have a free connection with the right specs
                //
                if (hostPool.freeConnections.size() > 0) {
                    connection = connectionPool.getFreeConnection(hostConfiguration);

                // have room to make more
                //
                } else if ((hostPool.numConnections < maxHostConnections) 
                    && (connectionPool.numConnections < maxTotalConnections)) {

                    connection = connectionPool.createConnection(hostConfiguration);

                // have room to add host connection, and there is at least one free
                // connection that can be liberated to make overall room
                //
                } else if ((hostPool.numConnections < maxHostConnections) 
                    && (connectionPool.freeConnections.size() > 0)) {

                    connectionPool.deleteLeastUsedConnection();
                    connection = connectionPool.createConnection(hostConfiguration);

                // otherwise, we have to wait for one of the above conditions to
                // become true
                //
                } else {
                    // TODO: keep track of which hostConfigurations have waiting
                    // threads, so they avoid being sacrificed before necessary

                    try {
                        
                        if (useTimeout && timeToWait <= 0) {
                            throw new ConnectionPoolTimeoutException("Timeout waiting for connection");
                        }
                        
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unable to get a connection, waiting..., hostConfig=" + hostConfiguration);
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
        return connection;
    }

    /**
     * Gets the total number of pooled connections for the given host configuration.  This 
     * is the total number of connections that have been created and are still in use 
     * by this connection manager for the host configuration.  This value will
     * not exceed the {@link #getMaxConnectionsPerHost() maximum number of connections per
     * host}.
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
     * manager.  This value will not exceed the {@link #getMaxTotalConnections() 
     * maximum number of connections}.
     * 
     * @return the total number of pooled connections
     */
    public int getConnectionsInPool() {
        synchronized (connectionPool) {
            return connectionPool.numConnections;
        }
    }
    
    /**
     * Deletes all closed connections.  Only connections currently owned by the connection
     * manager are processed.
     * 
     * @see HttpHostConnection#isOpen()
     * 
     * @since 3.0
     */
    public void deleteClosedConnections() {
        connectionPool.deleteClosedConnections();
    }
    
    /**
     * @since 3.0
     */
    public void closeIdleConnections(long idleTimeout) {
        connectionPool.closeIdleConnections(idleTimeout);
        deleteClosedConnections();
    }
    
    /**
     * Make the given HttpHostConnection available for use by other requests.
     * If another thread is blocked in getConnection() that could use this
     * connection, it will be woken up.
     *
     * @param conn the HttpHostConnection to make available.
     */
    public void releaseConnection(HttpHostConnection conn) {

        if (conn instanceof HttpConnectionAdapter) {
            // connections given out are wrapped in an HttpConnectionAdapter
            conn = ((HttpConnectionAdapter) conn).getWrappedConnection();
        } else {
            // this is okay, when an HttpConnectionAdapter is released
            // is releases the real connection
        }

        // make sure that the response has been read.
        SimpleHttpConnectionManager.finishLastResponse(conn);

        connectionPool.freeConnection(conn);
    }

    /**
     * Returns {@link HttpParams parameters} associated 
     * with this connection manager.
     * 
     * @since 3.0
     * 
     * @see HttpConnectionManagerParams
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
     * Global Connection Pool, including per-host pools
     */
    private class ConnectionPool {
        
        /** The list of free connections */
        private LinkedList freeConnections = new LinkedList();

        /** The list of WaitingThreads waiting for a connection */
        private LinkedList waitingThreads = new LinkedList();

        /**
         * Map where keys are {@link HostConfiguration}s and values are {@link
         * HostConnectionPool}s
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
                HttpHostConnection conn = (HttpHostConnection) iter.next();
                iter.remove();
                closeConnection(conn);
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
         * Creates a new connection and returns it for use of the calling method.
         *
         * @param hostConfiguration the configuration for the connection
         * @return a new connection or <code>null</code> if none are available
         */
        public synchronized HttpHostConnection createConnection(HostConfiguration hostConfiguration) {
            HostConnectionPool hostPool = getHostPool(hostConfiguration);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Allocating new connection, hostConfig=" + hostConfiguration);
            }
            HttpConnectionWithReference connection = new HttpConnectionWithReference();
            connection.setHttpConnectionManager(MultiThreadedHttpConnectionManager.this);
            numConnections++;
            hostPool.numConnections++;
    
            // store a reference to this connection so that it can be cleaned up
            // in the event it is not correctly released
            storeReferenceToConnection(connection, hostConfiguration, this);
            return connection;
        }
    
        /**
         * Handles cleaning up for a lost connection with the given config.  Decrements any 
         * connection counts and notifies waiting threads, if appropriate.
         * 
         * @param config the host configuration of the connection that was lost
         */
        public synchronized void handleLostConnection(HostConfiguration config) {
            HostConnectionPool hostPool = getHostPool(config);
            hostPool.numConnections--;

            numConnections--;
            notifyWaitingThread(config);
        }

        /**
         * Get the pool (list) of connections available for the given hostConfig.
         *
         * @param hostConfiguration the configuraton for the connection pool
         * @return a pool (list) of connections available for the given config
         */
        public synchronized HostConnectionPool getHostPool(HostConfiguration hostConfiguration) {

            // Look for a list of connections for the given config
            HostConnectionPool listConnections = (HostConnectionPool) 
                mapHosts.get(hostConfiguration);
            if (listConnections == null) {
                // First time for this config
                listConnections = new HostConnectionPool();
                listConnections.hostConfiguration = hostConfiguration;
                mapHosts.put(hostConfiguration, listConnections);
            }
            
            return listConnections;
        }

        /**
         * If available, get a free connection for this host
         *
         * @param hostConfiguration the configuraton for the connection pool
         * @return an available connection for the given config
         */
        public synchronized HttpHostConnection getFreeConnection(HostConfiguration hostConfiguration) {

            HttpConnectionWithReference connection = null;
            
            HostConnectionPool hostPool = getHostPool(hostConfiguration);

            if (hostPool.freeConnections.size() > 0) {
                connection = (HttpConnectionWithReference) hostPool.freeConnections.removeLast();
                freeConnections.remove(connection);
                // store a reference to this connection so that it can be cleaned up
                // in the event it is not correctly released
                storeReferenceToConnection(connection, hostConfiguration, this);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Getting free connection, hostConfig=" + hostConfiguration);
                }

                // remove the connection from the timeout handler
                idleConnectionHandler.remove(connection);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("There were no free connections to get, hostConfig=" 
                    + hostConfiguration);
            }
            return connection;
        }
        
        /**
         * Deletes all closed connections.
         */        
        public synchronized void deleteClosedConnections() {
            
            Iterator iter = freeConnections.iterator();
            
            while (iter.hasNext()) {
                HttpHostConnection conn = (HttpHostConnection) iter.next();
                if (!conn.isOpen()) {
                    iter.remove();
                    deleteConnection(conn);
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
         * Deletes the given connection.  This will remove all reference to the connection
         * so that it can be GCed.
         * 
         * <p><b>Note:</b> Does not remove the connection from the freeConnections list.  It
         * is assumed that the caller has already handled this case.</p>
         * 
         * @param connection The connection to delete
         */
        private synchronized void deleteConnection(HttpHostConnection connection) {
            
            HostConfiguration connectionConfiguration = connection.getHostConfiguration();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Reclaiming connection, hostConfig=" + connectionConfiguration);
            }

            closeConnection(connection);

            HostConnectionPool hostPool = getHostPool(connectionConfiguration);
            
            hostPool.freeConnections.remove(connection);
            hostPool.numConnections--;
            numConnections--;

            // remove the connection from the timeout handler
            idleConnectionHandler.remove(connection);            
        }
        
        /**
         * Close and delete an old, unused connection to make room for a new one.
         */
        public synchronized void deleteLeastUsedConnection() {

            HttpHostConnection connection = (HttpHostConnection) freeConnections.removeFirst();

            if (connection != null) {
                deleteConnection(connection);
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
         * @param conn a connection that is no longer being used
         */
        public void freeConnection(HttpHostConnection conn) {

            HostConfiguration connectionConfiguration = conn.getHostConfiguration();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Freeing connection, hostConfig=" + connectionConfiguration);
            }

            synchronized (this) {
                
                if (shutdown) {
                    // the connection manager has been shutdown, release the connection's
                    // resources and get out of here
                    closeConnection(conn);
                    return;
                }
                
                HostConnectionPool hostPool = getHostPool(connectionConfiguration);

                // Put the connect back in the available list and notify a waiter
                hostPool.freeConnections.add(conn);
                if (hostPool.numConnections == 0) {
                    // for some reason this connection pool didn't already exist
                    LOG.error("Host connection pool not found, hostConfig=" 
                              + connectionConfiguration);
                    hostPool.numConnections = 1;
                }

                freeConnections.add(conn);
                // we can remove the reference to this connection as we have control over
                // it again.  this also ensures that the connection manager can be GCed
                removeReferenceToConnection((HttpConnectionWithReference) conn);
                if (numConnections == 0) {
                    // for some reason this connection pool didn't already exist
                    LOG.error("Host connection pool not found, hostConfig=" 
                              + connectionConfiguration);
                    numConnections = 1;
                }

                // register the connection with the timeout handler
                idleConnectionHandler.add(conn);

                notifyWaitingThread(hostPool);
            }
        }
    }
    
    private static void closeConnection(final HttpHostConnection conn) {
        try {
            conn.close();
        } catch (IOException ex) {
            LOG.debug("I/O error closing connection", ex);
        }
    }

    /**
     * A simple struct-like class to combine the objects needed to release a connection's
     * resources when claimed by the garbage collector.
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

        private boolean shutdown = false;
        
        /**
         * Create an instance and make this a daemon thread.
         */
        public ReferenceQueueThread() {
            setDaemon(true);
            setName("MultiThreadedHttpConnectionManager cleanup");
        }

        public void shutdown() {
            this.shutdown = true;
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
            while (!shutdown) {
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
     * A connection that keeps a reference to itself.
     */
    private static class HttpConnectionWithReference extends DefaultHttpHostConnection {
        
        public WeakReference reference = new WeakReference(this, REFERENCE_QUEUE);
        
        /**
         * @param hostConfiguration
         */
        public HttpConnectionWithReference() {
            super();
        }

    }
    
    /**
     * An HttpHostConnection wrapper that ensures a connection cannot be used
     * once released.
     */
    private static class HttpConnectionAdapter implements HttpHostConnection {

        /** the wrapped connection
         */
        private HttpHostConnection wrappedConnection;

        /**
         * Creates a new HttpConnectionAdapter.
         * @param connection the connection to be wrapped
         */
        public HttpConnectionAdapter(HttpHostConnection connection) {
            super();
            this.wrappedConnection = connection;
        }

        /**
         * Tests if the wrapped connection is still available.
         * @return boolean
         */
        protected boolean hasConnection() {
            return wrappedConnection != null;
        }

        /**
         * @return HttpHostConnection
         */
        HttpHostConnection getWrappedConnection() {
            return wrappedConnection;
        }

        //=======================
        // HttpConnection methods
        //=======================
        
        public void close() throws IOException {
            if (hasConnection()) {
                wrappedConnection.close();
            } else {
                // do nothing
            }
        }

        public boolean isOpen() {
            if (hasConnection()) {
                return wrappedConnection.isOpen();
            } else {
                return false;
            }
        }

        public boolean isStale() {
            if (hasConnection()) {
                return wrappedConnection.isStale();
            } else {
                return true;
            }
        }

        public void shutdown() throws IOException {
            if (hasConnection()) {
                wrappedConnection.shutdown();
            } else {
                // do nothing
            }
        }

        //=============================
        // HttpClientConnection methods
        //=============================
        public void flush() throws IOException {
            if (hasConnection()) {
                wrappedConnection.flush();
            } else {
                // do nothing
            }
        }

        public boolean isResponseAvailable(int timeout) throws IOException {
            if (hasConnection()) {
                return wrappedConnection.isResponseAvailable(timeout);
            } else {
                return false;
            }
        }

        public HttpResponse receiveResponseHeader(final HttpParams params) 
                throws HttpException, IOException {
            if (hasConnection()) {
                return wrappedConnection.receiveResponseHeader(params);
            } else {
                return null;
            }
        }

        public void receiveResponseEntity(final HttpResponse response) 
                throws HttpException, IOException {
            if (hasConnection()) {
                wrappedConnection.receiveResponseEntity(response);
            } else {
                // do nothing
            }
        }

        public void sendRequestHeader(final HttpRequest request) 
                throws HttpException, IOException {
            if (hasConnection()) {
                wrappedConnection.sendRequestHeader(request);
            } else {
                // do nothing
            }
        }

        public void sendRequestEntity(final HttpEntityEnclosingRequest request) 
                throws HttpException, IOException {
            if (hasConnection()) {
                wrappedConnection.sendRequestEntity(request);
            } else {
                // do nothing
            }
        }

        //===========================
        // HttpInetConnection methods
        //===========================
        public InetAddress getLocalAddress() {
            if (hasConnection()) {
                return wrappedConnection.getLocalAddress();
            } else {
                return null;
            }
        }

        public InetAddress getRemoteAddress() {
            if (hasConnection()) {
                return wrappedConnection.getRemoteAddress();
            } else {
                return null;
            }
        }

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
        
    }

}

