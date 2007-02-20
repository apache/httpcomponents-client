/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.RouteTracker;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.params.HttpConnectionManagerParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;



/**
 * A connection "manager" for a single connection.
 * This manager is good only for single-threaded use.
 * Allocation <i>always</i> returns the connection immediately,
 * even if it has not been released after the previous allocation.
 * In that case, a {@link #MISUSE_MESSAGE warning} is logged
 * and the previously issued connection is revoked.
 * <p>
 * This class is derived from <code>SimpleHttpConnectionManager</code>
 * in HttpClient 3. See there for original authors.
 * </p>
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public class SimpleClientConnManager implements ClientConnectionManager {

    private static final Log LOG =
        LogFactory.getLog(SimpleClientConnManager.class);

    /** The message to be logged on multiple allocation. */
    public final static String MISUSE_MESSAGE =
    "Invalid use of SimpleClientConnManager: connection still allocated.\n" +
    "Make sure to release the connection before allocating another one.";


    /** The parameters of this connection manager. */
    protected HttpParams params = new BasicHttpParams(); 

    /** The operator for opening and updating connections. */
    protected ClientConnectionOperator connOperator;

    /** The one and only connection being managed here. */
    protected OperatedClientConnection operatedConn;

    /** The tracked route, or <code>null</code> while not connected. */
    protected RouteTracker trackedRoute;

    /** The currently issued managed connection, if any. */
    protected SimpleConnAdapter managedConn;

    /** The time of the last connection release, or -1. */
    protected long lastReleaseTime;

    /** Whether the connection should be shut down  on release. */
    protected boolean alwaysShutDown;

    /** Indicates whether this connection manager is shut down. */
    protected volatile boolean isShutDown;




    /**
     * Creates a new simple connection manager.
     *
     * @param params    the parameters for this manager
     * @param schreg    the scheme registry, or
     *                  <code>null</code> for the default registry
     */
    public SimpleClientConnManager(HttpParams params,
                                   SchemeRegistry schreg) {

        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        this.params          = params;
        this.connOperator    = createConnectionOperator(schreg);
        this.operatedConn    = this.connOperator.createConnection();
        this.trackedRoute    = null;
        this.managedConn     = null;
        this.lastReleaseTime = -1L;
        this.alwaysShutDown  = false; //@@@ from params? as argument?
        this.isShutDown      = false;

    } // <constructor>


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
     * Asserts that this manager is not shut down.
     *
     * @throws IllegalStateException    if this manager is shut down
     */
    protected final void assertStillUp()
        throws IllegalStateException {

        if (this.isShutDown)
            throw new IllegalStateException("Manager is shut down.");
    }


    /**
     * Obtains a connection.
     * Maps to {@link #getConnection(HttpRoute) getConnection(HttpRoute)}
     * since this manager never blocks the caller.
     *
     * @param route     where the connection should point to
     * @param timeout   ignored
     *
     * @return  a connection that can be used to communicate
     *          along the given route
     */
    public final ManagedClientConnection getConnection(HttpRoute route,
                                                       long timeout) {
        return getConnection(route);
    }


    /**
     * Obtains a connection.
     * This method does not block.
     *
     * @param route     where the connection should point to
     *
     * @return  a connection that can be used to communicate
     *          along the given route
     */
    public ManagedClientConnection getConnection(HttpRoute route) {

        if (route == null) {
            throw new IllegalArgumentException("Route may not be null.");
        }
        assertStillUp();

        if (LOG.isDebugEnabled()) {
            LOG.debug("SimpleClientConnManager.getConnection: " + route);
        }

        if (managedConn != null)
            revokeConnection();

        // check re-usability of the connection
        if (operatedConn.isOpen()) {
            final boolean shutdown =
                (trackedRoute == null) || // how could that happen?
                !trackedRoute.toRoute().equals(route);

            if (shutdown) {
                try {
                    operatedConn.shutdown();
                } catch (IOException iox) {
                    LOG.debug("Problem shutting down connection.", iox);
                    // create a new connection, just to be sure
                    operatedConn = connOperator.createConnection();
                } finally {
                    trackedRoute = null;
                }
            }
        }

        managedConn = new SimpleConnAdapter(operatedConn, route);

        return managedConn;
    }


    // non-javadoc, see interface ClientConnectionManager
    public void releaseConnection(ManagedClientConnection conn) {
        assertStillUp();

        if (!(conn instanceof SimpleConnAdapter)) {
            throw new IllegalArgumentException
                ("Connection class mismatch, " +
                 "connection not obtained from this manager.");
        }
        SimpleConnAdapter sca = (SimpleConnAdapter) conn;
        if (sca.connManager != this) {
            throw new IllegalArgumentException
                ("Connection not obtained from this manager.");
        }
        if (sca.wrappedConnection == null)
            return; // already released

        try {
            // make sure that the response has been read completely
            if (sca.isOpen() && (this.alwaysShutDown ||
                                 !sca.isMarkedReusable())
                ) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug
                        ("Released connection open but not reusable.");
                }

                // make sure this connection will not be re-used
                //@@@ Can we set some kind of flag before shutting down?
                //@@@ If shutdown throws an exception, we can't be sure
                //@@@ that the connection will consider itself closed.
                // we might have gotten here because of a shutdown trigger
                // shutdown of the adapter also clears the tracked route
                sca.shutdown();
            }
        } catch (IOException iox) {
            //@@@ log as warning? let pass?
            if (LOG.isDebugEnabled())
                LOG.debug("Exception shutting down released connection.",
                          iox);
        } finally {
            sca.detach();
            managedConn = null;
            lastReleaseTime = System.currentTimeMillis();
        }
    } // releaseConnection


    // non-javadoc, see interface ClientConnectionManager
    public void closeIdleConnections(long idletime) {
        assertStillUp();

        if ((managedConn == null) && operatedConn.isOpen()) {
            final long cutoff = System.currentTimeMillis() - idletime;
            if (lastReleaseTime <= cutoff) {
                try {
                    operatedConn.close();
                } catch (IOException iox) {
                    // ignore
                    LOG.debug("Problem closing idle connection.", iox);
                } finally {
                    trackedRoute = null;
                }
            }
        }
    }


    // non-javadoc, see interface ClientConnectionManager
    public void shutdown() {

        this.isShutDown = true;

        if (managedConn != null)
            managedConn.detach();

        try {
            if (operatedConn != null)
                operatedConn.shutdown();
        } catch (IOException iox) {
            // ignore
            LOG.debug("Problem while shutting down manager.", iox);
        } finally {
            trackedRoute = null;
            operatedConn = null;
        }
    }


    /**
     * Revokes the currently issued connection.
     * The adapter gets disconnected, the connection will be shut down.
     */
    protected void revokeConnection() {
        if (managedConn == null)
            return;

        // Generate a stack trace, it might help debugging.
        // Do NOT throw the exception, just log it!
        IllegalStateException isx = new IllegalStateException
            ("Revoking connection to " + managedConn.getRoute());
        LOG.warn(MISUSE_MESSAGE, isx);

        if (managedConn != null)
            managedConn.detach();

        try {
            if (operatedConn.isOpen())
                operatedConn.shutdown();
        } catch (IOException iox) {
            // ignore
            LOG.debug("Problem while shutting down connection.", iox);
        } finally {
            trackedRoute = null;
        }
    }



    /**
     * The connection adapter used by this manager.
     * <p><i>Refactoring pending!</i>
     * This has a lot of common code with private classes
     * <code>TrackingPoolEntry</code> and <code>HttpConnectionAdapter</code>
     * in {@link ThreadSafeClientConnManager ThreadSafeClientConnManager}.
     */
    protected class SimpleConnAdapter
        extends AbstractClientConnectionAdapter {

        /** The route for which the connection got allocated. */
        private HttpRoute plannedRoute;

        // the tracked route is kept in the enclosing manager
        //@@@ switch to an adapter+poolentry style, as in TSCCM


        /**
         * Creates a new connection adapter.
         *
         * @param occ   the underlying connection for this adapter
         * @param plan  the planned route for the connection
         */
        protected SimpleConnAdapter(OperatedClientConnection occ,
                                    HttpRoute plan) {
            super(SimpleClientConnManager.this, occ);
            super.markedReusable = true;

            this.plannedRoute = plan;
        }


        /**
         * Asserts that this adapter is still attached.
         *
         * @throws IllegalStateException
         *      if it is {@link #detach detach}ed
         */
        protected final void assertAttached() {
            if (wrappedConnection == null) {
                throw new IllegalStateException("Adapter is detached.");
            }
        }

        /**
         * Detaches this adapter from the wrapped connection.
         * This adapter becomes useless.
         */
        protected void detach() {
            this.wrappedConnection = null;
        }


        // non-javadoc, see interface ManagedHttpConnection
        public HttpRoute getRoute() {

            assertAttached();
            return (trackedRoute == null) ?
                null : trackedRoute.toRoute();
        }


        // non-javadoc, see interface ManagedHttpConnection
        public void open(HttpRoute route,
                         HttpContext context, HttpParams params)
            throws IOException {

            if (route == null) {
                throw new IllegalArgumentException
                    ("Route must not be null.");
            }
            //@@@ is context allowed to be null? depends on operator?
            if (params == null) {
                throw new IllegalArgumentException
                    ("Parameters must not be null.");
            }
            assertAttached();

            if ((trackedRoute != null) &&
                trackedRoute.isConnected()) {
                throw new IllegalStateException("Connection already open.");
            }

            // - collect the arguments
            // - call the operator
            // - update the tracking data
            // In this order, we can be sure that only a successful
            // opening of the connection will be tracked.

            //@@@ verify route against planned route?

            if (LOG.isDebugEnabled()) {
                LOG.debug("Open connection for " + route);
            }

            trackedRoute = new RouteTracker(route);
            final HttpHost proxy  = route.getProxyHost();

            SimpleClientConnManager.this.connOperator.openConnection
                (wrappedConnection,
                 (proxy != null) ? proxy : route.getTargetHost(),
                 route.getLocalAddress(),
                 context, params);

            if (proxy == null)
                trackedRoute.connectTarget(wrappedConnection.isSecure());
            else
                trackedRoute.connectProxy(proxy, wrappedConnection.isSecure());

        } // open


        // non-javadoc, see interface ManagedHttpConnection
        public void tunnelCreated(boolean secure, HttpParams params)
            throws IOException {

            if (params == null) {
                throw new IllegalArgumentException
                    ("Parameters must not be null.");
            }
            assertAttached();

            //@@@ check for proxy in planned route?
            if ((trackedRoute == null) ||
                !trackedRoute.isConnected()) {
                throw new IllegalStateException("Connection not open.");
            }
            if (trackedRoute.isTunnelled()) {
                throw new IllegalStateException
                    ("Connection is already tunnelled.");
            }

            wrappedConnection.update(null, trackedRoute.getTargetHost(),
                                   secure, params);
            trackedRoute.createTunnel(secure);

        } // tunnelCreated


        // non-javadoc, see interface ManagedHttpConnection
        public void layerProtocol(HttpContext context, HttpParams params)
            throws IOException {

            //@@@ is context allowed to be null? depends on operator?
            if (params == null) {
                throw new IllegalArgumentException
                    ("Parameters must not be null.");
            }
            assertAttached();

            if ((trackedRoute == null) ||
                !trackedRoute.isConnected()) {
                throw new IllegalStateException("Connection not open.");
            }
            if (!trackedRoute.isTunnelled()) {
                //@@@ allow this?
                throw new IllegalStateException
                    ("Protocol layering without a tunnel not supported.");
            }
            if (trackedRoute.isLayered()) {
                throw new IllegalStateException
                    ("Multiple protocol layering not supported.");
            }

            // - collect the arguments
            // - call the operator
            // - update the tracking data
            // In this order, we can be sure that only a successful
            // layering on top of the connection will be tracked.

            final HttpHost target = trackedRoute.getTargetHost();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Layer protocol on connection to " + target);
            }

            SimpleClientConnManager.this.connOperator
                .updateSecureConnection(wrappedConnection, target,
                                        context, params);

            trackedRoute.layerProtocol(wrappedConnection.isSecure());

        } // layerProtocol


        // non-javadoc, see interface HttpConnection        
        public void close() throws IOException {
            trackedRoute = null;

            if (wrappedConnection != null) {
                wrappedConnection.close();
            } else {
                // do nothing
            }
        }

        // non-javadoc, see interface HttpConnection        
        public void shutdown() throws IOException {
            trackedRoute = null;

            if (wrappedConnection != null) {
                wrappedConnection.shutdown();
            } else {
                // do nothing
            }
        }

    } // class SimpleConnAdapter


} // class SimpleClientConnManager
