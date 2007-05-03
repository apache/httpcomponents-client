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
 * The route for a request.
 * Instances of this class are unmodifiable and therefore suitable
 * for use as lookup keys.
 * 
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public final class HttpRoute {

    /** The target host to connect to. */
    private final HttpHost targetHost;

    /**
     * The local address to connect from.
     * <code>null</code> indicates that the default should be used.
     */
    private final InetAddress localAddress;

    /** The proxy server, if any. */
    private final HttpHost proxyHost;

    /** Whether the the route is tunnelled through the proxy. */
    private final boolean tunnelled;

    /** Whether the route is layered. */
    private final boolean layered;

    /** Whether the route is (supposed to be) secure. */
    private final boolean secure;


    /**
     * Creates a new route with all attributes specified explicitly.
     *
     * @param target    the host to which to route
     * @param local     the local address to route from, or
     *                  <code>null</code> for the default
     * @param proxy     the proxy to use, or
     *                  <code>null</code> for a direct route
     * @param secure    <code>true</code> if the route is (to be) secure,
     *                  <code>false</code> otherwise
     * @param tunnelled <code>true</code> if the route is (to be) tunnelled
     *                  via the proxy,
     *                  <code>false</code> otherwise
     * @param layered   <code>true</code> if the route includes a
     *                  layered protocol,
     *                  <code>false</code> otherwise
     */
    public HttpRoute(HttpHost target, InetAddress local, HttpHost proxy,
                     boolean secure, boolean tunnelled, boolean layered) {
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host may not be null.");
        }
        if (tunnelled && (proxy == null)) {
            throw new IllegalArgumentException
                ("Proxy host may not be null if tunnelled.");
        }

        this.targetHost   = target;
        this.localAddress = local;
        this.proxyHost    = proxy;
        this.secure       = secure;
        this.tunnelled    = tunnelled;
        this.layered      = layered;
    }


    /**
     * Creates a new direct route.
     * That is a route without a proxy.
     *
     * @param target    the host to which to route
     * @param local     the local address to route from, or
     *                  <code>null</code> for the default
     * @param secure    <code>true</code> if the route is (to be) secure,
     *                  <code>false</code> otherwise
     */
    public HttpRoute(HttpHost target, InetAddress local, boolean secure) {
        this(target, local, null, secure, false, false);
    }


    /**
     * Creates a new route through a proxy.
     * When using this constructor, the <code>proxy</code> MUST be given.
     * For convenience, it is assumed that a secure connection will be
     * layered over a tunnel through the proxy.
     *
     * @param target    the host to which to route
     * @param local     the local address to route from, or
     *                  <code>null</code> for the default
     * @param proxy     the proxy to use
     * @param secure    <code>true</code> if the route is (to be) secure,
     *                  <code>false</code> otherwise
     */
    public HttpRoute(HttpHost target, InetAddress local, HttpHost proxy,
                     boolean secure) {
        this(target, local, proxy, secure, secure, secure);
        if (proxy == null) {
            throw new IllegalArgumentException
                ("Proxy host may not be null.");
        }
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
     * @return the proxy host, or
     *         <code>null</code> if this route is direct
     */
    public final HttpHost getProxyHost() {
        return this.proxyHost;
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
     * Converts to the {@link HostConfiguration traditional} interface.
     *
     * @return  a host configuration matching this route as good as possible
     *
     * @deprecated No replacement.
     *          This class will replace {@link HostConfiguration}
     *          where routes need to be represented. No conversion necessary.
     */
    public final HostConfiguration toHostConfig() {
        return new HostConfiguration
            (this.targetHost, this.proxyHost, this.localAddress);
    }


    /**
     * Compares this route to another.
     *
     * @param o         the object to compare with
     *
     * @return  <code>true</code> if the argument is the same route,
     *          <code>false</code>
     */
    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof HttpRoute))
            return false;

        HttpRoute that = (HttpRoute) o;
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
            (this.secure    == that.secure) &&
            (this.tunnelled == that.tunnelled) &&
            (this.layered   == that.layered);

        return equal;
    }


    /**
     * Generates a hash code for this route.
     *
     * @return  the hash code
     */
    public final int hashCode() {

        int hc = this.targetHost.hashCode();

        if (this.localAddress != null)
            hc ^= localAddress.hashCode();
        if (this.proxyHost != null)
            hc ^= proxyHost.hashCode();

        if (this.secure)
            hc ^= 0x11111111;
        if (this.tunnelled)
            hc ^= 0x22222222;
        if (this.layered)
            hc ^= 0x44444444;

        return hc;
    }


    /**
     * Obtains a description of this route.
     *
     * @return  a human-readable representation of this route
     */
    public final String toString() {
        CharArrayBuffer cab = new CharArrayBuffer(80);

        cab.append("HttpRoute[");
        if (this.localAddress != null) {
            cab.append(this.localAddress);
            cab.append("->");
        }
        cab.append('{');
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

} // class HttpRoute
