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

package org.apache.http.conn;

import java.net.InetAddress;

import org.apache.http.HttpHost;
import org.apache.http.util.CharArrayBuffer;


/**
 * Helps tracking the steps in establishing a route.
 * 
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public final class RouteTracker {

    /** The target host to connect to. */
    private final HttpHost targetHost;

    /**
     * The local address to connect from.
     * <code>null</code> indicates that the default should be used.
     */
    private final InetAddress localAddress;

    // the attributes above are fixed at construction time
    // now follow attributes that indicate the established route

    /** Whether the first hop of the route is established. */
    private boolean connected;

    /** The proxy server, if any. */
    private HttpHost proxyHost;

    /** Whether the the route is tunnelled through the proxy. */
    private boolean tunnelled;

    /** Whether the route is layered over a tunnel. */
    private boolean layered;

    /** Whether the route is secure. */
    private boolean secure;


    /**
     * Creates a new route tracker.
     * The target and origin need to be specified at creation time.
     *
     * @param target    the host to which to route
     * @param local     the local address to route from, or
     *                  <code>null</code> for the default
     */
    public RouteTracker(HttpHost target, InetAddress local) {
        if (target == null) {
            throw new IllegalArgumentException("Target host may not be null.");
        }
        this.targetHost = target;
        this.localAddress = local;
    }


    /**
     * Creates a new tracker for the given route.
     * Only target and origin are taken from the route,
     * everything else remains to be tracked.
     *
     * @param route     the route to track
     */
    public RouteTracker(HttpRoute route) {
        this(route.getTargetHost(), route.getLocalAddress());
    }


    /**
     * Tracks connecting to the target.
     *
     * @param secure    <code>true</code> if the route is secure,
     *                  <code>false</code> otherwise
     */
    public final void connectTarget(boolean secure) {
        this.connected = true;
        this.secure = secure;
    }


    /**
     * Tracks connecting to a proxy.
     *
     * @param proxy     the proxy connected to
     * @param secure    <code>true</code> if the route is secure,
     *                  <code>false</code> otherwise
     */
    public final void connectProxy(HttpHost proxy, boolean secure) {
        if (proxy == null) {
            throw new IllegalArgumentException("Proxy host may not be null.");
        }
        this.connected = true;
        this.proxyHost = proxy;
        this.secure    = secure;
    }


    /**
     * Tracks tunnelling through the proxy.
     *
     * @param secure    <code>true</code> if the route is secure,
     *                  <code>false</code> otherwise
     */
    public final void createTunnel(boolean secure) {
        if (this.proxyHost == null) {
            throw new IllegalStateException("No tunnel without proxy.");
        }
        if (!this.connected) {
            throw new IllegalStateException("No tunnel unless connected.");
        }
        this.tunnelled = true;
        this.secure    = secure;
    }


    /**
     * Tracks layering a protocol.
     *
     * @param secure    <code>true</code> if the route is secure,
     *                  <code>false</code> otherwise
     */
    public final void layerProtocol(boolean secure) {
        // it is possible to layer a protocol over a direct connection,
        // although this case is probably not considered elsewhere
        if (!this.connected) {
            throw new IllegalStateException
                ("No layered protocol unless connected.");
        }
        this.layered = true;
        this.secure  = secure;
    }


    /**
     * Obtains the target host.
     * 
     * @return the target host
     */
    public final HttpHost getTargetHost() {
        return this.targetHost;
    }


    /**
     * Obtains the local address to connect from.
     * 
     * @return  the local address,
     *          or <code>null</code>
     */
    public final InetAddress getLocalAddress() {
        return this.localAddress;
    }


    /**
     * Obtains the proxy host.
     * 
     * @return the proxy host, or <code>null</code> if not tracked
     */
    public final HttpHost getProxyHost() {
        return this.proxyHost;
    }


    /**
     * Checks whether this route is connected to it's first hop.
     *
     * @return  <code>true</code> if connected,
     *          <code>false</code> otherwise
     */
    public final boolean isConnected() {
        return this.connected;
    }


    /**
     * Checks whether this route is tunnelled through a proxy.
     *
     * @return  <code>true</code> if tunnelled,
     *          <code>false</code> otherwise
     */
    public final boolean isTunnelled() {
        return this.tunnelled;
    }


    /**
     * Checks whether this route includes a layered protocol.
     *
     * @return  <code>true</code> if layered,
     *          <code>false</code> otherwise
     */
    public final boolean isLayered() {
        return this.layered;
    }


    /**
     * Checks whether this route is secure.
     *
     * @return  <code>true</code> if secure,
     *          <code>false</code> otherwise
     */
    public final boolean isSecure() {
        return this.secure;
    }


    /**
     * Obtains the tracked route.
     * If a route has been tracked, it is {@link #isConnected connected}.
     * If not connected, nothing has been tracked so far.
     *
     * @return  the tracked route, or
     *          <code>null</code> if nothing has been tracked so far
     */
    public final HttpRoute toRoute() {
        return !this.connected ?
            null : new HttpRoute(this.targetHost, this.localAddress,
                                 this.proxyHost, this.secure,
                                 this.tunnelled, this.layered);
    }


    /**
     * Obtains the tracked route.
     * <br/><b>Note:</b>
     * Currently, {@link HostConfiguration HostConfiguration} is used to
     * represent the route. It does not cover all tracked attributes.
     * In particular, it can not represent intermediate steps in establishing
     * a route.
     *
     * @deprecated use {@link #toRoute toRoute} instead. Kept temporarily
     *  until {@link HttpRoute} takes over from {@link HostConfiguration}.
     *
     * @return  a representation of the route tracked so far
     */
    public final HostConfiguration toHostConfig() {
        return new HostConfiguration
            (this.targetHost, this.proxyHost, this.localAddress);
    }


    /**
     * Compares this tracked route to another.
     *
     * @param o         the object to compare with
     *
     * @return  <code>true</code> if the argument is the same tracked route,
     *          <code>false</code>
     */
    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof RouteTracker))
            return false;

        RouteTracker that = (RouteTracker) o;
        boolean equal = this.targetHost.equals(that.targetHost);
        equal &=
            ( this.localAddress == that.localAddress) |
            ((this.localAddress != null) &&
              this.localAddress.equals(that.localAddress));
        equal &=
            ( this.proxyHost == that.proxyHost) |
            ((this.proxyHost != null) &&
              this.proxyHost.equals(that.proxyHost));
        equal &=
            (this.connected == that.connected) &&
            (this.secure    == that.secure) &&
            (this.tunnelled == that.tunnelled) &&
            (this.layered   == that.layered);

        return equal;
    }


    /**
     * Generates a hash code for this tracked route.
     * Route trackers are modifiable and should therefore not be used
     * as lookup keys. Use {@link #toRoute toRoute} to obtain an
     * unmodifiable representation of the tracked route.
     *
     * @return  the hash code
     */
    public final int hashCode() {

        int hc = this.targetHost.hashCode();

        if (this.localAddress != null)
            hc ^= localAddress.hashCode();
        if (this.proxyHost != null)
            hc ^= proxyHost.hashCode();

        if (this.connected)
            hc ^= 0x11111111;
        if (this.secure)
            hc ^= 0x22222222;
        if (this.tunnelled)
            hc ^= 0x44444444;
        if (this.layered)
            hc ^= 0x88888888;

        return hc;
    }


    /**
     * Obtains a description of the tracked route.
     *
     * @return  a human-readable representation of the tracked route
     */
    public final String toString() {
        CharArrayBuffer cab = new CharArrayBuffer(80);

        cab.append("RouteTracker[");
        if (this.localAddress != null) {
            cab.append(this.localAddress);
            cab.append("->");
        }
        cab.append('{');
        if (this.connected)
            cab.append('c');
        if (this.tunnelled)
            cab.append('t');
        if (this.layered)
            cab.append('l');
        if (this.secure)
            cab.append('s');
        cab.append("}->");
        if (this.proxyHost != null) {
            cab.append(this.proxyHost);
            cab.append("->");
        }
        cab.append(this.targetHost);
        cab.append(']');

        return cab.toString();
    }

} // class RouteTracker
