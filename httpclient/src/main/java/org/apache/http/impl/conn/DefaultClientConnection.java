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
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.annotation.NotThreadSafe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.impl.SocketHttpClientConnection;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;

import org.apache.http.conn.OperatedClientConnection;

/**
 * Default implementation of an operated client connection.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#STRICT_TRANSFER_ENCODING}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 * </ul>
 *
 * @since 4.0
 */
@NotThreadSafe // connSecure, targetHost
public class DefaultClientConnection extends SocketHttpClientConnection
    implements OperatedClientConnection, HttpContext {

    private final Log log = LogFactory.getLog(getClass());
    private final Log headerLog = LogFactory.getLog("org.apache.http.headers");
    private final Log wireLog = LogFactory.getLog("org.apache.http.wire");

    /** The unconnected socket */
    private volatile Socket socket;

    /** The target host of this connection. */
    private HttpHost targetHost;

    /** Whether this connection is secure. */
    private boolean connSecure;

    /** True if this connection was shutdown. */
    private volatile boolean shutdown;

    /** connection specific attributes */
    private final Map<String, Object> attributes;

    public DefaultClientConnection() {
        super();
        this.attributes = new HashMap<String, Object>();
    }

    public final HttpHost getTargetHost() {
        return this.targetHost;
    }

    public final boolean isSecure() {
        return this.connSecure;
    }

    @Override
    public final Socket getSocket() {
        return this.socket;
    }

    public void opening(Socket sock, HttpHost target) throws IOException {
        assertNotOpen();
        this.socket = sock;
        this.targetHost = target;

        // Check for shutdown after assigning socket, so that
        if (this.shutdown) {
            sock.close(); // allow this to throw...
            // ...but if it doesn't, explicitly throw one ourselves.
            throw new IOException("Connection already shutdown");
        }
    }

    public void openCompleted(boolean secure, HttpParams params) throws IOException {
        assertNotOpen();
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        this.connSecure = secure;
        bind(this.socket, params);
    }

    /**
     * Force-closes this connection.
     * If the connection is still in the process of being open (the method
     * {@link #opening opening} was already called but
     * {@link #openCompleted openCompleted} was not), the associated
     * socket that is being connected to a remote address will be closed.
     * That will interrupt a thread that is blocked on connecting
     * the socket.
     * If the connection is not yet open, this will prevent the connection
     * from being opened.
     *
     * @throws IOException      in case of a problem
     */
    @Override
    public void shutdown() throws IOException {
        shutdown = true;
        try {
            super.shutdown();
            log.debug("Connection shut down");
            Socket sock = this.socket; // copy volatile attribute
            if (sock != null)
                sock.close();
        } catch (IOException ex) {
            log.debug("I/O error shutting down connection", ex);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
            log.debug("Connection closed");
        } catch (IOException ex) {
            log.debug("I/O error closing connection", ex);
        }
    }

    @Override
    protected SessionInputBuffer createSessionInputBuffer(
            final Socket socket,
            int buffersize,
            final HttpParams params) throws IOException {
        if (buffersize == -1) {
            buffersize = 8192;
        }
        SessionInputBuffer inbuffer = super.createSessionInputBuffer(
                socket,
                buffersize,
                params);
        if (wireLog.isDebugEnabled()) {
            inbuffer = new LoggingSessionInputBuffer(
                    inbuffer,
                    new Wire(wireLog),
                    HttpProtocolParams.getHttpElementCharset(params));
        }
        return inbuffer;
    }

    @Override
    protected SessionOutputBuffer createSessionOutputBuffer(
            final Socket socket,
            int buffersize,
            final HttpParams params) throws IOException {
        if (buffersize == -1) {
            buffersize = 8192;
        }
        SessionOutputBuffer outbuffer = super.createSessionOutputBuffer(
                socket,
                buffersize,
                params);
        if (wireLog.isDebugEnabled()) {
            outbuffer = new LoggingSessionOutputBuffer(
                    outbuffer,
                    new Wire(wireLog),
                    HttpProtocolParams.getHttpElementCharset(params));
        }
        return outbuffer;
    }

    @Override
    protected HttpMessageParser createResponseParser(
            final SessionInputBuffer buffer,
            final HttpResponseFactory responseFactory,
            final HttpParams params) {
        // override in derived class to specify a line parser
        return new DefaultResponseParser
            (buffer, null, responseFactory, params);
    }

    public void update(Socket sock, HttpHost target,
                       boolean secure, HttpParams params)
        throws IOException {

        assertOpen();
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }

        if (sock != null) {
            this.socket = sock;
            bind(sock, params);
        }
        targetHost = target;
        connSecure = secure;
    }

    @Override
    public HttpResponse receiveResponseHeader() throws HttpException, IOException {
        HttpResponse response = super.receiveResponseHeader();
        if (log.isDebugEnabled()) {
            log.debug("Receiving response: " + response.getStatusLine());
        }
        if (headerLog.isDebugEnabled()) {
            headerLog.debug("<< " + response.getStatusLine().toString());
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerLog.debug("<< " + header.toString());
            }
        }
        return response;
    }

    @Override
    public void sendRequestHeader(HttpRequest request) throws HttpException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("Sending request: " + request.getRequestLine());
        }
        super.sendRequestHeader(request);
        if (headerLog.isDebugEnabled()) {
            headerLog.debug(">> " + request.getRequestLine().toString());
            Header[] headers = request.getAllHeaders();
            for (Header header : headers) {
                headerLog.debug(">> " + header.toString());
            }
        }
    }

    public Object getAttribute(final String id) {
        return this.attributes.get(id);
    }

    public Object removeAttribute(final String id) {
        return this.attributes.remove(id);
    }

    public void setAttribute(final String id, final Object obj) {
        this.attributes.put(id, obj);
    }

}
