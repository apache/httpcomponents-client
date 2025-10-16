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

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.ConnectExceptionSupport;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-home dialing strategy for {@link ConnectionInitiator}.
 * <p>
 * If {@link ConnectionConfig#isStaggeredConnectEnabled()} is {@code false} (or config is null),
 * attempts addresses sequentially (legacy behaviour). If enabled, performs staggered,
 * interleaved connection attempts across protocol families (Happy Eyeballs–style).
 *
 * @since 5.6
 */
final class MultihomeIOSessionRequester {

    private static final Logger LOG = LoggerFactory.getLogger(MultihomeIOSessionRequester.class);

    private static final long DEFAULT_ATTEMPT_DELAY_MS = 250L;
    private static final long DEFAULT_OTHER_FAMILY_DELAY_MS = 50L;

    private final DnsResolver dnsResolver;
    private final ConnectionConfig connectionConfig;

    // Stays alive for the lifetime of this requester (no premature shutdown)
    private final ScheduledExecutorService scheduler; // created only when staggered is enabled

    MultihomeIOSessionRequester(final DnsResolver dnsResolver) {
        this(dnsResolver, null);
    }

    MultihomeIOSessionRequester(final DnsResolver dnsResolver, final ConnectionConfig connectionConfig) {
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
        this.connectionConfig = connectionConfig;

        if (connectionConfig != null && connectionConfig.isStaggeredConnectEnabled()) {
            final ThreadFactory tf = r -> {
                final Thread t = new Thread(r, "hc-hev2-mh-scheduler");
                t.setDaemon(true);
                return t;
            };
            this.scheduler = Executors.newSingleThreadScheduledExecutor(tf);
        } else {
            this.scheduler = null;
        }
    }

    public Future<IOSession> connect(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {

        final ComplexFuture<IOSession> future = new ComplexFuture<>(callback);

        // If a specific address is given, dial it directly (no multi-home logic).
        if (remoteAddress != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}:{} connecting {}->{} ({})",
                        remoteEndpoint.getHostName(), remoteEndpoint.getPort(), localAddress, remoteAddress, connectTimeout);
            }
            final Future<IOSession> sessionFuture = connectionInitiator.connect(
                    remoteEndpoint, remoteAddress, localAddress, connectTimeout, attachment,
                    new FutureCallback<IOSession>() {
                        @Override
                        public void completed(final IOSession session) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{}:{} connected {}->{} as {}",
                                        remoteEndpoint.getHostName(), remoteEndpoint.getPort(),
                                        localAddress, remoteAddress, session.getId());
                            }
                            future.completed(session);
                        }

                        @Override
                        public void failed(final Exception cause) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{}:{} connection to {} failed ({}); terminating",
                                        remoteEndpoint.getHostName(), remoteEndpoint.getPort(),
                                        remoteAddress, cause.getClass());
                            }
                            if (cause instanceof IOException) {
                                final InetAddress[] addrs = new InetAddress[]{
                                        (remoteAddress instanceof InetSocketAddress)
                                                ? ((InetSocketAddress) remoteAddress).getAddress() : null
                                };
                                future.failed(ConnectExceptionSupport.enhance((IOException) cause, remoteEndpoint, addrs));
                            } else {
                                future.failed(cause);
                            }
                        }

                        @Override
                        public void cancelled() {
                            future.cancel();
                        }
                    });
            future.setDependency(sessionFuture);
            return future;
        }

        // Resolve all addresses
        final List<InetSocketAddress> remoteAddresses;
        try {
            remoteAddresses = dnsResolver.resolve(remoteEndpoint.getHostName(), remoteEndpoint.getPort());
            if (remoteAddresses == null || remoteAddresses.isEmpty()) {
                throw new UnknownHostException(remoteEndpoint.getHostName());
            }
        } catch (final UnknownHostException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} DNS resolution failed: {}", remoteEndpoint.getHostName(), ex.getMessage());
            }
            future.failed(ex);
            return future;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} resolved to {}", remoteEndpoint.getHostName(), remoteAddresses);
        }

        final boolean staggerEnabled = connectionConfig != null && connectionConfig.isStaggeredConnectEnabled();

        if (!staggerEnabled || remoteAddresses.size() == 1) {
            // Legacy sequential behaviour
            runSequential(connectionInitiator, remoteEndpoint, remoteAddresses, localAddress,
                    connectTimeout, attachment, future);
        } else {
            runStaggered(connectionInitiator, remoteEndpoint, remoteAddresses, localAddress,
                    connectTimeout, attachment, future);
        }

        return future;
    }

    public Future<IOSession> connect(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<IOSession> callback) {
        return connect(connectionInitiator, remoteEndpoint, null, localAddress, connectTimeout, attachment, callback);
    }

    // ----------------- legacy sequential -----------------

    private void runSequential(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final List<InetSocketAddress> remoteAddresses,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final ComplexFuture<IOSession> future) {

        final Runnable r = new Runnable() {
            private final AtomicInteger attempt = new AtomicInteger(0);

            void executeNext() {
                final int index = attempt.getAndIncrement();
                final InetSocketAddress nextRemote = remoteAddresses.get(index);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}:{} connecting {}->{} ({}) [sequential attempt {}/{}]",
                            remoteEndpoint.getHostName(), remoteEndpoint.getPort(),
                            localAddress, nextRemote, connectTimeout,
                            index + 1, remoteAddresses.size());
                }

                final Future<IOSession> sessionFuture = connectionInitiator.connect(
                        remoteEndpoint, nextRemote, localAddress, connectTimeout, attachment,
                        new FutureCallback<IOSession>() {
                            @Override
                            public void completed(final IOSession session) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("{}:{} connected {}->{} as {}",
                                            remoteEndpoint.getHostName(), remoteEndpoint.getPort(),
                                            localAddress, nextRemote, session.getId());
                                }
                                future.completed(session);
                            }

                            @Override
                            public void failed(final Exception cause) {
                                if (attempt.get() >= remoteAddresses.size()) {
                                    if (cause instanceof IOException) {
                                        final InetAddress[] addrs = toInetAddrs(remoteAddresses);
                                        future.failed(ConnectExceptionSupport.enhance((IOException) cause, remoteEndpoint, addrs));
                                    } else {
                                        future.failed(cause);
                                    }
                                } else {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{}:{} connection to {} failed ({}); trying next address",
                                                remoteEndpoint.getHostName(), remoteEndpoint.getPort(),
                                                nextRemote, cause.getClass());
                                    }
                                    executeNext();
                                }
                            }

                            @Override
                            public void cancelled() {
                                future.cancel();
                            }
                        });
                future.setDependency(sessionFuture);
            }

            @Override
            public void run() {
                executeNext();
            }
        };

        r.run();
    }

    // ----------------- staggered concurrent (Happy Eyeballs–style) -----------------

    private void runStaggered(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final List<InetSocketAddress> remoteAddresses,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final ComplexFuture<IOSession> future) {

        // Defensive: scheduler must exist if we are here
        if (scheduler == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Happy Eyeballs requested but scheduler missing; falling back to sequential", remoteEndpoint.getHostName());
            }
            runSequential(connectionInitiator, remoteEndpoint, remoteAddresses, localAddress,
                    connectTimeout, attachment, future);
            return;
        }

        // Split by family
        final List<InetSocketAddress> v6 = new ArrayList<>();
        final List<InetSocketAddress> v4 = new ArrayList<>();
        for (int i = 0; i < remoteAddresses.size(); i++) {
            final InetSocketAddress a = remoteAddresses.get(i);
            if (a.getAddress() instanceof Inet6Address) {
                v6.add(a);
            } else {
                v4.add(a);
            }
        }

        // Apply family preference (filtering if *_ONLY, otherwise just decide start family)
        final ProtocolFamilyPreference pref = connectionConfig.getProtocolFamilyPreference() != null
                ? connectionConfig.getProtocolFamilyPreference()
                : ProtocolFamilyPreference.INTERLEAVE;

        if (pref == ProtocolFamilyPreference.IPV6_ONLY && v6.isEmpty()
                || pref == ProtocolFamilyPreference.IPV4_ONLY && v4.isEmpty()) {
            // Nothing to try
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} no addresses for {}", remoteEndpoint.getHostName(), pref);
            }
            future.failed(new UnknownHostException(remoteEndpoint.getHostName()));
            return;
        }

        List<InetSocketAddress> v6Try = v6;
        List<InetSocketAddress> v4Try = v4;

        if (pref == ProtocolFamilyPreference.IPV6_ONLY) {
            v4Try = new ArrayList<>(); // empty
        } else if (pref == ProtocolFamilyPreference.IPV4_ONLY) {
            v6Try = new ArrayList<>();
        }

        // Determine starting family:
        // - PREFER_IPV6 -> v6 first
        // - PREFER_IPV4 -> v4 first
        // - INTERLEAVE -> start with family of first address from resolver (keeps RFC6724 order)
        final boolean startWithV6;
        if (pref == ProtocolFamilyPreference.PREFER_IPV6) {
            startWithV6 = true;
        } else if (pref == ProtocolFamilyPreference.PREFER_IPV4) {
            startWithV6 = false;
        } else {
            startWithV6 = !remoteAddresses.isEmpty() &&
                    remoteAddresses.get(0).getAddress() instanceof Inet6Address;
        }

        // Delays
        final long attemptDelayMs = toMillisOrDefault(connectionConfig.getHappyEyeballsAttemptDelay(), DEFAULT_ATTEMPT_DELAY_MS);
        final long otherFamilyDelayMs = Math.min(
                toMillisOrDefault(connectionConfig.getHappyEyeballsOtherFamilyDelay(), DEFAULT_OTHER_FAMILY_DELAY_MS),
                attemptDelayMs);

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} using Happy Eyeballs: attemptDelay={}ms, otherFamilyDelay={}ms, pref={}",
                    remoteEndpoint.getHostName(),
                    attemptDelayMs,
                    otherFamilyDelayMs,
                    pref);
        }

        final AtomicBoolean done = new AtomicBoolean(false);
        final CopyOnWriteArrayList<Future<IOSession>> ioFutures = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<ScheduledFuture<?>> scheduled = new CopyOnWriteArrayList<>();
        final AtomicReference<Exception> lastFailure = new AtomicReference<>(null);
        final AtomicInteger finishedCount = new AtomicInteger(0);
        final AtomicInteger totalAttempts = new AtomicInteger(0);

        // Helper to cancel everything
        final Runnable cancelAll = () -> {
            int cancelledIO = 0, cancelledTimers = 0;
            for (int i = 0; i < ioFutures.size(); i++) {
                try {
                    if (ioFutures.get(i).cancel(true)) {
                        cancelledIO++;
                    }
                } catch (final RuntimeException ignore) {
                }
            }
            for (int i = 0; i < scheduled.size(); i++) {
                try {
                    if (scheduled.get(i).cancel(true)) {
                        cancelledTimers++;
                    }
                } catch (final RuntimeException ignore) {
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} cancelled pending attempts: {} I/O futures, {} timers",
                        remoteEndpoint.getHostName(), cancelledIO, cancelledTimers);
            }
        };

        // Schedules a single attempt
        final Attempt attempt = new Attempt(connectionInitiator, remoteEndpoint, localAddress,
                connectTimeout, attachment, done, ioFutures, lastFailure, finishedCount, totalAttempts, future, cancelAll);

        long t = 0L;
        int i6 = 0, i4 = 0;

        // First attempts: start family at t=0, other family at otherFamilyDelay
        if (startWithV6) {
            if (!v6Try.isEmpty()) {
                scheduled.add(attempt.schedule(v6Try.get(i6++), t));
            }
            if (!v4Try.isEmpty()) {
                t = otherFamilyDelayMs;
                scheduled.add(attempt.schedule(v4Try.get(i4++), t));
            }
        } else {
            if (!v4Try.isEmpty()) {
                scheduled.add(attempt.schedule(v4Try.get(i4++), t));
            }
            if (!v6Try.isEmpty()) {
                t = otherFamilyDelayMs;
                scheduled.add(attempt.schedule(v6Try.get(i6++), t));
            }
        }

        // Subsequent attempts: interleave with attemptDelay spacing
        t = (t == 0L) ? attemptDelayMs : t + attemptDelayMs;
        while (i6 < v6Try.size() || i4 < v4Try.size()) {
            if (i6 < v6Try.size()) {
                scheduled.add(attempt.schedule(v6Try.get(i6++), t));
                t += attemptDelayMs;
            }
            if (i4 < v4Try.size()) {
                scheduled.add(attempt.schedule(v4Try.get(i4++), t));
                t += attemptDelayMs;
            }
        }
    }

    // ----------------- helpers -----------------

    private static long toMillisOrDefault(final TimeValue tv, final long defMs) {
        return tv != null ? tv.toMilliseconds() : defMs;
    }

    private static InetAddress[] toInetAddrs(final List<InetSocketAddress> sockAddrs) {
        final InetAddress[] out = new InetAddress[sockAddrs.size()];
        for (int i = 0; i < sockAddrs.size(); i++) {
            out[i] = sockAddrs.get(i).getAddress();
        }
        return out;
    }

    // Schedules and runs a single connect attempt
    private final class Attempt {

        private final ConnectionInitiator initiator;
        private final NamedEndpoint host;
        private final SocketAddress local;
        private final Timeout timeout;
        private final Object attachment;

        private final AtomicBoolean done;
        private final CopyOnWriteArrayList<Future<IOSession>> ioFutures;
        private final AtomicReference<Exception> lastFailure;
        private final AtomicInteger finishedCount;
        private final AtomicInteger totalAttempts;
        private final ComplexFuture<IOSession> promise;
        private final Runnable cancelAll;

        Attempt(final ConnectionInitiator initiator,
                final NamedEndpoint host,
                final SocketAddress local,
                final Timeout timeout,
                final Object attachment,
                final AtomicBoolean done,
                final CopyOnWriteArrayList<Future<IOSession>> ioFutures,
                final AtomicReference<Exception> lastFailure,
                final AtomicInteger finishedCount,
                final AtomicInteger totalAttempts,
                final ComplexFuture<IOSession> promise,
                final Runnable cancelAll) {
            this.initiator = initiator;
            this.host = host;
            this.local = local;
            this.timeout = timeout;
            this.attachment = attachment;
            this.done = done;
            this.ioFutures = ioFutures;
            this.lastFailure = lastFailure;
            this.finishedCount = finishedCount;
            this.totalAttempts = totalAttempts;
            this.promise = promise;
            this.cancelAll = cancelAll;
        }

        ScheduledFuture<?> schedule(final InetSocketAddress dest, final long delayMs) {
            totalAttempts.incrementAndGet();
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} scheduling connect to {} in {} ms", host.getHostName(), dest, delayMs);
            }
            return scheduler.schedule(() -> {
                if (done.get()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} skipping connect to {} (already satisfied)", host.getHostName(), dest);
                    }
                    return;
                }
                final Future<IOSession> ioFuture = initiator.connect(
                        host, dest, local, timeout, attachment,
                        new FutureCallback<IOSession>() {
                            @Override
                            public void completed(final IOSession session) {
                                if (done.compareAndSet(false, true)) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{} winner: connected to {} ({} total attempts scheduled)",
                                                host.getHostName(), dest, totalAttempts.get());
                                    }
                                    promise.completed(session);
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{} cancelling losing attempts", host.getHostName());
                                    }
                                    cancelAll.run();
                                } else {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{} late success to {} discarded (already have winner)", host.getHostName(), dest);
                                    }
                                    try {
                                        session.close();
                                    } catch (final RuntimeException ignore) {
                                    }
                                }
                            }

                            @Override
                            public void failed(final Exception ex) {
                                lastFailure.set(ex);
                                final int finished = finishedCount.incrementAndGet();
                                final int total = totalAttempts.get();
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("{} failed to connect to {} ({}/{}) : {}",
                                            host.getHostName(), dest, finished, total,
                                            ex.getClass().getSimpleName());
                                }
                                if (!done.get() && finished == total && done.compareAndSet(false, true)) {
                                    final Exception last = lastFailure.get();
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("{} all {} attempts exhausted; failing with {}",
                                                host.getHostName(), total, last != null ? last.getClass().getSimpleName() : "unknown");
                                    }
                                    if (last instanceof IOException) {
                                        promise.failed(ConnectExceptionSupport.enhance((IOException) last, host, (InetAddress) null));
                                    } else {
                                        promise.failed(last != null ? last
                                                : new ConnectException("All connection attempts failed"));
                                    }
                                    cancelAll.run();
                                }
                            }

                            @Override
                            public void cancelled() {
                                lastFailure.compareAndSet(null, new CancellationException("Cancelled"));
                                final int finished = finishedCount.incrementAndGet();
                                final int total = totalAttempts.get();
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("{} connect attempt to {} was CANCELLED ({}/{})",
                                            host.getHostName(), dest, finished, total);
                                }
                                if (!done.get() && finished == total && done.compareAndSet(false, true)) {
                                    final Exception last = lastFailure.get();
                                    promise.failed(last != null ? last
                                            : new ConnectException("All connection attempts failed"));
                                    cancelAll.run();
                                }
                            }
                        });
                ioFutures.add(ioFuture);
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
