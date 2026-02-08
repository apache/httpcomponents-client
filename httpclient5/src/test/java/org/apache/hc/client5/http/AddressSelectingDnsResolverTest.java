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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.apache.hc.client5.http.impl.InMemoryDnsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressSelectingDnsResolverTest {

    private static final AddressSelectingDnsResolver.SourceAddressResolver NO_SOURCE_ADDR =
            (final InetSocketAddress dest) -> null;

    private InMemoryDnsResolver delegate;

    @BeforeEach
    void setUp() {
        delegate = new InMemoryDnsResolver();
    }

    @Test
    void ipv4Only_filtersOutIPv6() throws Exception {
        final InetAddress v4 = inet("203.0.113.10"); // TEST-NET-3
        final InetAddress v6 = inet("2001:db8::10"); // documentation prefix

        delegate.add("dual.example", v6, v4);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.IPV4_ONLY, NO_SOURCE_ADDR);

        final InetAddress[] ordered = r.resolve("dual.example");
        assertEquals(1, ordered.length);
        assertInstanceOf(Inet4Address.class, ordered[0]);
        assertEquals(v4, ordered[0]);
    }

    @Test
    void ipv6Only_filtersOutIPv4() throws Exception {
        final InetAddress v4 = inet("192.0.2.1");     // TEST-NET-1
        final InetAddress v6 = inet("2001:db8::1");

        delegate.add("dual.example", v4, v6);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.IPV6_ONLY, NO_SOURCE_ADDR);

        final InetAddress[] ordered = r.resolve("dual.example");
        assertEquals(1, ordered.length);
        assertInstanceOf(Inet6Address.class, ordered[0]);
        assertEquals(v6, ordered[0]);
    }

    @Test
    void ipv4Only_emptyWhenNoIPv4Candidates() throws Exception {
        final InetAddress v6a = inet("2001:db8::1");
        final InetAddress v6b = inet("2001:db8::2");

        delegate.add("v6only.example", v6a, v6b);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.IPV4_ONLY, NO_SOURCE_ADDR);

        final InetAddress[] ordered = r.resolve("v6only.example");
        assertNull(ordered);
    }

    @Test
    void default_hasNoFamilyBias() throws Exception {
        final InetAddress v6a = inet("2001:db8::1");
        final InetAddress v6b = inet("2001:db8::2");
        final InetAddress v4a = inet("192.0.2.1");
        final InetAddress v4b = inet("203.0.113.10");

        delegate.add("dual.example", v6a, v6b, v4a, v4b);

        final AddressSelectingDnsResolver r1 =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, NO_SOURCE_ADDR);
        final AddressSelectingDnsResolver r2 =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, NO_SOURCE_ADDR);

        final InetAddress[] out1 = r1.resolve("dual.example");
        final InetAddress[] out2 = r2.resolve("dual.example");

        assertArrayEquals(out1, out2);
        assertEquals(4, out1.length);
    }

    @Test
    void interleave_alternatesFamilies_preservingRelativeOrder_whenRfcSortIsNoop() throws Exception {
        final InetAddress v6a = inet("2001:db8::1");
        final InetAddress v6b = inet("2001:db8::2");
        final InetAddress v4a = inet("192.0.2.1");
        final InetAddress v4b = inet("203.0.113.10");

        // With NO_SOURCE_ADDR, RFC sort becomes a stable no-op; deterministic interleave.
        delegate.add("dual.example", v6a, v6b, v4a, v4b);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.INTERLEAVE, NO_SOURCE_ADDR);

        final InetAddress[] out = r.resolve("dual.example");
        assertEquals(Arrays.asList(v6a, v4a, v6b, v4b), Arrays.asList(out));
    }

    @Test
    void preferIpv6_groupsAllV6First_preservingRelativeOrder_whenRfcSortIsNoop() throws Exception {
        final InetAddress v4a = inet("192.0.2.1");
        final InetAddress v6a = inet("2001:db8::1");
        final InetAddress v4b = inet("203.0.113.10");
        final InetAddress v6b = inet("2001:db8::2");

        delegate.add("dual.example", v4a, v6a, v4b, v6b);

        final AddressSelectingDnsResolver preferV6 =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.PREFER_IPV6, NO_SOURCE_ADDR);

        final InetAddress[] out = preferV6.resolve("dual.example");
        assertEquals(Arrays.asList(v6a, v6b, v4a, v4b), Arrays.asList(out));
        assertInstanceOf(Inet6Address.class, out[0]);
    }

    @Test
    void filtersOutMulticastDestinations() throws Exception {
        final InetAddress multicastV6 = inet("ff02::1");
        final InetAddress v6 = inet("2001:db8::1");

        delegate.add("mcast.example", multicastV6, v6);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, NO_SOURCE_ADDR);

        final InetAddress[] out = r.resolve("mcast.example");
        assertEquals(1, out.length);
        assertEquals(v6, out[0]);
    }

    // -------------------------------------------------------------------------
    // New: direct tests for classifyScope(..) and Scope.fromValue(..) via reflection
    // (Scope and classifyScope are private in the resolver).
    // -------------------------------------------------------------------------

    @Test
    void classifyScope_loopback_linkLocal_siteLocal_global() throws Exception {
        final Class<?> resolverClass = AddressSelectingDnsResolver.class;

        assertEquals("INTERFACE_LOCAL", classifyScope(resolverClass, inet("127.0.0.1")));
        assertEquals("INTERFACE_LOCAL", classifyScope(resolverClass, inet("::1")));

        assertEquals("LINK_LOCAL", classifyScope(resolverClass, inet("169.254.0.1")));
        assertEquals("LINK_LOCAL", classifyScope(resolverClass, inet("fe80::1")));

        assertEquals("SITE_LOCAL", classifyScope(resolverClass, inet("10.0.0.1")));

        assertEquals("GLOBAL", classifyScope(resolverClass, inet("8.8.8.8")));
        assertEquals("GLOBAL", classifyScope(resolverClass, inet("2003::1")));
    }

    @Test
    void classifyScope_ipv6Multicast_usesLowNibbleScope() throws Exception {
        final Class<?> resolverClass = AddressSelectingDnsResolver.class;

        // ff01::1 -> scope 0x1 -> INTERFACE_LOCAL
        assertEquals("INTERFACE_LOCAL", classifyScope(resolverClass, inet("ff01::1")));
        // ff02::1 -> scope 0x2 -> LINK_LOCAL
        assertEquals("LINK_LOCAL", classifyScope(resolverClass, inet("ff02::1")));
        // ff04::1 -> scope 0x4 -> ADMIN_LOCAL
        assertEquals("ADMIN_LOCAL", classifyScope(resolverClass, inet("ff04::1")));
        // ff05::1 -> scope 0x5 -> SITE_LOCAL
        assertEquals("SITE_LOCAL", classifyScope(resolverClass, inet("ff05::1")));
        // ff08::1 -> scope 0x8 -> ORG_LOCAL
        assertEquals("ORG_LOCAL", classifyScope(resolverClass, inet("ff08::1")));
        // ff0e::1 -> scope 0xe -> GLOBAL (default branch)
        assertEquals("GLOBAL", classifyScope(resolverClass, inet("ff0e::1")));
    }

    @Test
    void scopeFromValue_mapsKnownConstants_andDefaultsToGlobal() throws Exception {
        final Class<?> resolverClass = AddressSelectingDnsResolver.class;
        final Class<?> scopeClass = findDeclaredClass(resolverClass, "Scope");
        assertNotNull(scopeClass);

        assertEquals("INTERFACE_LOCAL", scopeFromValue(scopeClass, 0x1));
        assertEquals("LINK_LOCAL", scopeFromValue(scopeClass, 0x2));
        assertEquals("ADMIN_LOCAL", scopeFromValue(scopeClass, 0x4));
        assertEquals("SITE_LOCAL", scopeFromValue(scopeClass, 0x5));
        assertEquals("ORG_LOCAL", scopeFromValue(scopeClass, 0x8));

        assertEquals("GLOBAL", scopeFromValue(scopeClass, 0x0));
        assertEquals("GLOBAL", scopeFromValue(scopeClass, 0xe));
        assertEquals("GLOBAL", scopeFromValue(scopeClass, 0xf));
    }

    @Test
    void rfcRule2_prefersMatchingScope() throws Exception {
        final InetAddress aDst = inet("2001:db8::1");
        final InetAddress bDst = inet("2001:db8::2");

        // A matches scope (GLOBAL == GLOBAL); B mismatches (GLOBAL != LINK_LOCAL)
        final InetAddress aSrc = inet("2001:db8::abcd");
        final InetAddress bSrc = inet("fe80::1");

        delegate.add("t.example", bDst, aDst);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, sourceMap(aDst, aSrc, bDst, bSrc));

        final InetAddress[] out = r.resolve("t.example");
        assertEquals(Arrays.asList(aDst, bDst), Arrays.asList(out));
    }

    @Test
    void rfcRule5_prefersMatchingLabel() throws Exception {
        final InetAddress aDst = inet("2001:db8::1");        // label 5 (2001::/32)
        final InetAddress bDst = inet("2001:db8::2");        // label 5

        final InetAddress aSrc = inet("2001:db8::abcd");     // label 5 -> matches A
        final InetAddress bSrc = inet("::ffff:192.0.2.1");   // label 4 -> does not match B

        delegate.add("t.example", bDst, aDst);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, sourceMap(aDst, aSrc, bDst, bSrc));

        final InetAddress[] out = r.resolve("t.example");
        assertEquals(Arrays.asList(aDst, bDst), Arrays.asList(out));
    }

    @Test
    void rfcRule6_prefersHigherPrecedence() throws Exception {
        final InetAddress aDst = inet("::1");            // precedence 50 (policy ::1)
        final InetAddress bDst = inet("2001:db8::1");    // precedence 5  (policy 2001::/32)

        final InetAddress aSrc = inet("::1");
        final InetAddress bSrc = inet("2001:db8::abcd");

        delegate.add("t.example", bDst, aDst);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, sourceMap(aDst, aSrc, bDst, bSrc));

        final InetAddress[] out = r.resolve("t.example");
        assertEquals(Arrays.asList(aDst, bDst), Arrays.asList(out));
    }

    @Test
    void rfcRule8_prefersSmallerScope_whenPrecedenceAndLabelTie() throws Exception {
        // Both fall to ::/0 policy -> precedence 40, label 1, but different scopes.
        final InetAddress aDst = inet("fe80::1");  // LINK_LOCAL scope (0x2)
        final InetAddress bDst = inet("2003::1");  // GLOBAL scope     (0xe)

        final InetAddress aSrc = inet("fe80::2");  // LINK_LOCAL, label 1
        final InetAddress bSrc = inet("2003::2");  // GLOBAL, label 1

        delegate.add("t.example", bDst, aDst);

        final AddressSelectingDnsResolver r =
                new AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.DEFAULT, sourceMap(aDst, aSrc, bDst, bSrc));

        final InetAddress[] out = r.resolve("t.example");
        assertEquals(Arrays.asList(aDst, bDst), Arrays.asList(out));
    }

    @Test
    void addr_fmt_simpleName() throws Exception {
        assertEquals("null", AddressSelectingDnsResolver.addr(null));

        final InetAddress v4 = inet("192.0.2.1");
        final InetAddress v6 = inet("2001:db8::1");

        assertEquals("IPv4(" + v4.getHostAddress() + ")", AddressSelectingDnsResolver.addr(v4));
        assertEquals("IPv6(" + v6.getHostAddress() + ")", AddressSelectingDnsResolver.addr(v6));

        assertEquals(Arrays.asList("IPv6(" + v6.getHostAddress() + ")", "IPv4(" + v4.getHostAddress() + ")"),
                AddressSelectingDnsResolver.fmt(new InetAddress[]{v6, v4}));

        assertEquals(Arrays.asList("IPv4(" + v4.getHostAddress() + ")", "IPv6(" + v6.getHostAddress() + ")"),
                AddressSelectingDnsResolver.fmt(Arrays.asList(v4, v6)));

    }

    private static InetAddress inet(final String s) {
        try {
            return InetAddress.getByName(s);
        } catch (final UnknownHostException ex) {
            throw new AssertionError(ex);
        }
    }

    private static AddressSelectingDnsResolver.SourceAddressResolver sourceMap(
            final InetAddress aDst, final InetAddress aSrc,
            final InetAddress bDst, final InetAddress bSrc) {
        return (final InetSocketAddress dest) -> {
            final InetAddress d = dest.getAddress();
            if (aDst.equals(d)) {
                return aSrc;
            }
            if (bDst.equals(d)) {
                return bSrc;
            }
            return null;
        };
    }

    private static String classifyScope(final Class<?> resolverClass, final InetAddress ip) throws Exception {
        final java.lang.reflect.Method m = resolverClass.getDeclaredMethod("classifyScope", InetAddress.class);
        m.setAccessible(true);
        final Object scope = m.invoke(null, ip);
        return scope != null ? scope.toString() : null;
    }

    private static String scopeFromValue(final Class<?> scopeClass, final int v) throws Exception {
        final java.lang.reflect.Method m = scopeClass.getDeclaredMethod("fromValue", int.class);
        m.setAccessible(true);
        final Object scope = m.invoke(null, v);
        return scope != null ? scope.toString() : null;
    }

    private static Class<?> findDeclaredClass(final Class<?> outer, final String simpleName) {
        for (final Class<?> c : outer.getDeclaredClasses()) {
            if (simpleName.equals(c.getSimpleName())) {
                return c;
            }
        }
        return null;
    }

    @Test
    void networkContains_ipv6Prefix32() throws Exception {
        final Class<?> networkClass = findDeclaredClass(AddressSelectingDnsResolver.class, "Network");
        assertNotNull(networkClass);

        final Object p32 = newNetwork(networkClass, inet("2001:db8::").getAddress(), 32);

        assertTrue(networkContains(networkClass, p32, inet("2001:db8::1")));
        assertTrue(networkContains(networkClass, p32, inet("2001:db8:ffff::1")));

        assertFalse(networkContains(networkClass, p32, inet("2001:db9::1")));
        assertFalse(networkContains(networkClass, p32, inet("2000:db8::1")));
    }

    @Test
    void networkContains_ipv4IsMatchedViaV4MappedWhenPrefixIsV6Mapped96() throws Exception {
        final Class<?> networkClass = findDeclaredClass(AddressSelectingDnsResolver.class, "Network");
        assertNotNull(networkClass);

        // Build ::ffff:0:0 as raw 16 bytes. Do NOT use InetAddress.getByName(..) here:
        // the JDK may normalize it to an Inet4Address, yielding a 4-byte array.
        final byte[] v6mapped = new byte[16];
        v6mapped[10] = (byte) 0xff;
        v6mapped[11] = (byte) 0xff;

        final Object p96 = newNetwork(networkClass, v6mapped, 96);

        assertTrue(networkContains(networkClass, p96, inet("192.0.2.1")));
        assertTrue(networkContains(networkClass, p96, inet("203.0.113.10")));

        // A pure IPv6 address must not match that v4-mapped prefix.
        assertFalse(networkContains(networkClass, p96, inet("2001:db8::1")));
    }

    @Test
    void networkContains_nonByteAlignedPrefix7Boundary() throws Exception {
        final Class<?> networkClass = findDeclaredClass(AddressSelectingDnsResolver.class, "Network");
        assertNotNull(networkClass);

        // fc00::/7 (ULA) is in the policy table.
        final Object p7 = newNetwork(networkClass, inet("fc00::").getAddress(), 7);

        // Inside /7: fc00:: and fd00:: (since /7 covers fc00..fdff)
        assertTrue(networkContains(networkClass, p7, inet("fc00::1")));
        assertTrue(networkContains(networkClass, p7, inet("fd00::1")));

        // Just outside /7: fe00:: (top bits 11111110 vs 1111110x)
        assertFalse(networkContains(networkClass, p7, inet("fe00::1")));
        assertFalse(networkContains(networkClass, p7, inet("2001:db8::1")));
    }

    private static Object newNetwork(final Class<?> networkClass, final byte[] ip, final int bits) throws Exception {
        final java.lang.reflect.Constructor<?> c = networkClass.getDeclaredConstructor(byte[].class, int.class);
        c.setAccessible(true);
        return c.newInstance(ip, bits);
    }

    private static boolean networkContains(final Class<?> networkClass, final Object network, final InetAddress addr) throws Exception {
        final java.lang.reflect.Method m = networkClass.getDeclaredMethod("contains", InetAddress.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(network, addr);
    }

}