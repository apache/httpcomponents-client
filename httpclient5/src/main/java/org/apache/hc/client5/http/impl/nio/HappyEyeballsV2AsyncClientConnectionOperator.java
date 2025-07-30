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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
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
 * Happy Eyeballs V2 (RFC 8305) implementation for asynchronous client connection operator.
 * <p>
 * This class performs dual-stack (IPv6 and IPv4) address resolution and connection attempts in a staggered manner
 * to minimize connection latency, as recommended by RFC 8305. It integrates RFC 6724 address selection rules for
 * sorting candidate addresses based on precedence, scope, and other criteria.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>DNS resolution for unresolved hosts using the provided {@link DnsResolver}.</li>
 *   <li>Sorting of resolved addresses according to RFC 6724 rules, including source address discovery via UDP probes.</li>
 *   <li>Staggered connection attempts with a configurable delay (default: 250ms as per RFC 8305 for HTTP).</li>
 *   <li>Immediate cancellation of pending attempts upon successful connection.</li>
 *   <li>Support for resolved hosts (bypasses DNS and sorting).</li>
 *   <li>No-op upgrade method (extend for TLS if needed).</li>
 * </ul>
 * </p>
 * <p>
 * Usage example:
 * <pre>
 * DnsResolver dnsResolver = SystemDefaultDnsResolver.INSTANCE;
 * AsyncClientConnectionOperator operator = new HappyEyeballsV2AsyncClientConnectionOperator(dnsResolver);
 * PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
 *         .setConnectionOperator(operator)
 *         .build();
 * </pre>
 * </p>
 * <p>
 * Note: This implementation is a minimal port of RFC 6724, with some rules (e.g., deprecated addresses, home addresses)
 * marked as TODO for future enhancement.
 * </p>
 *
 * @see <a href="https://tools.ietf.org/html/rfc8305">RFC 8305 - Happy Eyeballs Version 2</a>
 * @see <a href="https://tools.ietf.org/html/rfc6724">RFC 6724 - Default Address Selection for IPv6</a>
 * @see org.apache.hc.client5.http.nio.AsyncClientConnectionOperator
 * @since 5.3
 */
public final class HappyEyeballsV2AsyncClientConnectionOperator implements AsyncClientConnectionOperator {

    private static final Logger LOG = LoggerFactory.getLogger(HappyEyeballsV2AsyncClientConnectionOperator.class);


    /**
     * The DNS resolver for host name resolution.
     */
    private final DnsResolver dnsResolver;

    /**
     * The connection attempt delay in milliseconds (default 250ms as per RFC 8305 for HTTP).
     */
    private final int connectionAttemptDelayMillis;

