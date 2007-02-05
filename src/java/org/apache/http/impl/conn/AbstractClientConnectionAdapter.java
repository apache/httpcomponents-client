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

package org.apache.http.impl.conn;


import java.io.IOException;
import java.net.InetAddress;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.ManagedClientConnection;



/**
 * Abstract adapter from {@link OperatedClientConnection operated} to
 * {@link ManagedClientConnection managed} client connections.
 * Read and write methods are delegated to the wrapped connection.
 * Operations affecting the connection state have to be implemented
 * by derived classes. Operations for querying the connection state
 * are delegated to the wrapped connection if there is one, or
 * return a default value if there is none.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$ $Date$
 *
 * @since 4.0
 */
public abstract class AbstractClientConnectionAdapter
    implements ManagedClientConnection {

    /** The wrapped connection. */
    protected OperatedClientConnection wrappedConnection;


    /**
     * Creates a new connection adapter.
     *
     * @param conn      the connection to wrap, or <code>null</code>
     */
    protected AbstractClientConnectionAdapter(OperatedClientConnection conn) {

        wrappedConnection = conn;

    } // <constructor>


    /**
     * Asserts that there is a wrapped connection to delegate to.
     *
     * @throws IllegalStateException    if there is no wrapped connection
     */
    protected final void assertWrappedConn() {
        if (wrappedConnection == null) {
            throw new IllegalStateException("No wrapped connection.");
        }
    }


    // non-javadoc, see interface HttpConnection
    public boolean isOpen() {
        if (wrappedConnection == null)
            return false;

        return wrappedConnection.isOpen();
    }


    // non-javadoc, see interface HttpConnection
    public boolean isStale() {
        if (wrappedConnection == null)
            return true;

        return wrappedConnection.isStale();
    }


    // non-javadoc, see interface HttpClientConnection
    public void flush()
        throws IOException {

        assertWrappedConn();
        wrappedConnection.flush();
    }


    // non-javadoc, see interface HttpClientConnection
    public boolean isResponseAvailable(int timeout)
        throws IOException {

        assertWrappedConn();
        return wrappedConnection.isResponseAvailable(timeout);
    }


    // non-javadoc, see interface HttpClientConnection
    public void receiveResponseEntity(HttpResponse response)
        throws HttpException, IOException {

        assertWrappedConn();
        wrappedConnection.receiveResponseEntity(response);
    }


    // non-javadoc, see interface HttpClientConnection
    public HttpResponse receiveResponseHeader(HttpParams params)
        throws HttpException, IOException {

        assertWrappedConn();
        return wrappedConnection.receiveResponseHeader(params);
    }


    // non-javadoc, see interface HttpClientConnection
    public void sendRequestEntity(HttpEntityEnclosingRequest request)
        throws HttpException, IOException {

        assertWrappedConn();
        wrappedConnection.sendRequestEntity(request);
    }


    // non-javadoc, see interface HttpClientConnection
    public void sendRequestHeader(HttpRequest request)
        throws HttpException, IOException {

        assertWrappedConn();
        wrappedConnection.sendRequestHeader(request);
    }


    // non-javadoc, see interface HttpInetConnection
    public InetAddress getLocalAddress() {
        assertWrappedConn();
        return wrappedConnection.getLocalAddress();
    }

    // non-javadoc, see interface HttpInetConnection
    public int getLocalPort() {
        assertWrappedConn();
        return wrappedConnection.getLocalPort();
    }


    // non-javadoc, see interface HttpInetConnection
    public InetAddress getRemoteAddress() {
        assertWrappedConn();
        return wrappedConnection.getRemoteAddress();
    }

    // non-javadoc, see interface HttpInetConnection
    public int getRemotePort() {
        assertWrappedConn();
        return wrappedConnection.getRemotePort();
    }

    // non-javadoc, see interface ManagedClientConnection
    public boolean isSecure() {
        assertWrappedConn();
        return wrappedConnection.isSecure();
    }


} // class AbstractClientConnectionAdapter
