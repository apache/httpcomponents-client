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

package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http2.impl.nio.ClientHttp2IOEventHandler;
import org.apache.hc.core5.http2.impl.nio.ClientHttp2StreamMultiplexer;
import org.apache.hc.core5.http2.impl.nio.ClientHttp2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.ssl.ApplicationProtocols;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.TextUtils;

/**
 * TODO: replace with Http2OnlyClientProtocolNegotiator after HttpCore 5.0b2
 */
final class InternalHttp2ClientProtocolNegotiator implements HttpConnectionEventHandler {

    // PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
    final static byte[] PREFACE = new byte[] {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50,
            0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d,
            0x0d, 0x0a, 0x0d, 0x0a};

    private final TlsCapableIOSession ioSession;
    private final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory;

    private final ByteBuffer preface;

    public InternalHttp2ClientProtocolNegotiator(
            final TlsCapableIOSession ioSession,
            final ClientHttp2StreamMultiplexerFactory http2StreamHandlerFactory) {
        this.ioSession = ioSession;
        this.http2StreamHandlerFactory = http2StreamHandlerFactory;
        this.preface = ByteBuffer.wrap(PREFACE);
    }

    @Override
    public void connected(final IOSession session) {
        try {
            final TlsDetails tlsDetails = ioSession.getTlsDetails();
            if (tlsDetails != null) {
                final String applicationProtocol = tlsDetails.getApplicationProtocol();
                if (!TextUtils.isEmpty(applicationProtocol)) {
                    if (!ApplicationProtocols.HTTP_2.id.equals(applicationProtocol)) {
                        throw new HttpException("Unexpected application protocol: " + applicationProtocol);
                    }
                }
            }
            writePreface(session);
        } catch (final Exception ex) {
            session.shutdown(ShutdownType.IMMEDIATE);
            exception(session, ex);
        }
    }

    private void writePreface(final IOSession session) throws IOException  {
        if (preface.hasRemaining()) {
            final ByteChannel channel = session.channel();
            channel.write(preface);
        }
        if (!preface.hasRemaining()) {
            final ClientHttp2StreamMultiplexer streamMultiplexer = http2StreamHandlerFactory.create(ioSession);
            final IOEventHandler newHandler = new ClientHttp2IOEventHandler(streamMultiplexer);
            newHandler.connected(session);
            session.setHandler(newHandler);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        outputReady(session);
    }

    @Override
    public void outputReady(final IOSession session) {
        try {
            if (preface != null) {
                writePreface(session);
            } else {
                session.shutdown(ShutdownType.IMMEDIATE);
            }
        } catch (final IOException ex) {
            session.shutdown(ShutdownType.IMMEDIATE);
            exception(session, ex);
        }
    }

    @Override
    public void timeout(final IOSession session) {
        exception(session, new SocketTimeoutException());
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        try {
            for (;;) {
                final Command command = ioSession.getCommand();
                if (command != null) {
                    if (command instanceof ExecutionCommand) {
                        final ExecutionCommand executionCommand = (ExecutionCommand) command;
                        final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                        exchangeHandler.failed(cause);
                        exchangeHandler.releaseResources();
                    } else {
                        command.cancel();
                    }
                } else {
                    break;
                }
            }
        } finally {
            session.shutdown(ShutdownType.IMMEDIATE);
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        for (;;) {
            final Command command = ioSession.getCommand();
            if (command != null) {
                if (command instanceof ExecutionCommand) {
                    final ExecutionCommand executionCommand = (ExecutionCommand) command;
                    final AsyncClientExchangeHandler exchangeHandler = executionCommand.getExchangeHandler();
                    exchangeHandler.failed(new ConnectionClosedException("Connection closed"));
                    exchangeHandler.releaseResources();
                } else {
                    command.cancel();
                }
            } else {
                break;
            }
        }
    }

    @Override
    public SSLSession getSSLSession() {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        return tlsDetails != null ? tlsDetails.getSSLSession() : null;
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return null;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ioSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return ioSession.getLocalAddress();
    }

    @Override
    public boolean isOpen() {
        return !ioSession.isClosed();
    }

    @Override
    public void close() throws IOException {
        ioSession.close();
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        ioSession.shutdown(shutdownType);
    }

}
