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

import java.io.IOException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpInetConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;


/**
 * A client-side connection with advanced connection logic.
 * Instances are typically obtained from a connection manager.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$ $Date$
 *
 * @since 4.0
 */
public interface ManagedClientConnection
    extends HttpClientConnection, HttpInetConnection {


    /**
     * Indicates whether this connection is secure.
     * The return value is well-defined only while the connection is open.
     * It may change even while the connection is open.
     *
     * @return  <code>true</code> if this connection is secure,
     *          <code>false</code> otherwise
     */
    boolean isSecure()
        ;


    /**
     * Obtains the current route of this connection.
     *
     * @return  the route established so far, or
     *          <code>null</code> if not connected
     */
    HttpRoute getRoute()
        ;


    /**
     * Opens this connection according to the given route.
     *
     * @param route     the route along which to open. It will be opened
     *                  to the proxy if present, or directly to the target.
     * @param context   the context for opening this connection
     * @param params    the parameters for opening this connection
     *
     * @throws IOException      in case of a problem
     */
    void open(HostConfiguration route, HttpContext context, HttpParams params)
        throws IOException
        ;


    /**
     * Indicates that a tunnel has been established.
     * The route is the one previously passed to {@link #open open}.
     * Subsequently, {@link #layerProtocol layerProtocol} can be called
     * to layer the TLS/SSL protocol on top of the tunnelled connection.
     * <br/>
     * <b>Note:</b> In HttpClient 3, a call to the corresponding method
     * would automatically trigger the layering of the TLS/SSL protocol.
     * This is not the case anymore, you can establish a tunnel without
     * layering a new protocol over the connection.
     *
     * @param secure    <code>true</code> if the tunnel should be considered
     *                  secure, <code>false</code> otherwise
     * @param params    the parameters for tunnelling this connection
     *
     * @throws IOException  in case of a problem
     */
    void tunnelCreated(boolean secure, HttpParams params)
        throws IOException
        ;


    /**
     * Layers a new protocol on top of a {@link #tunnelCreated tunnelled}
     * connection. This is typically used to create a TLS/SSL connection
     * through a proxy.
     * The route is the one previously passed to {@link #open open}.
     * It is not guaranteed that the layered connection is
     * {@link #isSecure secure}.
     *
     * @param context   the context for layering on top of this connection
     * @param params    the parameters for layering on top of this connection
     *
     * @throws IOException      in case of a problem
     */
    void layerProtocol(HttpContext context, HttpParams params)
        throws IOException
        ;


    /* *
     * Releases this connection back to it's connection manager.
     *
     * @throws IllegalStateException
     *          if this connection is already released
     * /
    boolean release()
        throws IllegalStateException
        ;
    */


} // interface ManagedClientConnection
