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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MultihomeIOSessionRequesterTest {

    private DnsResolver dnsResolver;
    private ConnectionInitiator connectionInitiator;
    private MultihomeIOSessionRequester sessionRequester;
    private NamedEndpoint namedEndpoint;

    @BeforeEach
    void setUp() {
        dnsResolver = Mockito.mock(DnsResolver.class);
        connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        namedEndpoint = Mockito.mock(NamedEndpoint.class);
        sessionRequester = new MultihomeIOSessionRequester(dnsResolver);
    }

    @Test
    void testConnectWithMultipleAddresses() throws Exception {
        final InetAddress address1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        final InetAddress address2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(address1, 8080),
                new InetSocketAddress(address2, 8080)
        );

        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> callback = invocation.getArgument(5);
                    // Simulate a failure for the first connection attempt
                    final CompletableFuture<IOSession> future = new CompletableFuture<>();
                    callback.failed(new IOException("Simulated connection failure"));
                    future.completeExceptionally(new IOException("Simulated connection failure"));
                    return future;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator,
                namedEndpoint,
                null,
                Timeout.ofMilliseconds(500),
                null,
                null
        );

        assertTrue(future.isDone());
        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (final ExecutionException ex) {
            assertInstanceOf(IOException.class, ex.getCause());
            assertEquals("Simulated connection failure", ex.getCause().getMessage());
        }
    }

    @Test
    void testConnectSuccessfulAfterRetries() throws Exception {
        final InetAddress address1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        final InetAddress address2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(address1, 8080),
                new InetSocketAddress(address2, 8080)
        );

        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> callback = invocation.getArgument(5);
                    final InetSocketAddress remoteAddress = invocation.getArgument(1);
                    final CompletableFuture<IOSession> future = new CompletableFuture<>();
                    if (remoteAddress.getAddress().equals(address1)) {
                        // Fail the first address
                        callback.failed(new IOException("Simulated connection failure"));
                        future.completeExceptionally(new IOException("Simulated connection failure"));
                    } else {
                        // Succeed for the second address
                        final IOSession mockSession = Mockito.mock(IOSession.class);
                        callback.completed(mockSession);
                        future.complete(mockSession);
                    }
                    return future;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator,
                namedEndpoint,
                null,
                Timeout.ofMilliseconds(500),
                null,
                null
        );

        assertTrue(future.isDone());
        try {
            final IOSession session = future.get();
            assertNotNull(session);
        } catch (final ExecutionException ex) {
            fail("Did not expect an ExecutionException", ex);
        }
    }

    // -------------------------------------------------------------------------
    // RFC 8305 connection racing (Happy Eyeballs v2)
    // -------------------------------------------------------------------------

    /** Captures scheduled stagger tasks instead of running them on a timer, for determinism. */
    private static final class FakeScheduler {
        final ScheduledExecutorService mock = Mockito.mock(ScheduledExecutorService.class);
        final Deque<Runnable> pending = new ArrayDeque<>();

        @SuppressWarnings("unchecked")
        FakeScheduler() {
            Mockito.when(mock.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        pending.add(invocation.getArgument(0));
                        return Mockito.mock(ScheduledFuture.class);
                    });
        }

        /** Runs the next pending stagger task, as if its delay had elapsed. */
        void fireNext() {
            final Runnable task = pending.poll();
            assertNotNull(task, "no stagger task was scheduled");
            task.run();
        }

        int pendingCount() {
            return pending.size();
        }
    }

    private static List<InetSocketAddress> addresses(final int count) throws Exception {
        final List<InetSocketAddress> out = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            out.add(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 0, (byte) i}), 8080));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static FutureCallback<IOSession>[] captureCallbacks(final ConnectionInitiator connectionInitiator, final int expected) {
        final ArgumentCaptor<FutureCallback> captor = ArgumentCaptor.forClass(FutureCallback.class);
        Mockito.verify(connectionInitiator, Mockito.times(expected))
                .connect(any(), any(), any(), any(), any(), captor.capture());
        return captor.getAllValues().toArray(new FutureCallback[0]);
    }

    @Test
    void happyEyeballs_firstAddressSucceedsImmediately_noStaggerNeeded() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession winnerSession = Mockito.mock(IOSession.class);
        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(winnerSession);
                    return CompletableFuture.completedFuture(winnerSession);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertTrue(future.isDone());
        assertEquals(winnerSession, future.get());
        // Only the first address was ever attempted; the second was never needed.
        Mockito.verify(connectionInitiator, Mockito.times(1)).connect(any(), any(), any(), any(), any(), any());
        Mockito.verify(winnerSession, Mockito.never()).close(any(CloseMode.class));
    }

    @Test
    void happyEyeballs_firstAddressStalls_secondWinsAfterStagger() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession winnerSession = Mockito.mock(IOSession.class);
        final FakeScheduler scheduler = new FakeScheduler();
        final Future<IOSession> firstAttempt = Mockito.mock(Future.class);
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> firstAttempt) // 1st call: stalls, no callback invoked yet.
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(winnerSession);
                    return CompletableFuture.completedFuture(winnerSession);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertFalse(future.isDone(), "must not complete before the stalled first attempt or the stagger fires");
        assertEquals(1, scheduler.pendingCount());

        scheduler.fireNext(); // simulate the 250ms stagger elapsing
        Mockito.verify(connectionInitiator, Mockito.times(2)).connect(any(), any(), any(), any(), any(), any());
        assertTrue(future.isDone());
        assertEquals(winnerSession, future.get());

        // The still-stalled first attempt must have been cancelled, not left to leak.
        Mockito.verify(firstAttempt).cancel(true);
    }

    @Test
    void happyEyeballs_firstAddressFailsFast_advancesPromptlyAtMinimumSpacingNotFullStagger() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession winnerSession = Mockito.mock(IOSession.class);
        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.failed(new IOException("refused"));
                    return CompletableFuture.completedFuture(null);
                })
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(winnerSession);
                    return CompletableFuture.completedFuture(winnerSession);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        // The 1st address failed immediately: the 2nd must be scheduled at the RFC 8305
        // Section 5 minimum inter-attempt spacing (10ms), not the full 250ms stagger delay.
        assertFalse(future.isDone());
        final ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(scheduler.mock).schedule(any(Runnable.class), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        assertTrue(delayCaptor.getValue() <= 10L && delayCaptor.getValue() > 0L,
                "must advance at the minimum spacing (<=10ms), not the full stagger delay: was " + delayCaptor.getValue());

        scheduler.fireNext();
        assertTrue(future.isDone());
        assertEquals(winnerSession, future.get());
        Mockito.verify(connectionInitiator, Mockito.times(2)).connect(any(), any(), any(), any(), any(), any());
    }

    @Test
    void happyEyeballs_bothAttemptsSucceed_loserSessionIsClosedNotLeaked() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession secondSession = Mockito.mock(IOSession.class);
        final Future<IOSession> firstAttempt = Mockito.mock(Future.class);
        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> firstAttempt) // stalls until completed manually below
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(secondSession);
                    return CompletableFuture.completedFuture(secondSession);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        // The first attempt stalls, so the stagger fires and starts the second attempt, which
        // wins the race immediately.
        assertFalse(future.isDone());
        scheduler.fireNext();
        assertTrue(future.isDone());
        assertEquals(secondSession, future.get());
        Mockito.verify(secondSession, Mockito.never()).close(any(CloseMode.class));

        // The first address now completes late, after the race was already decided: its
        // session must be discarded rather than leaked or allowed to override the winner.
        final FutureCallback<IOSession>[] allCallbacks = captureCallbacks(connectionInitiator, 2);
        final IOSession lateSession = Mockito.mock(IOSession.class);
        allCallbacks[0].completed(lateSession);

        Mockito.verify(lateSession).close(CloseMode.IMMEDIATE);
        assertEquals(secondSession, future.get(), "the already-decided winner must not change");
    }

    @Test
    void happyEyeballs_allAddressesFail_aggregatedFailure() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(3);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.failed(new IOException("refused"));
                    return CompletableFuture.completedFuture(null);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        // All 3 addresses fail fast, one after another; each advance is scheduled at the
        // RFC 8305 minimum inter-attempt spacing (10ms) rather than happening synchronously.
        assertFalse(future.isDone());
        scheduler.fireNext();
        assertFalse(future.isDone());
        scheduler.fireNext();
        assertTrue(future.isDone());
        Mockito.verify(connectionInitiator, Mockito.times(3)).connect(any(), any(), any(), any(), any(), any());
        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (final ExecutionException ex) {
            assertInstanceOf(IOException.class, ex.getCause());
        }
    }

    @Test
    void happyEyeballs_cancellationBeforeAnyAttemptResolves_cancelsOutstandingAndPendingStagger() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final FakeScheduler scheduler = new FakeScheduler();
        final Future<IOSession> firstAttempt = Mockito.mock(Future.class);
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> firstAttempt);

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertFalse(future.isDone());
        assertEquals(1, scheduler.pendingCount());

        future.cancel(true);

        Mockito.verify(firstAttempt).cancel(true);
        assertTrue(future.isCancelled());
    }

    @Test
    void happyEyeballs_lateCompletionAfterCancellation_closesSessionWithoutReachingFuture() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Mockito.mock(Future.class));

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);
        future.cancel(true);

        final FutureCallback<IOSession>[] callbacks = captureCallbacks(connectionInitiator, 1);
        final IOSession lateSession = Mockito.mock(IOSession.class);
        callbacks[0].completed(lateSession);

        Mockito.verify(lateSession).close(CloseMode.IMMEDIATE);
        assertTrue(future.isCancelled());
    }

    @Test
    void happyEyeballs_singleAddress_doesNotRace() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(1);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession session = Mockito.mock(IOSession.class);
        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(session);
                    return CompletableFuture.completedFuture(session);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertTrue(future.isDone());
        assertEquals(session, future.get());
        assertEquals(0, scheduler.pendingCount(), "a single address must never schedule a stagger");
    }

    @Test
    void happyEyeballs_disabledByDefault_usesSequentialPathEvenWithMultipleAddresses() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession session = Mockito.mock(IOSession.class);
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(session);
                    return CompletableFuture.completedFuture(session);
                });

        // Explicit happyEyeballsEnabled=false, as opposed to the default single-arg constructor
        // already exercised by the other tests in this class.
        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, false, TimeValue.ofMilliseconds(250));
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertTrue(future.isDone());
        assertEquals(session, future.get());
        Mockito.verify(connectionInitiator, Mockito.times(1)).connect(any(), any(), any(), any(), any(), any());
    }

    @Test
    void happyEyeballs_respectsConfiguredDelay() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Mockito.mock(Future.class));

        final ScheduledExecutorService schedulerMock = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(schedulerMock.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(Mockito.mock(ScheduledFuture.class));

        final MultihomeIOSessionRequester requester = new MultihomeIOSessionRequester(
                dnsResolver, true, TimeValue.ofMilliseconds(777), schedulerMock);
        requester.connect(connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        Mockito.verify(schedulerMock).schedule(any(Runnable.class), org.mockito.ArgumentMatchers.eq(777L), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void happyEyeballs_rejectsDelayBelowRfc8305Minimum() {
        assertThrows(IllegalArgumentException.class,
                () -> new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(5)));
    }

    @Test
    void happyEyeballs_unexpectedCancellationOfNonLastAttempt_advancesToNextAddress() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(2);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        final IOSession winnerSession = Mockito.mock(IOSession.class);
        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // Cancelled by something other than this race itself (e.g. an external
                    // close), not a failure and not our own decided/cancel() path.
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.cancelled();
                    return CompletableFuture.completedFuture(null);
                })
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.completed(winnerSession);
                    return CompletableFuture.completedFuture(winnerSession);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertFalse(future.isDone());
        scheduler.fireNext(); // the minimum-spacing advance after the unexpected cancellation
        assertTrue(future.isDone());
        assertEquals(winnerSession, future.get());
    }

    @Test
    void happyEyeballs_unexpectedCancellationOfLastAttempt_failsRatherThanHangingForever() throws Exception {
        final List<InetSocketAddress> remoteAddresses = addresses(1);
        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        // A single address racing alone still goes through the Race (not the sequential path)
        // when forced via the 4-arg constructor is not applicable here; use 2 addresses with
        // the 2nd address also cancelled, so the race is driven to full exhaustion.
        final List<InetSocketAddress> twoAddresses = addresses(2);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(twoAddresses);

        final FakeScheduler scheduler = new FakeScheduler();
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> cb = invocation.getArgument(5);
                    cb.cancelled();
                    return CompletableFuture.completedFuture(null);
                });

        final MultihomeIOSessionRequester requester =
                new MultihomeIOSessionRequester(dnsResolver, true, TimeValue.ofMilliseconds(250), scheduler.mock);
        final Future<IOSession> future = requester.connect(
                connectionInitiator, namedEndpoint, null, Timeout.ofSeconds(5), null, null);

        assertFalse(future.isDone());
        scheduler.fireNext();
        assertTrue(future.isDone(), "the future must not be left pending forever after every attempt is cancelled");
        assertTrue(future.isCancelled() || future.isDone());
        try {
            future.get();
            fail("Expected an ExecutionException rather than an indefinitely pending future");
        } catch (final ExecutionException ex) {
            assertInstanceOf(CancellationException.class, ex.getCause());
        }
    }

    /**
     * Reproduces the race between {@code Race#onAttemptCompleted} picking a winner and the
     * caller cancelling the outer {@code Future} directly (not through the Race's own
     * {@code cancel()}): {@code ComplexFuture#completed(T)} can then return {@code false}
     * even though the Race's own bookkeeping says it won, and the winning session must still
     * be closed rather than silently leaked. This genuinely requires two threads contending
     * on the same narrow window inside {@code ComplexFuture#cancel}, so the test runs many
     * iterations under real contention and asserts the invariant (no leaked session, exactly
     * one terminal outcome) holds on every one, rather than depending on hitting the window
     * a specific number of times.
     */
    @Test
    void happyEyeballs_concurrentExternalCancelDuringWinnerSelection_neverLeaksSession() throws Exception {
        for (int i = 0; i < 500; i++) {
            // 2 addresses so this goes through the Race (not the sequential) path; the 2nd
            // attempt is left permanently stalled behind a FakeScheduler so no real timer
            // ever fires during the test.
            final List<InetSocketAddress> remoteAddresses = addresses(2);
            final DnsResolver localDnsResolver = Mockito.mock(DnsResolver.class);
            final ConnectionInitiator localConnectionInitiator = Mockito.mock(ConnectionInitiator.class);
            final NamedEndpoint localEndpoint = Mockito.mock(NamedEndpoint.class);
            Mockito.when(localEndpoint.getHostName()).thenReturn("somehost");
            Mockito.when(localEndpoint.getPort()).thenReturn(8080);
            Mockito.when(localDnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

            final IOSession session = Mockito.mock(IOSession.class);
            final AtomicReference<FutureCallback<IOSession>> attemptCallback = new AtomicReference<>();
            final FakeScheduler localScheduler = new FakeScheduler();
            Mockito.when(localConnectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        attemptCallback.set(invocation.getArgument(5));
                        return Mockito.mock(Future.class);
                    });

            final MultihomeIOSessionRequester requester = new MultihomeIOSessionRequester(
                    localDnsResolver, true, TimeValue.ofMilliseconds(250), localScheduler.mock);
            final Future<IOSession> future = requester.connect(
                    localConnectionInitiator, localEndpoint, null, Timeout.ofSeconds(5), null, null);

            final CountDownLatch ready = new CountDownLatch(2);
            final CountDownLatch go = new CountDownLatch(1);
            final Thread canceller = new Thread(() -> {
                ready.countDown();
                await(go);
                future.cancel(true);
            });
            final Thread completer = new Thread(() -> {
                ready.countDown();
                await(go);
                attemptCallback.get().completed(session);
            });
            canceller.start();
            completer.start();
            ready.await();
            go.countDown();
            canceller.join(5000);
            completer.join(5000);

            // Exactly one terminal outcome, and the session is never left open.
            assertTrue(future.isDone());
            boolean sessionDelivered = false;
            try {
                sessionDelivered = future.get() == session;
            } catch (final CancellationException | ExecutionException ex) {
                sessionDelivered = false;
            }
            if (!sessionDelivered) {
                Mockito.verify(session, Mockito.atLeastOnce()).close(CloseMode.IMMEDIATE);
            } else {
                Mockito.verify(session, Mockito.never()).close(any(CloseMode.class));
            }
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
