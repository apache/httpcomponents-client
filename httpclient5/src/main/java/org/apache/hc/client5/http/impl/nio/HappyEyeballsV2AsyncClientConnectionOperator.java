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
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code HappyEyeballsV2AsyncClientConnectionOperator} is an
 * {@link AsyncClientConnectionOperator} implementation that applies the
 * <em>Happy&nbsp;Eyeballs Version&nbsp;2</em> algorithm (RFC&nbsp;8305) to
 * outbound connections.
 *
 * <p>The operator
 * <ol>
 *   <li>resolves <em>A</em> and <em>AAAA</em> records via the provided
 *       {@link DnsResolver},</li>
 *   <li>orders the resulting addresses according to the RFC&nbsp;6724
 *       default-address-selection rules,</li>
 *   <li>initiates a first connection attempt immediately, launches the
 *       first address of the <strong>other family</strong> after
 *       ≈ 50 ms, and then paces subsequent attempts every 250 ms
 *       (configurable),</li>
 *   <li>cancels all remaining attempts once one socket connects, and</li>
 *   <li>reports the <em>last</em> exception if every attempt fails.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * DnsResolver dns = SystemDefaultDnsResolver.INSTANCE;
 * AsyncClientConnectionOperator he =
 *         new HappyEyeballsV2AsyncClientConnectionOperator(dns, tlsRegistry);
 *
 * PoolingAsyncClientConnectionManager cm =
 *         PoolingAsyncClientConnectionManagerBuilder.create()
 *             .setConnectionOperator(he)
 *             .build();
 * }</pre>
 *
 * <h3>Thread-safety</h3>
 * All instances are immutable and therefore thread-safe; the internal
 * {@link ScheduledExecutorService} is created with a single
 * daemon thread.
 *
 * <h3>Limitations / TODO</h3>
 * Rules 3, 4 and 7 of RFC&nbsp;6724 (deprecated addresses, home vs
 * care-of, native transport) are <strong>not yet implemented</strong>.
 * Their placeholders are marked with TODO comments.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8305">RFC&nbsp;8305</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6724">RFC&nbsp;6724</a>
 * @since 5.6
 */
