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

import java.util.concurrent.TimeUnit;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;


/**
 * Thread to get a connection from a connection manager.
 * Used by connection manager tests.
 * Code based on HttpClient 3.x class <code>TestHttpConnectionManager</code>.
 */
public class GetConnThread extends Thread {

    protected final HttpRoute               conn_route;
    protected final long                    conn_timeout;
    protected final ClientConnectionRequest conn_request;

    protected volatile ManagedClientConnection connection;
    protected volatile Throwable               exception;

    /**
     * Creates a new thread for requesting a connection from the given manager.
     *
     * When this thread is started, it will try to obtain a connection.
     * The timeout is in milliseconds.
     */
    public GetConnThread(ClientConnectionManager mgr,
                         HttpRoute route, long timeout) {
        this(mgr.requestConnection(route, null), route, timeout);
    }

    /**
     * Creates a new for requesting a connection from the given request object.
     *
     * When this thread is started, it will try to obtain a connection.
     * The timeout is in milliseconds.
     */
    public GetConnThread(ClientConnectionRequest connectionRequest,
            HttpRoute route, long timeout) {
        conn_route = route;
        conn_timeout = timeout;
        conn_request = connectionRequest;
    }

    /**
     * This method is executed when the thread is started.
     */
    @Override
    public void run() {
        try {
            connection = conn_request.getConnection
                (conn_timeout, TimeUnit.MILLISECONDS);
        } catch (Throwable dart) {
            exception = dart;
        }
        // terminate
    }


    public Throwable getException() {
        return exception;
    }

    public ManagedClientConnection getConnection() {
        return connection;
    }

}
