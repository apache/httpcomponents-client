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
package org.apache.hc.client5.http.impl.nio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MultihomeIOSessionRequesterTest {

    private DnsResolver dnsResolver;
    private ConnectionInitiator connectionInitiator;
    private NamedEndpoint namedEndpoint;

    // Shared scheduler to make mock timings deterministic across platforms/CI
    private ScheduledExecutorService testScheduler;

    @BeforeEach
    void setUp() {
        dnsResolver = Mockito.mock(DnsResolver.class);
        connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        namedEndpoint = Mockito.mock(NamedEndpoint.class);

        testScheduler = Executors.newScheduledThreadPool(2, r -> {
            final Thread t = new Thread(r, "mh-test-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void shutdownScheduler() {
        if (testScheduler != null) {
            testScheduler.shutdownNow();
        }
    }

    @Test
    void testConnectWithMultipleAddresses_allFail_surfaceLastFailure() throws Exception {
        final MultihomeIOSessionRequester sessionRequester =
                new MultihomeIOSessionRequester(dnsResolver, ConnectionConfig.custom()
                        .setStaggeredConnectEnabled(false)
                        .build());

        final InetAddress address1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        final InetAddress address2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(address1, 8080),
                new InetSocketAddress(address2, 8080)
        );

        when(namedEndpoint.getHostName()).thenReturn("somehost");
        when(namedEndpoint.getPort()).thenReturn(8080);
        when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    final CompletableFuture<IOSession> f = new CompletableFuture<>();
                    final IOException io = new IOException("Simulated connection failure");
                    cb.failed(io);
                    f.completeExceptionally(io);
                    return f;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofMilliseconds(500), null, null
        );

        assertTrue(future.isDone());
        final ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IOException.class, ex.getCause());
        assertEquals("Simulated connection failure", ex.getCause().getMessage());
    }

    @Test
    void testConnectSuccessfulAfterRetries() throws Exception {
        final MultihomeIOSessionRequester sessionRequester =
                new MultihomeIOSessionRequester(dnsResolver, ConnectionConfig.custom()
                        .setStaggeredConnectEnabled(false)
                        .build());

        final InetAddress address1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        final InetAddress address2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(address1, 8080),
                new InetSocketAddress(address2, 8080)
        );

        when(namedEndpoint.getHostName()).thenReturn("somehost");
        when(namedEndpoint.getPort()).thenReturn(8080);
        when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    final InetSocketAddress remoteAddress = invocation.getArgument(1);
                    final CompletableFuture<IOSession> f = new CompletableFuture<>();
                    if (remoteAddress.getAddress().equals(address1)) {
                        final IOException io = new IOException("Simulated connection failure");
                        cb.failed(io);
                        f.completeExceptionally(io);
                    } else {
                        final IOSession s = Mockito.mock(IOSession.class);
                        cb.completed(s);
                        f.complete(s);
                    }
                    return f;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofMilliseconds(500), null, null
        );

        assertTrue(future.isDone());
        assertNotNull(future.get());
    }

    @Test
    void testHappyEyeballs_fastV4BeatsSlowerV6() throws Exception {
        final MultihomeIOSessionRequester sessionRequester =
                new MultihomeIOSessionRequester(dnsResolver, ConnectionConfig.custom()
                        .setStaggeredConnectEnabled(true)
                        .setHappyEyeballsAttemptDelay(TimeValue.ofMilliseconds(250))
                        .setHappyEyeballsOtherFamilyDelay(TimeValue.ofMilliseconds(50))
                        .setProtocolFamilyPreference(ProtocolFamilyPreference.INTERLEAVE)
                        .build());

        final InetAddress v6 = InetAddress.getByName("2001:db8::10");
        final InetAddress v4 = InetAddress.getByName("203.0.113.10");
        final InetSocketAddress aV6 = new InetSocketAddress(v6, 8080);
        final InetSocketAddress aV4 = new InetSocketAddress(v4, 8080);

        when(namedEndpoint.getHostName()).thenReturn("dual");
        when(namedEndpoint.getPort()).thenReturn(8080);
        // v6 first from DNS so requester will start with v6 and stagger v4 shortly after
        when(dnsResolver.resolve("dual", 8080)).thenReturn(Arrays.asList(aV6, aV4));

        final IOSession v6Session = Mockito.mock(IOSession.class, "v6Session");
        final IOSession v4Session = Mockito.mock(IOSession.class, "v4Session");

        when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final InetSocketAddress remote = invocation.getArgument(1);
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    final CompletableFuture<IOSession> f = new CompletableFuture<>();

                    // Large margin so v4 always wins even with CI jitter.
                    if (remote.equals(aV6)) {
                        testScheduler.schedule(() -> {
                            cb.completed(v6Session);
                            f.complete(v6Session);
                        }, 1200, TimeUnit.MILLISECONDS);
                    } else {
                        testScheduler.schedule(() -> {
                            cb.completed(v4Session);
                            f.complete(v4Session);
                        }, 60, TimeUnit.MILLISECONDS);
                    }
                    return f;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(3), null, null);

        final IOSession winner = future.get(3, TimeUnit.SECONDS);
        assertSame(v4Session, winner, "IPv4 should win with faster completion");
        verify(connectionInitiator, atLeast(2)).connect(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testHappyEyeballs_v6Fails_v4Succeeds() throws Exception {
        final MultihomeIOSessionRequester sessionRequester =
                new MultihomeIOSessionRequester(dnsResolver, ConnectionConfig.custom()
                        .setStaggeredConnectEnabled(true)
                        .setHappyEyeballsAttemptDelay(TimeValue.ofMilliseconds(200))
                        .setHappyEyeballsOtherFamilyDelay(TimeValue.ofMilliseconds(50))
                        .setProtocolFamilyPreference(ProtocolFamilyPreference.INTERLEAVE)
                        .build());

        final InetAddress v6 = InetAddress.getByName("2001:db8::10");
        final InetAddress v4 = InetAddress.getByName("203.0.113.10");
        final InetSocketAddress aV6 = new InetSocketAddress(v6, 8443);
        final InetSocketAddress aV4 = new InetSocketAddress(v4, 8443);

        when(namedEndpoint.getHostName()).thenReturn("dual");
        when(namedEndpoint.getPort()).thenReturn(8443);
        when(dnsResolver.resolve("dual", 8443)).thenReturn(Arrays.asList(aV6, aV4));

        final IOSession v4Session = Mockito.mock(IOSession.class, "v4Session");

        when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final InetSocketAddress remote = invocation.getArgument(1);
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    final CompletableFuture<IOSession> f = new CompletableFuture<>();

                    if (remote.equals(aV6)) {
                        // Fail v6 quickly
                        testScheduler.schedule(() -> {
                            final IOException io = new IOException("v6 down");
                            cb.failed(io);
                            f.completeExceptionally(io);
                        }, 30, TimeUnit.MILLISECONDS);
                    } else {
                        // Succeed v4 after a short delay
                        testScheduler.schedule(() -> {
                            cb.completed(v4Session);
                            f.complete(v4Session);
                        }, 60, TimeUnit.MILLISECONDS);
                    }
                    return f;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(2), null, null
        );

        final IOSession session = future.get(2, TimeUnit.SECONDS);
        assertSame(v4Session, session);
    }
}
