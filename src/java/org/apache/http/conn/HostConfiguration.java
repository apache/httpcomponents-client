/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

package org.apache.http.conn;

import java.net.InetAddress;

import org.apache.http.HttpHost;
import org.apache.http.util.LangUtils;

/**
 * Holds all of the variables needed to describe an HTTP connection to a host.  This includes 
 * remote host, port and protocol, proxy host and port, local address, and virtual host.
 * 
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author Laura Werner
 * 
 * @since 2.0 
 */
public class HostConfiguration implements Cloneable {

    /**
     * A value to represent any host configuration, instead of using something like
     * <code>null</code>. This value should be treated as immutable and only used in 
     * lookups and other such places to represent "any" host config.
     */
    public static final HostConfiguration ANY_HOST_CONFIGURATION = new HostConfiguration();

    /** The host to use. */
    private HttpHost host = null;

    /** The host name of the proxy server */
    private HttpHost proxyHost = null;

    /** The local address to use when creating the socket, or null to use the default */
    private InetAddress localAddress = null;

    /**
     * Constructor for HostConfiguration.
     */
    public HostConfiguration() {
        super();
    }
    
    /**
     * Copy constructor for HostConfiguration
     * 
     * @param hostConfiguration the hostConfiguration to copy
     */
    public HostConfiguration (final HostConfiguration hostConfiguration) {
        // wrap all of the assignments in a synchronized block to avoid
        // having to negotiate the monitor for each method call
        synchronized (hostConfiguration) {
            if (hostConfiguration.host != null) {
                this.host = new HttpHost(hostConfiguration.host);
            } else {
                this.host = null;
            }
            if (hostConfiguration.proxyHost != null) {
                this.proxyHost = new HttpHost(hostConfiguration.proxyHost);
            } else {
                this.proxyHost = null;
            }
            this.localAddress = hostConfiguration.getLocalAddress();
        }        
    }

    /**
     * @see java.lang.Object#clone()
     */
    public Object clone() {
        return new HostConfiguration(this);
    }    
    
    /**
     * @see java.lang.Object#toString()
     */
    public synchronized String toString() {
        
        boolean appendComma = false;
        StringBuffer b = new StringBuffer(50);        
        b.append("HostConfiguration[");
        
        if (this.host != null) {
            appendComma = true;
            b.append("host=").append(this.host);
        }
        if (this.proxyHost != null) {
            if (appendComma) {
                b.append(", ");
            } else {
                appendComma = true;
            }
            b.append("proxyHost=").append(this.proxyHost);
        }
        if (this.localAddress != null) {
            if (appendComma) {
                b.append(", ");
            } else {
                appendComma = true;
            }
            b.append("localAddress=").append(this.localAddress);
            if (appendComma) {
                b.append(", ");
            } else {
                appendComma = true;
            }
        }
        b.append("]");
        return b.toString();
    }    
    
    /**
     * Sets the given host
     * 
     * @param host the host
     */
    public synchronized void setHost(final HttpHost host) {
        this.host = host;
    }
    
    /**
     * Sets the given host, port and protocol
     * 
     * @param host the host(IP or DNS name)
     * @param port The port
     * @param protocol The protocol.
     */
    public synchronized void setHost(final String host, int port, final String protocol) {
        this.host = new HttpHost(host, port, protocol);
    }
    
    /**
     * Sets the given host and port.  Uses the default protocol "http".
     * 
     * @param host the host(IP or DNS name)
     * @param port The port
     */
    public synchronized void setHost(final String host, int port) {
        setHost(host, port, "http");
    }
    
    /**
     * Set the given host. Uses the default protocol("http") and its port.
     * 
     * @param host The host(IP or DNS name).
     */
    public synchronized void setHost(final String host) {
        setHost(host, -1, "http");
    }
    
    /**
     * Return the host url.
     * 
     * @return The host url.
     */
    public synchronized String getHostURL() {
        if (this.host == null) {
            throw new IllegalStateException("Host must be set to create a host URL");   
        } else {
            return this.host.toURI();
        }
    }

    /**
     * Returns the target host.
     * 
     * @return the target host, or <code>null</code> if not set
     * 
     * @see #isHostSet()
     */
    public synchronized HttpHost getHost() {
        return this.host;
    }

    /**
     * Returns the host name.
     * 
     * @return the host(IP or DNS name), or <code>null</code> if not set
     * 
     * @see #isHostSet()
     */
    public synchronized String getHostName() {
        if (this.host != null) {
            return this.host.getHostName();
        } else {
            return null;
        }
    }

    /**
     * Returns the port.
     * 
     * @return the host port, or <code>-1</code> if not set
     * 
     * @see #isHostSet()
     */
    public synchronized int getPort() {
        if (this.host != null) {
            return this.host.getPort();
        } else {
            return -1;
        }
    }

    /**
     * Returns the protocol.
     * @return The protocol.
     */
    public synchronized Scheme getScheme() {
        if (this.host != null) {
            return Scheme.getScheme(this.host.getSchemeName());
        } else {
            return null;
        }
    }

    /**
     * Sets the given proxy host
     * 
     * @param proxyHost the proxy host
     */
    public synchronized void setProxyHost(final HttpHost proxyHost) {
        this.proxyHost = proxyHost;
    }
    
    /**
     * Set the proxy settings.
     * @param proxyHost The proxy host
     * @param proxyPort The proxy port
     */
    public synchronized void setProxy(final String proxyHost, int proxyPort) {
        this.proxyHost = new HttpHost(proxyHost, proxyPort); 
    }

    /**
     * Returns the proxyHost.
     * 
     * @return the proxy host, or <code>null</code> if not set
     * 
     * @see #isProxySet()
     */
    public synchronized HttpHost getProxyHost() {
        return this.proxyHost;
    }

    /**
     * Returns the proxyHost.
     * 
     * @return the proxy host, or <code>null</code> if not set
     * 
     * @see #isProxySet()
     */
    public synchronized String getProxyHostName() {
        if (this.proxyHost != null) {
            return this.proxyHost.getHostName();
        } else {
            return null;
        }
    }

    /**
     * Returns the proxyPort.
     * 
     * @return the proxy port, or <code>-1</code> if not set
     * 
     * @see #isProxySet()
     */
    public synchronized int getProxyPort() {
        if (this.proxyHost != null) {
            return this.proxyHost.getPort();
        } else {
            return -1;
        }
    }

    /**
     * Set the local address to be used when creating connections.
     * If this is unset, the default address will be used.
     * This is useful for specifying the interface to use on multi-homed or clustered systems.
     * 
     * @param localAddress the local address to use
     */
    
    public synchronized void setLocalAddress(InetAddress localAddress) {
        this.localAddress = localAddress;
    }

    /**
     * Return the local address to be used when creating connections.
     * If this is unset, the default address should be used.
     * 
     * @return the local address to be used when creating Sockets, or <code>null</code>
     */
    
    public synchronized InetAddress getLocalAddress() {
        return this.localAddress;
    }
    
    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public synchronized boolean equals(final Object o) {
        if (o instanceof HostConfiguration) {
            // shortcut if we're comparing with ourselves
            if (o == this) { 
                return true;
            }
            HostConfiguration that = (HostConfiguration) o;
            return LangUtils.equals(this.host, that.host)
                && LangUtils.equals(this.proxyHost, that.proxyHost)
                && LangUtils.equals(this.localAddress, that.localAddress);
        } else {
            return false;
        }
        
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public synchronized int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.host);
        hash = LangUtils.hashCode(hash, this.proxyHost);
        hash = LangUtils.hashCode(hash, this.localAddress);
        return hash;
    }

}
