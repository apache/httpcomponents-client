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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2OverH2TunnelSupport {

    @Test
    void testEstablishBuildsH2ConnectForAuthorityAndCompletes() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true);
        final HttpHost target = new HttpHost("https", "example.org", 443);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(session, target, Timeout.ofSeconds(1), false, null, callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNull(callback.failed);
        Assertions.assertFalse(callback.cancelled);
        Assertions.assertNotNull(session.capturedRequest);
        Assertions.assertEquals("CONNECT", session.capturedRequest.getMethod());
        Assertions.assertEquals("example.org", session.capturedRequest.getAuthority().getHostName());
        Assertions.assertEquals(443, session.capturedRequest.getAuthority().getPort());
        Assertions.assertNull(session.capturedRequest.getScheme());
        Assertions.assertNull(session.capturedRequest.getPath());
    }

    @Test
    void testEstablishAppliesConnectRequestInterceptor() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("https", "example.org", 443),
                Timeout.ofSeconds(1),
                false,
                null,
                (request, entityDetails, context) -> request.addHeader("Proxy-Authorization", "Basic dGVzdDp0ZXN0"),
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNotNull(session.capturedRequest);
        Assertions.assertEquals("Basic dGVzdDp0ZXN0", session.capturedRequest.getFirstHeader("Proxy-Authorization").getValue());
    }

    @Test
    void testEstablishFailsOnRefusedTunnel() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, false);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("https", "example.org", 443),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertFalse(callback.completed);
        Assertions.assertNotNull(callback.failed);
        Assertions.assertInstanceOf(TunnelRefusedException.class, callback.failed);
        Assertions.assertEquals(
                HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
                ((TunnelRefusedException) callback.failed).getStatusCode());
    }

    @Test
    void testEstablishFailsWhenConnectResponseHasNoTunnelStream() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, false);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("https", "example.org", 443),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertFalse(callback.completed);
        Assertions.assertNotNull(callback.failed);
    }

    @Test
    void testEstablishWithProtocolStarterInvokesConnectedAndSeesInputBuffer() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true, false, true, false);
        final RecordingProtocolStarter protocolStarter = new RecordingProtocolStarter();
        final RecordingCallback<IOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                protocolStarter,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNull(callback.failed);
        Assertions.assertTrue(protocolStarter.connectedCalled);
        Assertions.assertTrue(protocolStarter.inputBufferSeen);
    }

    @Test
    void testEstablishSecureTunnelUsesTlsStrategy() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true);
        final RecordingTlsStrategy tlsStrategy = new RecordingTlsStrategy();
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("https", "example.org", 443),
                Timeout.ofSeconds(1),
                true,
                tlsStrategy,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNull(callback.failed);
        Assertions.assertTrue(tlsStrategy.invoked);
    }

    @Test
    void testClosingTunnelDoesNotClosePhysicalSession() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNotNull(callback.result);
        callback.result.close(CloseMode.IMMEDIATE);
        Assertions.assertTrue(session.isOpen(), "Closing tunnel session must not close physical HTTP/2 connection");
    }

    @Test
    void testTunnelImmediateCloseCancelsStreamControlWhenPresent() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true, false, false, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNotNull(callback.result);
        callback.result.close(CloseMode.IMMEDIATE);

        Assertions.assertTrue(session.streamCancelCalled.get(), "IMMEDIATE close must cancel the CONNECT stream");
        Assertions.assertTrue(session.isOpen(), "Cancelling tunnel stream must not close physical HTTP/2 connection");
    }

    @Test
    void testTunnelWriteBufferIsBounded() throws Exception {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNotNull(callback.result);
        final int payloadSize = 1024 * 1024;
        final ByteBuffer src = ByteBuffer.allocate(payloadSize);
        final int written = callback.result.write(src);
        Assertions.assertTrue(written > 0);
        Assertions.assertTrue(written < payloadSize, "Outbound writes must be bounded by tunnel buffer capacity");
    }

    @Test
    void testCapacityUpdateIsNotUnbounded() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true, true, false, false);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertTrue(session.lastCapacityUpdate > 0, "Tunnel setup should publish initial bounded capacity");
        Assertions.assertTrue(session.lastCapacityUpdate < Integer.MAX_VALUE, "Capacity must not be unbounded");
    }

    @Test
    void testGracefulCloseEndsStreamAfterDrain() throws Exception {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNotNull(callback.result);

        final ByteBuffer src = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});
        final int written = callback.result.write(src);
        Assertions.assertEquals(5, written);

        callback.result.close(CloseMode.GRACEFUL);

        // Drive output flush: test runs in same package -> can call package-private method.
        ((H2TunnelProtocolIOSession) callback.result).onOutputReady();

        Assertions.assertTrue(session.endStreamCalled.get(), "GRACEFUL close must end the CONNECT stream after draining");
        Assertions.assertTrue(session.isOpen(), "Ending tunnel stream must not close physical HTTP/2 connection");
    }

    @Test
    void testStreamEndAfterEstablishedTunnelDoesNotFireFailure() {
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, true, false, false, false, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertTrue(callback.completed, "Tunnel should complete successfully");
        Assertions.assertNull(callback.failed, "streamEnd after established tunnel must not overwrite with failure");
    }

    @Test
    void testStreamEndBeforeTunnelEstablishedFiresFailure() {
        // 200 but no entity details -> consumeResponse throws -> fail() fires
        // then streamEnd arrives on the already-failed handler
        final ScriptedProxySession session = new ScriptedProxySession(HttpStatus.SC_OK, false, false, false, false, true);
        final RecordingCallback<ProtocolIOSession> callback = new RecordingCallback<>();

        H2OverH2TunnelSupport.establish(
                session,
                new HttpHost("http", "example.org", 80),
                Timeout.ofSeconds(1),
                false,
                null,
                callback);

        Assertions.assertFalse(callback.completed);
        Assertions.assertNotNull(callback.failed, "streamEnd with no tunnel must report failure");
    }

    static class RecordingCallback<T> implements FutureCallback<T> {

        volatile boolean completed;
        volatile boolean cancelled;
        volatile Exception failed;
        volatile T result;

        @Override
        public void completed(final T result) {
            this.completed = true;
            this.result = result;
        }

        @Override
        public void failed(final Exception ex) {
            this.failed = ex;
        }

        @Override
        public void cancelled() {
            this.cancelled = true;
        }
    }

    static class RecordingProtocolStarter implements IOEventHandlerFactory {

        volatile boolean connectedCalled;
        volatile boolean inputBufferSeen;

        @Override
        public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
            return new IOEventHandler() {

                @Override
                public void connected(final IOSession session) {
                    connectedCalled = true;
                }

                @Override
                public void inputReady(final IOSession session, final ByteBuffer src) {
                    if (src != null && src.hasRemaining()) {
                        inputBufferSeen = true;
                    }
                }

                @Override
                public void outputReady(final IOSession session) {
                }

                @Override
                public void timeout(final IOSession session, final Timeout timeout) {
                }

                @Override
                public void exception(final IOSession session, final Exception cause) {
                }

                @Override
                public void disconnected(final IOSession session) {
                }
            };
        }
    }

    static class RecordingTlsStrategy implements TlsStrategy {

        volatile boolean invoked;

        @Override
        public boolean upgrade(
                final TransportSecurityLayer sessionLayer,
                final HttpHost host,
                final SocketAddress localAddress,
                final SocketAddress remoteAddress,
                final Object attachment,
                final Timeout handshakeTimeout) {
            invoked = true;
            return true;
        }

        @Override
        public void upgrade(
                final TransportSecurityLayer sessionLayer,
                final NamedEndpoint endpoint,
                final Object attachment,
                final Timeout handshakeTimeout,
                final FutureCallback<TransportSecurityLayer> callback) {
            invoked = true;
            if (callback != null) {
                callback.completed(sessionLayer);
            }
        }
    }

    static class ScriptedProxySession implements IOSession {

        private final int responseCode;
        private final boolean withTunnelStream;
        private final boolean signalCapacity;
        private final boolean emitInputData;
        private final boolean provideStreamControl;
        private final boolean sendStreamEnd;

        private final Lock lock;
        private Timeout socketTimeout;

        HttpRequest capturedRequest;
        volatile int lastCapacityUpdate;

        private final AtomicBoolean open;
        final AtomicBoolean streamCancelCalled;
        final AtomicBoolean endStreamCalled;

        ScriptedProxySession(final int responseCode, final boolean withTunnelStream) {
            this(responseCode, withTunnelStream, false, false, false, false);
        }

        ScriptedProxySession(final int responseCode, final boolean withTunnelStream, final boolean signalCapacity) {
            this(responseCode, withTunnelStream, signalCapacity, false, false, false);
        }

        ScriptedProxySession(
                final int responseCode,
                final boolean withTunnelStream,
                final boolean signalCapacity,
                final boolean emitInputData,
                final boolean provideStreamControl) {
            this(responseCode, withTunnelStream, signalCapacity, emitInputData, provideStreamControl, false);
        }

        ScriptedProxySession(
                final int responseCode,
                final boolean withTunnelStream,
                final boolean signalCapacity,
                final boolean emitInputData,
                final boolean provideStreamControl,
                final boolean sendStreamEnd) {
            this.responseCode = responseCode;
            this.withTunnelStream = withTunnelStream;
            this.signalCapacity = signalCapacity;
            this.emitInputData = emitInputData;
            this.provideStreamControl = provideStreamControl;
            this.sendStreamEnd = sendStreamEnd;

            this.lock = new ReentrantLock();
            this.socketTimeout = Timeout.ofSeconds(30);
            this.lastCapacityUpdate = -1;
            this.open = new AtomicBoolean(true);
            this.streamCancelCalled = new AtomicBoolean(false);
            this.endStreamCalled = new AtomicBoolean(false);
        }

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
            if (!(command instanceof RequestExecutionCommand)) {
                return;
            }

            final RequestExecutionCommand requestExecutionCommand = (RequestExecutionCommand) command;
            final AsyncClientExchangeHandler exchangeHandler = requestExecutionCommand.getExchangeHandler();
            final org.apache.hc.core5.http.protocol.HttpContext context = requestExecutionCommand.getContext();

            try {
                if (provideStreamControl && exchangeHandler instanceof H2OverH2TunnelExchangeHandler) {
                    final StreamControl streamControl = (StreamControl) Proxy.newProxyInstance(
                            StreamControl.class.getClassLoader(),
                            new Class<?>[]{StreamControl.class},
                            (proxy, method, args) -> {
                                if ("cancel".equals(method.getName())) {
                                    streamCancelCalled.set(true);
                                    return method.getReturnType() == Boolean.TYPE ? Boolean.TRUE : null;
                                }
                                return defaultValue(method);
                            });

                    ((H2OverH2TunnelExchangeHandler) exchangeHandler).initiated(streamControl);
                }

                exchangeHandler.produceRequest((RequestChannel) (request, entityDetails, requestContext) -> capturedRequest = request, context);

                if (signalCapacity) {
                    exchangeHandler.updateCapacity((CapacityChannel) increment -> lastCapacityUpdate = increment);
                }

                final EntityDetails responseEntityDetails =
                        withTunnelStream ? new org.apache.hc.core5.http.impl.BasicEntityDetails(-1, null) : null;
                exchangeHandler.consumeResponse(new BasicHttpResponse(responseCode), responseEntityDetails, context);

                if (withTunnelStream && responseCode == HttpStatus.SC_OK) {
                    exchangeHandler.produce(new DataStreamChannel() {

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

                    });

                    if (emitInputData) {
                        exchangeHandler.consume(ByteBuffer.wrap(new byte[]{1, 2, 3}));
                    }
                }

                if (sendStreamEnd) {
                    exchangeHandler.streamEnd(null);
                }
            } catch (final Exception ex) {
                exchangeHandler.failed(ex);
            }
        }

        private static Object defaultValue(final Method method) {
            final Class<?> rt = method.getReturnType();
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
            open.set(false);
        }

        @Override
        public void close(final CloseMode closeMode) {
            open.set(false);
        }

        @Override
        public Status getStatus() {
            return open.get() ? Status.ACTIVE : Status.CLOSED;
        }

        @Override
        public Timeout getSocketTimeout() {
            return socketTimeout;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            this.socketTimeout = timeout;
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
            if (remaining > 0) {
                final byte[] tmp = new byte[remaining];
                src.get(tmp);
            }
            return remaining;
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public String getId() {
            return "proxy-session";
        }
    }
}
