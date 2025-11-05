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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class Rfc6724AddressSelectingDnsResolverTest {

    private DnsResolver delegate;

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(DnsResolver.class);
    }

    @Test
    void ipv4Only_filtersOutIPv6() throws Exception {
        final InetAddress v4 = InetAddress.getByName("203.0.113.10"); // TEST-NET-3
        final InetAddress v6 = InetAddress.getByName("2001:db8::10"); // documentation prefix

        when(delegate.resolve("dual.example")).thenReturn(new InetAddress[]{v6, v4});

        final Rfc6724AddressSelectingDnsResolver r =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.IPV4_ONLY);

        final InetAddress[] ordered = r.resolve("dual.example");
        Assertions.assertNotNull(ordered);
        assertEquals(1, ordered.length);
        assertInstanceOf(Inet4Address.class, ordered[0]);
        assertEquals(v4, ordered[0]);
    }

    @Test
    void ipv6Only_filtersOutIPv4() throws Exception {
        final InetAddress v4 = InetAddress.getByName("192.0.2.1");     // TEST-NET-1
        final InetAddress v6 = InetAddress.getByName("2001:db8::1");

        when(delegate.resolve("dual.example")).thenReturn(new InetAddress[]{v4, v6});

        final Rfc6724AddressSelectingDnsResolver r =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.IPV6_ONLY);

        final InetAddress[] ordered = r.resolve("dual.example");
        Assertions.assertNotNull(ordered);
        assertEquals(1, ordered.length);
        assertInstanceOf(Inet6Address.class, ordered[0]);
        assertEquals(v6, ordered[0]);
    }

    @Test
    void ipv4Only_emptyWhenNoIPv4Candidates() throws Exception {
        final InetAddress v6a = InetAddress.getByName("2001:db8::1");
        final InetAddress v6b = InetAddress.getByName("2001:db8::2");

        when(delegate.resolve("v6only.example")).thenReturn(new InetAddress[]{v6a, v6b});

        final Rfc6724AddressSelectingDnsResolver r =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.IPV4_ONLY);

        final InetAddress[] ordered = r.resolve("v6only.example");
        Assertions.assertNotNull(ordered);
        assertEquals(0, ordered.length);
    }

    @Test
    void preferIpv6_groupsAllV6First_preservingRelativeOrder() throws Exception {
        final InetAddress v4a = InetAddress.getByName("192.0.2.1");
        final InetAddress v6a = InetAddress.getByName("2001:db8::1");
        final InetAddress v4b = InetAddress.getByName("203.0.113.10");
        final InetAddress v6b = InetAddress.getByName("2001:db8::2");

        when(delegate.resolve("dual.example")).thenReturn(new InetAddress[]{v4a, v6a, v4b, v6b});

        final Rfc6724AddressSelectingDnsResolver r =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.PREFER_IPV6);

        final InetAddress[] out = r.resolve("dual.example");

        // all v6 first, in original relative order
        final List<InetAddress> v6Seen = new ArrayList<>();
        final List<InetAddress> v4Seen = new ArrayList<>();
        Assertions.assertNotNull(out);
        for (final InetAddress a : out) {
            if (a instanceof Inet6Address) {
                v6Seen.add(a);
            } else {
                v4Seen.add(a);
            }
        }
        assertEquals(Arrays.asList(v6a, v6b), v6Seen);
        assertEquals(Arrays.asList(v4a, v4b), v4Seen);

        // ensure first element is IPv6 (grouping actually happened)
        assertInstanceOf(Inet6Address.class, out[0]);
    }

    @Test
    void interleave_alternatesFamilies_and_preservesRelativeOrder() throws Exception {
        final InetAddress v6a = InetAddress.getByName("2001:db8::1");
        final InetAddress v6b = InetAddress.getByName("2001:db8::2");
        final InetAddress v4a = InetAddress.getByName("192.0.2.1");
        final InetAddress v4b = InetAddress.getByName("203.0.113.10");

        when(delegate.resolve("dual.example")).thenReturn(new InetAddress[]{v6a, v6b, v4a, v4b});

        final Rfc6724AddressSelectingDnsResolver r =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.INTERLEAVE);

        final InetAddress[] out = r.resolve("dual.example");

        // Preserve per-family relative order
        final List<InetAddress> v6Seen = new ArrayList<>();
        final List<InetAddress> v4Seen = new ArrayList<>();
        Assertions.assertNotNull(out);
        for (final InetAddress a : out) {
            if (a instanceof Inet6Address) {
                v6Seen.add(a);
            } else {
                v4Seen.add(a);
            }
        }
        assertEquals(Arrays.asList(v6a, v6b), v6Seen);
        assertEquals(Arrays.asList(v4a, v4b), v4Seen);

        // Alternation (as far as both families have remaining items)
        final int pairs = Math.min(v6Seen.size(), v4Seen.size());
        for (int i = 1; i < pairs * 2; i++) {
            assertNotEquals(out[i - 1] instanceof Inet6Address, out[i] instanceof Inet6Address,
                    "adjacent entries should alternate family under INTERLEAVE");
        }
    }

    @Test
    void canonicalHostname_delegates() throws Exception {
        when(delegate.resolveCanonicalHostname("example.org")).thenReturn("canon.example.org");
        final Rfc6724AddressSelectingDnsResolver r =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.INTERLEAVE);
        assertEquals("canon.example.org", r.resolveCanonicalHostname("example.org"));
        Mockito.verify(delegate).resolveCanonicalHostname("example.org");
    }
}
