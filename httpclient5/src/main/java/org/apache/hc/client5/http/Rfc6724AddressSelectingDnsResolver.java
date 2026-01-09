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

package org.apache.hc.client5.http;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code Rfc6724AddressSelectingDnsResolver} wraps a delegate {@link DnsResolver}
 * and applies RFC&nbsp;6724 destination address selection rules to the returned
 * addresses. It can also enforce or bias a protocol family preference.
 *
 * <p>The canonical hostname lookup is delegated unchanged.</p>
 *
 * <p>
 * {@link ProtocolFamilyPreference#INTERLEAVE} is treated as "no family bias":
 * the resolver keeps the RFC 6724 sorted order intact. Family interleaving, if
 * desired, should be handled at dial time (e.g. Happy Eyeballs).
 * </p>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class Rfc6724AddressSelectingDnsResolver implements DnsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(Rfc6724AddressSelectingDnsResolver.class);

    private static final int PROBE_PORT = 53; // UDP connect trick; no packets sent

    private final DnsResolver delegate;
    private final ProtocolFamilyPreference familyPreference;

    /**
     * Creates a new resolver that applies RFC 6724 ordering with no family bias (INTERLEAVE).
     *
     * @param delegate underlying resolver to use.
     */
    public Rfc6724AddressSelectingDnsResolver(final DnsResolver delegate) {
        this(delegate, ProtocolFamilyPreference.INTERLEAVE);
    }

    /**
     * Creates a new resolver that applies RFC 6724 ordering and a specific protocol family preference.
     *
     * @param delegate         underlying resolver to use.
     * @param familyPreference family preference to apply (e.g. PREFER_IPV6, IPV4_ONLY).
     */
    public Rfc6724AddressSelectingDnsResolver(
            final DnsResolver delegate,
            final ProtocolFamilyPreference familyPreference) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.familyPreference = familyPreference != null ? familyPreference : ProtocolFamilyPreference.INTERLEAVE;
    }

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} resolving host '{}' via delegate {}", simpleName(), host, delegate.getClass().getName());
            LOG.debug("{} familyPreference={}", simpleName(), familyPreference);
        }

        final InetAddress[] resolved = delegate.resolve(host);
        if (resolved == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} delegate returned null for '{}'", simpleName(), host);
            }
            return null;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} delegate returned {} addresses for '{}': {}", simpleName(), resolved.length, host, fmt(resolved));
        }
        if (resolved.length <= 1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} nothing to sort/filter (<=1 address). Returning as-is.", simpleName());
            }
            return resolved;
        }

        // 1) Filter by family if forced
        final List<InetAddress> candidates = new ArrayList<>(resolved.length);
        switch (familyPreference) {
            case IPV4_ONLY: {
                for (final InetAddress a : resolved) {
                    if (a instanceof Inet4Address) {
                        candidates.add(a);
                    }
                }
                break;
            }
            case IPV6_ONLY: {
                for (final InetAddress a : resolved) {
                    if (a instanceof Inet6Address) {
                        candidates.add(a);
                    }
                }
                break;
            }
            default: {
                candidates.addAll(Arrays.asList(resolved));
                break;
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} after family filter {} -> {} candidate(s): {}", simpleName(), familyPreference, candidates.size(), fmt(candidates));
        }

        if (candidates.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} no address of requested family; returning empty for '{}'", simpleName(), host);
            }
            return new InetAddress[0];
        }

        // 2) RFC 6724 sort (uses UDP connect to infer source addresses; no packets sent)
        final List<InetAddress> rfcSorted = sortByRfc6724(candidates);

        // 3) Apply preference bias
        final List<InetAddress> ordered = applyFamilyPreference(rfcSorted, familyPreference);

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} final ordered list for '{}': {}", simpleName(), host, fmt(ordered));
        }

        return ordered.toArray(new InetAddress[0]);
    }

    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} resolveCanonicalHostname('{}') via delegate {}", simpleName(), host, delegate.getClass().getName());
        }
        return delegate.resolveCanonicalHostname(host);
    }

    // --- RFC 6724 helpers ---

    private static List<InetAddress> sortByRfc6724(final List<InetAddress> addrs) {
        if (addrs.size() < 2) {
            return addrs;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("RFC6724 input candidates: {}", fmt(addrs));
        }

        final List<InetSocketAddress> sockAddrs = new ArrayList<>(addrs.size());
        for (final InetAddress a : addrs) {
            sockAddrs.add(new InetSocketAddress(a, PROBE_PORT));
        }
        final List<InetAddress> srcs = srcAddrs(sockAddrs);

        final List<Info> infos = new ArrayList<>(addrs.size());
        for (int i = 0; i < addrs.size(); i++) {
            final InetAddress dst = addrs.get(i);
            final InetAddress src = srcs.get(i);
            infos.add(new Info(dst, src, ipAttrOf(dst), ipAttrOf(src)));
        }

        if (LOG.isDebugEnabled()) {
            for (final Info info : infos) {
                LOG.debug("RFC6724 candidate dst={} src={} dst[scope={},prec={},label={}] src[scope={},prec={},label={}]",
                        addr(info.dst), addr(info.src),
                        info.dstAttr.scope, info.dstAttr.precedence, info.dstAttr.label,
                        info.srcAttr.scope, info.srcAttr.precedence, info.srcAttr.label);
            }
        }

        infos.sort(RFC6724_COMPARATOR);

        final List<InetAddress> out = new ArrayList<>(infos.size());
        for (final Info info : infos) {
            out.add(info.dst);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("RFC6724 output order: {}", fmt(out));
        }
        return out;
    }

    private static List<InetAddress> applyFamilyPreference(
            final List<InetAddress> rfcSorted,
            final ProtocolFamilyPreference pref) {

        if (rfcSorted.size() <= 1) {
            return rfcSorted;
        }

        switch (pref) {
            case PREFER_IPV6:
            case PREFER_IPV4: {
                final boolean preferV6 = pref == ProtocolFamilyPreference.PREFER_IPV6;
                final List<InetAddress> first = new ArrayList<>();
                final List<InetAddress> second = new ArrayList<>();
                for (final InetAddress a : rfcSorted) {
                    final boolean isV6 = a instanceof Inet6Address;
                    if (preferV6 && isV6 || !preferV6 && !isV6) {
                        first.add(a);
                    } else {
                        second.add(a);
                    }
                }
                final List<InetAddress> merged = new ArrayList<>(rfcSorted.size());
                merged.addAll(first);
                merged.addAll(second);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Family preference {} applied. First bucket={}, second bucket={}", pref, fmt(first), fmt(second));
                    LOG.debug("Family preference output: {}", fmt(merged));
                }
                return merged;
            }
            case IPV4_ONLY:
            case IPV6_ONLY: {
                // already filtered earlier
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Family preference {} enforced earlier. Order unchanged: {}", pref, fmt(rfcSorted));
                }
                return rfcSorted;
            }
            case INTERLEAVE:
            default: {
                // No family bias. Keep RFC 6724 order intact.
                if (LOG.isDebugEnabled()) {
                    LOG.debug("INTERLEAVE treated as no-bias. Order unchanged: {}", fmt(rfcSorted));
                }
                return rfcSorted;
            }
        }
    }

    private static List<InetAddress> srcAddrs(final List<InetSocketAddress> addrs) {
        final List<InetAddress> srcs = new ArrayList<>(addrs.size());
        for (final InetSocketAddress dest : addrs) {
            InetAddress src = null;
            try (final DatagramSocket s = new DatagramSocket()) {
                s.connect(dest); // does not send packets; OS picks source addr/if
                src = s.getLocalAddress();
            } catch (final SocketException ignore) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("RFC6724 could not infer source address for {}: {}", dest, ignore.toString());
                }
            }
            srcs.add(src);
        }
        if (LOG.isDebugEnabled()) {
            final List<String> printable = new ArrayList<>(srcs.size());
            for (final InetAddress a : srcs) {
                printable.add(addr(a));
            }
            LOG.debug("RFC6724 inferred source addresses: {}", printable);
        }
        return srcs;
    }

    // --- RFC 6724 score structs ---

    private static final class Info {
        final InetAddress dst;
        final InetAddress src;
        final Attr dstAttr;
        final Attr srcAttr;

        Info(final InetAddress dst, final InetAddress src, final Attr dstAttr, final Attr srcAttr) {
            this.dst = dst;
            this.src = src;
            this.dstAttr = dstAttr;
            this.srcAttr = srcAttr;
        }
    }

    private static final class Attr {
        final Scope scope;
        final int precedence;
        final int label;

        Attr(final Scope scope, final int precedence, final int label) {
            this.scope = scope;
            this.precedence = precedence;
            this.label = label;
        }
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
                case 0x1: {
                    return INTERFACE_LOCAL;
                }
                case 0x2: {
                    return LINK_LOCAL;
                }
                case 0x4: {
                    return ADMIN_LOCAL;
                }
                case 0x5: {
                    return SITE_LOCAL;
                }
                case 0x8: {
                    return ORG_LOCAL;
                }
                default: {
                    return GLOBAL;
                }
            }
        }
    }

    private static Attr ipAttrOf(final InetAddress ip) {
        if (ip == null) {
            return new Attr(Scope.GLOBAL, 0, 0);
        }
        final PolicyEntry e = classify(ip);
        return new Attr(classifyScope(ip), e.precedence, e.label);
    }

    private static Scope classifyScope(final InetAddress ip) {
        if (ip.isLoopbackAddress()) {
            return Scope.INTERFACE_LOCAL;
        }
        if (ip.isLinkLocalAddress()) {
            return Scope.LINK_LOCAL;
        }
        if (ip.isMulticastAddress()) {
            if (ip instanceof Inet6Address) {
                // RFC 4291: low 4 bits of second byte are scope.
                return Scope.fromValue(ip.getAddress()[1] & 0x0f);
            }
            return Scope.GLOBAL;
        }
        if (ip.isSiteLocalAddress()) {
            return Scope.SITE_LOCAL;
        }
        return Scope.GLOBAL;
    }

    private static final class PolicyEntry {
        final Network prefix;
        final int precedence;
        final int label;

        PolicyEntry(final Network prefix, final int precedence, final int label) {
            this.prefix = prefix;
            this.precedence = precedence;
            this.label = label;
        }
    }

    private static final class Network {
        final byte[] ip;
        final int bits;

        Network(final byte[] ip, final int bits) {
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
            final int mask = 0xff << 8 - rem;
            return (a[fullBytes] & mask) == (ip[fullBytes] & mask);
        }

        private static byte[] v4toMapped(final byte[] v4) {
            final byte[] mapped = new byte[16];
            mapped[10] = (byte) 0xff;
            mapped[11] = (byte) 0xff;
            System.arraycopy(v4, 0, mapped, 12, 4);
            return mapped;
        }
    }

    private static Network toPrefix(final String text, final int bits) {
        try {
            return new Network(InetAddress.getByName(text).getAddress(), bits);
        } catch (final UnknownHostException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static final List<PolicyEntry> POLICY_TABLE =
            Collections.unmodifiableList(Arrays.asList(
                    new PolicyEntry(toPrefix("::1", 128), 50, 0),
                    new PolicyEntry(toPrefix("::ffff:0:0", 96), 35, 4),
                    new PolicyEntry(toPrefix("::", 96), 1, 3),
                    new PolicyEntry(toPrefix("2001::", 32), 5, 5),
                    new PolicyEntry(toPrefix("2002::", 16), 30, 2),
                    new PolicyEntry(toPrefix("3ffe::", 16), 1, 12),
                    new PolicyEntry(toPrefix("fec0::", 10), 1, 11),
                    new PolicyEntry(toPrefix("fc00::", 7), 3, 13),
                    new PolicyEntry(toPrefix("::", 0), 40, 1)
            ));

    private static PolicyEntry classify(final InetAddress ip) {
        for (final PolicyEntry e : POLICY_TABLE) {
            if (e.prefix.contains(ip)) {
                return e;
            }
        }
        return new PolicyEntry(null, 40, 1);
    }

    private static final Comparator<Info> RFC6724_COMPARATOR = (a, b) -> {
        final InetAddress DA = a.dst;
        final InetAddress DB = b.dst;
        final InetAddress SourceDA = a.src;
        final InetAddress SourceDB = b.src;
        final Attr attrDA = a.dstAttr;
        final Attr attrDB = b.dstAttr;
        final Attr attrSourceDA = a.srcAttr;
        final Attr attrSourceDB = b.srcAttr;

        final int preferDA = -1;
        final int preferDB = 1;

        // Rule 1: Avoid unusable destinations.
        final boolean validA = SourceDA != null && !SourceDA.isAnyLocalAddress();
        final boolean validB = SourceDB != null && !SourceDB.isAnyLocalAddress();
        if (!validA && !validB) {
            return 0;
        }
        if (!validB) {
            return preferDA;
        }
        if (!validA) {
            return preferDB;
        }

        // Rule 2: Prefer matching scope.
        if (attrDA.scope == attrSourceDA.scope && attrDB.scope != attrSourceDB.scope) {
            return preferDA;
        }
        if (attrDA.scope != attrSourceDA.scope && attrDB.scope == attrSourceDB.scope) {
            return preferDB;
        }

        // Rule 5: Prefer matching label.
        if (attrSourceDA.label == attrDA.label && attrSourceDB.label != attrDB.label) {
            return preferDA;
        }
        if (attrSourceDA.label != attrDA.label && attrSourceDB.label == attrDB.label) {
            return preferDB;
        }

        // Rule 6: Prefer higher precedence.
        if (attrDA.precedence > attrDB.precedence) {
            return preferDA;
        }
        if (attrDA.precedence < attrDB.precedence) {
            return preferDB;
        }

        // Rule 8: Prefer smaller scope.
        if (attrDA.scope.value < attrDB.scope.value) {
            return preferDA;
        }
        if (attrDA.scope.value > attrDB.scope.value) {
            return preferDB;
        }

        // Rule 9: Longest common prefix (IPv6 only).
        if (DA instanceof Inet6Address && DB instanceof Inet6Address) {
            final int commonA = commonPrefixLen(SourceDA, DA);
            final int commonB = commonPrefixLen(SourceDB, DB);
            if (commonA > commonB) {
                return preferDA;
            }
            if (commonA < commonB) {
                return preferDB;
            }
        }

        // Rule 10: Otherwise equal (original order preserved by stable sort).
        return 0;
    };

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
                    if ((x & 1 << j) != 0) {
                        return bits;
                    }
                    bits++;
                }
                return bits;
            }
        }
        return bits;
    }

    private static String addr(final InetAddress a) {
        if (a == null) {
            return "null";
        }
        final String family = a instanceof Inet6Address ? "IPv6" : "IPv4";
        return family + "(" + a.getHostAddress() + ")";
    }

    private static List<String> fmt(final InetAddress[] arr) {
        final List<String> out = new ArrayList<>(arr.length);
        for (final InetAddress a : arr) {
            out.add(addr(a));
        }
        return out;
    }

    private static List<String> fmt(final List<InetAddress> arr) {
        final List<String> out = new ArrayList<>(arr.size());
        for (final InetAddress a : arr) {
            out.add(addr(a));
        }
        return out;
    }

    private static String simpleName() {
        return "Rfc6724Resolver";
    }
}
