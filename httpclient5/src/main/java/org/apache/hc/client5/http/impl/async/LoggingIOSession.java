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
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.locks.Lock;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.Wire;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class LoggingIOSession implements ProtocolIOSession {

    private final Logger log;
    private final Wire wireLog;
    private final String id;
    private final ProtocolIOSession session;
    private final ByteChannel channel;

    public LoggingIOSession(final ProtocolIOSession session, final String id, final Logger log, final Logger wireLog) {
        super();
        this.session = session;
        this.id = id;
        this.log = log;
        this.wireLog = new Wire(wireLog, this.id);
        this.channel = new LoggingByteChannel();
    }

    public LoggingIOSession(final ProtocolIOSession session, final String id, final Logger log) {
        this(session, id, log, null);
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public Lock getLock() {
        return this.session.getLock();
    }

    @Override
    public Lock lock() {
        return this.session.lock();
    }

    @Override
    public boolean hasCommands() {
        return this.session.hasCommands();
    }

    @Override
    public Command poll() {
        return this.session.poll();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        this.session.enqueue(command, priority);
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
    public void close(final CloseMode closeMode) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Close " + closeMode);
        }
        this.session.close(closeMode);
    }

    @Override
    public Timeout getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + " " + this.session + ": Set timeout " + timeout);
        }
        this.session.setSocketTimeout(timeout);
    }

    @Override
    public long getLastReadTime() {
        return this.session.getLastReadTime();
    }

    @Override
    public long getLastWriteTime() {
        return this.session.getLastWriteTime();
    }

    @Override
    public void updateReadTime() {
        this.session.updateReadTime();
    }

    @Override
    public void updateWriteTime() {
        this.session.updateWriteTime();
    }

    @Override
    public IOEventHandler getHandler() {
        return this.session.getHandler();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        this.session.upgrade(handler);
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final NamedEndpoint endpoint,
            final SSLBufferMode sslBufferMode,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier,
            final Timeout handshakeTimeout) throws UnsupportedOperationException {
        session.startTls(sslContext, endpoint, sslBufferMode, initializer, verifier, handshakeTimeout);
    }

    @Override
    public TlsDetails getTlsDetails() {
        return session.getTlsDetails();
    }

    @Override
    public NamedEndpoint getInitialEndpoint() {
        return session.getInitialEndpoint();
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
            if (bytesRead > 0 && wireLog.isEnabled()) {
                final ByteBuffer b = dst.duplicate();
                final int p = b.position();
                b.limit(p);
                b.position(p - bytesRead);
                wireLog.input(b);
            }
            return bytesRead;
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int byteWritten = session.channel().write(src);
            if (log.isDebugEnabled()) {
                log.debug(id + " " + session + ": " + byteWritten + " bytes written");
            }
            if (byteWritten > 0 && wireLog.isEnabled()) {
                final ByteBuffer b = src.duplicate();
                final int p = b.position();
                b.limit(p);
                b.position(p - byteWritten);
                wireLog.output(b);
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
