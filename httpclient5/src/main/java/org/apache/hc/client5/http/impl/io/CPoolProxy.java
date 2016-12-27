/*
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
package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolEntry;

/**
 * @since 4.3
 */
class CPoolProxy implements ManagedHttpClientConnection, HttpContext {

    private volatile PoolEntry<HttpRoute, ManagedHttpClientConnection> poolEntry;
    private volatile boolean routeComplete;

    CPoolProxy(final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry) {
        super();
        this.poolEntry = entry;
    }

    PoolEntry<HttpRoute, ManagedHttpClientConnection> getPoolEntry() {
        return this.poolEntry;
    }

    boolean isDetached() {
        return this.poolEntry == null;
    }

    PoolEntry<HttpRoute, ManagedHttpClientConnection> detach() {
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> local = this.poolEntry;
        this.poolEntry = null;
        return local;
    }

    public void markRouteComplete() {
        this.routeComplete = true;
    }

    public boolean isRouteComplete() {
        return this.routeComplete;
    }

    ManagedHttpClientConnection getConnection() {
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> local = this.poolEntry;
        if (local == null) {
            return null;
        }
        return local.getConnection();
    }

    ManagedHttpClientConnection getValidConnection() {
        final ManagedHttpClientConnection conn = getConnection();
        if (conn == null) {
            throw new ConnectionShutdownException();
        }
        return conn;
    }

    @Override
    public void close() throws IOException {
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> local = this.poolEntry;
        if (local != null) {
            final ManagedHttpClientConnection conn = local.getConnection();
            if (conn != null) {
                conn.close();
            }
        }
    }

    @Override
    public void shutdown() throws IOException {
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> local = this.poolEntry;
        if (local != null) {
            final ManagedHttpClientConnection conn = local.getConnection();
            if (conn != null) {
                conn.close();
            }
        }
    }

    @Override
    public boolean isOpen() {
        final PoolEntry<HttpRoute, ManagedHttpClientConnection> local = this.poolEntry;
        if (local != null) {
            final ManagedHttpClientConnection conn = local.getConnection();
            if (conn != null) {
                return conn.isOpen();
            }
        }
        return false;
    }

    @Override
    public boolean isStale() throws IOException {
        final HttpClientConnection conn = getConnection();
        if (conn != null) {
            return conn.isStale();
        } else {
            return true;
        }
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return getValidConnection().getProtocolVersion();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        getValidConnection().setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return getValidConnection().getSocketTimeout();
    }

    @Override
    public String getId() {
        return getValidConnection().getId();
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        getValidConnection().bind(socket);
    }

    @Override
    public Socket getSocket() {
        return getValidConnection().getSocket();
    }

    @Override
    public SSLSession getSSLSession() {
        return getValidConnection().getSSLSession();
    }

    @Override
    public boolean isConsistent() {
        return getValidConnection().isConsistent();
    }

    @Override
    public void terminateRequest(final ClassicHttpRequest request) throws HttpException, IOException {
        getValidConnection().terminateRequest(request);
    }

    @Override
    public boolean isDataAvailable(final int timeout) throws IOException {
        return getValidConnection().isDataAvailable(timeout);
    }

    @Override
    public void sendRequestHeader(final ClassicHttpRequest request) throws HttpException, IOException {
        getValidConnection().sendRequestHeader(request);
    }

    @Override
    public void sendRequestEntity(final ClassicHttpRequest request) throws HttpException, IOException {
        getValidConnection().sendRequestEntity(request);
    }

    @Override
    public ClassicHttpResponse receiveResponseHeader() throws HttpException, IOException {
        return getValidConnection().receiveResponseHeader();
    }

    @Override
    public void receiveResponseEntity(final ClassicHttpResponse response) throws HttpException, IOException {
        getValidConnection().receiveResponseEntity(response);
    }

    @Override
    public void flush() throws IOException {
        getValidConnection().flush();
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        return getValidConnection().getMetrics();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return getValidConnection().getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return getValidConnection().getRemoteAddress();
    }

    @Override
    public Object getAttribute(final String id) {
        final ManagedHttpClientConnection conn = getValidConnection();
        if (conn instanceof HttpContext) {
            return ((HttpContext) conn).getAttribute(id);
        } else {
            return null;
        }
    }

    @Override
    public void setAttribute(final String id, final Object obj) {
        final ManagedHttpClientConnection conn = getValidConnection();
        if (conn instanceof HttpContext) {
            ((HttpContext) conn).setAttribute(id, obj);
        }
    }

    @Override
    public Object removeAttribute(final String id) {
        final ManagedHttpClientConnection conn = getValidConnection();
        if (conn instanceof HttpContext) {
            return ((HttpContext) conn).removeAttribute(id);
        } else {
            return null;
        }
    }

    @Override
    public void setProtocolVersion(final ProtocolVersion version) {
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CPoolProxy{");
        final ManagedHttpClientConnection conn = getConnection();
        if (conn != null) {
            sb.append(conn);
        } else {
            sb.append("detached");
        }
        sb.append('}');
        return sb.toString();
    }

    static CPoolProxy getProxy(final HttpClientConnection conn) {
        if (!CPoolProxy.class.isInstance(conn)) {
            throw new IllegalStateException("Unexpected connection proxy class: " + conn.getClass());
        }
        return CPoolProxy.class.cast(conn);
    }

}
