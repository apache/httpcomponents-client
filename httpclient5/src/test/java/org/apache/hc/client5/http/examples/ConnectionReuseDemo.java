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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.DefaultManagedAsyncClientConnection;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.command.StaleCheckCommand;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.VersionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.Level.WARN;

/**
 * This example demonstrates connection reuse, with a specific focus on what happens when there are not enough requests
 * in flight to keep all the connections active. There are several ways to configure a connection pool (see
 * {@link PoolReusePolicy}, {@link PoolConcurrencyPolicy}, and
 * {@link HttpAsyncClientBuilder#evictIdleConnections(TimeValue)}), and there are also numerous settings that affect
 * connection expiry, including:
 * <ul>
 *     <li>{@link ConnectionConfig#getTimeToLive()}</li>
 *     <li>{@link ConnectionConfig#getIdleTimeout()}</li>
 *     <li>{@link ConnectionConfig#getValidateAfterInactivity()}</li>
 *     <li>{@link RequestConfig#getConnectionKeepAlive()}</li>
 * </ul>
 * This example can be used to experiment with different config values in order to answer questions like:
 * <ul>
 *     <li>Which connections get reused? Which ones expire?</li>
 *     <li>Where are the various connection expiry settings implemented?</li>
 *     <li>Do expired connections get leased out? If so, what happens?</li>
 *     <li>When the connection pool is too large, does it shrink? If so, what size does it converge on?</li>
 *     <li>Does inactive connection validation through {@link StaleCheckCommand} add latency?</li>
 * </ul>
 */
public class ConnectionReuseDemo {
    private static final Logger LOG;

    static {
        final ConfigurationBuilder<BuiltConfiguration> config = ConfigurationBuilderFactory.newConfigurationBuilder();
        config.setStatusLevel(WARN);
        config.setConfigurationName("ConnectionReuseDemo");

        final AppenderComponentBuilder console = config.newAppender("APPLICATION", "CONSOLE")
            .add(config.newLayout("PatternLayout")
                .addAttribute("pattern", "%d{HH:mm:ss.SSS} %highlight{[%p]} (%t) %C{1}: %m%n"));

        config.add(console)
            .add(config.newRootLogger(INFO).add(config.newAppenderRef("APPLICATION")))
            .add(config.newLogger(DefaultManagedAsyncClientConnection.class.getName(), DEBUG));

        Configurator.initialize(config.build()).start();

        LOG = LogManager.getLogger(ConnectionReuseDemo.class);
    }

    public static void main(final String[] args) throws InterruptedException {
        final ClassLoader cl = ConnectionReuseDemo.class.getClassLoader();
        LOG.info("Running client {}, core {}",
            VersionInfo.loadVersionInfo("org.apache.hc.client5", cl).getRelease(),
            VersionInfo.loadVersionInfo("org.apache.hc.core5", cl).getRelease());

        final PoolConcurrencyPolicy concurrencyPolicy = PoolConcurrencyPolicy.OFFLOCK;
        final PoolReusePolicy reusePolicy = PoolReusePolicy.FIFO;
        final Timeout idleTimeout = null;
        final TimeValue timeToLive = null;
        final TimeValue validateAfterInactivity = TimeValue.ofSeconds(2);
        final TimeValue evictIdleConnections = null;
        final TimeValue connectionKeepAlive = TimeValue.ofSeconds(5);

        LOG.info("Pool type: {} ({})", concurrencyPolicy, reusePolicy);
        LOG.info("Connection config: idleTimeout={}, timeToLive={}, validateAfterInactivity={}",
            idleTimeout, timeToLive, validateAfterInactivity);
        LOG.info("evictIdleConnections: {}", evictIdleConnections);
        LOG.info("connectionKeepAlive: {}", connectionKeepAlive);

        final PoolingAsyncClientConnectionManager mgr = PoolingAsyncClientConnectionManagerBuilder.create()
            .setMaxConnPerRoute(Integer.MAX_VALUE)
            .setMaxConnTotal(Integer.MAX_VALUE)
            .setConnPoolPolicy(reusePolicy)
            .setPoolConcurrencyPolicy(concurrencyPolicy)
            .setDefaultTlsConfig(TlsConfig.custom()
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                .build())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(60, SECONDS)
                .setSocketTimeout(60, SECONDS)
                .setTimeToLive(timeToLive)
                .setIdleTimeout(idleTimeout)
                .setValidateAfterInactivity(validateAfterInactivity)
                .build())
            .build();

        final CloseableHttpAsyncClient client = getBuilder(evictIdleConnections)
            .disableAutomaticRetries()
            .setConnectionManager(mgr)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionKeepAlive(connectionKeepAlive)
                .build())
            .build();

        client.start();

        LOG.info("Sending warmup request");
        join(call(client));
        final HttpRoute route = mgr.getRoutes().iterator().next();
        mgr.getStats(route);

        LOG.info("Expanding connection pool");
        IntStream.range(0, 10)
            .mapToObj(unused -> call(client))
            .collect(Collectors.toList())
            .forEach(ConnectionReuseDemo::join);

        LOG.info("{} connections available. Walking connection pool...", mgr.getStats(route).getAvailable());
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1_000);
            LOG.info("Sending request {}; {} connections available", i + 1, mgr.getStats(route).getAvailable());
            final long startTime = System.nanoTime();
            join(call(client));
            LOG.info("Request took {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        }

        LOG.info("Waiting for all connections to expire");
        Thread.sleep(6_000);

        LOG.info("Sending one last request (should establish a new connection)");
        final int before = mgr.getStats(route).getAvailable();
        join(call(client));
        LOG.info("Connections available: {} -> {}", before, mgr.getStats(route).getAvailable());
    }

    private static HttpAsyncClientBuilder getBuilder(final TimeValue evictIdleConnections) {
        if (evictIdleConnections != null) {
            return HttpAsyncClientBuilder.create().evictIdleConnections(evictIdleConnections);
        }
        return HttpAsyncClientBuilder.create();
    }

    private static <T> void join(final Future<T> f) {
        try {
            f.get();
        } catch (final Throwable ignore) {
        }
    }

    private static Future<SimpleHttpResponse> call(final CloseableHttpAsyncClient client) {
        final SimpleHttpRequest req = SimpleHttpRequest.create(Method.GET, URI.create("https://www.amazon.co.jp/"));
        return client.execute(req,
            new FutureCallback<SimpleHttpResponse>() {
                @Override
                public void completed(final SimpleHttpResponse result) {
                }

                @Override
                public void failed(final Exception ex) {
                    LOG.error("Request failed", ex);
                }

                @Override
                public void cancelled() {
                    LOG.error("Request cancelled");
                }
            });
    }
}
