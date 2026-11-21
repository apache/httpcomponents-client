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
package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.hc.client5.http.ConnectExceptionSupport;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Races a blocking connect attempt against each of a host's resolved addresses (RFC 8305,
 * Happy Eyeballs v2) for the Classic (blocking) client. A single dispatcher, running on the
 * calling thread, starts the first address immediately and each subsequent one on its own
 * worker thread, bounded to the number of resolved addresses: it waits up to
 * {@link #connectionAttemptDelay} for an outstanding attempt to finish before starting the
 * next one, but starts the next one immediately, at the RFC 8305 Section 5 minimum spacing
 * (10ms), as soon as an attempt fails. No thread ever sleeps out a delay itself.
 *
 * <p>The first attempt to connect successfully wins the race. Because closing a socket is
 * the only reliable way to abort a blocking {@link Socket#connect}, every other socket, be it
 * already connecting, about to be registered, or not yet created, must end up closed; a
 * worker thread registers its socket through a shared, atomic registry rather than a bare
 * reference, so a socket created in the brief window around the race being decided is always
 * either excluded from cleanup because it does not exist yet, or closed by the registry the
 * instant it is registered. {@code ExecutorService#shutdownNow()} only interrupts threads; it
 * does not itself abort a blocking connect, so it is never relied on for that.</p>
 *
 * @since 5.7
 */
final class HappyEyeballsSocketConnector {

    /**
     * RFC 8305, Section 5: the minimum spacing between the start of successive connection
     * attempts, including one started promptly after an earlier attempt's failure.
     */
    private static final long MIN_CONNECTION_ATTEMPT_DELAY_MS = 10;

    /**
     * Connects a single socket to {@code remoteAddress}, registering the socket via
     * {@code socketConsumer} as soon as it is created and before the (blocking) connect
     * call, so that a concurrent winner can close it even while still connecting.
     */
    @FunctionalInterface
    interface ConnectAttempt {
        Socket connect(InetSocketAddress remoteAddress, Consumer<Socket> socketConsumer) throws IOException;
    }

    /**
     * Registers each attempt's socket so it can be closed by whichever happens first: this
     * race deciding a winner, or the socket being registered. {@link #decide} and
     * {@link #register} coordinate purely through {@link #decided} and the per-slot
     * {@link AtomicReference}, so neither a socket registered just before {@link #decide}
     * runs nor one registered just after is ever left open: {@link #decide} closes whatever
     * it finds already registered, and {@link #register} closes its own socket immediately
     * if it finds the registry already decided, including re-checking right after
     * registering to catch a {@link #decide} that ran in between.
     */
    private static final class SocketRegistry {

        private final AtomicReference<Socket>[] slots;
        private final AtomicBoolean decided = new AtomicBoolean(false);

        @SuppressWarnings("unchecked")
        SocketRegistry(final int n) {
            slots = new AtomicReference[n];
            for (int i = 0; i < n; i++) {
                slots[i] = new AtomicReference<>();
            }
        }

        void register(final int index, final Socket socket) {
            if (decided.get()) {
                Closer.closeQuietly(socket);
                return;
            }
            slots[index].set(socket);
            if (decided.get() && slots[index].compareAndSet(socket, null)) {
                Closer.closeQuietly(socket);
            }
        }

        /** Closes every registered socket except {@code winner} (may be {@code null}). */
        void decide(final Socket winner) {
            decided.set(true);
            for (final AtomicReference<Socket> slot : slots) {
                final Socket socket = slot.getAndSet(null);
                if (socket != null && socket != winner) {
                    Closer.closeQuietly(socket);
                }
            }
        }
    }

    private final TimeValue connectionAttemptDelay;

    HappyEyeballsSocketConnector(final TimeValue connectionAttemptDelay) {
        this.connectionAttemptDelay = connectionAttemptDelay;
    }

    Socket connect(
            final NamedEndpoint remoteEndpoint,
            final List<InetSocketAddress> remoteAddresses,
            final ConnectAttempt connectAttempt) throws IOException {
        final int n = remoteAddresses.size();
        final ExecutorService executor = Executors.newFixedThreadPool(
                n, new DefaultThreadFactory("Happy-eyeballs-classic-connect", true));
        final SocketRegistry registry = new SocketRegistry(n);
        final List<Future<Outcome>> futures = new ArrayList<>(n);
        Socket winner = null;
        try {
            final ExecutorCompletionService<Outcome> completionService = new ExecutorCompletionService<>(executor);
            futures.add(submit(completionService, 0, remoteAddresses, connectAttempt, registry));
            int started = 1;
            long lastStartNanos = System.nanoTime();
            IOException lastFailure = null;

            for (int received = 0; received < n && winner == null; ) {
                final Future<Outcome> completed;
                if (started < n) {
                    final long waitMs = pendingDelayMs(lastStartNanos, connectionAttemptDelay.toMilliseconds());
                    completed = poll(completionService, waitMs);
                    if (completed == null) {
                        // Nothing finished within the delay: start the next address now.
                        futures.add(submit(completionService, started, remoteAddresses, connectAttempt, registry));
                        started++;
                        lastStartNanos = System.nanoTime();
                        continue;
                    }
                } else {
                    completed = take(completionService);
                }
                received++;

                final Outcome outcome;
                try {
                    outcome = completed.get();
                } catch (final ExecutionException ex) {
                    final Throwable cause = ex.getCause();
                    lastFailure = cause instanceof IOException ? (IOException) cause : new IOException(cause);
                    continue;
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while racing connection attempts");
                }
                if (outcome.socket != null) {
                    winner = outcome.socket;
                    for (final Future<Outcome> future : futures) {
                        future.cancel(true);
                    }
                    break;
                }
                if (outcome.failure != null) {
                    lastFailure = outcome.failure;
                }
                if (started < n) {
                    // Advance as promptly as RFC 8305 allows, but never closer than the
                    // minimum spacing to the most recently started attempt.
                    final long waitMs = pendingDelayMs(lastStartNanos, MIN_CONNECTION_ATTEMPT_DELAY_MS);
                    if (waitMs > 0) {
                        sleepOrAbort(waitMs);
                    }
                    futures.add(submit(completionService, started, remoteAddresses, connectAttempt, registry));
                    started++;
                    lastStartNanos = System.nanoTime();
                }
            }
            if (winner != null) {
                return winner;
            }
            throw lastFailure != null ?
                    ConnectExceptionSupport.enhance(lastFailure, remoteEndpoint) :
                    new IOException("Connection to " + remoteEndpoint + " failed for an unknown reason");
        } finally {
            // The single point of truth for cleanup, covering every exit path (winner found,
            // every address failed, or this method itself was interrupted): closes every
            // socket except the winner (if any), including ones registered concurrently with
            // this call. shutdownNow() only interrupts worker threads; it cannot itself abort
            // a blocking Socket#connect, so the registry above is what actually does that.
            registry.decide(winner);
            executor.shutdownNow();
        }
    }

    private static long pendingDelayMs(final long lastStartNanos, final long targetDelayMs) {
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastStartNanos);
        return Math.max(0, targetDelayMs - elapsedMs);
    }

    /**
     * Blocks the dispatcher thread itself for a bounded, short minimum-spacing gap (at most
     * {@link #MIN_CONNECTION_ATTEMPT_DELAY_MS}). Unlike the per-address stagger this replaces,
     * this never delays a worker thread's connect attempt and never exceeds a few
     * milliseconds, so it does not amount to the blocking the rest of this class avoids. An
     * interruption here must abort racing immediately, exactly like one during {@link #poll}
     * or {@link #take}: it is propagated rather than swallowed, so the caller's {@code finally}
     * block runs cleanup and no further attempt is started.
     */
    private static void sleepOrAbort(final long millis) throws InterruptedIOException {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while racing connection attempts");
        }
    }

    private static Future<Outcome> poll(final ExecutorCompletionService<Outcome> completionService, final long timeoutMs) throws InterruptedIOException {
        try {
            return completionService.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while racing connection attempts");
        }
    }

    private static Future<Outcome> take(final ExecutorCompletionService<Outcome> completionService) throws InterruptedIOException {
        try {
            return completionService.take();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while racing connection attempts");
        }
    }

    private static Future<Outcome> submit(
            final ExecutorCompletionService<Outcome> completionService,
            final int index,
            final List<InetSocketAddress> remoteAddresses,
            final ConnectAttempt connectAttempt,
            final SocketRegistry registry) {
        final InetSocketAddress remoteAddress = remoteAddresses.get(index);
        return completionService.submit(() -> {
            try {
                final Socket socket = connectAttempt.connect(remoteAddress, socket1 -> registry.register(index, socket1));
                return new Outcome(socket, null);
            } catch (final IOException ex) {
                return new Outcome(null, ex);
            }
        });
    }

    private static final class Outcome {
        final Socket socket;
        final IOException failure;

        Outcome(final Socket socket, final IOException failure) {
            this.socket = socket;
            this.failure = failure;
        }
    }

}
