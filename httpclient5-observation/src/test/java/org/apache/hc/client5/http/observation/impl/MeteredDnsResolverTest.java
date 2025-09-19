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
package org.apache.hc.client5.http.observation.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.junit.jupiter.api.Test;

class MeteredDnsResolverTest {

    private static final class FakeOkResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(final String host) throws UnknownHostException {
            // No real DNS call: construct loopback address directly
            return new InetAddress[]{InetAddress.getByAddress("localhost", new byte[]{127, 0, 0, 1})};
        }

        @Override
        public String resolveCanonicalHostname(final String host) {
            return "localhost.localdomain";
        }
    }

    private static final class FakeFailResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(final String host) throws UnknownHostException {
            throw new UnknownHostException(host);
        }

        @Override
        public String resolveCanonicalHostname(final String host) throws UnknownHostException {
            throw new UnknownHostException(host);
        }
    }

    @Test
    void recordsTimersAndCounters_okPaths() throws Exception {
        final MeterRegistry reg = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("t").build();
        final ObservingOptions opts = ObservingOptions.builder()
                .tagLevel(ObservingOptions.TagLevel.LOW)
                .build();

        final MeteredDnsResolver r = new MeteredDnsResolver(new FakeOkResolver(), reg, mc, opts);

        // Exercise both methods
        r.resolve("example.test");
        r.resolveCanonicalHostname("example.test");

        // Timers and counters should have at least one measurement on OK path
        assertTrue(reg.find("t.dns.resolve").timer().count() >= 1L);
        assertTrue(reg.find("t.dns.resolutions").counter().count() >= 1.0d);
        assertTrue(reg.find("t.dns.canonical").timer().count() >= 1L);
        assertTrue(reg.find("t.dns.canonicals").counter().count() >= 1.0d);
    }

    @Test
    void recordsTimersAndCounters_errorPaths() {
        final MeterRegistry reg = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("t2").build();

        final MeteredDnsResolver r = new MeteredDnsResolver(new FakeFailResolver(), reg, mc, ObservingOptions.DEFAULT);

        try {
            r.resolve("boom.test");
        } catch (final Exception ignore) {
            // expected
        }
        try {
            r.resolveCanonicalHostname("boom.test");
        } catch (final Exception ignore) {
            // expected
        }

        // Even on error, we should have recorded time and incremented counters
        assertTrue(reg.find("t2.dns.resolve").timer().count() >= 1L);
        assertTrue(reg.find("t2.dns.resolutions").counter().count() >= 1.0d);
        assertTrue(reg.find("t2.dns.canonical").timer().count() >= 1L);
        assertTrue(reg.find("t2.dns.canonicals").counter().count() >= 1.0d);
    }
}
