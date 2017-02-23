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

package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.impl.logging.LoggingSocketHolder;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.utils.Identifiable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.impl.io.SocketHolder;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default {@link ManagedHttpClientConnection} implementation.
 * @since 4.3
 */
public class DefaultManagedHttpClientConnection
        extends DefaultBHttpClientConnection implements ManagedHttpClientConnection, Identifiable {

    private final Logger log = LogManager.getLogger(DefaultManagedHttpClientConnection.class);
    private final Logger headerlog = LogManager.getLogger("org.apache.hc.client5.http.headers");
    private final Logger wirelog = LogManager.getLogger("org.apache.hc.client5.http.wire");

    private final String id;
    private final AtomicBoolean closed;

    public DefaultManagedHttpClientConnection(
            final String id,
            final int buffersize,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final H1Config h1Config,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        super(buffersize, chardecoder, charencoder, h1Config, incomingContentStrategy, outgoingContentStrategy,
                requestWriterFactory, responseParserFactory);
        this.id = id;
        this.closed = new AtomicBoolean();
    }

    public DefaultManagedHttpClientConnection(
            final String id,
            final int buffersize) {
        this(id, buffersize, null, null, null, null, null, null, null);
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void bind(final SocketHolder socketHolder) throws IOException {
        if (this.closed.get()) {
            final Socket socket = socketHolder.getSocket();
            socket.close(); // allow this to throw...
            // ...but if it doesn't, explicitly throw one ourselves.
            throw new InterruptedIOException("Connection already shutdown");
        }
        super.bind(socketHolder);
    }

    @Override
    public Socket getSocket() {
        final SocketHolder socketHolder = getSocketHolder();
        return socketHolder != null ? socketHolder.getSocket() : null;
    }

    @Override
    public SSLSession getSSLSession() {
        final Socket socket = getSocket();
        if (socket instanceof SSLSocket) {
            return ((SSLSocket) socket).getSession();
        } else {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed.compareAndSet(false, true)) {
            if (this.log.isDebugEnabled()) {
                this.log.debug(this.id + ": Close connection");
            }
            super.close();
        }
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + ": set socket timeout to " + timeout);
        }
        super.setSocketTimeout(timeout);
    }

    @Override
    public void shutdown() throws IOException {
        if (this.closed.compareAndSet(false, true)) {
            if (this.log.isDebugEnabled()) {
                this.log.debug(this.id + ": Shutdown connection");
            }
            super.shutdown();
        }
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        super.bind(this.wirelog.isDebugEnabled() ? new LoggingSocketHolder(socket, this.id, this.wirelog) : new SocketHolder(socket));
    }

    @Override
    protected void onResponseReceived(final ClassicHttpResponse response) {
        if (response != null && this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(this.id + " << " + new StatusLine(response));
            final Header[] headers = response.getAllHeaders();
            for (final Header header : headers) {
                this.headerlog.debug(this.id + " << " + header.toString());
            }
        }
    }

    @Override
    protected void onRequestSubmitted(final ClassicHttpRequest request) {
        if (request != null && this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(this.id + " >> " + new RequestLine(request));
            final Header[] headers = request.getAllHeaders();
            for (final Header header : headers) {
                this.headerlog.debug(this.id + " >> " + header.toString());
            }
        }
    }

}
