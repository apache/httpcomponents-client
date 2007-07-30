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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.params.HttpConnectionManagerParams;



/**
 * A connection pool that maintains connections by route.
 * This class is derived from <code>MultiThreadedHttpConnectionManager</code>
 * in HttpClient 3.x, see there for original authors. It implements the same
 * algorithm for connection re-use and connection-per-host enforcement:
 * <ul>
 * <li>connections are re-used only for the exact same route</li>
 * <li>connection limits are enforced per route rather than per host</li>
 * </ul>
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author and others
 */
public class ConnPoolByRoute extends AbstractConnPool {
        
    //@@@ use a protected LOG in the base class?
    private final Log LOG = LogFactory.getLog(ConnPoolByRoute.class);


    /** The list of free connections */
    private LinkedList freeConnections;

    /** The list of WaitingThreads waiting for a connection */
    private LinkedList waitingThreads;

    /**
     * A map of route-specific pools.
     * Keys are of class {@link HttpRoute},
     * values of class {@link RouteSpecificPool}.
     */
    private final Map routeToPool;



    /**
     * A thread and the pool in which it is waiting.
     * <!-- @@@ will be revised for HTTPCLIENT-677 -->
     */
    protected static class WaitingThread {

        /** The thread that is waiting for an entry. */
        public Thread thread;

        /** The route specific pool the thread is waiting for. */
        public RouteSpecificPool pool;

        /**
         * Indicates the source of an interruption.
         * Set to <code>true</code> inside
         * {@link #notifyWaitingThread(RouteConnPool)}
         * and {@link #shutdown shutdown()}
         * before the thread is interrupted.
         * If not set, the thread was interrupted from the outside.
         */
        public boolean interruptedByConnectionPool = false;
    }



    /**
     * Creates a new connection pool, managed by route.
     *
     * @param mgr   the connection manager
     */
    public ConnPoolByRoute(ClientConnectionManager mgr) {
        super(mgr);

        freeConnections = new LinkedList();
        waitingThreads = new LinkedList();
        routeToPool = new HashMap();
    }


    /**
     * Get a route-specific pool of available connections.
     *
     * @param route   the route
     * @param create    whether to create the pool if it doesn't exist
     *
     * @return  the pool for the argument route,
     *     never <code>null</code> if <code>create</code> is <code>true</code>
     */
    protected synchronized RouteSpecificPool getRoutePool(HttpRoute route,
                                                          boolean create) {

        RouteSpecificPool rospl = (RouteSpecificPool) routeToPool.get(route);
        if ((rospl == null) && create) {
            // no pool for this route yet (or anymore)
            rospl = newRouteSpecificPool(route);
            routeToPool.put(route, rospl);
        }

        return rospl;
    }


    /**
     * Creates a new route-specific pool.
     * Called by {@link #getRoutePool getRoutePool}, if necessary.
     *
     * @param route     the route
     *
     * @return  the new pool
     */
    protected RouteSpecificPool newRouteSpecificPool(HttpRoute route) {
        return new RouteSpecificPool(route);
    }


    //@@@ consider alternatives for gathering statistics
    public synchronized int getConnectionsInPool(HttpRoute route) {
        //@@@ don't allow a pool to be created here!
        RouteSpecificPool rospl = getRoutePool(route, false);
        return (rospl != null) ? rospl.getEntryCount() : 0;
    }


