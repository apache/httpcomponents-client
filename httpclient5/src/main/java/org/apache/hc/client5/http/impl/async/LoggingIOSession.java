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

import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class LoggingIOSession implements IOSession {

    private final Logger log;
    private final Logger wireLog;
    private final IOSession session;

    public LoggingIOSession(final IOSession session, final Logger log, final Logger wireLog) {
        super();
        this.session = session;
        this.log = log;
        this.wireLog = wireLog;
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public Lock getLock() {
        return session.getLock();
    }

    @Override
    public boolean hasCommands() {
        return session.hasCommands();
    }

    @Override
    public Command poll() {
        return session.poll();
    }

    @Override
    public void enqueue(final Command command, final Command.Priority priority) {
        session.enqueue(command, priority);
        if (log.isDebugEnabled()) {
            log.debug("{} Enqueued {} with priority {}", session, command.getClass().getSimpleName(), priority);
        }
    }

    @Override
    public ByteChannel channel() {
        return session.channel();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return session.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return session.getRemoteAddress();
    }

    @Override
    public int getEventMask() {
        return session.getEventMask();
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
        session.setEventMask(ops);
        if (log.isDebugEnabled()) {
            log.debug("{} Event mask set {}", session, formatOps(ops));
        }
    }

    @Override
    public void setEvent(final int op) {
        session.setEvent(op);
        if (log.isDebugEnabled()) {
            log.debug("{} Event set {}", session, formatOps(op));
        }
    }

    @Override
    public void clearEvent(final int op) {
        session.clearEvent(op);
        if (log.isDebugEnabled()) {
            log.debug("{} Event cleared {}", session, formatOps(op));
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() {
        if (log.isDebugEnabled()) {
            log.debug("{} Close", session);
        }
        session.close();
    }

    @Override
    public Status getStatus() {
        return session.getStatus();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (log.isDebugEnabled()) {
            log.debug("{} Close {}", session, closeMode);
        }
        session.close(closeMode);
    }

    @Override
    public Timeout getSocketTimeout() {
        return session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        if (log.isDebugEnabled()) {
            log.debug("{} Set timeout {}", session, timeout);
        }
        session.setSocketTimeout(timeout);
    }

    @Override
    public long getLastReadTime() {
        return session.getLastReadTime();
    }

    @Override
    public long getLastWriteTime() {
        return session.getLastWriteTime();
    }

    @Override
    public void updateReadTime() {
        session.updateReadTime();
    }

    @Override
    public void updateWriteTime() {
        session.updateWriteTime();
    }

    @Override
    public long getLastEventTime() {
        return session.getLastEventTime();
    }

    @Override
    public IOEventHandler getHandler() {
        return session.getHandler();
    }

    @Override
    public void upgrade(final IOEventHandler handler) {
        Args.notNull(handler, "Protocol handler");
        if (log.isDebugEnabled()) {
            log.debug("{} protocol upgrade {}", session, handler.getClass());
        }
        session.upgrade(new IOEventHandler() {

            @Override
            public void connected(final IOSession protocolSession) throws IOException {
                handler.connected(protocolSession);
            }

            @Override
            public void inputReady(final IOSession protocolSession, final ByteBuffer src) throws IOException {
                if (src != null && wireLog.isDebugEnabled()) {
                    final ByteBuffer b = src.duplicate();
                    logData(b, "<< ");
                }
                handler.inputReady(protocolSession, src);
            }

            @Override
            public void outputReady(final IOSession protocolSession) throws IOException {
                handler.outputReady(protocolSession);
            }

            @Override
            public void timeout(final IOSession protocolSession, final Timeout timeout) throws IOException {
                handler.timeout(protocolSession, timeout);
            }

            @Override
            public void exception(final IOSession protocolSession, final Exception cause) {
                handler.exception(protocolSession, cause);
            }

            @Override
            public void disconnected(final IOSession protocolSession) {
                handler.disconnected(protocolSession);
            }

        });

    }

    private void logData(final ByteBuffer data, final String prefix) throws IOException {
        final byte[] line = new byte[16];
        final StringBuilder buf = new StringBuilder();
        while (data.hasRemaining()) {
            buf.setLength(0);
            buf.append(session).append(" ").append(prefix);
            final int chunk = Math.min(data.remaining(), line.length);
            data.get(line, 0, chunk);

            for (int i = 0; i < chunk; i++) {
                final char ch = (char) line[i];
                if (ch > Chars.SP && ch <= Chars.DEL) {
                    buf.append(ch);
                } else if (Character.isWhitespace(ch)) {
                    buf.append(' ');
                } else {
                    buf.append('.');
                }
            }
            for (int i = chunk; i < 17; i++) {
                buf.append(' ');
            }

            for (int i = 0; i < chunk; i++) {
                buf.append(' ');
                final int b = line[i] & 0xff;
                final String s = Integer.toHexString(b);
                if (s.length() == 1) {
                    buf.append("0");
                }
                buf.append(s);
            }
            wireLog.debug(buf.toString());
        }
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        final int bytesRead = session.read(dst);
        if (log.isDebugEnabled()) {
            log.debug("{} {} bytes read", session, bytesRead);
        }
        if (bytesRead > 0 && wireLog.isDebugEnabled()) {
            final ByteBuffer b = dst.duplicate();
            final int p = b.position();
            b.limit(p);
            b.position(p - bytesRead);
            logData(b, "<< ");
        }
        return bytesRead;
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        final int byteWritten = session.write(src);
        if (log.isDebugEnabled()) {
            log.debug("{} {} bytes written", session, byteWritten);
        }
        if (byteWritten > 0 && wireLog.isDebugEnabled()) {
            final ByteBuffer b = src.duplicate();
            final int p = b.position();
            b.limit(p);
            b.position(p - byteWritten);
            logData(b, ">> ");
        }
        return byteWritten;
    }

    @Override
    public String toString() {
        return session.toString();
    }

}
