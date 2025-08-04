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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.util.Args;

/**
 * {@link DnsResolver} wrapper that records DNS resolution metrics via Micrometer.
 * <p>
 * Exposes the following meters (names are prefixed by {@link MetricConfig#prefix}):
 * <ul>
 *   <li>{@code &lt;prefix&gt;.dns.resolve} (timer) — latency of {@link #resolve(String)}</li>
 *   <li>{@code &lt;prefix&gt;.dns.resolutions} (counter) — outcome-count of {@link #resolve(String)}</li>
 *   <li>{@code &lt;prefix&gt;.dns.canonical} (timer) — latency of {@link #resolveCanonicalHostname(String)}</li>
 *   <li>{@code &lt;prefix&gt;.dns.canonicals} (counter) — outcome-count of {@link #resolveCanonicalHostname(String)}</li>
 * </ul>
 * Tags:
 * <ul>
 *   <li>{@code result} = {@code ok}|{@code error}</li>
 *   <li>{@code host} (only when {@link ObservingOptions.TagLevel#EXTENDED})</li>
 *   <li>plus any {@link MetricConfig#commonTags common tags}</li>
 * </ul>
 *
 * @since 5.6
 */
public final class MeteredDnsResolver implements DnsResolver {

    private final DnsResolver delegate;
    private final MeterRegistry registry;
    private final MetricConfig mc;
    private final ObservingOptions opts;

    /**
     * @param delegate underlying resolver
     * @param registry meter registry
     * @param mc       metric configuration (prefix, common tags). If {@code null}, defaults are used.
     * @param opts     observing options (for tag level). If {@code null}, {@link ObservingOptions#DEFAULT} is used.
     */
    public MeteredDnsResolver(final DnsResolver delegate,
                              final MeterRegistry registry,
                              final MetricConfig mc,
                              final ObservingOptions opts) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.registry = Args.notNull(registry, "registry");
        this.mc = mc != null ? mc : MetricConfig.builder().build();
        this.opts = opts != null ? opts : ObservingOptions.DEFAULT;
    }

    private List<Tag> tags(final String result, final String host) {
        final List<Tag> ts = new ArrayList<>(2);
        ts.add(Tag.of("result", result));
        if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED && host != null) {
            ts.add(Tag.of("host", host));
        }
        if (!mc.commonTags.isEmpty()) {
            ts.addAll(mc.commonTags);
        }
        return ts;
    }

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        final long t0 = System.nanoTime();
        try {
            final InetAddress[] out = delegate.resolve(host);
            final List<Tag> t = tags("ok", host);
            Timer.builder(mc.prefix + ".dns.resolve").tags(t).register(registry)
                    .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            Counter.builder(mc.prefix + ".dns.resolutions").tags(t).register(registry).increment();
            return out;
        } catch (final UnknownHostException ex) {
            final List<Tag> t = tags("error", host);
            Timer.builder(mc.prefix + ".dns.resolve").tags(t).register(registry)
                    .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            Counter.builder(mc.prefix + ".dns.resolutions").tags(t).register(registry).increment();
            throw ex;
        }
    }

    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        final long t0 = System.nanoTime();
        try {
            final String out = delegate.resolveCanonicalHostname(host);
            final List<Tag> t = tags("ok", host);
            Timer.builder(mc.prefix + ".dns.canonical").tags(t).register(registry)
                    .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            Counter.builder(mc.prefix + ".dns.canonicals").tags(t).register(registry).increment();
            return out;
        } catch (final UnknownHostException ex) {
            final List<Tag> t = tags("error", host);
            Timer.builder(mc.prefix + ".dns.canonical").tags(t).register(registry)
                    .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            Counter.builder(mc.prefix + ".dns.canonicals").tags(t).register(registry).increment();
            throw ex;
        }
    }
}
