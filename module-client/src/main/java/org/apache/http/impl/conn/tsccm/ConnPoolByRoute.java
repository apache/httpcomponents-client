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
import java.util.Queue;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;

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
 * Note that access to the pool datastructures is synchronized via the
 * {@link AbstractConnPool#poolLock poolLock} in the base class,
 * not via <code>synchronized</code> methods.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author and others
 */
public class ConnPoolByRoute extends AbstractConnPool {
        
    //@@@ use a protected LOG in the base class?
    private final Log LOG = LogFactory.getLog(ConnPoolByRoute.class);


    /** The list of free connections */
    private Queue<BasicPoolEntry> freeConnections;

    /** The list of WaitingThreads waiting for a connection */
    private Queue<WaitingThread> waitingThreads;

    /**
     * A map of route-specific pools.
     * Keys are of class {@link HttpRoute},
     * values of class {@link RouteSpecificPool}.
     */
    private final Map<HttpRoute,RouteSpecificPool> routeToPool;



    /**
     * Creates a new connection pool, managed by route.
     *
     * @param mgr   the connection manager
     */
    public ConnPoolByRoute(ClientConnectionManager mgr) {
        super(mgr);

        //@@@ use factory method, at least for waitingThreads
        freeConnections = new LinkedList<BasicPoolEntry>();
        waitingThreads = new LinkedList<WaitingThread>();
        routeToPool = new HashMap<HttpRoute,RouteSpecificPool>();
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
    protected RouteSpecificPool getRoutePool(HttpRoute route,
                                             boolean create) {
        RouteSpecificPool rospl = null;

        try {
            poolLock.lock();

            rospl = routeToPool.get(route);
            if ((rospl == null) && create) {
                // no pool for this route yet (or anymore)
                rospl = newRouteSpecificPool(route);
                routeToPool.put(route, rospl);
            }

        } finally {
            poolLock.unlock();
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
    public int getConnectionsInPool(HttpRoute route) {

        try {
            poolLock.lock();

            // don't allow a pool to be created here!
            RouteSpecificPool rospl = getRoutePool(route, false);
            return (rospl != null) ? rospl.getEntryCount() : 0;

        } finally {
            poolLock.unlock();
        }
    }


    // non-javadoc, see base class AbstractConnPool
    public BasicPoolEntry getEntry(HttpRoute route, long timeout,
                                   ClientConnectionOperator operator)
        throws ConnectionPoolTimeoutException, InterruptedException {

        int maxHostConnections = HttpConnectionManagerParams
            .getMaxConnectionsPerHost(this.params, route);
        int maxTotalConnections = HttpConnectionManagerParams
            .getMaxTotalConnections(this.params);
        
        BasicPoolEntry entry = null;

        try {
            poolLock.lock();

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

                    boolean success = false;
                    try {
                        if (useTimeout && timeToWait <= 0) {
                            throw new ConnectionPoolTimeoutException
                                ("Timeout waiting for connection");
                        }
   
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Need to wait for connection. " + route);
                        }
   
                        if (waitingThread == null) {
                            //@@@ use factory method?
                            waitingThread = new WaitingThread
                                (poolLock.newCondition(), rospl);
                        }

                        if (useTimeout) {
                            startWait = System.currentTimeMillis();
                        }

                        rospl.queueThread(waitingThread);
                        waitingThreads.add(waitingThread);
                        success = waitingThread.await(timeToWait); //@@@, TimeUnit.MILLISECONDS); or deadline
                    } finally {
                        if (!success) {
                            // Either we timed out, experienced a
                            // "spurious wakeup", or were interrupted by
                            // an external thread. Regardless, we need to 
                            // cleanup for ourselves in the wait queue.
                            rospl.removeThread(waitingThread);
                            waitingThreads.remove(waitingThread);
                        }
                        // In case of 'success', we were woken up by the
                        // connection pool and should now have a connection
                        // waiting for us, or else we're shutting down.
                        // Just continue in the loop, both cases are checked.

                        if (useTimeout) {
                            endWait = System.currentTimeMillis();
                            timeToWait -= (endWait - startWait);
                        }
                    }
                }
            } // while no entry

        } finally {
            poolLock.unlock();
        }

        return entry;

    } // getEntry


    // non-javadoc, see base class AbstractConnPool
    public void freeEntry(BasicPoolEntry entry) {

        HttpRoute route = entry.getPlannedRoute();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Freeing connection. " + route);
        }

        try {
            poolLock.lock();

            if (isShutDown) {
                // the pool is shut down, release the
                // connection's resources and get out of here
                closeConnection(entry.getConnection());
                return;
            }

            // no longer issued, we keep a hard reference now
            issuedConnections.remove(entry.getWeakRef());

            RouteSpecificPool rospl = getRoutePool(route, true);

            rospl.freeEntry(entry);
            freeConnections.add(entry);

            if (numConnections == 0) {
                // for some reason this pool didn't already exist
                LOG.error("Master connection pool not found. " + route);
                numConnections = 1;
            }

            idleConnHandler.add(entry.getConnection());

            notifyWaitingThread(rospl);

        } finally {
            poolLock.unlock();
        }

    } // freeEntry



    /**
     * If available, get a free pool entry for a route.
     *
     * @param rospl       the route-specific pool from which to get an entry
     *
     * @return  an available pool entry for the given route, or
     *          <code>null</code> if none is available
     */
    protected BasicPoolEntry getFreeEntry(RouteSpecificPool rospl) {

        BasicPoolEntry entry = null;
        try {
            poolLock.lock();

            entry = rospl.allocEntry();

            if (entry != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Getting free connection. " + rospl.getRoute());
                }
                freeConnections.remove(entry);
                idleConnHandler.remove(entry.getConnection());// no longer idle

                issuedConnections.add(entry.getWeakRef());

            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No free connections. " + rospl.getRoute());
                }
            }

        } finally {
            poolLock.unlock();
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
    protected BasicPoolEntry createEntry(RouteSpecificPool rospl,
                                         ClientConnectionOperator op) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating new connection. " + rospl.getRoute());
        }

        BasicPoolEntry entry = null;
        try {
            poolLock.lock();

            // the entry will create the connection when needed
            entry = new BasicPoolEntry(op, rospl.getRoute(), refQueue);
            rospl.createdEntry(entry);
            numConnections++;
    
            issuedConnections.add(entry.getWeakRef());

        } finally {
            poolLock.unlock();
        }

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
    protected void deleteEntry(BasicPoolEntry entry) {

        HttpRoute route = entry.getPlannedRoute();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleting connection. " + route);
        }

        try {
            poolLock.lock();

            closeConnection(entry.getConnection());

            RouteSpecificPool rospl = getRoutePool(route, true);
            rospl.deleteEntry(entry);
            numConnections--;
            if (rospl.isUnused()) {
                routeToPool.remove(route);
            }

            idleConnHandler.remove(entry.getConnection());// not idle, but dead

        } finally {
            poolLock.unlock();
        }
    }


    /**
     * Delete an old, free pool entry to make room for a new one.
     * Used to replace pool entries with ones for a different route.
     */
    protected void deleteLeastUsedEntry() {

        try {
            poolLock.lock();

            //@@@ with get() instead of remove, we could
            //@@@ leave the removing to deleteEntry()
            BasicPoolEntry entry = freeConnections.remove();

            if (entry != null) {
                deleteEntry(entry);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("No free connection to delete.");
            }

        } finally {
            poolLock.unlock();
        }
    }


    // non-javadoc, see base class AbstractConnPool
    protected void handleLostEntry(HttpRoute route) {

        try {
            poolLock.lock();

            RouteSpecificPool rospl = getRoutePool(route, true);
            rospl.dropEntry();
            if (rospl.isUnused()) {
                routeToPool.remove(route);
            }

            numConnections--;
            notifyWaitingThread(rospl);

        } finally {
            poolLock.unlock();
        }
    }


    /**
     * Notifies a waiting thread that a connection is available.
     * This will wake a thread waiting in the specific route pool,
     * if there is one.
     * Otherwise, a thread in the connection pool will be notified.
     * 
     * @param rospl     the pool in which to notify, or <code>null</code>
     */
    protected void notifyWaitingThread(RouteSpecificPool rospl) {

        //@@@ while this strategy provides for best connection re-use,
        //@@@ is it fair? only do this if the connection is open?
        // Find the thread we are going to notify. We want to ensure that
        // each waiting thread is only interrupted once, so we will remove
        // it from all wait queues before interrupting.
        WaitingThread waitingThread = null;

        try {
            poolLock.lock();

            if ((rospl != null) && rospl.hasThread()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Notifying thread waiting on pool. "
                              + rospl.getRoute());
                }
                waitingThread = rospl.dequeueThread();
                waitingThreads.remove(waitingThread);

            } else if (!waitingThreads.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Notifying thread waiting on any pool.");
                }
                waitingThread = waitingThreads.remove();
                waitingThread.getPool().removeThread(waitingThread);

            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Notifying no-one, there are no waiting threads");
            }

            if (waitingThread != null) {
                waitingThread.wakeup();
            }

        } finally {
            poolLock.unlock();
        }
    }


    //@@@ revise this cleanup stuff
    //@@@ move method to base class when deleteEntry() is fixed
    // non-javadoc, see base class AbstractConnPool
    public void deleteClosedConnections() {

        try {
            poolLock.lock();

            Iterator<BasicPoolEntry>  iter = freeConnections.iterator();
            while (iter.hasNext()) {
                BasicPoolEntry entry = iter.next();
                if (!entry.getConnection().isOpen()) {
                    iter.remove();
                    deleteEntry(entry);
                }
            }

        } finally {
            poolLock.unlock();
        }
    }


    // non-javadoc, see base class AbstractConnPool
    public void shutdown() {

        try {
            poolLock.lock();

            super.shutdown();

            // close all free connections
            //@@@ move this to base class?
            Iterator<BasicPoolEntry> ibpe = freeConnections.iterator();
            while (ibpe.hasNext()) {
                BasicPoolEntry entry = ibpe.next();
                ibpe.remove();
                closeConnection(entry.getConnection());
            }

            // wake up all waiting threads
            Iterator<WaitingThread> iwth = waitingThreads.iterator();
            while (iwth.hasNext()) {
                WaitingThread waiter = iwth.next();
                iwth.remove();
                waiter.wakeup();
            }

            routeToPool.clear();

        } finally {
            poolLock.unlock();
        }
    }


} // class ConnPoolByRoute

