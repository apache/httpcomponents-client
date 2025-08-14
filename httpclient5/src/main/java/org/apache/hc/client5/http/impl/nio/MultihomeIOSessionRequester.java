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
import java.util.concurrent.RejectedExecutionException;
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
 * If {@link ConnectionConfig#isStaggeredConnectEnabled()} is {@code false},
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

    private final ScheduledExecutorService scheduler; // created only when staggered is enabled
    private final AtomicBoolean shutdown;

    MultihomeIOSessionRequester(final DnsResolver dnsResolver) {
        this(dnsResolver, null);
    }

    MultihomeIOSessionRequester(final DnsResolver dnsResolver, final ConnectionConfig connectionConfig) {
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
        this.connectionConfig = connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
        this.shutdown = new AtomicBoolean(false);

        if (this.connectionConfig.isStaggeredConnectEnabled()) {
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
                        remoteEndpoint.getHostName(), remoteEndpoint.getPort(),
                        localAddress, remoteAddress, connectTimeout);
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
                                final InetAddress[] addrs;
                                if (remoteAddress instanceof InetSocketAddress) {
                                    final InetAddress a = ((InetSocketAddress) remoteAddress).getAddress();
                                    addrs = a != null ? new InetAddress[]{a} : null;
                                } else {
                                    addrs = null;
                                }
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

        final boolean staggerEnabled = this.connectionConfig.isStaggeredConnectEnabled();

        if (!staggerEnabled || remoteAddresses.size() == 1) {
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

    private void runStaggered(
            final ConnectionInitiator connectionInitiator,
            final NamedEndpoint remoteEndpoint,
            final List<InetSocketAddress> remoteAddresses,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final ComplexFuture<IOSession> future) {

        if (scheduler == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Happy Eyeballs requested but scheduler missing; falling back to sequential",
                        remoteEndpoint.getHostName());
            }
            runSequential(connectionInitiator, remoteEndpoint, remoteAddresses, localAddress,
                    connectTimeout, attachment, future);
            return;
        }

        final InetAddress[] resolvedAddrs = toInetAddrs(remoteAddresses);

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

        final ProtocolFamilyPreference pref = this.connectionConfig.getProtocolFamilyPreference() != null
                ? this.connectionConfig.getProtocolFamilyPreference()
                : ProtocolFamilyPreference.INTERLEAVE;

        if ((pref == ProtocolFamilyPreference.IPV6_ONLY && v6.isEmpty())
                || (pref == ProtocolFamilyPreference.IPV4_ONLY && v4.isEmpty())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} no addresses for {}", remoteEndpoint.getHostName(), pref);
            }
            future.failed(new UnknownHostException(remoteEndpoint.getHostName()));
            return;
        }

        List<InetSocketAddress> v6Try = v6;
        List<InetSocketAddress> v4Try = v4;

        if (pref == ProtocolFamilyPreference.IPV6_ONLY) {
            v4Try = new ArrayList<>(0);
        } else if (pref == ProtocolFamilyPreference.IPV4_ONLY) {
            v6Try = new ArrayList<>(0);
        }

        final boolean startWithV6;
        if (pref == ProtocolFamilyPreference.PREFER_IPV6) {
            startWithV6 = true;
        } else if (pref == ProtocolFamilyPreference.PREFER_IPV4) {
            startWithV6 = false;
        } else {
            startWithV6 = !remoteAddresses.isEmpty()
                    && remoteAddresses.get(0).getAddress() instanceof Inet6Address;
        }

        final long attemptDelayMs = toMillisOrDefault(this.connectionConfig.getHappyEyeballsAttemptDelay(),
                DEFAULT_ATTEMPT_DELAY_MS);
        final long otherFamilyDelayMs = Math.min(
                toMillisOrDefault(this.connectionConfig.getHappyEyeballsOtherFamilyDelay(), DEFAULT_OTHER_FAMILY_DELAY_MS),
                attemptDelayMs);

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} using Happy Eyeballs: attemptDelay={}ms, otherFamilyDelay={}ms, pref={}",
                    remoteEndpoint.getHostName(), attemptDelayMs, otherFamilyDelayMs, pref);
        }

        final AtomicBoolean done = new AtomicBoolean(false);
        final CopyOnWriteArrayList<Future<IOSession>> ioFutures = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<ScheduledFuture<?>> scheduled = new CopyOnWriteArrayList<>();
        final AtomicReference<Exception> lastFailure = new AtomicReference<>(null);
        final AtomicInteger finishedCount = new AtomicInteger(0);
        final AtomicInteger totalAttempts = new AtomicInteger(0);

        final Runnable cancelAll = () -> {
            int cancelledIO = 0;
            int cancelledTimers = 0;

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

        // Propagate cancellation from the returned future into scheduled timers and in-flight connects.
        future.setDependency(new Future<IOSession>() {
            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                done.set(true);
                cancelAll.run();
                return true;
            }

            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }

            @Override
            public boolean isDone() {
                return future.isDone();
            }

            @Override
            public IOSession get() {
                throw new UnsupportedOperationException();
            }

            @Override
            public IOSession get(final long timeout, final TimeUnit unit) {
                throw new UnsupportedOperationException();
            }
        });

        final Attempt attempt = new Attempt(
                connectionInitiator,
                remoteEndpoint,
                localAddress,
                connectTimeout,
                attachment,
                done,
                ioFutures,
                lastFailure,
                finishedCount,
                totalAttempts,
                resolvedAddrs,
                future,
                cancelAll);

        final List<InetSocketAddress> A = startWithV6 ? v6Try : v4Try;
        final List<InetSocketAddress> B = startWithV6 ? v4Try : v6Try;

        long tA = 0L;
        long tB = A.isEmpty() ? 0L : otherFamilyDelayMs;

        int iA = 0;
        int iB = 0;

        if (iA < A.size()) {
            scheduled.add(attempt.schedule(A.get(iA++), tA));
            tA += attemptDelayMs;
        }
        if (iB < B.size()) {
            scheduled.add(attempt.schedule(B.get(iB++), tB));
            tB += attemptDelayMs;
        }

        while (iA < A.size() || iB < B.size()) {
            if (iA < A.size()) {
                scheduled.add(attempt.schedule(A.get(iA++), tA));
                tA += attemptDelayMs;
            }
            if (iB < B.size()) {
                scheduled.add(attempt.schedule(B.get(iB++), tB));
                tB += attemptDelayMs;
            }
        }
    }

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
        private final InetAddress[] resolvedAddrs;
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
                final InetAddress[] resolvedAddrs,
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
            this.resolvedAddrs = resolvedAddrs;
            this.promise = promise;
            this.cancelAll = cancelAll;
        }

        ScheduledFuture<?> schedule(final InetSocketAddress dest, final long delayMs) {
            totalAttempts.incrementAndGet();
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} scheduling connect to {} in {} ms", host.getHostName(), dest, delayMs);
            }

            try {
                return scheduler.schedule(() -> {
                    if (done.get()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} skipping connect to {} (already satisfied)", host.getHostName(), dest);
                        }
                        onAttemptFinished();
                        return;
                    }

                    try {
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
                                                LOG.debug("{} late success to {} discarded (already have winner)",
                                                        host.getHostName(), dest);
                                            }
                                            try {
                                                session.close();
                                            } catch (final RuntimeException ignore) {
                                            }
                                        }
                                        onAttemptFinished();
                                    }

                                    @Override
                                    public void failed(final Exception ex) {
                                        lastFailure.set(ex);
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("{} failed to connect to {} ({}/{}) : {}",
                                                    host.getHostName(), dest,
                                                    finishedCount.incrementAndGet(), totalAttempts.get(),
                                                    ex.getClass().getSimpleName());
                                        } else {
                                            finishedCount.incrementAndGet();
                                        }
                                        maybeFailAll();
                                    }

                                    @Override
                                    public void cancelled() {
                                        lastFailure.compareAndSet(null, new CancellationException("Cancelled"));
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("{} connect attempt to {} was CANCELLED ({}/{})",
                                                    host.getHostName(), dest,
                                                    finishedCount.incrementAndGet(), totalAttempts.get());
                                        } else {
                                            finishedCount.incrementAndGet();
                                        }
                                        maybeFailAll();
                                    }
                                });
                        ioFutures.add(ioFuture);
                    } catch (final RuntimeException ex) {
                        lastFailure.set(ex);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} connect() threw for {} ({}/{}) : {}",
                                    host.getHostName(), dest,
                                    finishedCount.incrementAndGet(), totalAttempts.get(),
                                    ex.getClass().getSimpleName());
                        } else {
                            finishedCount.incrementAndGet();
                        }
                        maybeFailAll();
                    }

                }, delayMs, TimeUnit.MILLISECONDS);
            } catch (final RejectedExecutionException ex) {
                lastFailure.set(ex);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} scheduling rejected for {} ({}/{}) : {}",
                            host.getHostName(), dest,
                            finishedCount.incrementAndGet(), totalAttempts.get(),
                            ex.getClass().getSimpleName());
                } else {
                    finishedCount.incrementAndGet();
                }
                maybeFailAll();
                return new CompletedScheduledFuture<>();
            }
        }

        private void onAttemptFinished() {
            // no-op placeholder; left to keep the accounting in one place if needed later
        }

        private void maybeFailAll() {
            final int finished = finishedCount.get();
            final int total = totalAttempts.get();

            if (!done.get() && finished == total && done.compareAndSet(false, true)) {
                final Exception last = lastFailure.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} all {} attempts exhausted; failing with {}",
                            host.getHostName(), total, last != null ? last.getClass().getSimpleName() : "unknown");
                }
                if (last instanceof IOException) {
                    promise.failed(ConnectExceptionSupport.enhance((IOException) last, host, resolvedAddrs));
                } else {
                    promise.failed(last != null ? last : new ConnectException("All connection attempts failed"));
                }
                cancelAll.run();
            }
        }
    }

    /**
     * Minimal ScheduledFuture implementation used when scheduling is rejected.
     */
    private static final class CompletedScheduledFuture<V> implements ScheduledFuture<V> {

        @Override
        public long getDelay(final TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(final java.util.concurrent.Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public V get() {
            return null;
        }

        @Override
        public V get(final long timeout, final TimeUnit unit) {
            return null;
        }
    }

    void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }
    }

}
