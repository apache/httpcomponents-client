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

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.InetAddress;

import org.apache.http.annotation.ThreadSafe;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;

/**
 * Default implementation of a {@link ClientConnectionOperator}. It uses 
 * a {@link SchemeRegistry} to look up {@link SocketFactory} objects.
 * <p>
 * The following parameters can be used to customize the behavior of this 
 * class: 
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#TCP_NODELAY}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_LINGER}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.0
 */
@ThreadSafe
public class DefaultClientConnectionOperator implements ClientConnectionOperator {

    /** The scheme registry for looking up socket factories. */
    protected final SchemeRegistry schemeRegistry; // @ThreadSafe

    /**
     * Creates a new client connection operator for the given scheme registry.
     *
     * @param schemes   the scheme registry
     */
    public DefaultClientConnectionOperator(SchemeRegistry schemes) {
        if (schemes == null) {
            throw new IllegalArgumentException
                ("Scheme registry must not be null.");
        }
        schemeRegistry = schemes;
    }

    public OperatedClientConnection createConnection() {
        return new DefaultClientConnection();
    }

    public void openConnection(OperatedClientConnection conn,
                               HttpHost target,
                               InetAddress local,
                               HttpContext context,
                               HttpParams params)
        throws IOException {

        if (conn == null) {
            throw new IllegalArgumentException
                ("Connection must not be null.");
        }
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }
        // local address may be null
        //@@@ is context allowed to be null?
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        if (conn.isOpen()) {
            throw new IllegalArgumentException
                ("Connection must not be open.");
        }

        final Scheme schm = schemeRegistry.getScheme(target.getSchemeName());
        final SocketFactory sf = schm.getSocketFactory();

        Socket sock = sf.createSocket();
        conn.opening(sock, target);

        try {
            sock = sf.connectSocket(sock, target.getHostName(),
                    schm.resolvePort(target.getPort()),
                    local, 0, params);
        } catch (ConnectException ex) {
            throw new HttpHostConnectException(target, ex);
        }
        prepareSocket(sock, context, params);
        conn.openCompleted(sf.isSecure(sock), params);
    }

    public void updateSecureConnection(OperatedClientConnection conn,
                                       HttpHost target,
                                       HttpContext context,
                                       HttpParams params)
        throws IOException {


        if (conn == null) {
            throw new IllegalArgumentException
                ("Connection must not be null.");
        }
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        if (!conn.isOpen()) {
            throw new IllegalArgumentException
                ("Connection must be open.");
        }

        final Scheme schm = schemeRegistry.getScheme(target.getSchemeName());
        if (!(schm.getSocketFactory() instanceof LayeredSocketFactory)) {
            throw new IllegalArgumentException
                ("Target scheme (" + schm.getName() +
                 ") must have layered socket factory.");
        }

        final LayeredSocketFactory lsf = (LayeredSocketFactory) schm.getSocketFactory();
        final Socket sock; 
        try {
            sock = lsf.createSocket
                (conn.getSocket(), target.getHostName(), target.getPort(), true);
        } catch (ConnectException ex) {
            throw new HttpHostConnectException(target, ex);
        }
        prepareSocket(sock, context, params);
        conn.update(sock, target, lsf.isSecure(sock), params);
    }

    /**
     * Performs standard initializations on a newly created socket.
     *
     * @param sock      the socket to prepare
     * @param context   the context for the connection
     * @param params    the parameters from which to prepare the socket
     *
     * @throws IOException      in case of an IO problem
     */
    protected void prepareSocket(Socket sock, HttpContext context,
                                 HttpParams params)
        throws IOException {

        sock.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        sock.setSoTimeout(HttpConnectionParams.getSoTimeout(params));

        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) {
            sock.setSoLinger(linger > 0, linger);
        }
    }

}

