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
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.RouteTracker;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

@NotThreadSafe
class ManagedClientConnectionImpl implements ManagedClientConnection {

    private final ClientConnectionManager manager;
    private final ClientConnectionOperator operator;
    private volatile HttpPoolEntry poolEntry;
    private volatile boolean reusable;
    private volatile long duration;

    ManagedClientConnectionImpl(
            final ClientConnectionManager manager,
            final ClientConnectionOperator operator,
            final HttpPoolEntry entry) {
        super();
        if (manager == null) {
            throw new IllegalArgumentException("Connection manager may not be null");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Connection operator may not be null");
        }
        if (entry == null) {
            throw new IllegalArgumentException("HTTP pool entry may not be null");
        }
        this.manager = manager;
        this.operator = operator;
        this.poolEntry = entry;
        this.reusable = false;
        this.duration = Long.MAX_VALUE;
    }

    HttpPoolEntry getPoolEntry() {
        return this.poolEntry;
    }

    HttpPoolEntry detach() {
        HttpPoolEntry local = this.poolEntry;
        this.poolEntry = null;
        return local;
    }

    public ClientConnectionManager getManager() {
        return this.manager;
    }

    private OperatedClientConnection getConnection() {
        HttpPoolEntry local = this.poolEntry;
        if (local == null) {
            return null;
        }
        return local.getConnection();
    }

    private OperatedClientConnection ensureConnection() {
        HttpPoolEntry local = this.poolEntry;
        if (local == null) {
            throw new ConnectionShutdownException();
        }
        return local.getConnection();
    }

    private HttpPoolEntry ensurePoolEntry() {
        HttpPoolEntry local = this.poolEntry;
        if (local == null) {
            throw new ConnectionShutdownException();
        }
        return local;
    }

    public void close() throws IOException {
        HttpPoolEntry local = this.poolEntry;
        if (local != null) {
            OperatedClientConnection conn = local.getConnection();
            local.getTracker().reset();
            conn.close();
        }
    }

    public void shutdown() throws IOException {
        HttpPoolEntry local = this.poolEntry;
        if (local != null) {
            OperatedClientConnection conn = local.getConnection();
            local.getTracker().reset();
            conn.shutdown();
        }
    }

    public boolean isOpen() {
        OperatedClientConnection conn = getConnection();
        if (conn != null) {
            return conn.isOpen();
        } else {
            return false;
        }
    }

    public boolean isStale() {
        OperatedClientConnection conn = getConnection();
        if (conn != null) {
            return conn.isStale();
        } else {
            return true;
        }
    }

    public void setSocketTimeout(int timeout) {
        OperatedClientConnection conn = ensureConnection();
        conn.setSocketTimeout(timeout);
    }

    public int getSocketTimeout() {
        OperatedClientConnection conn = ensureConnection();
        return conn.getSocketTimeout();
    }

    public HttpConnectionMetrics getMetrics() {
        OperatedClientConnection conn = ensureConnection();
        return conn.getMetrics();
    }

    public void flush() throws IOException {
        OperatedClientConnection conn = ensureConnection();
        conn.flush();
    }

    public boolean isResponseAvailable(int timeout) throws IOException {
        OperatedClientConnection conn = ensureConnection();
        return conn.isResponseAvailable(timeout);
    }

    public void receiveResponseEntity(
            final HttpResponse response) throws HttpException, IOException {
        OperatedClientConnection conn = ensureConnection();
        conn.receiveResponseEntity(response);
    }

    public HttpResponse receiveResponseHeader() throws HttpException, IOException {
        OperatedClientConnection conn = ensureConnection();
        return conn.receiveResponseHeader();
    }

    public void sendRequestEntity(
            final HttpEntityEnclosingRequest request) throws HttpException, IOException {
        OperatedClientConnection conn = ensureConnection();
        conn.sendRequestEntity(request);
    }

    public void sendRequestHeader(
            final HttpRequest request) throws HttpException, IOException {
        OperatedClientConnection conn = ensureConnection();
        conn.sendRequestHeader(request);
    }

    public InetAddress getLocalAddress() {
        OperatedClientConnection conn = ensureConnection();
        return conn.getLocalAddress();
    }

    public int getLocalPort() {
        OperatedClientConnection conn = ensureConnection();
        return conn.getLocalPort();
    }

    public InetAddress getRemoteAddress() {
        OperatedClientConnection conn = ensureConnection();
        return conn.getRemoteAddress();
    }

    public int getRemotePort() {
        OperatedClientConnection conn = ensureConnection();
        return conn.getRemotePort();
    }

    public boolean isSecure() {
        OperatedClientConnection conn = ensureConnection();
        return conn.isSecure();
    }

    public SSLSession getSSLSession() {
        OperatedClientConnection conn = ensureConnection();
        SSLSession result = null;
        Socket sock = conn.getSocket();
        if (sock instanceof SSLSocket) {
            result = ((SSLSocket)sock).getSession();
        }
        return result;
    }

