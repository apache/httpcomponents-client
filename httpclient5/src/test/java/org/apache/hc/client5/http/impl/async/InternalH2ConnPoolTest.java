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

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.nio.AsyncPingHandler;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalH2ConnPoolTest {

    @Mock
    Clock clock;
    @Mock(strictness = Mock.Strictness.LENIENT)
    ConnectionInitiator connectionInitiator;
    @Mock(strictness = Mock.Strictness.LENIENT)
    TlsStrategy tlsStrategy;
    @Mock(strictness = Mock.Strictness.LENIENT)
    Resolver<HttpHost, InetSocketAddress> addressResolver;
    @Mock(extraInterfaces = TransportSecurityLayer.class)
    IOSession ioSession;
    @Mock(extraInterfaces = HttpConnection.class)
    IOEventHandler ioEventHandler;
    @InjectMocks
    InternalH2ConnPool.SessionPool sessionPool;

    @BeforeEach
    void setup() {
        Mockito.doAnswer(invocation -> {
            final FutureCallback<IOSession> callback = invocation.getArgument(5);
            if (callback != null) {
                callback.completed(ioSession);
            }
            return null;
        }).when(connectionInitiator).connect(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            final FutureCallback<IOSession> callback = invocation.getArgument(4);
            if (callback != null) {
                callback.completed(ioSession);
            }
            return null;
        }).when(tlsStrategy).upgrade(
                Mockito.any(TransportSecurityLayer.class), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            final HttpHost host = invocation.getArgument(0);
            return InetSocketAddress.createUnresolved(host.getHostName(), host.getPort());
        }).when(addressResolver).resolve(Mockito.any());
    }

    @Test
    void testConnectHttp() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        final AtomicReference<IOSession> resultRef = new AtomicReference<>();
        sessionPool.connectSession(route, Timeout.ofSeconds(45), new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                resultRef.set(ioSession);
            }

            @Override
            public void failed(final Exception e) {
            }

            @Override
            public void cancelled() {
            }

        });

        Assertions.assertSame(ioSession, resultRef.get());
        Mockito.verify(connectionInitiator).connect(
                Mockito.eq(target),
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(45)),
                Mockito.isNull(),
                Mockito.any());
        Mockito.verify(ioSession, Mockito.never()).setSocketTimeout(Mockito.any());
        Mockito.verifyNoInteractions(tlsStrategy);
    }

    @Test
    void testConnectHttps() {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final HttpRoute route = new HttpRoute(target);
        final AtomicReference<IOSession> resultRef = new AtomicReference<>();
        sessionPool.connectSession(route, Timeout.ofSeconds(45), new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                resultRef.set(ioSession);
            }

            @Override
            public void failed(final Exception e) {
            }

            @Override
            public void cancelled() {
            }

        });

        Assertions.assertSame(ioSession, resultRef.get());
        Mockito.verify(connectionInitiator).connect(
                Mockito.eq(target),
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 443)),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(45)),
                Mockito.isNull(),
                Mockito.any());
        Mockito.verify(ioSession).setSocketTimeout(Mockito.eq(Timeout.ofSeconds(45)));
        Mockito.verify(tlsStrategy).upgrade(
                Mockito.same((TransportSecurityLayer) ioSession),
                Mockito.eq(target),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(45)),
                Mockito.any());
    }

    @Test
    void testConnectHttpConnectionConfig() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(34))
                .setSocketTimeout(Timeout.ofSeconds(56))
                .build());
        final AtomicReference<IOSession> resultRef = new AtomicReference<>();
        sessionPool.connectSession(route, null, new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                resultRef.set(ioSession);
            }

            @Override
            public void failed(final Exception e) {
            }

            @Override
            public void cancelled() {
            }

        });

        Assertions.assertSame(ioSession, resultRef.get());
        Mockito.verify(connectionInitiator).connect(
                Mockito.eq(target),
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 80)),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(34)),
                Mockito.isNull(),
                Mockito.any());
        Mockito.verifyNoInteractions(tlsStrategy);
        Mockito.verify(ioSession).setSocketTimeout(Timeout.ofSeconds(56));
    }

    @Test
    void testConnectHttpsConnectionConfig() {
        final HttpHost target = new HttpHost("https", "somehost", 443);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(34))
                .setSocketTimeout(Timeout.ofSeconds(56))
                .build());
        final AtomicReference<IOSession> resultRef = new AtomicReference<>();
        sessionPool.connectSession(route, null, new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                resultRef.set(ioSession);
            }

            @Override
            public void failed(final Exception e) {
            }

            @Override
            public void cancelled() {
            }

        });

        Assertions.assertSame(ioSession, resultRef.get());
        Mockito.verify(connectionInitiator).connect(
                Mockito.eq(target),
                Mockito.eq(InetSocketAddress.createUnresolved("somehost", 443)),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(34)),
                Mockito.isNull(),
                Mockito.any());
        Mockito.verify(tlsStrategy).upgrade(
                Mockito.same((TransportSecurityLayer) ioSession),
                Mockito.eq(target),
                Mockito.isNull(),
                Mockito.eq(Timeout.ofSeconds(34)),
                Mockito.any());
        Mockito.verify(ioSession).setSocketTimeout(Mockito.eq(Timeout.ofSeconds(56)));
    }

    @Test
    void testValidateOpenSessionNoValidateAfterInactivity() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setValidateAfterInactivity(null)
                .build());

        Mockito.when(ioSession.isOpen()).thenReturn(Boolean.TRUE);

        final AtomicBoolean resultRef = new AtomicBoolean();
        sessionPool.validateSession(route, ioSession, resultRef::set);

        Assertions.assertTrue(resultRef.get());
    }

    @Test
    void testValidateClosedSession() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        Mockito.when(ioSession.isOpen()).thenReturn(Boolean.FALSE);

        final AtomicBoolean resultRef = new AtomicBoolean();
        sessionPool.validateSession(route, ioSession, resultRef::set);

        Assertions.assertFalse(resultRef.get());
        Mockito.verify(ioSession, Mockito.never()).enqueue(Mockito.any(), Mockito.any());
    }

    @Test
    void testValidateOpenSessionValidateAfterInactivitySessionActive() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setValidateAfterInactivity(TimeValue.ofSeconds(15))
                .build());

        Mockito.when(ioSession.isOpen()).thenReturn(Boolean.TRUE);
        final long now = System.currentTimeMillis();
        Mockito.when(clock.millis()).thenReturn(now);
        Mockito.when(ioSession.getLastEventTime()).thenReturn(now - 100);

        final AtomicBoolean resultRef = new AtomicBoolean();
        sessionPool.validateSession(route, ioSession, resultRef::set);

        Assertions.assertTrue(resultRef.get());
        Mockito.verify(ioSession, Mockito.never()).enqueue(Mockito.any(), Mockito.any());
    }

    @Test
    void testValidateOpenSessionValidateAfterInactivitySessionInactivePingWithNoResponse() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setValidateAfterInactivity(TimeValue.ofSeconds(15))
                .build());

        Mockito.when(ioSession.isOpen()).thenReturn(Boolean.TRUE);
        final long now = System.currentTimeMillis();
        Mockito.when(clock.millis()).thenReturn(now);
        Mockito.when(ioSession.getLastEventTime()).thenReturn(now - TimeValue.ofSeconds(15).toMilliseconds() - 100);

        final AtomicBoolean resultRef = new AtomicBoolean();
        sessionPool.validateSession(route, ioSession, resultRef::set);

        Assertions.assertFalse(resultRef.get());
        Mockito.verify(ioSession).enqueue(Mockito.argThat(argument -> argument instanceof PingCommand), Mockito.any());
    }

    @Test
    void testValidateOpenSessionValidateAfterInactivitySessionInactivePingWithResponse() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setValidateAfterInactivity(TimeValue.ofSeconds(15))
                .build());

        Mockito.when(ioSession.isOpen()).thenReturn(Boolean.TRUE);
        final long now = System.currentTimeMillis();
        Mockito.when(clock.millis()).thenReturn(now);
        Mockito.when(ioSession.getLastEventTime()).thenReturn(now - TimeValue.ofSeconds(15).toMilliseconds() - 100);
        Mockito.doAnswer(invocation -> {
            final Command command = invocation.getArgument(0);
            if (command instanceof PingCommand) {
                final PingCommand pingCommand = (PingCommand) command;
                final AsyncPingHandler handler = pingCommand.getHandler();
                handler.consumeResponse(handler.getData());
            }
            return null;
        }).when(ioSession).enqueue(Mockito.any(), Mockito.any());

        final AtomicBoolean resultRef = new AtomicBoolean();
        sessionPool.validateSession(route, ioSession, resultRef::set);

        Assertions.assertTrue(resultRef.get());
        Mockito.verify(ioSession).enqueue(Mockito.argThat(argument -> argument instanceof PingCommand), Mockito.any());
    }

    @Test
    void testCloseSessionImmediate() {
        sessionPool.closeSession(ioSession, CloseMode.IMMEDIATE);

        Mockito.verify(ioSession).close(CloseMode.IMMEDIATE);
    }

    @Test
    void testCloseSessionGraceful() {
        sessionPool.closeSession(ioSession, CloseMode.GRACEFUL);

        Mockito.verify(ioSession, Mockito.never()).close(Mockito.any());
        Mockito.verify(ioSession).enqueue(
                ArgumentMatchers.argThat(argument -> argument instanceof ShutdownCommand),
                Mockito.eq(Command.Priority.NORMAL));
    }

    @Test
    void testIdle() {
        Mockito.when(ioSession.getHandler()).thenReturn(ioEventHandler);
        Mockito.when(((HttpConnection) ioEventHandler).isIdle()).thenReturn(Boolean.TRUE);

        final boolean idle = sessionPool.isIdle(ioSession);

        Assertions.assertTrue(idle);
    }

    @Test
    void testNotIdle() {
        Mockito.when(ioSession.getHandler()).thenReturn(ioEventHandler);
        Mockito.when(((HttpConnection) ioEventHandler).isIdle()).thenReturn(Boolean.FALSE);

        final boolean idle = sessionPool.isIdle(ioSession);

        Assertions.assertFalse(idle);
    }

    @Test
    void testExpired() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        final boolean expired = sessionPool.isExpired(route, ioSession);

        Assertions.assertFalse(expired);
        Mockito.verifyNoInteractions(ioSession);
    }

    @Test
    void testNotExpiredDueToValidateAfterInactivity() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setIdleTimeout(Timeout.ofSeconds(30))
                .build());

        final long now = System.currentTimeMillis();
        Mockito.when(clock.millis()).thenReturn(now);
        Mockito.when(ioSession.getLastEventTime()).thenReturn(now - 100);

        final boolean expired = sessionPool.isExpired(route, ioSession);

        Assertions.assertFalse(expired);
        Mockito.verify(ioSession).getLastEventTime();
    }

    @Test
    void testExpiredDueToValidateAfterInactivity() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setIdleTimeout(Timeout.ofSeconds(30))
                .build());

        final long now = System.currentTimeMillis();
        Mockito.when(clock.millis()).thenReturn(now);
        Mockito.when(ioSession.getLastEventTime()).thenReturn(now - Timeout.ofSeconds(30).toMilliseconds() - 100);

        final boolean expired = sessionPool.isExpired(route, ioSession);

        Assertions.assertTrue(expired);
        Mockito.verify(ioSession).getLastEventTime();
        Mockito.verifyNoMoreInteractions(ioSession);
    }

    @Test
    void testNotExpiredDueToTimeToLive() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setTimeToLive(Timeout.ofMinutes(1))
                .build());

        final long now = System.currentTimeMillis();
        Mockito.when(ioSession.getInitiationTime()).thenReturn(now - 100);

        final boolean expired = sessionPool.isExpired(route, ioSession);

        Assertions.assertFalse(expired);
        Mockito.verify(ioSession).getInitiationTime();
        Mockito.verifyNoMoreInteractions(ioSession);
    }

    @Test
    void testExpiredDueToTimeToLive() {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        sessionPool.setConnectionConfigResolver(host -> ConnectionConfig.custom()
                .setTimeToLive(Timeout.ofMinutes(1))
                .build());

        final long now = System.currentTimeMillis();
        Mockito.when(clock.millis()).thenReturn(now);
        Mockito.when(ioSession.getInitiationTime()).thenReturn(now - Timeout.ofMinutes(1).toMilliseconds() - 100);

        final boolean expired = sessionPool.isExpired(route, ioSession);

        Assertions.assertTrue(expired);
        Mockito.verify(ioSession).getInitiationTime();
        Mockito.verifyNoMoreInteractions(ioSession);
    }

}
