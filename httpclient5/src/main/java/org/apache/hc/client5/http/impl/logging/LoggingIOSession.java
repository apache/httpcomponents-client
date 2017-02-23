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

package org.apache.hc.client5.http.impl.logging;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferManagement;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.logging.log4j.Logger;

public class LoggingIOSession implements IOSession, TransportSecurityLayer {

    private final Logger log;
    private final Wire wirelog;
    private final String id;
    private final IOSession session;
    private final ByteChannel channel;

    public LoggingIOSession(final IOSession session, final String id, final Logger log, final Logger wirelog) {
        super();
        this.session = session;
        this.id = id;
        this.log = log;
        this.wirelog = new Wire(wirelog, this.id);
        this.channel = new LoggingByteChannel();
    }

    public LoggingIOSession(final IOSession session, final String id, final Logger log) {
        this(session, id, log, null);
    }

    @Override
    public void addLast(final Command command) {
        this.session.addLast(command);
    }

    @Override
    public void addFirst(final Command command) {
        this.session.addFirst(command);
    }

    @Override
    public Command getCommand() {
        return this.session.getCommand();
    }

    @Override
    public ByteChannel channel() {
        return this.channel;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return this.session.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.session.getRemoteAddress();
    }

    @Override
    public int getEventMask() {
        return this.session.getEventMask();
    }

    private static String formatOps(final int ops) {
        final StringBuilder buffer = new StringBuilder(6);
        buffer.append('[');
        if ((ops & SelectionKey.OP_READ) > 0) {
            buffer.append('r');
        }
        if ((ops & SelectionKey.OP_WRITE) > 0) {
            buffer.append('w');
        }
        if ((ops & SelectionKey.OP_ACCEPT) > 0) {
            buffer.append('a');
        }
        if ((ops & SelectionKey.OP_CONNECT) > 0) {
            buffer.append('c');
        }
        buffer.append(']');
        return buffer.toString();
    }

    @Override
    public void setEventMask(final int ops) {
        this.session.setEventMask(ops);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Event mask set " + formatOps(ops));
        }
    }

    @Override
    public void setEvent(final int op) {
        this.session.setEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Event set " + formatOps(op));
        }
    }

    @Override
    public void clearEvent(final int op) {
        this.session.clearEvent(op);
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Event cleared " + formatOps(op));
        }
    }

    @Override
    public void close() {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Close");
        }
        this.session.close();
    }

    @Override
    public int getStatus() {
        return this.session.getStatus();
    }

    @Override
    public boolean isClosed() {
        return this.session.isClosed();
    }

    @Override
    public void shutdown() {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Shutdown");
        }
        this.session.shutdown();
    }

    @Override
    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Set timeout " + timeout);
        }
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public IOEventHandler getHandler() {
        return this.session.getHandler();
    }

    @Override
    public void setHandler(final IOEventHandler handler) {
        this.session.setHandler(handler);
    }

    @Override
    public void start(
            final SSLContext sslContext,
            final SSLBufferManagement sslBufferManagement,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) throws UnsupportedOperationException {
        if (session instanceof TransportSecurityLayer) {
            ((TransportSecurityLayer) session).start(sslContext, sslBufferManagement, initializer, verifier);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public SSLSession getSSLSession() {
        if (session instanceof TransportSecurityLayer) {
            return ((TransportSecurityLayer) session).getSSLSession();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.id + " " + this.session.toString();
    }

    class LoggingByteChannel implements ByteChannel {

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            final int bytesRead = session.channel().read(dst);
            if (log.isDebugEnabled()) {
                log.debug(id + " " + session + ": " + bytesRead + " bytes read");
            }
            if (bytesRead > 0 && wirelog.isEnabled()) {
                final ByteBuffer b = dst.duplicate();
                final int p = b.position();
                b.limit(p);
                b.position(p - bytesRead);
                wirelog.input(b);
            }
            return bytesRead;
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int byteWritten = session.channel().write(src);
            if (log.isDebugEnabled()) {
                log.debug(id + " " + session + ": " + byteWritten + " bytes written");
            }
            if (byteWritten > 0 && wirelog.isEnabled()) {
                final ByteBuffer b = src.duplicate();
                final int p = b.position();
                b.limit(p);
                b.position(p - byteWritten);
                wirelog.output(b);
            }
            return byteWritten;
        }

        @Override
        public void close() throws IOException {
            if (log.isDebugEnabled()) {
                log.debug(id + " " + session + ": Channel close");
            }
            session.channel().close();
        }

        @Override
        public boolean isOpen() {
            return session.channel().isOpen();
        }

    }

}