    public Object getAttribute(final String id) {
        OperatedClientConnection conn = ensureConnection();
        if (conn instanceof HttpContext) {
            return ((HttpContext) conn).getAttribute(id);
        } else {
            return null;
        }
    }

    public Object removeAttribute(final String id) {
        OperatedClientConnection conn = ensureConnection();
        if (conn instanceof HttpContext) {
            return ((HttpContext) conn).removeAttribute(id);
        } else {
            return null;
        }
    }

    public void setAttribute(final String id, final Object obj) {
        OperatedClientConnection conn = ensureConnection();
        if (conn instanceof HttpContext) {
            ((HttpContext) conn).setAttribute(id, obj);
        }
    }

    public HttpRoute getRoute() {
        HttpPoolEntry local = ensurePoolEntry();
        return local.getEffectiveRoute();
    }

    public void open(
            final HttpRoute route,
            final HttpContext context,
            final HttpParams params) throws IOException {
        if (route == null) {
            throw new IllegalArgumentException("Route may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        OperatedClientConnection conn;
        synchronized (this) {
            if (this.poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            if (tracker.isConnected()) {
                throw new IllegalStateException("Connection already open");
            }
            conn = this.poolEntry.getConnection();
        }

        HttpHost proxy  = route.getProxyHost();
        this.operator.openConnection(
                conn,
                (proxy != null) ? proxy : route.getTargetHost(),
                route.getLocalAddress(),
                context, params);

        synchronized (this) {
            if (this.poolEntry == null) {
                throw new InterruptedIOException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            if (proxy == null) {
                tracker.connectTarget(conn.isSecure());
            } else {
                tracker.connectProxy(proxy, conn.isSecure());
            }
        }
    }

    public void tunnelTarget(
            boolean secure, final HttpParams params) throws IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        HttpHost target;
        OperatedClientConnection conn;
        synchronized (this) {
            if (this.poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            if (!tracker.isConnected()) {
                throw new IllegalStateException("Connection not open");
            }
            if (tracker.isTunnelled()) {
                throw new IllegalStateException("Connection is already tunnelled");
            }
            target = tracker.getTargetHost();
            conn = this.poolEntry.getConnection();
        }

        conn.update(null, target, secure, params);

        synchronized (this) {
            if (this.poolEntry == null) {
                throw new InterruptedIOException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            tracker.tunnelTarget(secure);
        }
    }

    public void tunnelProxy(
            final HttpHost next, boolean secure, final HttpParams params) throws IOException {
        if (next == null) {
            throw new IllegalArgumentException("Next proxy amy not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        OperatedClientConnection conn;
        synchronized (this) {
            if (this.poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            if (!tracker.isConnected()) {
                throw new IllegalStateException("Connection not open");
            }
            conn = this.poolEntry.getConnection();
        }

        conn.update(null, next, secure, params);

        synchronized (this) {
            if (this.poolEntry == null) {
                throw new InterruptedIOException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            tracker.tunnelProxy(next, secure);
        }
    }

    public void layerProtocol(
            final HttpContext context, final HttpParams params) throws IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        HttpHost target;
        OperatedClientConnection conn;
        synchronized (this) {
            if (this.poolEntry == null) {
                throw new ConnectionShutdownException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            if (!tracker.isConnected()) {
                throw new IllegalStateException("Connection not open");
            }
            if (!tracker.isTunnelled()) {
                throw new IllegalStateException("Protocol layering without a tunnel not supported");
            }
            if (tracker.isLayered()) {
                throw new IllegalStateException("Multiple protocol layering not supported");
            }
            target = tracker.getTargetHost();
            conn = this.poolEntry.getConnection();
        }
        this.operator.updateSecureConnection(conn, target, context, params);

        synchronized (this) {
            if (this.poolEntry == null) {
                throw new InterruptedIOException();
            }
            RouteTracker tracker = this.poolEntry.getTracker();
            tracker.layerProtocol(conn.isSecure());
        }
    }

    public Object getState() {
        HttpPoolEntry local = ensurePoolEntry();
        return local.getState();
    }

    public void setState(final Object state) {
        HttpPoolEntry local = ensurePoolEntry();
        local.setState(state);
    }

    public void markReusable() {
        this.reusable = true;
    }

    public void unmarkReusable() {
        this.reusable = false;
    }

    public boolean isMarkedReusable() {
        return this.reusable;
    }

    public void setIdleDuration(long duration, TimeUnit unit) {
        if(duration > 0) {
            this.duration = unit.toMillis(duration);
        } else {
            this.duration = -1;
        }
    }

    public void releaseConnection() {
        synchronized (this) {
            if (this.poolEntry == null) {
                return;
            }
            this.manager.releaseConnection(this, this.duration, TimeUnit.MILLISECONDS);
            this.poolEntry = null;
        }
    }

    public void abortConnection() {
        synchronized (this) {
            if (this.poolEntry == null) {
                return;
            }
            this.reusable = false;
            OperatedClientConnection conn = this.poolEntry.getConnection();
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
            this.manager.releaseConnection(this, this.duration, TimeUnit.MILLISECONDS);
            this.poolEntry = null;
        }
    }

}
