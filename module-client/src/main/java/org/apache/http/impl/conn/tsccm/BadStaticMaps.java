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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.http.conn.HttpRoute;



/**
 * Some static maps and associated methods.
 * These are currently still used, but should be removed a.s.a.p.
 */
final /*default*/ class BadStaticMaps {

    private final static Log LOG = LogFactory.getLog(BadStaticMaps.class);


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
    static /*default*/ final ReferenceQueue REFERENCE_QUEUE = new ReferenceQueue();

    /**
     * The thread responsible for handling lost connections.
     */
    private static ReferenceQueueThread REFERENCE_QUEUE_THREAD;

    
    /**
     * Holds references to all active instances of this class.
     */    
    static /*default*/ WeakHashMap ALL_CONNECTION_MANAGERS = new WeakHashMap();


    /** Disabled default constructor. */
    private BadStaticMaps() {
        // no body
    }


    /**
     * Shuts down and cleans up resources used by all instances of 
     * ThreadSafeClientConnManager. All static resources are released, all threads are 
     * stopped, and {@link ThreadSafeClientConnManager#shutdown()} is called on all live instances of 
     * ThreadSafeClientConnManager.
     */
    static /*default*/ void shutdownAll() {

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
     * @param entry             the pool entry to store a reference for
     * @param route             the connection's planned route
     * @param connectionPool    the connection pool that created the entry
     * 
     * @see #removeReferenceToConnection
     */
    static /*default*/ void storeReferenceToConnection(
        BasicPoolEntry entry,
        HttpRoute route,
        ThreadSafeClientConnManager.ConnectionPool connectionPool
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
            
            REFERENCE_TO_CONNECTION_SOURCE.put(entry.getWeakRef(), source);
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
    static /*default*/ void removeReferenceToConnection(BasicPoolEntry entry) {
        
        synchronized (REFERENCE_TO_CONNECTION_SOURCE) {
            REFERENCE_TO_CONNECTION_SOURCE.remove(entry.getWeakRef());
        }
    }    


    /**
     * Closes and releases all connections currently checked out of the
     * given connection pool.
     * @param connectionPool the pool for which to shutdown the connections
     */
    static /*default*/
    void shutdownCheckedOutConnections(ThreadSafeClientConnManager.ConnectionPool connectionPool) {

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
                    Object entry = ref.get(); // BasicPoolEntry
                    if (entry != null) {
                        connectionsToClose.add(entry);
                    }
                }
            }
        }

        // close and release the connections outside of the synchronized block
        // to avoid holding the lock for too long
        for (Iterator i = connectionsToClose.iterator(); i.hasNext();) {
            BasicPoolEntry entry =
                (BasicPoolEntry) i.next();
            ThreadSafeClientConnManager.closeConnection(entry.getConnection());
            entry.getManager().releasePoolEntry(entry);
        }
    }


    /**
     * A simple struct-like class to combine the objects needed to release
     * a connection's resources when claimed by the garbage collector.
     */
    private static class ConnectionSource {

        /** The connection pool that created the connection */
        public ThreadSafeClientConnManager.ConnectionPool connectionPool;

        /** The connection's planned route. */
        public HttpRoute route;
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

}