    // non-javadoc, see base class AbstractConnPool
    public synchronized
        BasicPoolEntry getEntry(HttpRoute route, long timeout,
                                ClientConnectionOperator operator)
        throws ConnectionPoolTimeoutException {

        BasicPoolEntry entry = null;

        int maxHostConnections = HttpConnectionManagerParams
            .getMaxConnectionsPerHost(this.params, route);
        int maxTotalConnections = HttpConnectionManagerParams
            .getMaxTotalConnections(this.params);
        
        RouteSpecificPool rospl = getRoutePool(route, true);
        WaitingThread waitingThread = null;

        boolean useTimeout = (timeout > 0);
        long timeToWait = timeout;
        long startWait = 0;
        long endWait = 0;

        while (entry == null) {

            if (isShutDown) {
                throw new IllegalStateException
                    ("Connection pool shut down.");
            }

            // the cases to check for:
            // - have a free connection for that route
            // - allowed to create a free connection for that route
            // - can delete and replace a free connection for another route
            // - need to wait for one of the things above to come true

            entry = getFreeEntry(rospl);
            if (entry != null) {
                // we're fine
                //@@@ yeah this is ugly, but historical... will be revised
            } else if ((rospl.getEntryCount() < maxHostConnections) &&
                       (numConnections < maxTotalConnections)) {

                entry = createEntry(rospl, operator);

            } else if ((rospl.getEntryCount() < maxHostConnections) &&
                       (freeConnections.size() > 0)) {

                deleteLeastUsedEntry();
                entry = createEntry(rospl, operator);

            } else {
                // TODO: keep track of which routes have waiting threads,
                // so they avoid being sacrificed before necessary

                try {
                    if (useTimeout && timeToWait <= 0) {
                        throw new ConnectionPoolTimeoutException
                            ("Timeout waiting for connection");
                    }
   
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Need to wait for connection. " + route);
                    }
   
                    if (waitingThread == null) {
                        waitingThread = new WaitingThread();
                        waitingThread.pool = rospl;
                        waitingThread.thread = Thread.currentThread();
                    } else {
                        waitingThread.interruptedByConnectionPool = false;
                    }

                    if (useTimeout) {
                        startWait = System.currentTimeMillis();
                    }

                    rospl.waitingThreads.addLast(waitingThread);
                    waitingThreads.addLast(waitingThread);
                    wait(timeToWait);

                } catch (InterruptedException e) {
                    if (!waitingThread.interruptedByConnectionPool) {
                        LOG.debug("Interrupted while waiting for connection.", e);
                        throw new IllegalThreadStateException(
                            "Interrupted while waiting in " + this);
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
                        rospl.waitingThreads.remove(waitingThread);
                        waitingThreads.remove(waitingThread);
                    }

                    if (useTimeout) {
                        endWait = System.currentTimeMillis();
                        timeToWait -= (endWait - startWait);
                    }
                }
            }
        } // while no entry