@Experimental
public final class HappyEyeballsV2AsyncClientConnectionOperator
        implements AsyncClientConnectionOperator, ModalCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HappyEyeballsV2AsyncClientConnectionOperator.class);

    private static final int DEFAULT_ATTEMPT_DELAY_MS = 250;
    private static final int DEFAULT_OTHER_FAMILY_DELAY_MS = 50;
    private static final Timeout DEFAULT_CONNECT_TIMEOUT = Timeout.ofSeconds(10);

    private final DnsResolver dnsResolver;
    private final int attemptDelayMillis;
    private final int otherFamilyDelayMillis;
    private final ScheduledExecutorService scheduler;
    private final boolean ownScheduler;

    private final Lookup<TlsStrategy> tlsStrategyLookup;
    private final Timeout tlsHandshakeTimeout = Timeout.ofSeconds(10);

    private static final List<PolicyTableEntry> POLICY_TABLE =
            Collections.unmodifiableList(Arrays.asList(
                    new PolicyTableEntry(toPrefix("::1", 128), 50, 0),
                    new PolicyTableEntry(toPrefix("::ffff:0:0", 96), 35, 4),
                    new PolicyTableEntry(toPrefix("::", 96), 1, 3),
                    new PolicyTableEntry(toPrefix("2001::", 32), 5, 5),
                    new PolicyTableEntry(toPrefix("2002::", 16), 30, 2),
                    new PolicyTableEntry(toPrefix("3ffe::", 16), 1, 12),
                    new PolicyTableEntry(toPrefix("fec0::", 10), 1, 11),
                    new PolicyTableEntry(toPrefix("fc00::", 7), 3, 13),
                    new PolicyTableEntry(toPrefix("::", 0), 40, 1)
            ));

    /**
     * Creates a new operator with default pacing delays.
     *
     * @param dnsResolver DNS resolver to use.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver) {
        this(dnsResolver, DEFAULT_ATTEMPT_DELAY_MS, null, null);
    }

    /**
     * Creates a new operator with default pacing delays and a TLS strategy registry
     * used to perform TLS upgrades via {@link #upgrade(ManagedAsyncClientConnection, HttpHost, NamedEndpoint, Object, HttpContext, FutureCallback)}.
     *
     * @param dnsResolver DNS resolver to use.
     * @param tlsStrategyLookup registry mapping scheme names to {@link TlsStrategy} instances.
     * @since 5.6
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver,
                                                        final Lookup<TlsStrategy> tlsStrategyLookup) {
        this(dnsResolver, DEFAULT_ATTEMPT_DELAY_MS, null, tlsStrategyLookup);
    }

    /**
     * Creates a new operator with a custom pacing delay and a TLS strategy registry.
     *
     * @param dnsResolver DNS resolver to use.
     * @param attemptDelayMillis delay between subsequent connection attempts (ms).
     * @param tlsStrategyLookup registry mapping scheme names to {@link TlsStrategy} instances.
     * @since 5.6
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver,
                                                        final int attemptDelayMillis,
                                                        final Lookup<TlsStrategy> tlsStrategyLookup) {
        this(dnsResolver, attemptDelayMillis, null, tlsStrategyLookup);
    }

    /**
     * Creates a new operator with full control over pacing and scheduling, and a TLS strategy registry.
     *
     * @param dnsResolver DNS resolver to use.
     * @param attemptDelayMillis delay between subsequent connection attempts (ms).
     * @param scheduler external scheduler to use; if {@code null}, an internal single-threaded
     *                  daemon scheduler is created.
     * @param tlsStrategyLookup registry mapping scheme names to {@link TlsStrategy} instances.
     * @since 5.6
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver,
                                                        final int attemptDelayMillis,
                                                        final ScheduledExecutorService scheduler,
                                                        final Lookup<TlsStrategy> tlsStrategyLookup) {
        this.dnsResolver = Args.notNull(dnsResolver, "DNS resolver");
        this.tlsStrategyLookup = tlsStrategyLookup;
        this.attemptDelayMillis = Args.positive(attemptDelayMillis, "Attempt delay");
        this.otherFamilyDelayMillis = Math.min(DEFAULT_OTHER_FAMILY_DELAY_MS, attemptDelayMillis);
        if (scheduler != null) {
            this.scheduler = scheduler;
            this.ownScheduler = false;
        } else {
            final ThreadFactory tf = r -> {
                final Thread t = new Thread(r, "hc-hev2-scheduler");
                t.setDaemon(true);
                return t;
            };
            this.scheduler = Executors.newSingleThreadScheduledExecutor(tf);
            this.ownScheduler = true;
        }
    }

    @Override
    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<ManagedAsyncClientConnection> callback) {

        final Timeout effTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        final BasicFuture<ManagedAsyncClientConnection> promise = new BasicFuture<>(callback);

        final InetAddress resolved = host.getAddress();
        if (resolved != null) {
            final InetSocketAddress target = new InetSocketAddress(resolved, host.getPort());
            connectionInitiator.connect(host, target, localAddress, effTimeout, attachment,
                    new ResolvedConnectCallback(promise));
            return promise;
        }

        try {
            final int port = host.getPort();
            final InetAddress[] ips = dnsResolver.resolve(host.getHostName());
            final List<InetSocketAddress> addrs = new ArrayList<>(ips.length);
            for (final InetAddress ip : ips) {
                addrs.add(new InetSocketAddress(ip, port));
            }
            sortByRFC6724(addrs);
            startStaggeredConnects(connectionInitiator, host, localAddress, addrs, effTimeout, attachment, promise);
        } catch (final UnknownHostException ex) {
            promise.failed(ex);
        }
        return promise;
    }

    @Override
    public void upgrade(final ManagedAsyncClientConnection conn, final HttpHost host, final Object attachment) {

    }

    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost endpointHost,
            final NamedEndpoint endpointName,
            final Object attachment,
            final HttpContext context,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        final TlsStrategy tlsStrategy = tlsStrategyLookup != null ? tlsStrategyLookup.lookup(endpointHost.getSchemeName()) : null;
        if (tlsStrategy != null && URIScheme.HTTPS.same(endpointHost.getSchemeName())) {
            tlsStrategy.upgrade(
                    connection,
                    endpointHost,
                    attachment,
                    tlsHandshakeTimeout,
                    new FutureCallback<TransportSecurityLayer>() {
                        @Override
                        public void completed(final TransportSecurityLayer transportSecurityLayer) {
                            if (callback != null) {
                                callback.completed(connection);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (callback != null) {
                                callback.failed(ex);
                            }
                        }

                        @Override
                        public void cancelled() {
                            if (callback != null) {
                                callback.failed(new CancellationException("Upgrade was cancelled"));
                            }
                        }
                    });
        } else {
            if (callback != null) {
                callback.completed(connection);
            }
        }
    }

    private void startStaggeredConnects(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final List<InetSocketAddress> addrs,
            final Timeout connectTimeout,
            final Object attachment,
            final BasicFuture<ManagedAsyncClientConnection> promise) {

        if (addrs.isEmpty()) {
            promise.failed(new UnknownHostException("No addresses"));
            return;
        }

        final AtomicBoolean done = new AtomicBoolean(false);
        final CopyOnWriteArrayList<Future<IOSession>> ioFutures = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<ScheduledFuture<?>> scheduled = new CopyOnWriteArrayList<>();
        final AtomicReference<Exception> lastFailure = new AtomicReference<>();
        final AtomicInteger finishedCount = new AtomicInteger(0);
        final AtomicInteger totalAttempts = new AtomicInteger(0);

        final boolean preferV6 = addrs.get(0).getAddress() instanceof Inet6Address;
        final List<InetSocketAddress> v6 = new ArrayList<>();
        final List<InetSocketAddress> v4 = new ArrayList<>();
        for (final InetSocketAddress a : addrs) {
            if (a.getAddress() instanceof Inet6Address) {
                v6.add(a);
            } else {
                v4.add(a);
            }
        }

        final Runnable cancelAll = () -> {
            for (final Future<IOSession> f : ioFutures) {
                try {
                    f.cancel(true);
                } catch (final RuntimeException ignore) {
                }
            }
            for (final ScheduledFuture<?> t : scheduled) {
                try {
                    t.cancel(true);
                } catch (final RuntimeException ignore) {
                }
            }
        };

        final AttemptScheduler attempt = new AttemptScheduler(
                connectionInitiator, host, localAddress, connectTimeout, attachment,
                done, ioFutures, lastFailure, finishedCount, totalAttempts, promise, cancelAll);

        long t = 0;
        int i6 = 0, i4 = 0;
        if (preferV6) {
            if (!v6.isEmpty()) {
                scheduled.add(attempt.schedule(v6.get(i6++), t));
            }
            if (!v4.isEmpty()) {
                t = otherFamilyDelayMillis;
                scheduled.add(attempt.schedule(v4.get(i4++), t));
            }
        } else {
            if (!v4.isEmpty()) {
                scheduled.add(attempt.schedule(v4.get(i4++), t));
            }
            if (!v6.isEmpty()) {
                t = otherFamilyDelayMillis;
                scheduled.add(attempt.schedule(v6.get(i6++), t));
            }
        }

        t = (t == 0) ? attemptDelayMillis : t + attemptDelayMillis;
        while (i6 < v6.size() || i4 < v4.size()) {
            if (i6 < v6.size()) {
                scheduled.add(attempt.schedule(v6.get(i6++), t));
                t += attemptDelayMillis;
            }
            if (i4 < v4.size()) {
                scheduled.add(attempt.schedule(v4.get(i4++), t));
                t += attemptDelayMillis;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 5.6
     */
    @Override
    public void close() throws IOException {
        shutdown(CloseMode.GRACEFUL);
    }


    private final class AttemptScheduler {
        private final ConnectionInitiator initiator;
        private final HttpHost host;
        private final SocketAddress local;
        private final Timeout timeout;
        private final Object attachment;

        private final AtomicBoolean done;
        private final CopyOnWriteArrayList<Future<IOSession>> ioFutures;
        private final AtomicReference<Exception> lastFailure;
        private final AtomicInteger finishedCount;
        private final AtomicInteger totalAttempts;
        private final BasicFuture<ManagedAsyncClientConnection> promise;
        private final Runnable cancelAll;

        AttemptScheduler(final ConnectionInitiator initiator,
                         final HttpHost host,
                         final SocketAddress local,
                         final Timeout timeout,
                         final Object attachment,
                         final AtomicBoolean done,
                         final CopyOnWriteArrayList<Future<IOSession>> ioFutures,
                         final AtomicReference<Exception> lastFailure,
                         final AtomicInteger finishedCount,
                         final AtomicInteger totalAttempts,
                         final BasicFuture<ManagedAsyncClientConnection> promise,
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
            return scheduler.schedule(() -> {
                if (done.get()) {
                    return;
                }
                final Future<IOSession> ioFuture =
                        initiator.connect(host, dest, local, timeout, attachment,
                                new FutureCallback<IOSession>() {
                                    @Override
                                    public void completed(final IOSession session) {
                                        if (done.compareAndSet(false, true)) {
                                            promise.completed(new DefaultManagedAsyncClientConnection(session));
                                            cancelAll.run();
                                        } else {
                                            try {
                                                session.close(CloseMode.IMMEDIATE);
                                            } catch (final RuntimeException ignore) {
                                            }
                                        }
                                    }

                                    @Override
                                    public void failed(final Exception ex) {
                                        lastFailure.set(ex);
                                        if (!done.get()) {
                                            final int finished = finishedCount.incrementAndGet();
                                            final int total = totalAttempts.get();
                                            if (finished == total && done.compareAndSet(false, true)) {
                                                promise.failed(lastFailure.get() != null
                                                        ? lastFailure.get()
                                                        : new ConnectException("All connection attempts failed"));
                                            }
                                        }
                                    }

                                    @Override
                                    public void cancelled() {
                                        lastFailure.compareAndSet(null, new CancellationException("Cancelled"));
                                        if (!done.get()) {
                                            final int finished = finishedCount.incrementAndGet();
                                            final int total = totalAttempts.get();
                                            if (finished == total && done.compareAndSet(false, true)) {
                                                final Exception ex = lastFailure.get();
                                                promise.failed(ex != null
                                                        ? ex
                                                        : new ConnectException("All connection attempts failed"));
                                            }
                                        }
                                    }
                                });
                ioFutures.add(ioFuture);
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    @Internal
    static void sortByRFC6724(final List<InetSocketAddress> addrs) {
        if (addrs.size() < 2) {
            return;
        }
        final List<InetAddress> srcs = srcAddrs(addrs);

        final List<ByRFC6724Info> infos = new ArrayList<>(addrs.size());
        for (int i = 0; i < addrs.size(); i++) {
            final ByRFC6724Info info = new ByRFC6724Info();
            info.addr = addrs.get(i).getAddress();
            info.addrAttr = ipAttrOf(info.addr);
            info.src = srcs.get(i);
            info.srcAttr = ipAttrOf(info.src);
            infos.add(info);
        }

        infos.sort(HappyEyeballsV2AsyncClientConnectionOperator::compareByRFC6724);

        for (int i = 0; i < infos.size(); i++) {
            addrs.set(i, new InetSocketAddress(infos.get(i).addr, addrs.get(i).getPort()));
        }
    }

    private static List<InetAddress> srcAddrs(final List<InetSocketAddress> addrs) {
        final List<InetAddress> srcs = new ArrayList<>(addrs.size());
        for (final InetSocketAddress dest : addrs) {
            InetAddress src = null;
            try (final DatagramSocket sock = new DatagramSocket()) {
                sock.connect(dest);
                src = sock.getLocalAddress();
            } catch (final SocketException ignore) {
            }
            srcs.add(src);
        }
        return srcs;
    }

    private static IpAttr ipAttrOf(final InetAddress ip) {
        if (ip == null) {
            return new IpAttr(Scope.GLOBAL, 0, 0);
        }
        final PolicyTableEntry entry = classify(ip);
        return new IpAttr(classifyScope(ip), entry.precedence, entry.label);
    }

    private static PolicyTableEntry classify(final InetAddress ip) {
        for (final PolicyTableEntry e : POLICY_TABLE) {
            if (e.prefix.contains(ip)) {
                return e;
            }
        }
        return new PolicyTableEntry(null, 40, 1);
    }

    private static Scope classifyScope(final InetAddress ip) {
        if (ip.isLoopbackAddress() || ip.isLinkLocalAddress()) {
            return Scope.LINK_LOCAL;
        }
        if (ip.isMulticastAddress()) {
            if (ip instanceof Inet6Address) {
                return Scope.fromValue(ip.getAddress()[1] & 0x0f);
            }
            return Scope.GLOBAL;
        }
        if (ip.isSiteLocalAddress()) {
            return Scope.SITE_LOCAL;
        }
        return Scope.GLOBAL;
    }

    static int compareByRFC6724(final ByRFC6724Info a, final ByRFC6724Info b) {

        final InetAddress da = a.addr, db = b.addr;
        final InetAddress sa = a.src, sb = b.src;
        final IpAttr aDest = a.addrAttr, bDest = b.addrAttr;
        final IpAttr aSrc = a.srcAttr, bSrc = b.srcAttr;

        final int preferA = -1, preferB = 1;

        /* Rule 1 – unusable destinations */
        final boolean validA = sa != null && !sa.isAnyLocalAddress();
        final boolean validB = sb != null && !sb.isAnyLocalAddress();
        if (!validA && !validB) {
            return 0;
        }
        if (!validB) {
            return preferA;
        }
        if (!validA) {
            return preferB;
        }

        /* Rule 2 – prefer matching scope */
        if (aDest.scope == aSrc.scope && bDest.scope != bSrc.scope) {
            return preferA;
        }
        if (aDest.scope != aSrc.scope && bDest.scope == bSrc.scope) {
            return preferB;
        }

        /* Rule 3 – TODO deprecated */
        /* Rule 4 – TODO home vs care-of */

        /* Rule 5 – prefer matching label */
        if (aSrc.label == aDest.label && bSrc.label != bDest.label) {
            return preferA;
        }
        if (aSrc.label != aDest.label && bSrc.label == bDest.label) {
            return preferB;
        }

        /* Rule 6 – higher precedence */
        if (aDest.precedence > bDest.precedence) {
            return preferA;
        }
        if (aDest.precedence < bDest.precedence) {
            return preferB;
        }

        /* Rule 7 – TODO native transport */

        /* Rule 8 – smaller scope */
        if (aDest.scope.value < bDest.scope.value) {
            return preferA;
        }
        if (aDest.scope.value > bDest.scope.value) {
            return preferB;
        }

        /* Rule 9 – longest matching prefix (IPv6 only) */
        if (da instanceof Inet6Address && db instanceof Inet6Address) {
            final int commonA = commonPrefixLen(sa, da);
            final int commonB = commonPrefixLen(sb, db);
            if (commonA > commonB) {
                return preferA;
            }
            if (commonA < commonB) {
                return preferB;
            }
        }

        /* Rule 10 – equal */
        return 0;
    }

    private static int commonPrefixLen(final InetAddress a, final InetAddress b) {
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return 0;
        }
        final byte[] aa = a.getAddress();
        final byte[] bb = b.getAddress();
        final int len = Math.min(aa.length, bb.length);
        int bits = 0;
        for (int i = 0; i < len; i++) {
            final int x = (aa[i] ^ bb[i]) & 0xFF;
            if (x == 0) {
                bits += 8;
            } else {
                for (int j = 7; j >= 0; j--) {
                    if ((x & (1 << j)) != 0) {
                        return bits;
                    }
                    bits++;
                }
                return bits;
            }
        }
        return bits;
    }

    private enum Scope {
        INTERFACE_LOCAL(0x1),
        LINK_LOCAL(0x2),
        ADMIN_LOCAL(0x4),
        SITE_LOCAL(0x5),
        ORG_LOCAL(0x8),
        GLOBAL(0xe);

        final int value;

        Scope(final int v) {
            this.value = v;
        }

        static Scope fromValue(final int v) {
            switch (v) {
                case 0x1:
                    return INTERFACE_LOCAL;
                case 0x2:
                    return LINK_LOCAL;
                case 0x4:
                    return ADMIN_LOCAL;
                case 0x5:
                    return SITE_LOCAL;
                case 0x8:
                    return ORG_LOCAL;
                default:
                    return GLOBAL;
            }
        }
    }

    private static final class IpAttr {
        final Scope scope;
        final int precedence;
        final int label;

        IpAttr(final Scope scope, final int precedence, final int label) {
            this.scope = scope;
            this.precedence = precedence;
            this.label = label;
        }
    }

    private static final class PolicyTableEntry {
        final IPNetwork prefix;
        final int precedence;
        final int label;

        PolicyTableEntry(final IPNetwork prefix, final int precedence, final int label) {
            this.prefix = prefix;
            this.precedence = precedence;
            this.label = label;
        }
    }

    private static final class IPNetwork {
        final byte[] ip;
        final int bits;

        IPNetwork(final byte[] ip, final int bits) {
            this.ip = ip;
            this.bits = bits;
        }

        boolean contains(final InetAddress addr) {
            final byte[] a = addr instanceof Inet4Address ? v4toMapped(addr.getAddress()) : addr.getAddress();
            if (a.length != ip.length) {
                return false;
            }
            final int fullBytes = bits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (a[i] != ip[i]) {
                    return false;
                }
            }
            final int rem = bits % 8;
            if (rem == 0) {
                return true;
            }
            final int mask = 0xff << (8 - rem);
            return (a[fullBytes] & mask) == (ip[fullBytes] & mask);
        }

        private static byte[] v4toMapped(final byte[] v4) {
            final byte[] mapped = new byte[16];
            mapped[10] = mapped[11] = (byte) 0xff;
            System.arraycopy(v4, 0, mapped, 12, 4);
            return mapped;
        }
    }

    private static final class ByRFC6724Info {
        InetAddress addr;
        InetAddress src;
        IpAttr addrAttr;
        IpAttr srcAttr;
    }

    private static IPNetwork toPrefix(final String text, final int bits) {
        try {
            return new IPNetwork(InetAddress.getByName(text).getAddress(), bits);
        } catch (final UnknownHostException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public void shutdown(final CloseMode mode) {
        if (!ownScheduler) {
            return;
        }
        if (mode == CloseMode.GRACEFUL) {
            scheduler.shutdown();
        } else {
            scheduler.shutdownNow();
        }
    }

    public void shutdown() {
        shutdown(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        shutdown(closeMode);
    }

    private static final class ResolvedConnectCallback implements FutureCallback<IOSession> {
        private final BasicFuture<ManagedAsyncClientConnection> promise;

        ResolvedConnectCallback(final BasicFuture<ManagedAsyncClientConnection> promise) {
            this.promise = promise;
        }

        @Override
        public void completed(final IOSession session) {
            promise.completed(new DefaultManagedAsyncClientConnection(session));
        }

        @Override
        public void failed(final Exception ex) {
            promise.failed(ex);
        }

        @Override
        public void cancelled() {
            promise.cancel(true);
        }
    }
}
