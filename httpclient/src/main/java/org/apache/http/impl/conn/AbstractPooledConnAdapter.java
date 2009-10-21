/*
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

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.OperatedClientConnection;

/**
 * Abstract adapter from pool {@link AbstractPoolEntry entries} to
 * {@link org.apache.http.conn.ManagedClientConnection managed}
 * client connections.
 * The connection in the pool entry is used to initialize the base class.
 * In addition, methods to establish a route are delegated to the
 * pool entry. {@link #shutdown shutdown} and {@link #close close}
 * will clear the tracked route in the pool entry and call the
 * respective method of the wrapped connection.
 *
 * @since 4.0
 */
public abstract class AbstractPooledConnAdapter extends AbstractClientConnAdapter {

    /** The wrapped pool entry. */
    protected volatile AbstractPoolEntry poolEntry;

    /**
     * Creates a new connection adapter.
     *
     * @param manager   the connection manager
     * @param entry     the pool entry for the connection being wrapped
     */
    protected AbstractPooledConnAdapter(ClientConnectionManager manager,
                                        AbstractPoolEntry entry) {
        super(manager, entry.connection);
        this.poolEntry = entry;
    }

    /**
     * Asserts that this adapter is still attached.
     *
     * @throws IllegalStateException
     *      if it is {@link #detach detach}ed
     * @deprecated
     */
    @Deprecated
    protected final void assertAttached() {
        if (poolEntry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
    }

    /**
     * Detaches this adapter from the wrapped connection.
     * This adapter becomes useless.
     */
    @Override
    protected synchronized void detach() {
        super.detach();
        poolEntry = null;
    }

    public HttpRoute getRoute() {
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        return (entry.tracker == null) ?
            null : entry.tracker.toRoute();
    }

    public void open(HttpRoute route,
                     HttpContext context, HttpParams params)
        throws IOException {
        assertNotAborted();
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        entry.open(route, context, params);
    }

    public void tunnelTarget(boolean secure, HttpParams params)
        throws IOException {
        assertNotAborted();
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        entry.tunnelTarget(secure, params);
    }

    public void tunnelProxy(HttpHost next, boolean secure, HttpParams params)
        throws IOException {
        assertNotAborted();
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        entry.tunnelProxy(next, secure, params);
    }

    public void layerProtocol(HttpContext context, HttpParams params)
        throws IOException {
        assertNotAborted();
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        entry.layerProtocol(context, params);
    }

    public void close() throws IOException {
        AbstractPoolEntry entry = poolEntry;
        if (entry != null)
            entry.shutdownEntry();

        OperatedClientConnection conn = getWrappedConnection();
        if (conn != null) {
            conn.close();
        }
    }

    public void shutdown() throws IOException {
        AbstractPoolEntry entry = poolEntry;
        if (entry != null)
            entry.shutdownEntry();

        OperatedClientConnection conn = getWrappedConnection();
        if (conn != null) {
            conn.shutdown();
        }
    }

    public Object getState() {
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        return entry.getState();
    }

    public void setState(final Object state) {
        AbstractPoolEntry entry = poolEntry;
        if (entry == null) {
            throw new IllegalStateException("Adapter is detached.");
        }
        entry.setState(state);
    }

}
