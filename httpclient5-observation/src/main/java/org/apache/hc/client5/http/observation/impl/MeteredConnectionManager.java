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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Connection manager wrapper that records pool lease wait time via Micrometer.
 *
 * @since 5.7
 */
public final class MeteredConnectionManager implements HttpClientConnectionManager, ConnPoolControl<HttpRoute> {

    private final HttpClientConnectionManager delegate;
    private final MeterRegistry registry;
    private final MetricConfig mc;
    private final ObservingOptions opts;
    private final ConnPoolControl<HttpRoute> poolControl;

    public MeteredConnectionManager(final HttpClientConnectionManager delegate,
                                    final MeterRegistry registry,
                                    final MetricConfig mc,
                                    final ObservingOptions opts) {
        this.delegate = Args.notNull(delegate, "delegate");
        this.registry = Args.notNull(registry, "registry");
        this.mc = mc != null ? mc : MetricConfig.builder().build();
        this.opts = opts != null ? opts : ObservingOptions.DEFAULT;
        @SuppressWarnings("unchecked") final ConnPoolControl<HttpRoute> pc =
                delegate instanceof ConnPoolControl ? (ConnPoolControl<HttpRoute>) delegate : null;
        this.poolControl = pc;
    }

    @Override
    public LeaseRequest lease(final String id, final HttpRoute route, final Timeout requestTimeout, final Object state) {
        final long start = System.nanoTime();
        final LeaseRequest leaseRequest = delegate.lease(id, route, requestTimeout, state);
        final AtomicBoolean recorded = new AtomicBoolean(false);
        return new LeaseRequest() {
            @Override
            public ConnectionEndpoint get(final Timeout timeout)
                    throws InterruptedException, ExecutionException, TimeoutException {
                try {
                    final ConnectionEndpoint endpoint = leaseRequest.get(timeout);
                    recordOnce(recorded, "ok", route, start);
                    return endpoint;
                } catch (final TimeoutException ex) {
                    recordOnce(recorded, "timeout", route, start);
                    throw ex;
                } catch (final InterruptedException ex) {
                    recordOnce(recorded, "cancel", route, start);
                    throw ex;
                } catch (final ExecutionException ex) {
                    recordOnce(recorded, "error", route, start);
                    throw ex;
                }
            }

            @Override
            public boolean cancel() {
                final boolean cancelled = leaseRequest.cancel();
                if (cancelled) {
                    recordOnce(recorded, "cancel", route, start);
                }
                return cancelled;
            }
        };
    }

    @Override
    public void release(final ConnectionEndpoint endpoint, final Object newState, final TimeValue validDuration) {
        delegate.release(endpoint, newState, validDuration);
    }

    @Override
    public void connect(final ConnectionEndpoint endpoint, final TimeValue connectTimeout,
                        final org.apache.hc.core5.http.protocol.HttpContext context) throws IOException {
        delegate.connect(endpoint, connectTimeout, context);
    }

    @Override
    public void upgrade(final ConnectionEndpoint endpoint,
                        final org.apache.hc.core5.http.protocol.HttpContext context) throws IOException {
        delegate.upgrade(endpoint, context);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void close(final org.apache.hc.core5.io.CloseMode closeMode) {
        delegate.close(closeMode);
    }

    @Override
    public PoolStats getTotalStats() {
        return poolControl != null ? poolControl.getTotalStats() : new PoolStats(0, 0, 0, 0);
    }

    @Override
    public PoolStats getStats(final HttpRoute route) {
        return poolControl != null ? poolControl.getStats(route) : new PoolStats(0, 0, 0, 0);
    }

    @Override
    public void setMaxTotal(final int max) {
        if (poolControl != null) {
            poolControl.setMaxTotal(max);
        }
    }

    @Override
    public int getMaxTotal() {
        return poolControl != null ? poolControl.getMaxTotal() : 0;
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        if (poolControl != null) {
            poolControl.setDefaultMaxPerRoute(max);
        }
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return poolControl != null ? poolControl.getDefaultMaxPerRoute() : 0;
    }

    @Override
    public void setMaxPerRoute(final HttpRoute route, final int max) {
        if (poolControl != null) {
            poolControl.setMaxPerRoute(route, max);
        }
    }

    @Override
    public int getMaxPerRoute(final HttpRoute route) {
        return poolControl != null ? poolControl.getMaxPerRoute(route) : 0;
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        if (poolControl != null) {
            poolControl.closeIdle(idleTime);
        }
    }

    @Override
    public void closeExpired() {
        if (poolControl != null) {
            poolControl.closeExpired();
        }
    }

    @Override
    public Set<HttpRoute> getRoutes() {
        return poolControl != null ? poolControl.getRoutes() : Collections.emptySet();
    }

    private void recordOnce(final AtomicBoolean recorded,
                            final String result,
                            final HttpRoute route,
                            final long startNanos) {
        if (recorded.compareAndSet(false, true)) {
            record(result, route, startNanos);
        }
    }

    private void record(final String result, final HttpRoute route, final long startNanos) {
        final List<Tag> tags = new ArrayList<>(3);
        tags.add(Tag.of("result", result));
        if (opts.tagLevel == ObservingOptions.TagLevel.EXTENDED && route != null && route.getTargetHost() != null) {
            tags.add(Tag.of("target", route.getTargetHost().getHostName()));
        }
        Timer.builder(mc.prefix + ".pool.lease")
                .tags(mc.commonTags)
                .tags(tags)
                .register(registry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        Counter.builder(mc.prefix + ".pool.leases")
                .tags(mc.commonTags)
                .tags(tags)
                .register(registry)
                .increment();
    }
}
