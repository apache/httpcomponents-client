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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code AddressSelectingDnsResolver} wraps a delegate {@link DnsResolver}
 * and applies destination address selection rules (scope, precedence, label matching,
 * longest-prefix) to the returned addresses. It can also enforce or bias a protocol
 * family preference.
 *
 * <p>The canonical hostname lookup is delegated unchanged.</p>
 *
 * <p>
 * {@link ProtocolFamilyPreference#DEFAULT} keeps the sorted order intact (no family bias).
 * {@link ProtocolFamilyPreference#INTERLEAVE} interleaves IPv6 and IPv4 addresses (v6, v4, v6, …),
 * preserving the relative order within each family as produced by destination address sorting.
 * </p>
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class AddressSelectingDnsResolver implements DnsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AddressSelectingDnsResolver.class);

    private static final int PROBE_PORT = 53; // UDP connect trick; no packets sent

    @FunctionalInterface
    interface SourceAddressResolver {
        InetAddress resolveSource(final InetSocketAddress destination) throws SocketException;
    }

    private static final SourceAddressResolver DEFAULT_SOURCE_ADDRESS_RESOLVER = destination -> {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(destination);
            return socket.getLocalAddress();
        }
    };

    private final DnsResolver delegate;
    private final ProtocolFamilyPreference familyPreference;
    private final SourceAddressResolver sourceAddressResolver;

    /**
     * Creates a new resolver that applies destination address ordering with no family bias (DEFAULT).
     *
     * @param delegate underlying resolver to use.
     */
    public AddressSelectingDnsResolver(final DnsResolver delegate) {
        this(delegate, ProtocolFamilyPreference.DEFAULT);
    }

    /**
     * Creates a new resolver that applies destination address ordering and a specific protocol family preference.
     *
     * @param delegate         underlying resolver to use.
     * @param familyPreference family preference to apply (e.g. PREFER_IPV6, IPV4_ONLY).
     */
    public AddressSelectingDnsResolver(
            final DnsResolver delegate,
            final ProtocolFamilyPreference familyPreference) {
        this(delegate, familyPreference, DEFAULT_SOURCE_ADDRESS_RESOLVER);
    }

    // Package-private for unit tests: allows deterministic source address inference.
    AddressSelectingDnsResolver(
            final DnsResolver delegate,
            final ProtocolFamilyPreference familyPreference,
            final SourceAddressResolver sourceAddressResolver) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.familyPreference = familyPreference != null ? familyPreference : ProtocolFamilyPreference.DEFAULT;
        this.sourceAddressResolver = sourceAddressResolver != null ? sourceAddressResolver : DEFAULT_SOURCE_ADDRESS_RESOLVER;
    }

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        final InetAddress[] resolved = delegate.resolve(host);

        if (resolved == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("resolved '{}' -> null", host);
            }
            return null;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("resolving host '{}' via delegate {}", host, delegate.getClass().getName());
            LOG.trace("familyPreference={}", familyPreference);
            LOG.trace("delegate returned {} addresses for '{}': {}", resolved.length, host, fmt(resolved));
        }

        final List<InetAddress> candidates = filterCandidates(resolved, familyPreference);

        if (candidates.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("resolved '{}' -> []", host);
            }
            return null;
        }

        if (candidates.size() == 1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("resolved '{}' -> {}", host, fmt(candidates));
            }
            return candidates.toArray(new InetAddress[0]);
        }

        final List<InetAddress> sorted = sortByDestinationAddressSelection(candidates);
        final List<InetAddress> ordered = applyFamilyPreference(sorted, familyPreference);

        if (LOG.isDebugEnabled()) {
            LOG.debug("resolved '{}' -> {}", host, fmt(ordered));
        }

        return ordered.toArray(new InetAddress[0]);
    }

    @Override
    public List<InetSocketAddress> resolve(final String host, final int port) throws UnknownHostException {
        final InetAddress[] addresses = resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        return Arrays.stream(addresses)
                .map(a -> new InetSocketAddress(a, port))
                .collect(Collectors.toList());
    }

    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("resolveCanonicalHostname('{}') via delegate {}", host, delegate.getClass().getName());
        }
        return delegate.resolveCanonicalHostname(host);
    }

    private static boolean isUsableDestination(final InetAddress ip) {
        if (ip == null) {
            return false;
        }
        if (ip.isAnyLocalAddress()) {
            return false;
        }
        // HTTP/TCP is for unicast destinations; multicast is not a valid connect target.
        return !ip.isMulticastAddress();
    }


    private static List<InetAddress> filterCandidates(
            final InetAddress[] resolved,
            final ProtocolFamilyPreference pref) {

        return Arrays.stream(resolved)
                .filter(AddressSelectingDnsResolver::isUsableDestination)
                .filter(a -> shouldInclude(pref, a))
                .collect(Collectors.toList());
    }

    private static boolean shouldInclude(final ProtocolFamilyPreference pref, final InetAddress a) {
        switch (pref) {
            case IPV4_ONLY:
                return a instanceof Inet4Address;
            case IPV6_ONLY:
                return a instanceof Inet6Address;
            default:
                return true;
        }
    }


    // --- Destination address selection helpers ---

    private List<InetAddress> sortByDestinationAddressSelection(final List<InetAddress> addrs) {
        if (addrs.size() < 2) {
            return addrs;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("address-selection input candidates: {}", fmt(addrs));
        }

        final List<Info> infos = new ArrayList<>(addrs.size());
        for (final InetAddress dst : addrs) {
            final InetAddress src = inferSourceAddress(new InetSocketAddress(dst, PROBE_PORT));
            infos.add(new Info(dst, src, ipAttrOf(dst), ipAttrOf(src)));
        }

        if (LOG.isTraceEnabled()) {
            for (final Info info : infos) {
                LOG.trace("address-selection candidate dst={} src={} dst[scope={},prec={},label={}] src[scope={},prec={},label={}]",
                        addr(info.dst), addr(info.src),
                        info.dstAttr.scope, info.dstAttr.precedence, info.dstAttr.label,
                        info.srcAttr.scope, info.srcAttr.precedence, info.srcAttr.label);
            }
        }

        infos.sort(DESTINATION_ADDRESS_COMPARATOR);

        final List<InetAddress> out = infos.stream()
                .map(info -> info.dst)
                .collect(Collectors.toList());

        if (LOG.isTraceEnabled()) {
            LOG.trace("address-selection output order: {}", fmt(out));
        }

        return out;
    }

    private InetAddress inferSourceAddress(final InetSocketAddress destination) {
        try {
            return sourceAddressResolver.resolveSource(destination);
        } catch (final SocketException ex) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("address-selection could not infer source address for {}: {}", destination, ex.toString());
            }
            return null;
        }
    }

    private static List<InetAddress> applyFamilyPreference(
            final List<InetAddress> sorted,
            final ProtocolFamilyPreference pref) {

        if (sorted.size() <= 1) {
            return sorted;
        }

        switch (pref) {
            case PREFER_IPV6:
            case PREFER_IPV4: {
                final boolean preferV6 = pref == ProtocolFamilyPreference.PREFER_IPV6;

                // Stable: preserves the destination-sorted order within each family.
                final List<InetAddress> out = sorted.stream()
                        .sorted(Comparator.comparingInt(a -> ((a instanceof Inet6Address) == preferV6) ? 0 : 1))
                        .collect(Collectors.toList());

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Family preference {} applied. Output: {}", pref, fmt(out));
                }
                return out;
            }
            case INTERLEAVE: {
                final List<InetAddress> out = interleaveFamilies(sorted);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Family preference {} applied. Output: {}", pref, fmt(out));
                }
                return out;
            }
            case IPV4_ONLY:
            case IPV6_ONLY: {
                // already filtered earlier
                return sorted;
            }
            case DEFAULT:
            default: {
                // No family bias. Keep destination-sorted order intact.
                return sorted;
            }
        }
    }

    private static List<InetAddress> interleaveFamilies(final List<InetAddress> sorted) {
        final List<InetAddress> v6 = new ArrayList<>();
        final List<InetAddress> v4 = new ArrayList<>();

        for (final InetAddress a : sorted) {
            if (a instanceof Inet6Address) {
                v6.add(a);
            } else {
                v4.add(a);
            }
        }

        if (v6.isEmpty() || v4.isEmpty()) {
            return sorted;
        }

        final boolean startWithV6 = sorted.get(0) instanceof Inet6Address;
        final List<InetAddress> first = startWithV6 ? v6 : v4;
        final List<InetAddress> second = startWithV6 ? v4 : v6;

        final List<InetAddress> out = new ArrayList<>(sorted.size());
        final Iterator<InetAddress> it1 = first.iterator();
        final Iterator<InetAddress> it2 = second.iterator();
        while (it1.hasNext() || it2.hasNext()) {
            if (it1.hasNext()) {
                out.add(it1.next());
            }
            if (it2.hasNext()) {
                out.add(it2.next());
            }
        }
        return out;
    }

    // --- Address selection score structs ---

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

    // Package-private for unit testing.
    enum Scope {
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
                case 0xe: {
                    return GLOBAL;
                }
                default: {
                    throw new IllegalArgumentException("Unknown scope value: 0x" + Integer.toHexString(v));
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

    // Package-private for unit testing.
    //
    // RFC 6724 §3.1 scope assignment:
    //   IPv6 ::1              → link-local  (interface-local is multicast-only per RFC 4291 §2.7)
    //   IPv6 link-local       → link-local
    //   IPv6 site-local       → site-local  (deprecated but still defined)
    //   IPv6 multicast        → scope nibble from second byte (RFC 4291)
    //   IPv6 ULA (fc00::/7)   → global  (handled via policy table, not scope)
    //   IPv6 everything else  → global
    //
    //   IPv4 127/8            → link-local
    //   IPv4 169.254/16       → link-local
    //   IPv4 everything else  → global  (including RFC1918 and 100.64/10)
    //
    static Scope classifyScope(final InetAddress ip) {
        if (ip instanceof Inet4Address) {
            // IPv4 scope rules per RFC 6724 §3.1:
            // Only loopback (127/8) and link-local (169.254/16) get link-local scope;
            // all other IPv4 addresses (including RFC1918 private and 100.64/10) are global.
            if (ip.isLoopbackAddress() || ip.isLinkLocalAddress()) {
                return Scope.LINK_LOCAL;
            }
            return Scope.GLOBAL;
        }

        // IPv6 scope rules.
        if (ip.isLoopbackAddress()) {
            // Interface-local scope (0x1) is a multicast-only concept (RFC 4291 §2.7).
            // For unicast, the smallest meaningful scope is link-local.
            return Scope.LINK_LOCAL;
        }
        if (ip.isLinkLocalAddress()) {
            return Scope.LINK_LOCAL;
        }
        if (ip.isMulticastAddress()) {
            // RFC 6724 §3.1 and RFC 4291: low 4 bits of second byte encode scope for IPv6 multicast.
            // Not all nibble values map to a known Scope constant; treat unknown values as GLOBAL.
            final int nibble = ip.getAddress()[1] & 0x0f;
            try {
                return Scope.fromValue(nibble);
            } catch (final IllegalArgumentException e) {
                return Scope.GLOBAL;
            }
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

    // Package-private for unit testing.
    static final class Network {
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
            final int mask = 0xff << (8 - rem);
            final int aByte = a[fullBytes] & 0xff;
            final int ipByte = ip[fullBytes] & 0xff;
            return (aByte & mask) == (ipByte & mask);
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

    private static final Comparator<Info> DESTINATION_ADDRESS_COMPARATOR = (a, b) -> {
        final InetAddress aDst = a.dst;
        final InetAddress bDst = b.dst;
        final InetAddress aSrc = a.src;
        final InetAddress bSrc = b.src;
        final Attr aDstAttr = a.dstAttr;
        final Attr bDstAttr = b.dstAttr;
        final Attr aSrcAttr = a.srcAttr;
        final Attr bSrcAttr = b.srcAttr;

        final int preferA = -1;
        final int preferB = 1;

        // Destination address selection rules.
        //
        // Rules 3, 4 and 7 are not implementable with standard JDK APIs:
        //   Rule 3 (Avoid deprecated source addresses) — InetAddress exposes no deprecation state.
        //   Rule 4 (Prefer home addresses) — Mobile IPv6 concept; not applicable to an HTTP client.
        //   Rule 7 (Prefer native transport) — JDK provides no encapsulation/tunneling info.

        // Rule 1: Avoid unusable destinations.
        final boolean validA = aSrc != null && !aSrc.isAnyLocalAddress();
        final boolean validB = bSrc != null && !bSrc.isAnyLocalAddress();
        if (!validA && !validB) {
            return 0;
        }
        if (!validB) {
            return preferA;
        }
        if (!validA) {
            return preferB;
        }

        // Rule 2: Prefer matching scope.
        if (aDstAttr.scope == aSrcAttr.scope && bDstAttr.scope != bSrcAttr.scope) {
            return preferA;
        }
        if (aDstAttr.scope != aSrcAttr.scope && bDstAttr.scope == bSrcAttr.scope) {
            return preferB;
        }

        // Rule 3: Avoid deprecated addresses — skipped (see above).

        // Rule 4: Prefer home addresses — skipped (see above).

        // Rule 5: Prefer matching label.
        if (aSrcAttr.label == aDstAttr.label && bSrcAttr.label != bDstAttr.label) {
            return preferA;
        }
        if (aSrcAttr.label != aDstAttr.label && bSrcAttr.label == bDstAttr.label) {
            return preferB;
        }

        // Rule 6: Prefer higher precedence.
        if (aDstAttr.precedence > bDstAttr.precedence) {
            return preferA;
        }
        if (aDstAttr.precedence < bDstAttr.precedence) {
            return preferB;
        }

        // Rule 7: Prefer native transport — skipped (see above).

        // Rule 8: Prefer smaller scope.
        if (aDstAttr.scope.value < bDstAttr.scope.value) {
            return preferA;
        }
        if (aDstAttr.scope.value > bDstAttr.scope.value) {
            return preferB;
        }

        // Rule 9: Use longest matching prefix.
        // Applies when DA and DB belong to the same address family.
        //
        // Note: this is an approximation. A fully correct implementation would
        // use the source address's on-link prefix length (from the interface
        // configuration / routing table), not the bit-wise common-prefix of
        // source and destination. The JDK does not expose interface prefix
        // lengths for source address selection, so we fall back to
        // CommonPrefixLen(Source(D), D) as a reasonable heuristic.
        if (aDst.getClass() == bDst.getClass()) {
            final int commonA = commonPrefixLen(aSrc, aDst);
            final int commonB = commonPrefixLen(bSrc, bDst);
            if (commonA > commonB) {
                return preferA;
            }
            if (commonA < commonB) {
                return preferB;
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

    static String addr(final InetAddress a) {
        if (a == null) {
            return "null";
        }
        final String family = a instanceof Inet6Address ? "IPv6" : "IPv4";
        return family + "(" + a.getHostAddress() + ")";
    }

    static List<String> fmt(final InetAddress[] arr) {
        return Arrays.stream(arr)
                .map(AddressSelectingDnsResolver::addr)
                .collect(Collectors.toList());
    }

    static List<String> fmt(final List<InetAddress> arr) {
        return arr.stream()
                .map(AddressSelectingDnsResolver::addr)
                .collect(Collectors.toList());
    }

}
