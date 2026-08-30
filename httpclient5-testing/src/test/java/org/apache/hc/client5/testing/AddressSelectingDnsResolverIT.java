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

package org.apache.hc.client5.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.AddressSelectingDnsResolver;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "httpclient.rfc6724.it", matches = "true")
class AddressSelectingDnsResolverIT {

    private static final String PROP_HOST = "httpclient.rfc6724.host";

    @Test
    void interleave_isStableInterleavingOfDefault_forSameResolution() throws Exception {
        final String host = System.getProperty(PROP_HOST, "localhost");

        final InetAddress[] resolvedOnce = SystemDefaultDnsResolver.INSTANCE.resolve(host);

        final DnsResolver captured = new DnsResolver() {
            @Override
            public InetAddress[] resolve(final String h) throws UnknownHostException {
                if (!host.equals(h)) {
                    throw new UnknownHostException(h);
                }
                return resolvedOnce.clone();
            }

            @Override
            public String resolveCanonicalHostname(final String h) throws UnknownHostException {
                return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(h);
            }
        };

        final AddressSelectingDnsResolver rDefault =
                new AddressSelectingDnsResolver(captured, ProtocolFamilyPreference.DEFAULT);

        final AddressSelectingDnsResolver rInterleave =
                new AddressSelectingDnsResolver(captured, ProtocolFamilyPreference.INTERLEAVE);

        final InetAddress[] outDefault = rDefault.resolve(host);
        final InetAddress[] outInterleave = rInterleave.resolve(host);

        // 0) Both outputs must be permutations of the captured, single resolution.
        Assertions.assertNotNull(outDefault);
        assertSameElements(Arrays.asList(resolvedOnce), Arrays.asList(outDefault));
        Assertions.assertNotNull(outInterleave);
        assertSameElements(Arrays.asList(resolvedOnce), Arrays.asList(outInterleave));

        // 1) Same elements between DEFAULT and INTERLEAVE (no drops, no additions).
        assertSameElements(Arrays.asList(outDefault), Arrays.asList(outInterleave));

        // 2) Family counts must match between DEFAULT and INTERLEAVE.
        final Counts cDefault = countFamilies(outDefault);
        final Counts cInterleave = countFamilies(outInterleave);
        assertEquals(cDefault.v4, cInterleave.v4);
        assertEquals(cDefault.v6, cInterleave.v6);

        // 3) INTERLEAVE must be the stable interleaving of the DEFAULT-ordered list.
        final List<InetAddress> expected = expectedInterleaveFromBaseline(Arrays.asList(outDefault));
        assertEquals(expected, Arrays.asList(outInterleave));

        // 4) If both families are present, the first 2*min(v4,v6) addresses must alternate by family.
        final int pairs = Math.min(cInterleave.v4, cInterleave.v6);
        if (pairs > 0) {
            assertAlternatingPrefix(outInterleave, 2 * pairs);
        }

        // 5) Relative order within each family is preserved from DEFAULT.
        assertFamilyRelativeOrderPreserved(outDefault, outInterleave);

        // Diagnostics for manual runs.
        System.out.println("Host: " + host);
        dump("DEFAULT", outDefault);
        dump("INTERLEAVE", outInterleave);
    }

    private static void assertSameElements(final List<InetAddress> a, final List<InetAddress> b) {
        assertEquals(a.size(), b.size());

        final List<InetAddress> remaining = new ArrayList<>(b);
        for (final InetAddress x : a) {
            assertTrue(remaining.remove(x), "Missing address: " + x);
        }
        assertTrue(remaining.isEmpty(), "Extra addresses: " + remaining);
    }

    private static final class Counts {
        final int v4;
        final int v6;

        Counts(final int v4, final int v6) {
            this.v4 = v4;
            this.v6 = v6;
        }
    }

    private static Counts countFamilies(final InetAddress[] out) {
        int v4 = 0;
        int v6 = 0;
        for (final InetAddress a : out) {
            if (a instanceof Inet6Address) {
                v6++;
            } else {
                v4++;
            }
        }
        return new Counts(v4, v6);
    }

    private static void assertAlternatingPrefix(final InetAddress[] out, final int length) {
        if (length <= 1) {
            return;
        }
        final boolean firstV6 = out[0] instanceof Inet6Address;
        for (int i = 0; i < length; i++) {
            final boolean expectV6 = firstV6 == (i % 2 == 0);
            final boolean isV6 = out[i] instanceof Inet6Address;
            assertEquals(expectV6, isV6, "Not alternating at index " + i);
        }
    }

    private static void assertFamilyRelativeOrderPreserved(
            final InetAddress[] base,
            final InetAddress[] interleaved) {

        final List<InetAddress> baseV6 = new ArrayList<>();
        final List<InetAddress> baseV4 = new ArrayList<>();
        for (final InetAddress a : base) {
            if (a instanceof Inet6Address) {
                baseV6.add(a);
            } else {
                baseV4.add(a);
            }
        }

        final List<InetAddress> gotV6 = new ArrayList<>();
        final List<InetAddress> gotV4 = new ArrayList<>();
        for (final InetAddress a : interleaved) {
            if (a instanceof Inet6Address) {
                gotV6.add(a);
            } else {
                gotV4.add(a);
            }
        }

        assertEquals(baseV6, gotV6, "IPv6 relative order changed");
        assertEquals(baseV4, gotV4, "IPv4 relative order changed");
    }

    private static List<InetAddress> expectedInterleaveFromBaseline(final List<InetAddress> baseline) {
        if (baseline.size() <= 1) {
            return baseline;
        }

        final List<InetAddress> v6 = new ArrayList<>();
        final List<InetAddress> v4 = new ArrayList<>();

        for (final InetAddress a : baseline) {
            if (a instanceof Inet6Address) {
                v6.add(a);
            } else {
                v4.add(a);
            }
        }

        if (v6.isEmpty() || v4.isEmpty()) {
            return baseline;
        }

        final boolean startV6 = baseline.get(0) instanceof Inet6Address;

        final List<InetAddress> out = new ArrayList<>(baseline.size());
        int i6 = 0;
        int i4 = 0;

        while (i6 < v6.size() || i4 < v4.size()) {
            if (startV6) {
                if (i6 < v6.size()) {
                    out.add(v6.get(i6++));
                }
                if (i4 < v4.size()) {
                    out.add(v4.get(i4++));
                }
            } else {
                if (i4 < v4.size()) {
                    out.add(v4.get(i4++));
                }
                if (i6 < v6.size()) {
                    out.add(v6.get(i6++));
                }
            }
        }

        return out;
    }

    private static void dump(final String label, final InetAddress[] out) {
        int v4 = 0;
        int v6 = 0;

        System.out.println("Preference: " + label);
        for (final InetAddress a : out) {
            if (a instanceof Inet6Address) {
                v6++;
                System.out.println("  IPv6 " + a.getHostAddress());
            } else {
                v4++;
                System.out.println("  IPv4 " + a.getHostAddress());
            }
        }
        System.out.println("Counts: IPv4=" + v4 + " IPv6=" + v6);
        System.out.println();
    }
}
