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

import org.apache.hc.client5.http.impl.Wire;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

class LoggingIOSession implements IOSession {

    private final Logger log;
    private final Wire wireLog;
    private final String id;
    private final IOSession session;

    public LoggingIOSession(final IOSession session, final Logger log, final Logger wireLog) {
        super();
        this.session = session;
        this.id = session.getId();
        this.log = log;
        this.wireLog = new Wire(wireLog, this.id);
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
        if (log.isDebugEnabled()) {
            log.debug("{} Enqueued {} with priority {}", this.session, command.getClass().getSimpleName(), priority);
        }
    }

    @Override
    public ByteChannel channel() {
        return this.session.channel();
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
        if (log.isDebugEnabled()) {
            log.debug("{} {}: Event mask set {}", this.id, this.session, formatOps(ops));
        }
    }

    @Override
    public void setEvent(final int op) {
        this.session.setEvent(op);
        if (log.isDebugEnabled()) {
            log.debug("{} {}: Event set {}", this.id, this.session, formatOps(op));
        }
    }

    @Override
    public void clearEvent(final int op) {
        this.session.clearEvent(op);
        if (log.isDebugEnabled()) {
            log.debug("{} {}: Event cleared {}", this.id, this.session, formatOps(op));
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() {
        if (log.isDebugEnabled()) {
            log.debug("{} {}: Close", this.id, this.session);
        }
        this.session.close();
    }

    @Override
    public Status getStatus() {
        return this.session.getStatus();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (log.isDebugEnabled()) {
            log.debug("{} {}: Close {}", this.id, this.session, closeMode);
        }
        this.session.close(closeMode);
    }

    @Override
    public Timeout getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        if (log.isDebugEnabled()) {
            log.debug("{} {}: Set timeout {}", this.id, this.session, timeout);
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
    public long getLastEventTime() {
        return this.session.getLastEventTime();
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
    public int read(final ByteBuffer dst) throws IOException {
        final int bytesRead = this.session.channel().read(dst);
        if (log.isDebugEnabled()) {
            log.debug("{} {}: {} bytes read", this.id, this.session, bytesRead);
        }
        if (bytesRead > 0 && this.wireLog.isEnabled()) {
            final ByteBuffer b = dst.duplicate();
            final int p = b.position();
            b.limit(p);
            b.position(p - bytesRead);
            this.wireLog.input(b);
        }
        return bytesRead;
    }


    @Override
    public int write(final ByteBuffer src) throws IOException {
        final int byteWritten = session.channel().write(src);
        if (log.isDebugEnabled()) {
            log.debug("{} {}: {} bytes written", this.id, this.session, byteWritten);
        }
        if (byteWritten > 0 && this.wireLog.isEnabled()) {
            final ByteBuffer b = src.duplicate();
            final int p = b.position();
            b.limit(p);
            b.position(p - byteWritten);
            this.wireLog.output(b);
        }
        return byteWritten;
    }

    @Override
    public String toString() {
        return this.id + " " + this.session;
    }

}
