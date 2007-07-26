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
 * An abstract connection pool.
 * It is used by the {@link ThreadSafeClientConnManager}.
 */
public abstract class AbstractConnPool implements RefQueueHandler {

    //@@@ protected, obtain with getClass()?
    private final Log LOG = LogFactory.getLog(AbstractConnPool.class);

    /**
     * References to issued connections.
     * Objects in this set are of class
     * {@link BasicPoolEntryRef BasicPoolEntryRef},
     * and point to the pool entry for the issued connection.
     * GCed connections are detected by the missing pool entries.
     */
    protected Set issuedConnections;

    /** The handler for idle connections. */
    protected IdleConnectionHandler idleConnHandler;

    /** The current total number of connections. */
    protected int numConnections;

    /** The parameters of this connection pool. */
    //@@@ allow get/set? synchronized?
    //@@@ currently needed for connection limits
    protected HttpParams params;


    /** The connection manager. */
    //@@@ replace with a weak reference to allow for GC
    //@@@ is it necessary to have the manager in the pool entry?
    protected ThreadSafeClientConnManager connManager;


    /** A reference queue to track loss of pool entries to GC. */
    //@@@ this should be a pool-specific reference queue
    protected ReferenceQueue refQueue = BadStaticMaps.REFERENCE_QUEUE; //@@@

    /** A worker (thread) to track loss of pool entries to GC. */
    private RefQueueWorker refWorker;


    /** Indicates whether this pool is shut down. */
    protected volatile boolean isShutDown;



    /**
     * Creates a new connection pool.
     *
     * @param tsccm     the connection manager
     */
    protected AbstractConnPool(ThreadSafeClientConnManager tsccm) {

        connManager = tsccm;
        params = tsccm.getParams();

        issuedConnections = new HashSet();
        idleConnHandler = new IdleConnectionHandler();

        //@@@ currently must be false, otherwise the TSCCM
        //@@@ will not be garbage collected in the unit test...
        boolean conngc = false; //@@@ check parameters to decide
        if (conngc) {
            refQueue = new ReferenceQueue();
            refWorker = new RefQueueWorker(refQueue, this);
            Thread t = new Thread(refWorker); //@@@ use a thread factory
            t.setDaemon(true);
            t.setName("RefQueueWorker@" + this);
            t.start();
        }
    }


    /**
     * Obtains a pool entry with a connection within the given timeout.
     *
     * @param route     the route for which to get the connection
     * @param timeout   the timeout, or 0 for no timeout
     * @param operator  the connection operator, in case
     *                  a connection has to be created
     *
     * @return  pool entry holding a connection for the route
     *
     * @throws ConnectionPoolTimeoutException
     *         if the timeout expired
     */
    public abstract
        BasicPoolEntry getEntry(HttpRoute route, long timeout,
                                ClientConnectionOperator operator)
        throws ConnectionPoolTimeoutException
        ;


    /**
     * Returns an entry into the pool.
     * The connection of the entry is expected to be in a suitable state,
     * either open and re-usable, or closed. The pool will not make any
     * attempt to determine whether it can be re-used or not.
     *
     * @param entry     the entry for the connection to release
     */
    public abstract void freeEntry(BasicPoolEntry entry)
        ;



    // non-javadoc, see interface RefQueueHandler
    public synchronized void handleReference(Reference ref) {

        if (ref instanceof BasicPoolEntryRef) {
            // check if the GCed pool entry was still in use
            //@@@ find a way to detect this without lookup
            //@@@ flag in the BasicPoolEntryRef, to be reset when freed?
            final boolean lost = issuedConnections.remove(ref);
            if (lost) {
                final HttpRoute route = ((BasicPoolEntryRef)ref).getRoute();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Connection garbage collected. " + route);
                }
                handleLostEntry(route);
            }
        }
        //@@@ else check if the connection manager was GCed
    }


    /**
     * Handles cleaning up for a lost pool entry with the given route.
     * A lost pool entry corresponds to a connection that was
     * garbage collected instead of being properly released.
     *
     * @param route     the route of the pool entry that was lost
     */
    protected abstract void handleLostEntry(HttpRoute route)
        ;


    /**
     * Closes idle connections.
     *
     * @param idletime  the time the connections should have been idle
     *                  in order to be closed now
     */
    public synchronized void closeIdleConnections(long idletime) {
        idleConnHandler.closeIdleConnections(idletime);
    }
        
    //@@@ revise this cleanup stuff (closeIdle+deleteClosed), it's not good

    /**
     * Deletes all entries for closed connections.
     */
    public abstract void deleteClosedConnections()
        ;


    /**
     * Shuts down this pool and all associated resources.
     * Overriding methods MUST call the implementation here!
     */
    public synchronized void shutdown() {

        isShutDown = true;

        // no point in monitoring GC anymore
        if (refWorker != null)
            refWorker.shutdown();

        // close all connections that are issued to an application
        Iterator iter = issuedConnections.iterator();
        while (iter.hasNext()) {
            BasicPoolEntryRef per = (BasicPoolEntryRef) iter.next();
            iter.remove();
            BasicPoolEntry entry = (BasicPoolEntry) per.get();
            if (entry != null) {
                closeConnection(entry.getConnection());
            }
        }
        //@@@ while the static map exists, call there to clean it up
        BadStaticMaps.shutdownCheckedOutConnections(this); //@@@

        // remove all references to connections
        //@@@ use this for shutting them down instead?
        idleConnHandler.removeAll();
    }


    /**
     * Closes a connection from this pool.
     *
     * @param conn      the connection to close, or <code>null</code>
     */
    protected void closeConnection(final OperatedClientConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException ex) {
                LOG.debug("I/O error closing connection", ex);
            }
        }
    }



} // class AbstractConnPool

