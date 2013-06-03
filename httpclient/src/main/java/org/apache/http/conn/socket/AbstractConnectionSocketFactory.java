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

package org.apache.http.conn.socket;

import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * This is a base class for {@link ConnectionSocketFactory} implementations.
 * This class provides a common exception handling logic for connect operations.
 *
 * @since 4.3
 */
public abstract class AbstractConnectionSocketFactory implements ConnectionSocketFactory {

     /**
     * Connects a socket to the target host with the given resolved remote address.
     *
     * @param connectTimeout connect timeout.
     * @param socket the socket to connect, as obtained from {@link #createSocket(HttpContext)}.
     * <code>null</code> indicates that a new socket should be created and connected.
     * @param host target host as specified by the caller (end user).
     * @param remoteAddress the resolved remote address to connect to.
     * @param localAddress the local address to bind the socket to, or <code>null</code> for any.
     * @param context the actual HTTP context.
     *
      * @return  the connected socket. The returned object may be different
      *          from the <code>sock</code> argument if this factory supports
      *          a layered protocol.
     * @throws org.apache.http.conn.ConnectTimeoutException if the socket cannot be connected
     *          within the time limit defined by connectTimeout parameter.
     * @throws org.apache.http.conn.HttpHostConnectException if the connection is refused
     *          by the opposite endpoint.
     */
    public Socket connectSocket(
            final int connectTimeout,
            final Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException {
        final Socket sock = socket != null ? socket : createSocket(context);
        if (localAddress != null) {
            sock.bind(localAddress);
        }
        try {
            sock.connect(remoteAddress, connectTimeout);
        } catch (final SocketTimeoutException ex) {
            closeSocket(socket);
            throw new ConnectTimeoutException(host, remoteAddress, ex);
        } catch (final ConnectException ex) {
            closeSocket(socket);
            String msg = ex.getMessage();
            if ("Connection timed out".equals(msg)) {
                throw new ConnectTimeoutException(host, remoteAddress, ex);
            } else {
                throw new HttpHostConnectException(host, remoteAddress, ex);
            }
        }
        return sock;
    }

    private void closeSocket(final Socket sock) {
        try {
            sock.close();
        } catch (final IOException ignore) {
        }
    }

}