    /**
     * Scheduler for staggering connection attempts.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * The policy table for RFC 6724.
     */
    private static final List<PolicyTableEntry> POLICY_TABLE = Collections.unmodifiableList(Arrays.asList(
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
     * Constructs a new instance.
     *
     * @param dnsResolver the DNS resolver.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver) {
        this(dnsResolver, 250);
    }

    /**
     * Constructs a new instance with custom connection attempt delay.
     *
     * @param dnsResolver                  the DNS resolver.
     * @param connectionAttemptDelayMillis the delay between connection attempts in milliseconds.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final DnsResolver dnsResolver, final int connectionAttemptDelayMillis) {
        this.dnsResolver = Args.notNull(dnsResolver, "DNS resolver");
        this.connectionAttemptDelayMillis = connectionAttemptDelayMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        final BasicFuture<ManagedAsyncClientConnection> future = new BasicFuture<>(callback);
        final InetAddress address = host.getAddress();
        if (address != null) {
            // Resolved address
            final InetSocketAddress remoteAddr = new InetSocketAddress(address, host.getPort());
            connectionInitiator.connect(
                    host,
                    remoteAddr,
                    localAddress,
                    connectTimeout,
                    attachment,
                    new FutureCallback<IOSession>() {

                        @Override
                        public void completed(final IOSession session) {
                            final ManagedAsyncClientConnection conn = new DefaultManagedAsyncClientConnection(session);
                            future.completed(conn);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            future.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            future.cancel(true);
                        }

                    });
            return future;
        } else {
            try {
                final String hostname = host.getHostName();
                final int port = host.getPort();
                final InetAddress[] ips = dnsResolver.resolve(hostname);
                final List<InetSocketAddress> addrs = new ArrayList<>(ips.length);
                for (final InetAddress ip : ips) {
                    addrs.add(new InetSocketAddress(ip, port));
                }
                sortByRFC6724(addrs);
                startStaggeredConnects(connectionInitiator, host, localAddress, addrs, connectTimeout, attachment, future);
            } catch (final UnknownHostException ex) {
                future.failed(ex);
            }
            return future;
        }
    }

    @Override
    public void upgrade(final ManagedAsyncClientConnection conn, final HttpHost host, final Object attachment) {
        // Default no-op or delegate to TLS upgrade if needed
    }

    /**
     * Sorts the list of addresses according to RFC 6724.
     *
     * @param addrs the list of socket addresses to sort.
     */
    @Internal
    static void sortByRFC6724(final List<InetSocketAddress> addrs) {
        if (addrs.size() < 2) {
            return;
        }
        final List<InetAddress> srcs = srcAddrs(addrs);
        final List<ByRFC6724Info> infos = new ArrayList<>(addrs.size());
        for (int i = 0; i < addrs.size(); i++) {
            final InetAddress addr = addrs.get(i).getAddress();
            final ByRFC6724Info info = new ByRFC6724Info();
            info.addr = addr;
            info.addrAttr = ipAttrOf(addr);
            info.src = srcs.get(i);
            info.srcAttr = ipAttrOf(info.src);
            infos.add(info);
        }
        infos.sort(HappyEyeballsV2AsyncClientConnectionOperator::compareByRFC6724);
        for (int i = 0; i < infos.size(); i++) {
            addrs.set(i, new InetSocketAddress(infos.get(i).addr, addrs.get(i).getPort()));
        }
    }

    /**
     * Retrieves source addresses for each destination by attempting UDP connect.
     *
     * @param addrs the list of destination addresses.
     * @return the list of source addresses.
     */
    private static List<InetAddress> srcAddrs(final List<InetSocketAddress> addrs) {
        final List<InetAddress> srcs = new ArrayList<>(addrs.size());
        for (final InetSocketAddress addr : addrs) {
            InetAddress src = null;
            try (final DatagramSocket sock = new DatagramSocket()) {
                sock.connect(addr);
                src = sock.getLocalAddress();
            } catch (final SocketException ignored) {
                // Ignore, source undefined
            }
            srcs.add(src);
        }
        return srcs;
    }

    /**
     * Computes IP attributes for the given address.
     *
     * @param ip the IP address.
     * @return the IP attributes.
     */
    private static IpAttr ipAttrOf(final InetAddress ip) {
        if (ip == null) {
            return new IpAttr(Scope.GLOBAL, 0, 0);  // Invalid
        }
        final PolicyTableEntry match = classify(ip);
        final Scope scope = classifyScope(ip);
        return new IpAttr(scope, match.precedence, match.label);
    }

    /**
     * Classifies the address using the policy table.
     *
     * @param ip the IP address.
     * @return the matching policy table entry.
     */
    private static PolicyTableEntry classify(final InetAddress ip) {
        for (final PolicyTableEntry ent : POLICY_TABLE) {
            if (ent.prefix.contains(ip)) {
                return ent;
            }
        }
        return new PolicyTableEntry(null, 40, 1);  // Default
    }

    /**
     * Classifies the scope of the address.
     *
     * @param ip the IP address.
     * @return the scope.
     */
    private static Scope classifyScope(final InetAddress ip) {
        if (ip.isLoopbackAddress() || ip.isLinkLocalAddress()) {
            return Scope.LINK_LOCAL;
        }
        if (ip.isMulticastAddress()) {
            if (ip instanceof Inet6Address) {
                final byte[] bytes = ip.getAddress();
                return Scope.fromValue(bytes[1] & 0x0f);
            }
            return Scope.GLOBAL;
        }
        if (ip.isSiteLocalAddress()) {
            return Scope.SITE_LOCAL;
        }
        return Scope.GLOBAL;
    }

    /**
     * Computes the common prefix length between two addresses.
     *
     * @param a the first address.
     * @param b the second address.
     * @return the common prefix length in bits.
     */
    private static int commonPrefixLen(final InetAddress a, final InetAddress b) {
        if (a.getClass() != b.getClass()) {
            return 0;
        }
        final byte[] aa = a.getAddress();
        final byte[] bb = b.getAddress();
        int len = aa.length;
        if (len > 8) {
            len = 8;
        }
        int cpl = 0;
        for (int i = 0; i < len; i++) {
            if (aa[i] != bb[i]) {
                int ab = aa[i] & 0xff;
                int bv = bb[i] & 0xff;
                int bits = 8;
                while (bits > 0) {
                    ab >>>= 1;
                    bv >>>= 1;
                    bits--;
                    if (ab == bv) {
                        return cpl + bits;
                    }
                }
                return cpl;
            }
            cpl += 8;
        }
        return cpl;
    }

    /**
     * Compares two address infos according to RFC 6724 rules.
     *
     * @param a the first info.
     * @param b the second info.
     * @return -1 if a preferred, 1 if b preferred, 0 if equal.
     */
    private static int compareByRFC6724(final ByRFC6724Info a, final ByRFC6724Info b) {
        final InetAddress da = a.addr;
        final InetAddress db = b.addr;
        final InetAddress sourceDa = a.src;
        final InetAddress sourceDb = b.src;
        final IpAttr attrDa = a.addrAttr;
        final IpAttr attrDb = b.addrAttr;
        final IpAttr attrSourceDa = a.srcAttr;
        final IpAttr attrSourceDb = b.srcAttr;

        final int preferDa = -1;
        final int preferDb = 1;

        // Rule 1: Avoid unusable destinations.
        final boolean validDa = sourceDa != null && !sourceDa.isAnyLocalAddress();
        final boolean validDb = sourceDb != null && !sourceDb.isAnyLocalAddress();
        if (!validDa && !validDb) {
            return 0;
        }
        if (!validDb) {
            return preferDa;
        }
        if (!validDa) {
            return preferDb;
        }

        // Rule 2: Prefer matching scope.
        if (attrDa.scope == attrSourceDa.scope && attrDb.scope != attrSourceDb.scope) {
            return preferDa;
        }
        if (attrDa.scope != attrSourceDa.scope && attrDb.scope == attrSourceDb.scope) {
            return preferDb;
        }

        // Rule 3: Avoid deprecated addresses. (TODO)

        // Rule 4: Prefer home addresses. (TODO)

        // Rule 5: Prefer matching label.
        if (attrSourceDa.label == attrDa.label && attrSourceDb.label != attrDb.label) {
            return preferDa;
        }
        if (attrSourceDa.label != attrDa.label && attrSourceDb.label == attrDb.label) {
            return preferDb;
        }

        // Rule 6: Prefer higher precedence.
        if (attrDa.precedence > attrDb.precedence) {
            return preferDa;
        }
        if (attrDa.precedence < attrDb.precedence) {
            return preferDb;
        }

        // Rule 7: Prefer native transport. (TODO)

        // Rule 8: Prefer smaller scope.
        if (attrDa.scope.value < attrDb.scope.value) {
            return preferDa;
        }
        if (attrDa.scope.value > attrDb.scope.value) {
            return preferDb;
        }

        // Rule 9: Use longest matching prefix for IPv6.
        if (da instanceof Inet6Address && db instanceof Inet6Address) {
            final int commonA = commonPrefixLen(sourceDa, da);
            final int commonB = commonPrefixLen(sourceDb, db);
            if (commonA > commonB) {
                return preferDa;
            }
            if (commonA < commonB) {
                return preferDb;
            }
        }

        // Rule 10: Equal.
        return 0;
    }

    /**
     * Starts staggered connection attempts.
     *
     * @param connectionInitiator the connection initiator.
     * @param host                the remote host.
     * @param localAddress        the local address.
     * @param addrs               the sorted list of addresses.
     * @param connectTimeout      the connect timeout.
     * @param attachment          the attachment.
     * @param future              the future to complete.
     */
    private void startStaggeredConnects(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final List<InetSocketAddress> addrs,
            final Timeout connectTimeout,
            final Object attachment,
            final BasicFuture<ManagedAsyncClientConnection> future) {
        if (addrs.isEmpty()) {
            future.failed(new UnknownHostException("No addresses"));
            return;
        }
        final AtomicBoolean done = new AtomicBoolean(false);
        final List<Future<IOSession>> ioFutures = Collections.synchronizedList(new ArrayList<>());

        final Runnable attemptRunner = new Runnable() {
            int index = 0;

            @Override
            public void run() {
                if (done.get() || index >= addrs.size()) {
                    return;
                }
                final InetSocketAddress addr = addrs.get(index);
                index++;
                final Future<IOSession> ioFuture = connectionInitiator.connect(
                        host,
                        addr,
                        localAddress,
                        connectTimeout,
                        attachment,
                        new FutureCallback<IOSession>() {

                            @Override
                            public void completed(final IOSession session) {
                                final ManagedAsyncClientConnection conn = new DefaultManagedAsyncClientConnection(session);
                                if (done.compareAndSet(false, true)) {
                                    future.completed(conn);
                                    // Cancel others
                                    for (final Future<IOSession> f : ioFutures) {
                                        f.cancel(true);
                                    }
                                }
                            }

                            @Override
                            public void failed(final Exception ex) {
                                checkAllFailed(ex);
                            }

                            @Override
                            public void cancelled() {
                                checkAllFailed(new CancellationException("Cancelled"));
                            }

                            private void checkAllFailed(final Exception lastEx) {
                                if (done.get()) {
                                    return;
                                }
                                boolean allDone = true;
                                for (final Future<IOSession> f : ioFutures) {
                                    if (!f.isDone()) {
                                        allDone = false;
                                        break;
                                    }
                                }
                                if (allDone && done.compareAndSet(false, true)) {
                                    future.failed(lastEx);
                                }
                            }

                        });
                ioFutures.add(ioFuture);
                if (index < addrs.size()) {
                    scheduler.schedule(this, connectionAttemptDelayMillis, TimeUnit.MILLISECONDS);
                }
            }
        };
        attemptRunner.run();
    }

    /**
     * IP attribute class.
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
     * Scope enum.
     */
    private enum Scope {
        INTERFACE_LOCAL(0x1),
        LINK_LOCAL(0x2),
        ADMIN_LOCAL(0x4),
        SITE_LOCAL(0x5),
        ORG_LOCAL(0x8),
        GLOBAL(0xe);

        final int value;

        Scope(final int value) {
            this.value = value;
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
     * Policy table entry.
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
     * IP network prefix.
     */
    private static final class IPNetwork {
        final byte[] ip;
        final int bits;

        IPNetwork(final byte[] ip, final int bits) {
            this.ip = ip;
            this.bits = bits;
        }

        boolean contains(final InetAddress addr) {
            byte[] a = addr.getAddress();
            if (addr instanceof Inet4Address) {
                a = new byte[16];
                a[10] = (byte) 0xff;
                a[11] = (byte) 0xff;
                System.arraycopy(addr.getAddress(), 0, a, 12, 4);
            }
            if (a.length != ip.length) {
                return false;
            }
            final int bytes = bits / 8;
            for (int i = 0; i < bytes; i++) {
                if (a[i] != ip[i]) {
                    return false;
                }
            }
            final int rem = bits % 8;
            if (rem > 0) {
                final int m = 0xff << (8 - rem);
                return (a[bytes] & m) == (ip[bytes] & m);
            }
            return true;
        }
    }

    /**
     * Creates IP network from string prefix.
     *
     * @param s    the prefix string.
     * @param bits the prefix bits.
     * @return the IP network.
     */
    private static IPNetwork toPrefix(final String s, final int bits) {
        try {
            final InetAddress ip = InetAddress.getByName(s);
            return new IPNetwork(ip.getAddress(), bits);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * RFC 6724 info class.
     */
    private static final class ByRFC6724Info {
        InetAddress addr;
        IpAttr addrAttr;
        InetAddress src;
        IpAttr srcAttr;
    }

    public void shutdown(final CloseMode closeMode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutdown ScheduledExecutorService {}", closeMode);
        }
        if (closeMode == CloseMode.GRACEFUL) {
            scheduler.shutdown();
        } else {
            scheduler.shutdownNow();
        }
    }

    public void shutdown() {
        shutdown(CloseMode.GRACEFUL);
    }

}