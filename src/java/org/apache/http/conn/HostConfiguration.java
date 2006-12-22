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
import org.apache.http.util.LangUtils;



/**
 * Provides configuration data for connecting to a host.
 * That is the host to connect to plus
 * a proxy to use or a local IP address to select
 * one of several network interfaces.
 * Instances of this class are immutable.
 * Instances of derived classes should be immutable, too.
 * 
 * @author <a href="mailto:rolandw@apache.org">Roland Weber</a>
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author Laura Werner
 * 
 * @since 2.0 
 */
public class HostConfiguration implements Cloneable {

    /**
     * Constant representing a configuration for <i>any</i> host.
     * That means to no host in particular. Use this constant in
     * cases where you'd otherwise pass <code>null</code> to
     * refer to a default value that applies to "any" host.
     */
    public static final
        HostConfiguration ANY_HOST_CONFIGURATION = new HostConfiguration();

    /** The host to connect to. */
    private final HttpHost targetHost;

    /** The host name of the proxy server */
    private final HttpHost proxyHost;

    /**
     * The local address to use when creating the socket.
     * <code>null</code> indicates that the default should be used.
     */
    private final InetAddress localAddress;


    /**
     * Creates a new host configuration.
     *
     * @param host      the target host to connect to
     * @param proxy     the proxy host to use, or
     *                  <code>null</code> for a direct connection
     * @param laddr     the local IP address to use, or
     *                  <code>null</code> for any
     */
    public HostConfiguration(HttpHost host, HttpHost proxy,
                             InetAddress laddr) {
        if (host == null) {
            throw new IllegalArgumentException("Target host may not be null.");
        }
        this.targetHost = host;
        this.proxyHost = proxy;
        this.localAddress = laddr;
    }


    /**
     * Creates a new "any" host configuration.
     * This is the only way to create a host configuration
     * without a target host. It is used exclusively to initialize
     * {@link #ANY_HOST_CONFIGURATION ANY_HOST_CONFIGURATION}.
     */
    private HostConfiguration() {
        this.targetHost = null;
        this.proxyHost = null;
        this.localAddress = null;
    }


    // non-javadoc, see java.lang.Object#toString()
    public String toString() {
        
        StringBuffer b = new StringBuffer(50);        
        b.append("HostConfiguration[");
        
        if (this.targetHost != null) {
            b.append("host=").append(this.targetHost);
        } else {
            b.append("host=*any*");
        }
        if (this.proxyHost != null) {
            b.append(", ").append("proxyHost=").append(this.proxyHost);
        }
        if (this.localAddress != null) {
            b.append(", ").append("localAddress=").append(this.localAddress);
        }
        b.append("]");
        return b.toString();
    }


    /**
     * Returns the target host.
     * 
     * @return the target host, or <code>null</code> if this is
     *          {@link #ANY_HOST_CONFIGURATION ANY_HOST_CONFIGURATION}
     */
    public HttpHost getHost() {
        return this.targetHost;
    }


    /**
     * Returns the proxy to use.
     * 
     * @return the proxy host, or <code>null</code> if not set
     */
    public HttpHost getProxyHost() {
        return this.proxyHost;
    }


    /**
     * Return the local address to be used when creating connections.
     * If this is unset, the default address should be used.
     * 
     * @return  the local address to be used when creating Sockets,
     *          or <code>null</code>
     */
    public InetAddress getLocalAddress() {
        return this.localAddress;
    }


    // non-javadoc, see java.lang.Object#equals(java.lang.Object)
    public boolean equals(final Object o) {
        if (o instanceof HostConfiguration) {
            // shortcut if we're comparing with ourselves
            if (o == this) { 
                return true;
            }
            HostConfiguration that = (HostConfiguration) o;
            return LangUtils.equals(this.targetHost, that.targetHost)
                && LangUtils.equals(this.proxyHost, that.proxyHost)
                && LangUtils.equals(this.localAddress, that.localAddress);
        } else {
            return false;
        }
        
    }

    // non-javadoc, see java.lang.Object#hashCode()
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.targetHost);
        hash = LangUtils.hashCode(hash, this.proxyHost);
        hash = LangUtils.hashCode(hash, this.localAddress);
        return hash;
    }

}
