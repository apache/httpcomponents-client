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
package org.apache.hc.client5.http.observation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.cache.CachingHttpClientBuilder;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.observation.binder.ConnPoolMeters;
import org.apache.hc.client5.http.observation.binder.ConnPoolMetersAsync;
import org.apache.hc.client5.http.observation.impl.MeteredAsyncConnectionManager;
import org.apache.hc.client5.http.observation.impl.MeteredConnectionManager;
import org.apache.hc.client5.http.observation.impl.ObservationAsyncExecInterceptor;
import org.apache.hc.client5.http.observation.impl.ObservationClassicExecInterceptor;
import org.apache.hc.client5.http.observation.impl.MeteredDnsResolver;
import org.apache.hc.client5.http.observation.impl.MeteredTlsStrategy;
import org.apache.hc.client5.http.observation.interceptors.AsyncIoByteCounterExec;
import org.apache.hc.client5.http.observation.interceptors.AsyncTimerExec;
import org.apache.hc.client5.http.observation.interceptors.IoByteCounterExec;
import org.apache.hc.client5.http.observation.interceptors.TimerExec;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;

/**
 * Utility class that wires Micrometer / OpenTelemetry instrumentation into
 * the HttpClient execution pipeline(s).
 *
 * <p>This helper can install:</p>
 * <ul>
 *   <li><b>Observations</b> (via Micrometer {@link ObservationRegistry})
 *       which can be bridged to OpenTelemetry tracing, and</li>
 *   <li><b>Metrics</b> (via Micrometer {@link MeterRegistry}) such as
 *       per-request latency timers, response counters, I/O counters, and
 *       connection pool gauges.</li>
 * </ul>
 *
 * <p><strong>Optional dependencies</strong></p>
 * <p>
 * Micrometer and OpenTelemetry are <em>optional</em> dependencies. Use the
 * overloads that accept explicit registries (recommended) or the convenience
 * overloads that use {@link Metrics#globalRegistry}. When Micrometer is not
 * on the classpath, only the observation / metric features you actually call
 * will be required.
 * </p>
 *
 * <p><strong>Typical usage (classic client)</strong></p>
 * <pre>{@code
 * ObservationRegistry obs = ObservationRegistry.create();
 * MeterRegistry meters = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 * HttpClientBuilder b = HttpClients.custom();
 *
 * HttpClientObservationSupport.enable(
 *     b, obs, meters,
 *     ObservingOptions.DEFAULT,
 *     MetricConfig.DEFAULT);
 *
 * CloseableHttpClient client = b.build();
 * }</pre>
 *
 * <p><strong>What gets installed</strong></p>
 * <ul>
 *   <li>An <em>observation</em> interceptor (if {@code obsReg} is non-null)
 *       that surrounds each execution with a start/stop span.</li>
 *   <li>Metric interceptors according to {@link ObservingOptions.MetricSet}:
 *       BASIC (latency timer + response counter), IO (bytes in/out counters),
 *       and CONN_POOL (pool gauges; classic and async variants). Pool lease
 *       timing is available when using {@link #meteredConnectionManager} or
 *       {@link #meteredAsyncConnectionManager}.</li>
 * </ul>
 *
 * <p><strong>Thread safety:</strong> This class is stateless. Methods may be
 * called from any thread before the client is built.</p>
 *
 * @since 5.6
 */
public final class HttpClientObservationSupport {

    /**
     * Internal ID for the observation interceptor.
     */
    private static final String OBS_ID = "observation";
    /**
     * Internal ID for latency/counter metrics interceptor.
     */
    private static final String TIMER_ID = "metric-timer";
    /**
     * Internal ID for I/O byte counters interceptor.
     */
    private static final String IO_ID = "metric-io";

    /* ====================== Classic ====================== */

