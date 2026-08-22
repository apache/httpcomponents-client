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
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.CancelledKeyException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2TunnelRawIOSession {

    @Test
    void testCapacityInitialUpdateIsBounded() throws Exception {
        final DummyPhysicalSession physical = new DummyPhysicalSession();
        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), null);

        final AtomicInteger last = new AtomicInteger(0);
        raw.updateCapacityChannel(new CapacityChannel() {
            @Override
            public void update(final int increment) {
                last.set(increment);
            }
        });

        Assertions.assertTrue(last.get() > 0);
        Assertions.assertTrue(last.get() < Integer.MAX_VALUE);
    }

    @Test
    void testAppendInputOverflowFails() throws Exception {
        final DummyPhysicalSession physical = new DummyPhysicalSession();
        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), null);

        // INBOUND_BUFFER_LIMIT is 64k in the implementation; overflow by 1.
        final ByteBuffer tooBig = ByteBuffer.allocate(64 * 1024 + 1);
        Assertions.assertThrows(IOException.class, () -> raw.appendInput(tooBig));
    }

    @Test
    void testReadTriggersCapacityUpdateOnConsumption() throws Exception {
        final DummyPhysicalSession physical = new DummyPhysicalSession();
        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), null);

        final AtomicInteger last = new AtomicInteger(-1);
        raw.updateCapacityChannel(new CapacityChannel() {
            @Override
            public void update(final int increment) {
                last.set(increment);
            }
        });

        raw.appendInput(ByteBuffer.wrap(new byte[]{1, 2, 3, 4}));

        final ByteBuffer dst = ByteBuffer.allocate(4);
        final int n = raw.read(dst);
        Assertions.assertEquals(4, n);
        // Capacity update should publish the consumed bytes (4), not unbounded.
        Assertions.assertEquals(4, last.get());
    }

    @Test
    void testWriteIsBounded() {
        final DummyPhysicalSession physical = new DummyPhysicalSession();
        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), null);

        final ByteBuffer src = ByteBuffer.allocate(1024 * 1024);
        final int n = raw.write(src);
        Assertions.assertTrue(n > 0);
        Assertions.assertTrue(n < 1024 * 1024);
        Assertions.assertEquals(64 * 1024, n, "Expected OUTBOUND_BUFFER_LIMIT (64k) write bound");
    }

    @Test
    void testImmediateCloseCancelsStreamControlButNotPhysicalSession() {
        final DummyPhysicalSession physical = new DummyPhysicalSession();

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final StreamControl streamControl = (StreamControl) Proxy.newProxyInstance(
                StreamControl.class.getClassLoader(),
                new Class<?>[]{StreamControl.class},
                (proxy, method, args) -> {
                    final String name = method.getName();
                    final Class<?> rt = method.getReturnType();

                    if ("cancel".equals(name)) {
                        cancelled.set(true);
                        // IMPORTANT: cancel() may return boolean (Cancellable)
                        return rt == Boolean.TYPE ? Boolean.TRUE : null;
                    }

                    if (rt == Void.TYPE) {
                        return null;
                    }
                    if (rt == Boolean.TYPE) {
                        return false;
                    }
                    if (rt == Integer.TYPE) {
                        return 0;
                    }
                    if (rt == Long.TYPE) {
                        return 0L;
                    }
                    if (rt == Short.TYPE) {
                        return (short) 0;
                    }
                    if (rt == Byte.TYPE) {
                        return (byte) 0;
                    }
                    if (rt == Character.TYPE) {
                        return (char) 0;
                    }
                    if (rt == Float.TYPE) {
                        return 0f;
                    }
                    if (rt == Double.TYPE) {
                        return 0d;
                    }
                    return null;
                });

        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), streamControl);
        raw.close(CloseMode.IMMEDIATE);

        Assertions.assertTrue(cancelled.get(), "IMMEDIATE close must cancel stream");
        Assertions.assertTrue(physical.isOpen(), "Tunnel close must not close physical HTTP/2 connection");
    }

    @Test
    void testGracefulCloseEndsStreamAfterDrain() throws Exception {
        final DummyPhysicalSession physical = new DummyPhysicalSession();

        final AtomicBoolean endStreamCalled = new AtomicBoolean(false);
        rawAttachChannel(physical, endStreamCalled);

        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), null);
        raw.attachChannel(physical.dataChannel);

        // Put some outbound bytes
        final ByteBuffer src = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});
        final int written = raw.write(src);
        Assertions.assertEquals(5, written);

        raw.close(CloseMode.GRACEFUL);
        raw.flushOutput();

        Assertions.assertTrue(endStreamCalled.get(), "GRACEFUL close must endStream once outbound drained");
    }

    @Test
    void testRequestOutputOnCancelledKeyClosesTunnel() {
        final DummyPhysicalSession physical = new DummyPhysicalSession();
        final H2TunnelRawIOSession raw = new H2TunnelRawIOSession(physical, Timeout.ofSeconds(5), null);
        raw.attachChannel(new DataStreamChannel() {

            @Override
            public void requestOutput() {
                throw new CancelledKeyException();
            }

            @Override
            public int write(final ByteBuffer src) {
                return 0;
            }

            @Override
            public void endStream() {
            }

            @Override
            public void endStream(final java.util.List<? extends org.apache.hc.core5.http.Header> trailers) {
            }
        });

        raw.enqueue(ShutdownCommand.GRACEFUL, Command.Priority.NORMAL);

        Assertions.assertEquals(IOSession.Status.CLOSED, raw.getStatus());
    }

    private static void rawAttachChannel(final DummyPhysicalSession physical, final AtomicBoolean endStreamCalled) {
        physical.dataChannel = new DataStreamChannel() {

            @Override
            public void requestOutput() {
            }

            @Override
            public int write(final ByteBuffer src) {
                final int remaining = src != null ? src.remaining() : 0;
                if (remaining > 0 && src != null) {
                    final byte[] tmp = new byte[remaining];
                    src.get(tmp);
                }
                return remaining;
            }

            @Override
            public void endStream() throws IOException {

            }

            @Override
            public void endStream(final java.util.List<? extends org.apache.hc.core5.http.Header> trailers) {
                endStreamCalled.set(true);
            }
        };
    }

    static final class DummyPhysicalSession implements IOSession {

        private final Lock lock = new ReentrantLock();
        private volatile boolean open = true;
        private volatile Timeout socketTimeout = Timeout.ofSeconds(30);

        volatile DataStreamChannel dataChannel;

        @Override
        public IOEventHandler getHandler() {
            return null;
        }

        @Override
        public void upgrade(final IOEventHandler handler) {
        }

        @Override
        public Lock getLock() {
            return lock;
        }

        @Override
        public void enqueue(final Command command, final Command.Priority priority) {
        }

        @Override
        public boolean hasCommands() {
            return false;
        }

        @Override
        public Command poll() {
            return null;
        }

        @Override
        public ByteChannel channel() {
            return this;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public int getEventMask() {
            return 0;
        }

        @Override
        public void setEventMask(final int ops) {
        }

        @Override
        public void setEvent(final int op) {
        }

        @Override
        public void clearEvent(final int op) {
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void close(final CloseMode closeMode) {
            open = false;
        }

        @Override
        public Status getStatus() {
            return open ? Status.ACTIVE : Status.CLOSED;
        }

        @Override
        public Timeout getSocketTimeout() {
            return socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            socketTimeout = timeout;
        }

        @Override
        public long getLastReadTime() {
            return 0;
        }

        @Override
        public long getLastWriteTime() {
            return 0;
        }

        @Override
        public long getLastEventTime() {
            return 0;
        }

        @Override
        public void updateReadTime() {
        }

        @Override
        public void updateWriteTime() {
        }

        @Override
        public int read(final ByteBuffer dst) {
            return 0;
        }

        @Override
        public int write(final ByteBuffer src) {
            final int remaining = src != null ? src.remaining() : 0;
            if (remaining > 0 && src != null) {
                final byte[] tmp = new byte[remaining];
                src.get(tmp);
            }
            return remaining;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public String getId() {
            return "dummy-physical";
        }
    }
}
