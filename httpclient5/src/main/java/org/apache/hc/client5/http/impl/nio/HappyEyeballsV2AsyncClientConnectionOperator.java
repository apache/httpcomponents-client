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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
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
 *         new HappyEyeballsV2AsyncClientConnectionOperator(dns);
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
 * @since 5.4
 */
@Experimental
public final class HappyEyeballsV2AsyncClientConnectionOperator
        implements AsyncClientConnectionOperator {

    // --------------------------------------------------------------------- constants

    private static final Logger LOG =
            LoggerFactory.getLogger(HappyEyeballsV2AsyncClientConnectionOperator.class);

    /**
     * Gap between normal connection attempts (HTTP profile, RFC 8305&nbsp;§5).
     */
    private static final int DEFAULT_ATTEMPT_DELAY_MS = 250;

    /**
     * Delay before the first opposite-family attempt (≈ 50 ms, RFC 8305&nbsp;§5).
     */
    private static final int DEFAULT_OTHER_FAMILY_DELAY_MS = 50;

    /**
     * Fallback connect timeout applied when the caller supplies {@code null}.
     */
    private static final Timeout DEFAULT_CONNECT_TIMEOUT = Timeout.ofSeconds(10);

    // --------------------------------------------------------------------- instance fields

    private final DnsResolver dnsResolver;
    private final int attemptDelayMillis;
    private final int otherFamilyDelayMillis;
    private final ScheduledExecutorService scheduler;

    // --------------------------------------------------------------------- RFC-6724 policy table (verbatim)

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

    // --------------------------------------------------------------------- constructors

    /**
     * Creates an operator with the default 250 ms / 50 ms pacing.
     *
     * @param dnsResolver DNS resolver to use for host-name resolution.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver) {
        this(dnsResolver, DEFAULT_ATTEMPT_DELAY_MS);
    }

    /**
     * Creates an operator with a custom attempt delay.
     *
     * @param dnsResolver        DNS resolver to use.
     * @param attemptDelayMillis delay (in ms) between consecutive connection
     *                           attempts <em>after</em> the first two.
     *                           The first opposite-family attempt is launched
     *                           {@code min(50 ms, attemptDelayMillis)} after
     *                           the initial dial.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver,
                                                        final int attemptDelayMillis) {
        this.dnsResolver = Args.notNull(dnsResolver, "DNS resolver");
        this.attemptDelayMillis = attemptDelayMillis;
        this.otherFamilyDelayMillis = Math.min(DEFAULT_OTHER_FAMILY_DELAY_MS, attemptDelayMillis);

        final ThreadFactory tf = r -> {
            final Thread t = new Thread(r, "hc-hev2-scheduler");
            t.setDaemon(true);
            return t;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(tf);
    }

    // --------------------------------------------------------------------- public API

    /**
     * {@inheritDoc}
     *
     * <p>The returned {@link Future} completes with the first successfully
     * established {@link ManagedAsyncClientConnection}, or fails with the
     * <strong>last</strong> exception if none of the candidate addresses
     * could be reached.</p>
     */
    @Override
    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<ManagedAsyncClientConnection> callback) {

        final Timeout effTimeout = connectTimeout != null
                ? connectTimeout
                : DEFAULT_CONNECT_TIMEOUT;

        final BasicFuture<ManagedAsyncClientConnection> promise =
                new BasicFuture<>(callback);

        // ---------- fast-path: the host already carries an IP address
        final InetAddress resolved = host.getAddress();
        if (resolved != null) {
            final InetSocketAddress target = new InetSocketAddress(resolved, host.getPort());
            connectionInitiator.connect(
                    host, target, localAddress, effTimeout, attachment,
                    new ResolvedConnectCallback(promise));
            return promise;
        }

        // ---------- unresolved host: do DNS + Happy Eyeballs
        try {
            final int port = host.getPort();
            final InetAddress[] ips = dnsResolver.resolve(host.getHostName());

            final List<InetSocketAddress> addrs = new ArrayList<>(ips.length);
            for (final InetAddress ip : ips) {
                addrs.add(new InetSocketAddress(ip, port));
            }

            sortByRFC6724(addrs);
            startStaggeredConnects(connectionInitiator, host, localAddress,
                    addrs, effTimeout, attachment, promise);
        } catch (final UnknownHostException ex) {
            promise.failed(ex);
        }
        return promise;
    }

    @Override
    public void upgrade(final ManagedAsyncClientConnection conn,
                        final HttpHost host,
                        final Object attachment) {
        // no-op – TLS is handled by the selected TlsStrategy in the connection manager
    }

    // *****************************************************************
    // **         RFC 8305 connection loop implementation             **
    // *****************************************************************

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
        final List<Future<IOSession>> ioFutures =
                Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<Exception> lastErr = new AtomicReference<>();

        final Class<?> firstFamily = addrs.get(0).getAddress().getClass();

        final Runnable attemptRunner = new Runnable() {
            int idx = 0;

            @Override
            public void run() {
                if (done.get() || idx >= addrs.size()) {
                    return;
                }

                final InetSocketAddress dest = addrs.get(idx++);
                final Future<IOSession> ioFuture =
                        connectionInitiator.connect(
                                host, dest, localAddress, connectTimeout, attachment,
                                new FutureCallback<IOSession>() {
                                    @Override
                                    public void completed(final IOSession session) {
                                        if (done.compareAndSet(false, true)) {
                                            promise.completed(
                                                    new DefaultManagedAsyncClientConnection(session));
                                            ioFutures.forEach(f -> f.cancel(true));
                                        }
                                    }

                                    @Override
                                    public void failed(final Exception ex) {
                                        lastErr.set(ex);
                                        checkAllFailed();
                                    }

                                    @Override
                                    public void cancelled() {
                                        lastErr.set(new CancellationException("Cancelled"));
                                        checkAllFailed();
                                    }

                                    private void checkAllFailed() {
                                        if (done.get()) {
                                            return;
                                        }
                                        for (final Future<IOSession> f : ioFutures) {
                                            if (!f.isDone()) {
                                                return;              // another attempt is still flying
                                            }
                                        }
                                        if (idx >= addrs.size()
                                                && done.compareAndSet(false, true)) {
                                            promise.failed(lastErr.get() != null
                                                    ? lastErr.get()
                                                    : new ConnectException("All connection attempts failed"));
                                        }
                                    }
                                });
                ioFutures.add(ioFuture);

                // schedule next attempt
                if (idx < addrs.size()) {
                    final Class<?> nextFamily = addrs.get(idx).getAddress().getClass();
                    final int delay = (idx == 1 && nextFamily != firstFamily)
                            ? otherFamilyDelayMillis
                            : attemptDelayMillis;
                    scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
                }
            }
        };
        attemptRunner.run();                  // kick-off immediately
    }

    // *****************************************************************
    // **                 RFC-6724 helper utilities                   **
    // *****************************************************************

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
        return new PolicyTableEntry(null, 40, 1);   // default
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

    // --- RFC 6724 rule comparator (rules 3, 4 & 7 still TODO) ---------------------

    private static int compareByRFC6724(final ByRFC6724Info a,
                                        final ByRFC6724Info b) {

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
        if (a.getClass() != b.getClass()) {
            return 0;
        }
        final byte[] aa = a.getAddress();
        final byte[] bb = b.getAddress();
        final int len = Math.min(aa.length, 8);
        int bits = 0;
        for (int i = 0; i < len; i++) {
            if (aa[i] != bb[i]) {
                final int x = aa[i] & 0xff;
                final int y = bb[i] & 0xff;
                for (int j = 7; j >= 0; j--) {
                    if (((x ^ y) & (1 << j)) != 0) {
                        return bits;
                    }
                    bits++;
                }
                return bits;
            }
            bits += 8;
        }
        return bits;
    }

    // --------------------------------------------------------------------- helper classes

    /**
     * Address scope enumeration (RFC 4007 §5).
     */
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

    /**
     * Lightweight container for precedence / label / scope triplets.
     */
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

    /**
     * One row of the RFC 6724 policy table.
     */
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

    /**
     * IPv6 network prefix helper.
     */
    private static final class IPNetwork {
        final byte[] ip;        // always 16 bytes
        final int bits;

        IPNetwork(final byte[] ip, final int bits) {
            this.ip = ip;
            this.bits = bits;
        }

        boolean contains(final InetAddress addr) {
            final byte[] a = addr instanceof Inet4Address
                    ? v4toMapped(addr.getAddress())
                    : addr.getAddress();
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

    /**
     * Bundle of destination & source attrs for the RFC 6724 comparator.
     */
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

    // --------------------------------------------------------------------- shutdown support

    /**
     * Stops the internal scheduler.
     *
     * @param mode {@link CloseMode#GRACEFUL} waits for already scheduled
     *             tasks to finish, {@link CloseMode#IMMEDIATE} cancels them
     *             immediately.
     */
    public void shutdown(final CloseMode mode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutting down Happy-Eyeballs scheduler ({})", mode);
        }
        if (mode == CloseMode.GRACEFUL) {
            scheduler.shutdown();
        } else {
            scheduler.shutdownNow();
        }
    }

    /**
     * Convenience wrapper for {@link #shutdown(CloseMode) GRACEFUL}.
     */
    public void shutdown() {
        shutdown(CloseMode.GRACEFUL);
    }

    // --------------------------------------------------------------------- callbacks

    /**
     * Fast-path callback used when the target host is already an IP literal.
     */
    private static final class ResolvedConnectCallback
            implements FutureCallback<IOSession> {

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