    /**
     * Enables observations and default metrics on a classic client builder using
     * {@link Metrics#globalRegistry} and {@link MetricConfig#DEFAULT}.
     *
     * @param builder client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @since 5.6
     */
    public static void enable(final HttpClientBuilder builder,
                              final ObservationRegistry obsReg) {
        enable(builder, obsReg, Metrics.globalRegistry, ObservingOptions.DEFAULT, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a classic client builder using
     * {@link Metrics#globalRegistry} and a custom {@link ObservingOptions}.
     *
     * @param builder client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @param opts    observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final HttpClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, Metrics.globalRegistry, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a classic client builder with an explicit
     * meter registry and default {@link MetricConfig}.
     *
     * @param builder  client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final HttpClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, meterReg, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a classic client builder using explicit
     * registries and {@link MetricConfig}.
     *
     * <p>Installs interceptors at the <em>beginning</em> of the execution chain.</p>
     *
     * @param builder  client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final HttpClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts,
                              final MetricConfig mc) {

        Args.notNull(builder, "builder");
        Args.notNull(meterReg, "meterRegistry");

        final ObservingOptions o = (opts != null) ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = (mc != null) ? mc : MetricConfig.DEFAULT;

        // Observations (spans) — only if registry provided
        if (obsReg != null) {
            builder.addExecInterceptorFirst(OBS_ID, new ObservationClassicExecInterceptor(obsReg, opts));
        }

        // Metrics
        if (o.metricSets.contains(ObservingOptions.MetricSet.BASIC)) {
            builder.addExecInterceptorFirst(TIMER_ID, new TimerExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.IO)) {
            builder.addExecInterceptorFirst(IO_ID, new IoByteCounterExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.CONN_POOL)) {
            ConnPoolMeters.bindTo(builder, meterReg, config);
        }
    }

    /* ============== Classic (with caching) =============== */

    /**
     * Enables observations and default metrics on a caching classic client builder using
     * {@link Metrics#globalRegistry} and {@link MetricConfig#DEFAULT}.
     *
     * @param builder caching client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @since 5.6
     */
    public static void enable(final CachingHttpClientBuilder builder,
                              final ObservationRegistry obsReg) {
        enable(builder, obsReg, Metrics.globalRegistry, ObservingOptions.DEFAULT, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a caching classic client builder using
     * {@link Metrics#globalRegistry} and a custom {@link ObservingOptions}.
     *
     * @param builder caching client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @param opts    observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final CachingHttpClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, Metrics.globalRegistry, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a caching classic client builder with an explicit
     * meter registry and default {@link MetricConfig}.
     *
     * @param builder  caching client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final CachingHttpClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, meterReg, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a caching classic client builder using explicit
     * registries and {@link MetricConfig}.
     *
     * <p>Interceptors are installed <em>after</em> the caching element so that
     * metrics/observations reflect the actual exchange.</p>
     *
     * @param builder  caching client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final CachingHttpClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts,
                              final MetricConfig mc) {

        Args.notNull(builder, "builder");
        Args.notNull(meterReg, "meterRegistry");

        final ObservingOptions o = (opts != null) ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = (mc != null) ? mc : MetricConfig.DEFAULT;

        // Observations (after caching stage so they see the real exchange)
        if (obsReg != null) {
            builder.addExecInterceptorAfter(ChainElement.CACHING.name(), OBS_ID,
                    new ObservationClassicExecInterceptor(obsReg, opts));
        }

        // Metrics
        if (o.metricSets.contains(ObservingOptions.MetricSet.BASIC)) {
            builder.addExecInterceptorAfter(ChainElement.CACHING.name(), TIMER_ID, new TimerExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.IO)) {
            builder.addExecInterceptorAfter(ChainElement.CACHING.name(), IO_ID, new IoByteCounterExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.CONN_POOL)) {
            ConnPoolMeters.bindTo(builder, meterReg, config);
        }
    }

    /* ======================== Async ====================== */

    /**
     * Enables observations and default metrics on an async client builder using
     * {@link Metrics#globalRegistry} and {@link MetricConfig#DEFAULT}.
     *
     * @param builder async client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @since 5.6
     */
    public static void enable(final HttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg) {
        enable(builder, obsReg, Metrics.globalRegistry, ObservingOptions.DEFAULT, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on an async client builder using
     * {@link Metrics#globalRegistry} and a custom {@link ObservingOptions}.
     *
     * @param builder async client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @param opts    observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final HttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, Metrics.globalRegistry, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on an async client builder with an explicit
     * meter registry and default {@link MetricConfig}.
     *
     * @param builder  async client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final HttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, meterReg, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on an async client builder using explicit
     * registries and {@link MetricConfig}.
     *
     * @param builder  async client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final HttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts,
                              final MetricConfig mc) {

        Args.notNull(builder, "builder");
        Args.notNull(meterReg, "meterRegistry");

        final ObservingOptions o = opts != null ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = mc != null ? mc : MetricConfig.DEFAULT;

        // Observations
        if (obsReg != null) {
            builder.addExecInterceptorFirst(OBS_ID, new ObservationAsyncExecInterceptor(obsReg, o));
        }

        // Metrics
        if (o.metricSets.contains(ObservingOptions.MetricSet.BASIC)) {
            builder.addExecInterceptorFirst(TIMER_ID, new AsyncTimerExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.IO)) {
            builder.addExecInterceptorFirst(IO_ID, new AsyncIoByteCounterExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.CONN_POOL)) {
            ConnPoolMetersAsync.bindTo(builder, meterReg, config);
        }
    }

    /* ============== Async (with caching) ================= */

    /**
     * Enables observations and default metrics on a caching async client builder using
     * {@link Metrics#globalRegistry} and {@link MetricConfig#DEFAULT}.
     *
     * @param builder caching async client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @since 5.6
     */
    public static void enable(final CachingHttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg) {
        enable(builder, obsReg, Metrics.globalRegistry, ObservingOptions.DEFAULT, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a caching async client builder using
     * {@link Metrics#globalRegistry} and a custom {@link ObservingOptions}.
     *
     * @param builder caching async client builder to instrument
     * @param obsReg  observation registry; if {@code null} no observations are installed
     * @param opts    observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final CachingHttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, Metrics.globalRegistry, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a caching async client builder with an explicit
     * meter registry and default {@link MetricConfig}.
     *
     * @param builder  caching async client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final CachingHttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts) {
        enable(builder, obsReg, meterReg, opts, MetricConfig.DEFAULT);
    }

    /**
     * Enables observations and metrics on a caching async client builder using explicit
     * registries and {@link MetricConfig}.
     *
     * <p>Interceptors are installed <em>after</em> the caching element.</p>
     *
     * @param builder  caching async client builder to instrument
     * @param obsReg   observation registry; if {@code null} no observations are installed
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @since 5.6
     */
    public static void enable(final CachingHttpAsyncClientBuilder builder,
                              final ObservationRegistry obsReg,
                              final MeterRegistry meterReg,
                              final ObservingOptions opts,
                              final MetricConfig mc) {

        Args.notNull(builder, "builder");
        Args.notNull(meterReg, "meterRegistry");

        final ObservingOptions o = opts != null ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = mc != null ? mc : MetricConfig.DEFAULT;

        // Observations (after caching)
        if (obsReg != null) {
            builder.addExecInterceptorAfter(ChainElement.CACHING.name(), OBS_ID,
                    new ObservationAsyncExecInterceptor(obsReg, o));
        }

        // Metrics
        if (o.metricSets.contains(ObservingOptions.MetricSet.BASIC)) {
            builder.addExecInterceptorAfter(ChainElement.CACHING.name(), TIMER_ID, new AsyncTimerExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.IO)) {
            builder.addExecInterceptorAfter(ChainElement.CACHING.name(), IO_ID, new AsyncIoByteCounterExec(meterReg, o, config));
        }
        if (o.metricSets.contains(ObservingOptions.MetricSet.CONN_POOL)) {
            ConnPoolMetersAsync.bindTo(builder, meterReg, config);
        }
    }

    /**
     * Wraps a classic connection manager with Micrometer pool-lease metrics if
     * {@link ObservingOptions.MetricSet#CONN_POOL} is enabled. Otherwise returns
     * the delegate unchanged.
     *
     * @param delegate connection manager to wrap
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @return metered connection manager or original delegate
     * @since 5.7
     */
    public static HttpClientConnectionManager meteredConnectionManager(final HttpClientConnectionManager delegate,
                                                                       final MeterRegistry meterReg,
                                                                       final ObservingOptions opts,
                                                                       final MetricConfig mc) {
        Args.notNull(delegate, "delegate");
        Args.notNull(meterReg, "meterRegistry");
        final ObservingOptions o = opts != null ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = mc != null ? mc : MetricConfig.DEFAULT;
        if (!o.metricSets.contains(ObservingOptions.MetricSet.CONN_POOL)) {
            return delegate;
        }
        return new MeteredConnectionManager(delegate, meterReg, config, o);
    }

    /**
     * Wraps an async connection manager with Micrometer pool-lease metrics if
     * {@link ObservingOptions.MetricSet#CONN_POOL} is enabled. Otherwise returns
     * the delegate unchanged.
     *
     * @param delegate connection manager to wrap
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @return metered connection manager or original delegate
     * @since 5.7
     */
    public static AsyncClientConnectionManager meteredAsyncConnectionManager(final AsyncClientConnectionManager delegate,
                                                                             final MeterRegistry meterReg,
                                                                             final ObservingOptions opts,
                                                                             final MetricConfig mc) {
        Args.notNull(delegate, "delegate");
        Args.notNull(meterReg, "meterRegistry");
        final ObservingOptions o = opts != null ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = mc != null ? mc : MetricConfig.DEFAULT;
        if (!o.metricSets.contains(ObservingOptions.MetricSet.CONN_POOL)) {
            return delegate;
        }
        return new MeteredAsyncConnectionManager(delegate, meterReg, config, o);
    }

    /**
     * Wraps a DNS resolver with Micrometer metrics if {@link ObservingOptions.MetricSet#DNS}
     * is enabled. Otherwise returns the delegate unchanged.
     *
     * @param delegate underlying DNS resolver
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @return metered resolver or original delegate
     * @since 5.7
     */
    public static DnsResolver meteredDnsResolver(final DnsResolver delegate,
                                                 final MeterRegistry meterReg,
                                                 final ObservingOptions opts,
                                                 final MetricConfig mc) {
        Args.notNull(delegate, "delegate");
        Args.notNull(meterReg, "meterRegistry");
        final ObservingOptions o = opts != null ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = mc != null ? mc : MetricConfig.DEFAULT;
        if (!o.metricSets.contains(ObservingOptions.MetricSet.DNS)) {
            return delegate;
        }
        return new MeteredDnsResolver(delegate, meterReg, config, o);
    }

    /**
     * Wraps a TLS strategy with Micrometer metrics if {@link ObservingOptions.MetricSet#TLS}
     * is enabled. Otherwise returns the delegate unchanged.
     *
     * @param delegate TLS strategy to wrap
     * @param meterReg meter registry to register meters with (must not be {@code null})
     * @param opts     observation/metric options; when {@code null} {@link ObservingOptions#DEFAULT} is used
     * @param mc       metric configuration; when {@code null} {@link MetricConfig#DEFAULT} is used
     * @return metered strategy or original delegate
     * @since 5.7
     */
    public static TlsStrategy meteredTlsStrategy(final TlsStrategy delegate,
                                                 final MeterRegistry meterReg,
                                                 final ObservingOptions opts,
                                                 final MetricConfig mc) {
        Args.notNull(delegate, "delegate");
        Args.notNull(meterReg, "meterRegistry");
        final ObservingOptions o = opts != null ? opts : ObservingOptions.DEFAULT;
        final MetricConfig config = mc != null ? mc : MetricConfig.DEFAULT;
        if (!o.metricSets.contains(ObservingOptions.MetricSet.TLS)) {
            return delegate;
        }
        return new MeteredTlsStrategy(delegate, meterReg, config, o);
    }

    /**
     * No instantiation.
     *
     * @since 5.6
     */
    private HttpClientObservationSupport() {
    }
}
