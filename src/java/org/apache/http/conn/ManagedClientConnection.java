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
import java.net.Socket;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpInetConnection;
import org.apache.http.protocol.HttpContext;
import org.apache.http.params.HttpParams;


/**
 * A client-side connection with advanced connection logic.
 * Instances are typically obtained from a connection manager.
 *
 * @author <a href="mailto:rolandw@apache.org">Roland Weber</a>
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


    /* *
     * Indicates that a tunnel has been created.
     *
     * @param route     the route along which the tunnel has been created
     * @param params    the parameters for updating the connection
     */
    //@@@ tunnelCreated in the old implementation triggers layering
    //@@@ of the TLS/SSL connection. This should be split in two.


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
