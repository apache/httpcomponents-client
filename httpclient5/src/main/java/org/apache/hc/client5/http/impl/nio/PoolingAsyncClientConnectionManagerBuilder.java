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

package org.apache.hc.client5.http.impl.nio;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.ssl.ConscryptClientTlsStrategy;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.ReflectionUtils;
import org.apache.hc.core5.util.TimeValue;

/**
 * Builder for {@link PoolingAsyncClientConnectionManager} instances.
 * <p>
 * When a particular component is not explicitly set this class will
 * use its default implementation. System properties will be taken
 * into account when configuring the default implementations when
 * {@link #useSystemProperties()} method is called prior to calling
 * {@link #build()}.
 * </p>
 * <ul>
 *  <li>ssl.TrustManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.trustStoreType</li>
 *  <li>javax.net.ssl.trustStore</li>
 *  <li>javax.net.ssl.trustStoreProvider</li>
 *  <li>javax.net.ssl.trustStorePassword</li>
 *  <li>ssl.KeyManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.keyStoreType</li>
 *  <li>javax.net.ssl.keyStore</li>
 *  <li>javax.net.ssl.keyStoreProvider</li>
 *  <li>javax.net.ssl.keyStorePassword</li>
 *  <li>https.protocols</li>
 *  <li>https.cipherSuites</li>
 * </ul>
 *
 * @since 5.0
 */
public class PoolingAsyncClientConnectionManagerBuilder {

    private TlsStrategy tlsStrategy;
    private SchemePortResolver schemePortResolver;
    private DnsResolver dnsResolver;
    private PoolConcurrencyPolicy poolConcurrencyPolicy;
    private PoolReusePolicy poolReusePolicy;

    private boolean systemProperties;

    private int maxConnTotal;
    private int maxConnPerRoute;

    private Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver;
    private Resolver<HttpHost, TlsConfig> tlsConfigResolver;
    private boolean messageMultiplexing;

    public static PoolingAsyncClientConnectionManagerBuilder create() {
        return new PoolingAsyncClientConnectionManagerBuilder();
    }

    @Internal
    protected PoolingAsyncClientConnectionManagerBuilder() {
        super();
    }

    /**
     * Sets {@link TlsStrategy} instance for TLS connections.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setTlsStrategy(
            final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Sets {@link DnsResolver} instance.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Sets {@link SchemePortResolver} instance.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Sets {@link PoolConcurrencyPolicy} value.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    /**
     * Sets {@link PoolReusePolicy} value.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setConnPoolPolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    /**
     * Sets maximum total connection value.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setMaxConnTotal(final int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
        return this;
    }

    /**
     * Sets maximum connection per route value.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setMaxConnPerRoute(final int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }

    /**
     * Sets the same {@link ConnectionConfig} for all routes.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingAsyncClientConnectionManagerBuilder setDefaultConnectionConfig(final ConnectionConfig config) {
        this.connectionConfigResolver = route -> config;
        return this;
    }

    /**
     * Sets {@link Resolver} of {@link ConnectionConfig} on a per route basis.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingAsyncClientConnectionManagerBuilder setConnectionConfigResolver(
            final Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver) {
        this.connectionConfigResolver = connectionConfigResolver;
        return this;
    }

    /**
     * Sets the same {@link TlsConfig} for all hosts.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingAsyncClientConnectionManagerBuilder setDefaultTlsConfig(final TlsConfig config) {
        this.tlsConfigResolver = host -> config;
        return this;
    }

    /**
     * Sets {@link Resolver} of {@link TlsConfig} on a per host basis.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingAsyncClientConnectionManagerBuilder setTlsConfigResolver(
            final Resolver<HttpHost, TlsConfig> tlsConfigResolver) {
        this.tlsConfigResolver = tlsConfigResolver;
        return this;
    }

    /**
     * Sets maximum time to live for persistent connections
     *
     * @return this instance.
     * @deprecated Use {@link #setDefaultConnectionConfig(ConnectionConfig)}
     */
    @Deprecated
    public final PoolingAsyncClientConnectionManagerBuilder setConnectionTimeToLive(final TimeValue timeToLive) {
        setDefaultConnectionConfig(ConnectionConfig.custom()
                .setTimeToLive(timeToLive)
                .build());
        return this;
    }

    /**
     * Sets period after inactivity after which persistent
     * connections must be checked to ensure they are still valid.
     *
     * @return this instance.
     * @deprecated Use {@link #setConnectionConfigResolver(Resolver)}.
     */
    @Deprecated
    public final PoolingAsyncClientConnectionManagerBuilder setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        setDefaultConnectionConfig(ConnectionConfig.custom()
                .setValidateAfterInactivity(validateAfterInactivity)
                .build());
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     *
     * @return this instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    /**
     * Use experimental connections pool implementation that acts as a caching facade
     * in front of a standard connection pool and shares already leased connections
     * to multiplex message exchanges over active HTTP/2 connections.
     *<p>
     * Please note this flag has no effect on HTTP/1.1 and HTTP/1.0 connections.
     *<p>
     * This feature is considered experimenal
     *
     * @since 5.5
     * @return this instance.
     */
    @Experimental
    public final PoolingAsyncClientConnectionManagerBuilder setMessageMultiplexing(final boolean messageMultiplexing) {
        this.messageMultiplexing = messageMultiplexing;
        return this;
    }

    @Internal
    protected AsyncClientConnectionOperator createConnectionOperator(
            final TlsStrategy tlsStrategy,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        return new DefaultAsyncClientConnectionOperator(
                RegistryBuilder.<TlsStrategy>create()
                        .register(URIScheme.HTTPS.getId(), tlsStrategy)
                        .build(),
                schemePortResolver,
                dnsResolver);
    }

    public PoolingAsyncClientConnectionManager build() {
        final TlsStrategy tlsStrategyCopy;
        if (tlsStrategy != null) {
            tlsStrategyCopy = tlsStrategy;
        } else {
            if (ReflectionUtils.determineJRELevel() <= 8 && ConscryptClientTlsStrategy.isSupported()) {
                if (systemProperties) {
                    tlsStrategyCopy = ConscryptClientTlsStrategy.getSystemDefault();
                } else {
                    tlsStrategyCopy = ConscryptClientTlsStrategy.getDefault();
                }
            } else {
                if (systemProperties) {
                    tlsStrategyCopy = DefaultClientTlsStrategy.createSystemDefault();
                } else {
                    tlsStrategyCopy = DefaultClientTlsStrategy.createDefault();
                }
            }
        }
        final PoolingAsyncClientConnectionManager poolingmgr = new PoolingAsyncClientConnectionManager(
                createConnectionOperator(tlsStrategyCopy, schemePortResolver, dnsResolver),
                poolConcurrencyPolicy,
                poolReusePolicy,
                null,
                messageMultiplexing);
        poolingmgr.setConnectionConfigResolver(connectionConfigResolver);
        poolingmgr.setTlsConfigResolver(tlsConfigResolver);
        if (maxConnTotal > 0) {
            poolingmgr.setMaxTotal(maxConnTotal);
        }
        if (maxConnPerRoute > 0) {
            poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
        }
        return poolingmgr;
    }

}
