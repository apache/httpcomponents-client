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

package org.apache.hc.client5.http.examples;

import java.net.InetAddress;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.AddressSelectingDnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ProtocolFamilyPreference;

public final class AddressSelectingDnsResolverExample {

    public static void main(final String[] args) throws Exception {
        final String host = args.length > 0 ? args[0] : "localhost";
        final ProtocolFamilyPreference pref = args.length > 1
                ? ProtocolFamilyPreference.valueOf(args[1])
                : ProtocolFamilyPreference.DEFAULT;

        final DnsResolver resolver = new AddressSelectingDnsResolver(SystemDefaultDnsResolver.INSTANCE, pref);

        final InetAddress[] out = resolver.resolve(host);

        System.out.println("Host: " + host);
        System.out.println("Preference: " + pref);
        if (out == null) {
            System.out.println("Result: null");
            return;
        }
        if (out.length == 0) {
            System.out.println("Result: []");
            return;
        }
        for (final InetAddress a : out) {
            final String family = a instanceof java.net.Inet6Address ? "IPv6" : "IPv4";
            System.out.println("  " + family + " " + a.getHostAddress());
        }
    }

    private AddressSelectingDnsResolverExample() {
    }
}
