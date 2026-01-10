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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    void interleave_isDefault_and_hasNoFamilyBias() throws Exception {
        final InetAddress v6a = InetAddress.getByName("2001:db8::1");
        final InetAddress v6b = InetAddress.getByName("2001:db8::2");
        final InetAddress v4a = InetAddress.getByName("192.0.2.1");
        final InetAddress v4b = InetAddress.getByName("203.0.113.10");

        when(delegate.resolve("dual.example")).thenReturn(new InetAddress[]{v6a, v6b, v4a, v4b});

        final Rfc6724AddressSelectingDnsResolver rDefault = new Rfc6724AddressSelectingDnsResolver(delegate);
        final Rfc6724AddressSelectingDnsResolver rInterleave =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.INTERLEAVE);

        final InetAddress[] outDefault = rDefault.resolve("dual.example");
        final InetAddress[] outInterleave = rInterleave.resolve("dual.example");

        assertArrayEquals(outDefault, outInterleave);
        Assertions.assertNotNull(outInterleave);
        assertEquals(4, outInterleave.length);
    }

    @Test
    void preferIpv6_groupsAllV6First_preservingRelativeOrder() throws Exception {
        final InetAddress v4a = InetAddress.getByName("192.0.2.1");
        final InetAddress v6a = InetAddress.getByName("2001:db8::1");
        final InetAddress v4b = InetAddress.getByName("203.0.113.10");
        final InetAddress v6b = InetAddress.getByName("2001:db8::2");

        when(delegate.resolve("dual.example")).thenReturn(new InetAddress[]{v4a, v6a, v4b, v6b});

        final Rfc6724AddressSelectingDnsResolver baseline =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.INTERLEAVE);
        final InetAddress[] baseOut = baseline.resolve("dual.example");

        final Rfc6724AddressSelectingDnsResolver preferV6 =
                new Rfc6724AddressSelectingDnsResolver(delegate, ProtocolFamilyPreference.PREFER_IPV6);
        final InetAddress[] out = preferV6.resolve("dual.example");

        // Expected: stable partition of the RFC-sorted baseline.
        final List<InetAddress> baseV6 = new ArrayList<>();
        final List<InetAddress> baseV4 = new ArrayList<>();
        for (final InetAddress a : baseOut) {
            if (a instanceof Inet6Address) {
                baseV6.add(a);
            } else {
                baseV4.add(a);
            }
        }
        final List<InetAddress> expected = new ArrayList<>(baseOut.length);
        expected.addAll(baseV6);
        expected.addAll(baseV4);

        assertEquals(expected, Arrays.asList(out));
        assertInstanceOf(Inet6Address.class, out[0]);
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