        return entry;

    } // getEntry


    // non-javadoc, see base class AbstractConnPool
    public synchronized void freeEntry(BasicPoolEntry entry) {

        HttpRoute route = entry.getPlannedRoute();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Freeing connection. " + route);
        }

        if (isShutDown) {
            // the pool is shut down, release the
            // connection's resources and get out of here
            closeConnection(entry.getConnection());
            return;
        }

        // no longer issued, we keep a hard reference now
        issuedConnections.remove(entry.getWeakRef());

        RouteSpecificPool rospl = getRoutePool(route, true); //@@@ true???

        rospl.freeEntry(entry);
        freeConnections.add(entry);

        if (numConnections == 0) {
            // for some reason this pool didn't already exist
            LOG.error("Master connection pool not found. " + route);
            numConnections = 1;
        }

        idleConnHandler.add(entry.getConnection());

        notifyWaitingThread(rospl);

    } // freeEntry



    /**
     * If available, get a free pool entry for a route.
     *
     * @param rospl       the route-specific pool from which to get an entry
     *
     * @return  an available pool entry for the given route, or
     *          <code>null</code> if none is available
     */
    protected synchronized
        BasicPoolEntry getFreeEntry(RouteSpecificPool rospl) {

        BasicPoolEntry entry = rospl.allocEntry();

        if (entry != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting free connection. " + rospl.getRoute());
            }
            freeConnections.remove(entry);
            idleConnHandler.remove(entry.getConnection()); // no longer idle

            issuedConnections.add(entry.getWeakRef());

        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No free connections. " + rospl.getRoute());
            }
        }
        return entry;
    }


    /**
     * Creates a new pool entry.
     * This method assumes that the new connection will be handed
     * out immediately.
     *
     * @param rospl       the route-specific pool for which to create the entry
     * @param op        the operator for creating a connection
     *
     * @return  the new pool entry for a new connection
     */
    protected synchronized
        BasicPoolEntry createEntry(RouteSpecificPool rospl,
                                   ClientConnectionOperator op) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating new connection. " + rospl.getRoute());
        }
        // the entry will create the connection when needed
        BasicPoolEntry entry =
            new BasicPoolEntry(op, rospl.getRoute(), refQueue);
        rospl.createdEntry(entry);
        numConnections++;
    
        issuedConnections.add(entry.getWeakRef());

        return entry;
    }

        
    /**
     * Deletes a given pool entry.
     * This closes the pooled connection and removes all references,
     * so that it can be GCed.
     * 
     * <p><b>Note:</b> Does not remove the entry from the freeConnections list.
     * It is assumed that the caller has already handled this step.</p>
     * <!-- @@@ is that a good idea? or rather fix it? -->
     * 
     * @param entry         the pool entry for the connection to delete
     */
    protected synchronized void deleteEntry(BasicPoolEntry entry) {

        HttpRoute route = entry.getPlannedRoute();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleting connection. " + route);
        }

        closeConnection(entry.getConnection());

        RouteSpecificPool rospl = getRoutePool(route, true); //@@@ true???
        rospl.deleteEntry(entry);
        numConnections--;
        if (rospl.isUnused()) {
            routeToPool.remove(route);
        }

        idleConnHandler.remove(entry.getConnection()); // not idle, but dead
    }


    /**
     * Delete an old, free pool entry to make room for a new one.
     * Used to replace pool entries with ones for a different route.
     */
    protected synchronized void deleteLeastUsedEntry() {

        //@@@ with get() instead of remove, we could
        //@@@ leave the removing to deleteEntry()
        BasicPoolEntry entry = (BasicPoolEntry) freeConnections.removeFirst();

        if (entry != null) {
            deleteEntry(entry);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("No free connection to delete.");
        }
    }


    // non-javadoc, see base class AbstractConnPool
    protected synchronized void handleLostEntry(HttpRoute route) {

        RouteSpecificPool rospl = getRoutePool(route, true); //@@@ true???
        rospl.dropEntry();
        if (rospl.isUnused()) {
            routeToPool.remove(route);
        }

        numConnections--;
        notifyWaitingThread(rospl);
    }


    /**
     * Notifies a waiting thread that a connection is available.
     * This will wake a thread waiting in the specific route pool,
     * if there is one.
     * Otherwise, a thread in the connection pool will be notified.
     * 
     * @param rospl     the pool in which to notify, or <code>null</code>
     */
    protected synchronized void notifyWaitingThread(RouteSpecificPool rospl) {

        //@@@ while this strategy provides for best connection re-use,
        //@@@ is it fair? only do this if the connection is open?
        // Find the thread we are going to notify. We want to ensure that
        // each waiting thread is only interrupted once, so we will remove
        // it from all wait queues before interrupting.
        WaitingThread waitingThread = null;

        if ((rospl != null) && !rospl.waitingThreads.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Notifying thread waiting on pool. "
                          + rospl.getRoute());
            }
            waitingThread = (WaitingThread)
                rospl.waitingThreads.removeFirst();
            waitingThreads.remove(waitingThread);

        } else if (!waitingThreads.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Notifying thread waiting on any pool.");
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


    //@@@ revise this cleanup stuff
    //@@@ move method to base class when deleteEntry() is fixed
    // non-javadoc, see base class AbstractConnPool
    public synchronized void deleteClosedConnections() {

        Iterator iter = freeConnections.iterator();
        while (iter.hasNext()) {
            BasicPoolEntry entry = (BasicPoolEntry) iter.next();
            if (!entry.getConnection().isOpen()) {
                iter.remove();
                deleteEntry(entry);
            }
        }
    }


    // non-javadoc, see base class AbstractConnPool
    public synchronized void shutdown() {

        super.shutdown();

        // close all free connections
        //@@@ move this to base class?
        Iterator iter = freeConnections.iterator();
        while (iter.hasNext()) {
            BasicPoolEntry entry = (BasicPoolEntry) iter.next();
            iter.remove();
            closeConnection(entry.getConnection());
        }

            
        // interrupt all waiting threads
        iter = waitingThreads.iterator();
        while (iter.hasNext()) {
            WaitingThread waiter = (WaitingThread) iter.next();
            iter.remove();
            waiter.interruptedByConnectionPool = true;
            waiter.thread.interrupt();
        }

        routeToPool.clear();
    }


} // class ConnPoolByRoute

