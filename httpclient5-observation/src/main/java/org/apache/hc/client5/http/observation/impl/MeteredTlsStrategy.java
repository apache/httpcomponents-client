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

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link TlsStrategy} decorator that records TLS handshake metrics via Micrometer.
 * <p>
 * Exposes the following meters (names are prefixed by {@link MetricConfig#prefix}):
 * <ul>
 *   <li>{@code &lt;prefix&gt;.tls.handshake} (timer) — TLS handshake latency</li>
 *   <li>{@code &lt;prefix&gt;.tls.handshakes} (counter) — handshake outcome count</li>
 * </ul>
 * Tags:
 * <ul>
 *   <li>{@code result} = {@code ok}|{@code error}|{@code cancel}</li>
 *   <li>{@code sni} (only when {@link ObservingOptions.TagLevel#EXTENDED})</li>
 *   <li>plus any {@link MetricConfig#commonTags common tags}</li>
 * </ul>
 *
 * @since 5.6
 */
public final class MeteredTlsStrategy implements TlsStrategy {

    private final TlsStrategy delegate;
    private final MeterRegistry registry;
    private final MetricConfig mc;
    private final ObservingOptions opts;

    /**
     * Primary constructor.
     *
     * @param delegate TLS strategy to wrap
     * @param registry meter registry
     * @param mc       metric configuration (prefix, common tags). If {@code null}, defaults are used.
     * @param opts     observing options (tag level). If {@code null}, {@link ObservingOptions#DEFAULT} is used.
     */
    public MeteredTlsStrategy(final TlsStrategy delegate,
                              final MeterRegistry registry,
                              final MetricConfig mc,
                              final ObservingOptions opts) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.registry = Args.notNull(registry, "registry");
        this.mc = mc != null ? mc : MetricConfig.builder().build();
        this.opts = opts != null ? opts : ObservingOptions.DEFAULT;
    }

    /**
     * Convenience constructor.
     *
     * @deprecated Use
     * {@link #MeteredTlsStrategy(TlsStrategy, MeterRegistry, MetricConfig, ObservingOptions)}
     * supplying {@link MetricConfig} and {@link ObservingOptions}.
     */
    @Deprecated
    public MeteredTlsStrategy(final TlsStrategy delegate,
                              final MeterRegistry registry,
                              final String prefix) {
        this(delegate, registry,
                MetricConfig.builder().prefix(prefix != null ? prefix : "hc").build(),
                ObservingOptions.DEFAULT);
    }

    private List<Tag> tags(final String result, final String sniOrNull) {
        final List<Tag> ts = new ArrayList<>(2);
        ts.add(Tag.of("result", result));
        if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED && sniOrNull != null) {
            ts.add(Tag.of("sni", sniOrNull));
        }
        if (!mc.commonTags.isEmpty()) {
            ts.addAll(mc.commonTags);
        }
        return ts;
    }


    @Override
    public void upgrade(
            final TransportSecurityLayer sessionLayer,
            final NamedEndpoint endpoint,
            final Object attachment,
            final Timeout handshakeTimeout,
            final FutureCallback<TransportSecurityLayer> callback) {

        final long t0 = System.nanoTime();
        final String sni = endpoint != null ? endpoint.getHostName() : null;

        delegate.upgrade(sessionLayer, endpoint, attachment, handshakeTimeout,
                new FutureCallback<TransportSecurityLayer>() {
                    @Override
                    public void completed(final TransportSecurityLayer result) {
                        final List<Tag> t = tags("ok", sni);
                        Timer.builder(mc.prefix + ".tls.handshake").tags(t).register(registry)
                                .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
                        Counter.builder(mc.prefix + ".tls.handshakes").tags(t).register(registry).increment();
                        if (callback != null) {
                            callback.completed(result);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        final List<Tag> t = tags("error", sni);
                        Timer.builder(mc.prefix + ".tls.handshake").tags(t).register(registry)
                                .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
                        Counter.builder(mc.prefix + ".tls.handshakes").tags(t).register(registry).increment();
                        if (callback != null) {
                            callback.failed(ex);
                        }
                    }

                    @Override
                    public void cancelled() {
                        final List<Tag> t = tags("cancel", sni);
                        Timer.builder(mc.prefix + ".tls.handshake").tags(t).register(registry)
                                .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
                        Counter.builder(mc.prefix + ".tls.handshakes").tags(t).register(registry).increment();
                        if (callback != null) {
                            callback.cancelled();
                        }
                    }
                });
    }


    /**
     * Records metrics while delegating to the classic upgrade path.
     *
     * @deprecated Implementations should prefer the async overload; this remains to fulfill the interface.
     */
    @Deprecated
    @Override
    public boolean upgrade(
            final TransportSecurityLayer sessionLayer,
            final HttpHost host,
            final SocketAddress localAddress,
            final SocketAddress remoteAddress,
            final Object attachment,
            final Timeout handshakeTimeout) {

        final long t0 = System.nanoTime();
        final String sni = host != null ? host.getHostName() : null;

        try {
            final boolean upgraded = delegate.upgrade(
                    sessionLayer, host, localAddress, remoteAddress, attachment, handshakeTimeout);
            final List<Tag> t = tags("ok", sni);
            Timer.builder(mc.prefix + ".tls.handshake").tags(t).register(registry)
                    .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            Counter.builder(mc.prefix + ".tls.handshakes").tags(t).register(registry).increment();
            return upgraded;
        } catch (final RuntimeException ex) {
            final List<Tag> t = tags("error", sni);
            Timer.builder(mc.prefix + ".tls.handshake").tags(t).register(registry)
                    .record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            Counter.builder(mc.prefix + ".tls.handshakes").tags(t).register(registry).increment();
            throw ex;
        }
    }
}
