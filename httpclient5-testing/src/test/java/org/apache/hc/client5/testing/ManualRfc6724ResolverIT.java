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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.Inet6Address;
import java.net.InetAddress;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.Rfc6724AddressSelectingDnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.junit.jupiter.api.Test;

class ManualRfc6724ResolverIT {

    private static final String PROP_ENABLE = "httpclient.rfc6724.it";
    private static final String PROP_HOST = "httpclient.rfc6724.host";

    @Test
    void resolve_and_dump_order() throws Exception {
        assumeTrue(Boolean.getBoolean(PROP_ENABLE),
                "Enable with -Dhttpclient.rfc6724.it=true");

        final String host = System.getProperty(PROP_HOST, "localhost");

        final DnsResolver base = SystemDefaultDnsResolver.INSTANCE;

        System.out.println("Host: " + host);

        dump(base, host, ProtocolFamilyPreference.DEFAULT);
        System.out.println();
        dump(base, host, ProtocolFamilyPreference.INTERLEAVE);
    }

    private static void dump(
            final DnsResolver base,
            final String host,
            final ProtocolFamilyPreference pref) throws Exception {

        final Rfc6724AddressSelectingDnsResolver resolver =
                new Rfc6724AddressSelectingDnsResolver(base, pref);

        final InetAddress[] out = resolver.resolve(host);

        int v4 = 0;
        int v6 = 0;

        System.out.println("Preference: " + pref);
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
    }
}
