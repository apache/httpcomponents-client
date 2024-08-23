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

package org.apache.hc.client5.http.io;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.io.HttpClientConnection;

/**
 * Represents a managed connection whose state and life cycle is managed by
 * a connection manager. This interface extends {@link HttpClientConnection}
 * with methods to bind the connection to an arbitrary socket and
 * to obtain SSL session details.
 *
 * @since 4.3
 */
@Internal
public interface ManagedHttpClientConnection extends HttpClientConnection {

    /**
     * Binds this connection to the given socket. The connection
     * is considered open if it is bound and the underlying socket
     * is connection to a remote host.
     *
     * @param socket the socket to bind the connection to.
     */
    void bind(Socket socket) throws IOException;

    /**
     * Binds this connection to the SSL given socket and the underlying network
     * socket. The connection is considered open if it is bound, the underlying
     * network socket is connection to a remote host and the SSL socket is
     * fully initialized (TLS handshake has been successfully executed).
     *
     * @param sslSocket the SSL socket to bind the connection to.
     * @param socket the underlying network socket of the SSL socket.
     *
     * @since 5.4
     */
    default void bind(SSLSocket sslSocket, Socket socket) throws IOException {
        bind(sslSocket);
    }

    /**
     * Returns the underlying socket.
     */
    Socket getSocket();

    /**
     * Obtains the SSL session of the underlying connection, if any.
     * If this connection is open, and the underlying socket is an
     * {@link javax.net.ssl.SSLSocket SSLSocket}, the SSL session of
     * that socket is obtained. This is a potentially blocking operation.
     *
     * @return  the underlying SSL session if available,
     *          {@code null} otherwise
     */
    @Override
    SSLSession getSSLSession();

    /**
     * Puts the connection into idle mode.
     *
     * @since 5.0
     */
    void passivate();

    /**
     * Restores the connection from idle mode.
     *
     * @since 5.0
     */
    void activate();

}
