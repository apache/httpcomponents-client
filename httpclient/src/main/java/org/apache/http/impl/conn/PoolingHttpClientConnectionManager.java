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
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpClientConnection;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;

/**
 * <tt>ClientConnectionPoolManager</tt> maintains a pool of
 * {@link HttpClientConnection}s and is able to service connection requests
 * from multiple execution threads. Connections are pooled on a per route
 * basis. A request for a route which already the manager has persistent
 * connections for available in the pool will be services by leasing
 * a connection from the pool rather than creating a brand new connection.
 * <p/>
 * <tt>ClientConnectionPoolManager</tt> maintains a maximum limit of connection
 * on a per route basis and in total. Per default this implementation will
 * create no more than than 2 concurrent connections per given route
 * and no more 20 connections in total. For many real-world applications
 * these limits may prove too constraining, especially if they use HTTP
 * as a transport protocol for their services. Connection limits, however,
 * can be adjusted using {@link ConnPoolControl} methods.
 *
 * @since 4.3
 */
@ThreadSafe
public class PoolingHttpClientConnectionManager extends HttpClientConnectionManagerBase {

    private final Log log = LogFactory.getLog(getClass());

    private final CPool pool;

    private static Registry<ConnectionSocketFactory> getDefaultRegistry() {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainSocketFactory.getSocketFactory())
                .register("https", SSLSocketFactory.getSocketFactory())
                .build();
    }

    public PoolingHttpClientConnectionManager() {
        this(getDefaultRegistry());
    }

    public PoolingHttpClientConnectionManager(final long timeToLive, final TimeUnit tunit) {
        this(getDefaultRegistry(), timeToLive, tunit);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        this(socketFactoryRegistry, -1, TimeUnit.MILLISECONDS);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final DnsResolver dnsResolver) {
        this(socketFactoryRegistry, null, dnsResolver, -1, TimeUnit.MILLISECONDS);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final long timeToLive, final TimeUnit tunit) {
        this(new CPool(2, 20, timeToLive, tunit), socketFactoryRegistry, null, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final long timeToLive, final TimeUnit tunit) {
        this(new CPool(2, 20, timeToLive, tunit),
                socketFactoryRegistry, schemePortResolver, dnsResolver);
    }

    PoolingHttpClientConnectionManager(
            final CPool pool,
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        super(pool, socketFactoryRegistry, schemePortResolver, dnsResolver);
        this.pool = pool;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    private String format(final HttpRoute route, final Object state) {
        StringBuilder buf = new StringBuilder();
        buf.append("[route: ").append(route).append("]");
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        return buf.toString();
    }

    private String formatStats(final HttpRoute route) {
        StringBuilder buf = new StringBuilder();
        PoolStats totals = this.pool.getTotalStats();
        PoolStats stats = this.pool.getStats(route);
        buf.append("[total kept alive: ").append(totals.getAvailable()).append("; ");
        buf.append("route allocated: ").append(stats.getLeased() + stats.getAvailable());
        buf.append(" of ").append(stats.getMax()).append("; ");
        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
        buf.append(" of ").append(totals.getMax()).append("]");
        return buf.toString();
    }

    private String format(final CPoolEntry entry) {
        StringBuilder buf = new StringBuilder();
        buf.append("[id: ").append(entry.getId()).append("]");
        buf.append("[route: ").append(entry.getRoute()).append("]");
        Object state = entry.getState();
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        return buf.toString();
    }

    @Override
    protected void onConnectionLeaseRequest(final HttpRoute route, final Object state) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection request: " + format(route, state) + formatStats(route));
        }
    }

    @Override
    protected void onConnectionLease(final CPoolEntry entry) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection leased: " + format(entry) + formatStats(entry.getRoute()));
        }
    }

    @Override
    protected void onConnectionKeepAlive(final CPoolEntry entry) {
        if (this.log.isDebugEnabled()) {
            long keepalive = entry.getExpiry();
            String s;
            if (keepalive > 0) {
                s = "for " + (double) keepalive / 1000 + " seconds";
            } else {
                s = "indefinitely";
            }
            this.log.debug("Connection " + format(entry) + " can be kept alive " + s);
        }
    }

    @Override
    protected void onConnectionRelease(final CPoolEntry entry) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection released: " + format(entry) + formatStats(entry.getRoute()));
        }
    }

    public void shutdown() {
        this.log.debug("Connection manager is shutting down");
        try {
            this.pool.shutdown();
        } catch (IOException ex) {
            this.log.debug("I/O exception shutting down connection manager", ex);
        }
        this.log.debug("Connection manager shut down");
    }

    public void closeIdleConnections(long idleTimeout, TimeUnit tunit) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Closing connections idle longer than " + idleTimeout + " " + tunit);
        }
        this.pool.closeIdle(idleTimeout, tunit);
    }

    public void closeExpiredConnections() {
        this.log.debug("Closing expired connections");
        this.pool.closeExpired();
    }

    public int getMaxTotal() {
        return this.pool.getMaxTotal();
    }

    public void setMaxTotal(int max) {
        this.pool.setMaxTotal(max);
    }

    public int getDefaultMaxPerRoute() {
        return this.pool.getDefaultMaxPerRoute();
    }

    public void setDefaultMaxPerRoute(int max) {
        this.pool.setDefaultMaxPerRoute(max);
    }

    public int getMaxPerRoute(final HttpRoute route) {
        return this.pool.getMaxPerRoute(route);
    }

    public void setMaxPerRoute(final HttpRoute route, int max) {
        this.pool.setMaxPerRoute(route, max);
    }

    public PoolStats getTotalStats() {
        return this.pool.getTotalStats();
    }

    public PoolStats getStats(final HttpRoute route) {
        return this.pool.getStats(route);
    }

}
