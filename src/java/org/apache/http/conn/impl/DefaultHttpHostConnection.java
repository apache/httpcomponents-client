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

package org.apache.http.conn.impl;

import java.io.IOException;
import java.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.conn.HostConfiguration;
import org.apache.http.conn.HttpConnectionManager;
import org.apache.http.conn.HttpHostConnection;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeSet;
import org.apache.http.conn.SecureSocketFactory;
import org.apache.http.conn.SocketFactory;
import org.apache.http.impl.SocketHttpClientConnection;
import org.apache.http.params.HttpParams;

/**
 * Default {@link HttpHostConnection} implementation.
 *
 * @author Rod Waldhoff
 * @author Sean C. Sullivan
 * @author Ortwin Glueck
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author Michael Becke
 * @author Eric E Johnson
 * @author Laura Werner
 * 
 * @version   $Revision$ $Date$
 */
public class DefaultHttpHostConnection 
    extends SocketHttpClientConnection implements HttpHostConnection {

    private static final Log LOG = LogFactory.getLog(DefaultHttpHostConnection.class);
    
    /** the connection manager that created this connection or null */
    private HttpConnectionManager manager;
    
    private HostConfiguration hostconf;

    /** flag to indicate if this connection can be released, if locked the connection cannot be 
     * released */
    private boolean locked = false;
    
    /** Whether the connection is open via a secure tunnel or not */
    private boolean tunnelEstablished = false;
    
    private HttpResponse lastResponse;
    
    public DefaultHttpHostConnection() {
        super();
    }
    
    public void setHttpConnectionManager(final HttpConnectionManager manager) {
        this.manager = manager;
    }

    public void setHostConfiguration(final HostConfiguration hostconf) {
        assertNotOpen();
        this.hostconf = hostconf;
    }
    
    public HostConfiguration getHostConfiguration() {
        return this.hostconf;
    }

    /**
     * Establishes a connection to the specified host and port
     * (via a proxy if specified).
     * The underlying socket is created from the {@link SocketFactory}.
     *
     * @throws IOException if an attempt to establish the connection results in an
     *   I/O error.
     */
    public void open(final HttpParams params) 
            throws IllegalStateException, IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertNotOpen();
        if (this.hostconf == null) {
            throw new IllegalArgumentException("Host configuration is null");
        }

        HttpHost host = this.hostconf.getHost();
        if (host == null) {
            throw new IllegalStateException("Target http host is null");
        }
        HttpHost proxyHost = this.hostconf.getProxyHost();
        
        if (LOG.isDebugEnabled()) {
            if (proxyHost == null) {
                LOG.debug("Open connection to " + host);
            } else {
                LOG.debug("Open connection to " + host + " via proxy " + proxyHost);
            }
        }

        // Determine the type of the connection
        Scheme scheme = SchemeSet.DEFAULT.getScheme(host.getSchemeName());
        SocketFactory socketFactory = scheme.getSocketFactory();
        boolean secure = (socketFactory instanceof SecureSocketFactory);
        boolean proxied = (proxyHost != null);
        
        // Determine the target host
        HttpHost target = null;
        if (proxyHost != null) {
            target = proxyHost; 
        } else {
            target = host; 
        }

        // Create the socket
        String hostname = target.getHostName();
        int port = target.getPort();
        if (port < 0) {
           port = scheme.getDefaultPort(); 
        }
        if (secure && proxied) {
            scheme = SchemeSet.DEFAULT.getScheme("http");
            socketFactory = scheme.getSocketFactory();
        } else {
            scheme = SchemeSet.DEFAULT.getScheme(target.getSchemeName());
        }
        socketFactory = scheme.getSocketFactory();
        Socket socket = socketFactory.connectSocket(
                null, hostname, port, 
                this.hostconf.getLocalAddress(), 0, params);

        // Bind connection to the socket
        bind(socket, params);
    }

    /**
     * Instructs the proxy to establish a secure tunnel to the host. The socket will 
     * be switched to the secure socket. Subsequent communication is done via the secure 
     * socket. The method can only be called once on a proxied secure connection.
     *
     * @throws IllegalStateException if connection is not secure and proxied or
     * if the socket is already secure.
     * @throws IOException if an attempt to establish the secure tunnel results in an
     *   I/O error.
     */
    public void tunnelCreated(final HttpParams params) 
            throws IllegalStateException, IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertOpen();
        if (this.tunnelEstablished) {
            throw new IllegalStateException("Tunnel already established");
        }
        HttpHost host = this.hostconf.getHost();
        if (host == null) {
            throw new IllegalStateException("Target http host is null");
        }
        HttpHost proxyHost = this.hostconf.getProxyHost();
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Secure tunnel to " + host);
        }
        
        Scheme scheme = SchemeSet.DEFAULT.getScheme(host.getSchemeName());
        SocketFactory socketFactory = scheme.getSocketFactory();
        boolean secure = (socketFactory instanceof SecureSocketFactory);
        boolean proxied = (proxyHost != null);

        if (!secure || !proxied) {
            throw new IllegalStateException(
                "Connection must be secure "
                    + "and proxied to use this feature");
        }

        String hostname = host.getHostName();
        int port = host.getPort();
        if (port < 0) {
           port = scheme.getDefaultPort(); 
        }
        SecureSocketFactory securesocketFactory = (SecureSocketFactory) socketFactory;

        Socket tunnel = securesocketFactory.createSocket(
                this.socket, hostname, port, true);
        bind(tunnel, params);
        this.tunnelEstablished = true;
    }

    /**
     * Returns the httpConnectionManager.
     * @return HttpConnectionManager
     */
    public HttpConnectionManager getHttpConnectionManager() {
        return this.manager;
    }

    /**
     * Releases the connection. If the connection is locked or does not have a connection
     * manager associated with it, this method has no effect. Note that it is completely safe 
     * to call this method multiple times.
     */
    public void releaseConnection() {
        if (locked) {
            LOG.debug("Connection is locked.  Call to releaseConnection() ignored.");
        } else if (this.manager != null) {
            LOG.debug("Releasing connection back to connection manager.");
            this.manager.releaseConnection(this);
        } else {
            LOG.warn("HttpConnectionManager is null.  Connection cannot be released.");
        }
    }

    /**
     * Tests if the connection is locked. Locked connections cannot be released. 
     * An attempt to release a locked connection will have no effect.
     * 
     * @return <tt>true</tt> if the connection is locked, <tt>false</tt> otherwise.
     * 
     * @since 3.0
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Locks or unlocks the connection. Locked connections cannot be released. 
     * An attempt to release a locked connection will have no effect.
     * 
     * @param locked <tt>true</tt> to lock the connection, <tt>false</tt> to unlock
     *  the connection.
     * 
     * @since 3.0
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public HttpResponse getLastResponse() {
        return this.lastResponse;
    }

    public void setLastResponse(final HttpResponse lastResponse) {
        this.lastResponse = lastResponse;
    }

}
