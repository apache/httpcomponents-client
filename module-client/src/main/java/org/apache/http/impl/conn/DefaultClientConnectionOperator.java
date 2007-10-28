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

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.InetAddress;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.LayeredSocketFactory;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.ClientConnectionOperator;


/**
 * Default implementation of a
 * {@link ClientConnectionOperator ClientConnectionOperator}.
 * It uses a {@link SchemeRegistry SchemeRegistry} to look up
 * {@link SocketFactory SocketFactory} objects.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$ $Date$
 *
 * @since 4.0
 */
public class DefaultClientConnectionOperator
    implements ClientConnectionOperator {


    /** The scheme registry for looking up socket factories. */
    protected SchemeRegistry schemeRegistry;


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


    // non-javadoc, see interface ClientConnectionOperator
    public OperatedClientConnection createConnection() {
        return new DefaultClientConnection();
    }


    // non-javadoc, see interface ClientConnectionOperator
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
        if (schm == null) {
            throw new IllegalArgumentException
                ("Unknown scheme '" + target.getSchemeName() +
                 "' in target host.");
        }
        final SocketFactory sf = schm.getSocketFactory();

        Socket sock = sf.createSocket();
        conn.announce(sock);

        try {
            sock = sf.connectSocket(sock, target.getHostName(),
                    schm.resolvePort(target.getPort()),
                    local, 0, params);
        } catch (ConnectException ex) {
            throw new HttpHostConnectException(target, ex);
        }
        prepareSocket(sock, context, params);

        final boolean secure = sf.isSecure(sock);

        conn.open(sock, target, secure, params);
        //@@@ error handling: unannounce at connection?
        //@@@ error handling: close the created socket?

    } // openConnection


    // non-javadoc, see interface ClientConnectionOperator
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
        //@@@ is context allowed to be null?
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        if (!conn.isOpen()) {
            throw new IllegalArgumentException
                ("Connection must be open.");
        }

        final Scheme schm = schemeRegistry.getScheme(target.getSchemeName());
        if (schm == null) {
            throw new IllegalArgumentException
                ("Unknown scheme '" + target.getSchemeName() +
                 "' in target host.");
        }
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
        //@@@ error handling: close the layered socket in case of exception?

    } // updateSecureConnection


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

        // context currently not used, but derived classes may need it
        //@@@ is context allowed to be null?

        sock.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        sock.setSoTimeout(HttpConnectionParams.getSoTimeout(params));

        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) {
            sock.setSoLinger(linger > 0, linger);
        }

    } // prepareSocket


} // class DefaultClientConnectionOperator

