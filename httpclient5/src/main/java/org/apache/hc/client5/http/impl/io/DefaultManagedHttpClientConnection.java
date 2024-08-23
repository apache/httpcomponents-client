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

import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.impl.io.SocketHolder;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultManagedHttpClientConnection
        extends DefaultBHttpClientConnection implements ManagedHttpClientConnection, Identifiable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagedHttpClientConnection.class);
    private static final Logger HEADER_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http.headers");
    private static final Logger WIRE_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http.wire");

    private final String id;
    private final AtomicBoolean closed;

    private Timeout socketTimeout;

    public DefaultManagedHttpClientConnection(
            final String id,
            final CharsetDecoder charDecoder,
            final CharsetEncoder charEncoder,
            final Http1Config h1Config,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final ResponseOutOfOrderStrategy responseOutOfOrderStrategy,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        super(
                h1Config,
                charDecoder,
                charEncoder,
                incomingContentStrategy,
                outgoingContentStrategy,
                responseOutOfOrderStrategy,
                requestWriterFactory,
                responseParserFactory);
        this.id = id;
        this.closed = new AtomicBoolean();
    }

    public DefaultManagedHttpClientConnection(
            final String id,
            final CharsetDecoder charDecoder,
            final CharsetEncoder charEncoder,
            final Http1Config h1Config,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        this(
                id,
                charDecoder,
                charEncoder,
                h1Config,
                incomingContentStrategy,
                outgoingContentStrategy,
                null,
                requestWriterFactory,
                responseParserFactory);
    }

    public DefaultManagedHttpClientConnection(final String id) {
        this(id, null, null, null, null, null, null, null);
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
        socketTimeout = Timeout.ofMilliseconds(socketHolder.getSocket().getSoTimeout());
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Close connection", this.id);
            }
            super.close();
        }
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} set socket timeout to {}", this.id, timeout);
        }
        super.setSocketTimeout(timeout);
        socketTimeout = timeout;
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.closed.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} close connection {}", this.id, closeMode);
            }
            super.close(closeMode);
        }
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        super.bind(WIRE_LOG.isDebugEnabled() ? new LoggingSocketHolder(socket, this.id, WIRE_LOG) : new SocketHolder(socket));
        socketTimeout = Timeout.ofMilliseconds(socket.getSoTimeout());
    }

    @Override
    public void bind(final SSLSocket sslSocket, final Socket socket) throws IOException {
        super.bind(WIRE_LOG.isDebugEnabled() ?
                new LoggingSocketHolder(sslSocket, socket, this.id, WIRE_LOG) :
                new SocketHolder(sslSocket, socket));
        socketTimeout = Timeout.ofMilliseconds(sslSocket.getSoTimeout());
    }

    @Override
    protected void onResponseReceived(final ClassicHttpResponse response) {
        if (response != null && HEADER_LOG.isDebugEnabled()) {
            HEADER_LOG.debug("{} << {}", this.id, new StatusLine(response));
            final Header[] headers = response.getHeaders();
            for (final Header header : headers) {
                HEADER_LOG.debug("{} << {}", this.id, header);
            }
        }
    }

    @Override
    protected void onRequestSubmitted(final ClassicHttpRequest request) {
        if (request != null && HEADER_LOG.isDebugEnabled()) {
            HEADER_LOG.debug("{} >> {}", this.id, new RequestLine(request));
            final Header[] headers = request.getHeaders();
            for (final Header header : headers) {
                HEADER_LOG.debug("{} >> {}", this.id, header);
            }
        }
    }

    @Override
    public void passivate() {
        super.setSocketTimeout(Timeout.ZERO_MILLISECONDS);
    }

    @Override
    public void activate() {
        super.setSocketTimeout(socketTimeout);
    }

}
