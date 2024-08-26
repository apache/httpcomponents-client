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

package org.apache.hc.client5.http.impl.io;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;

/**
 * Builder for {@link PoolingHttpClientConnectionManager} instances.
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
public class PoolingHttpClientConnectionManagerBuilder {

    private HttpConnectionFactory<ManagedHttpClientConnection> connectionFactory;
    private TlsSocketStrategy tlsSocketStrategy;
    private SchemePortResolver schemePortResolver;
    private DnsResolver dnsResolver;
    private PoolConcurrencyPolicy poolConcurrencyPolicy;
    private PoolReusePolicy poolReusePolicy;
    private Resolver<HttpRoute, SocketConfig> socketConfigResolver;
    private Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver;
    private Resolver<HttpHost, TlsConfig> tlsConfigResolver;

    private boolean systemProperties;

    private int maxConnTotal;
    private int maxConnPerRoute;

    public static PoolingHttpClientConnectionManagerBuilder create() {
        return new PoolingHttpClientConnectionManagerBuilder();
    }

    @Internal
    protected PoolingHttpClientConnectionManagerBuilder() {
        super();
    }

    /**
     * Sets {@link HttpConnectionFactory} instance.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setConnectionFactory(
            final HttpConnectionFactory<ManagedHttpClientConnection> connectionFactory) {
        this.connectionFactory = connectionFactory;
        return this;
    }

    /**
     * Sets {@link org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory} instance.
     *
     * @return this instance.
     * @deprecated Use {@link #setTlsSocketStrategy(TlsSocketStrategy)}
     */
    @Deprecated
    public final PoolingHttpClientConnectionManagerBuilder setSSLSocketFactory(
            final org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory sslSocketFactory) {
        this.tlsSocketStrategy = (socket, target, port, attachment, context) ->
                (SSLSocket) sslSocketFactory.createLayeredSocket(socket, target, port, context);
        return this;
    }

    /**
     * Sets {@link TlsSocketStrategy} instance.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setTlsSocketStrategy(final TlsSocketStrategy tlsSocketStrategy) {
        this.tlsSocketStrategy = tlsSocketStrategy;
        return this;
    }

    /**
     * Sets {@link DnsResolver} instance.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Sets {@link SchemePortResolver} instance.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Sets {@link PoolConcurrencyPolicy} value.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    /**
     * Sets {@link PoolReusePolicy} value.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setConnPoolPolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    /**
     * Sets maximum total connection value.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setMaxConnTotal(final int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
        return this;
    }

    /**
     * Sets maximum connection per route value.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setMaxConnPerRoute(final int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }

    /**
     * Sets the same {@link SocketConfig} for all routes.
     *
     * @return this instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setDefaultSocketConfig(final SocketConfig config) {
        this.socketConfigResolver = route -> config;
        return this;
    }

    /**
     * Sets {@link Resolver} of {@link SocketConfig} on a per route basis.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingHttpClientConnectionManagerBuilder setSocketConfigResolver(
            final Resolver<HttpRoute, SocketConfig> socketConfigResolver) {
        this.socketConfigResolver = socketConfigResolver;
        return this;
    }

    /**
     * Sets the same {@link ConnectionConfig} for all routes.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingHttpClientConnectionManagerBuilder setDefaultConnectionConfig(final ConnectionConfig config) {
        this.connectionConfigResolver = route -> config;
        return this;
    }

    /**
     * Sets {@link Resolver} of {@link ConnectionConfig} on a per route basis.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingHttpClientConnectionManagerBuilder setConnectionConfigResolver(
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
    public final PoolingHttpClientConnectionManagerBuilder setDefaultTlsConfig(final TlsConfig config) {
        this.tlsConfigResolver = host -> config;
        return this;
    }

    /**
     * Sets {@link Resolver} of {@link TlsConfig} on a per host basis.
     *
     * @return this instance.
     * @since 5.2
     */
    public final PoolingHttpClientConnectionManagerBuilder setTlsConfigResolver(
            final Resolver<HttpHost, TlsConfig> tlsConfigResolver) {
        this.tlsConfigResolver = tlsConfigResolver;
        return this;
    }

    /**
     * Sets maximum time to live for persistent connections
     *
     * @return this instance.
     * @deprecated Use {@link #setDefaultConnectionConfig(ConnectionConfig)}.
     */
    @Deprecated
    public final PoolingHttpClientConnectionManagerBuilder setConnectionTimeToLive(final TimeValue timeToLive) {
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
     * @deprecated Use {@link #setDefaultConnectionConfig(ConnectionConfig)}.
     */
    @Deprecated
    public final PoolingHttpClientConnectionManagerBuilder setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
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
    public final PoolingHttpClientConnectionManagerBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    @Internal
    protected HttpClientConnectionOperator createConnectionOperator(
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final TlsSocketStrategy tlsSocketStrategy) {
        return new DefaultHttpClientConnectionOperator(schemePortResolver, dnsResolver,
                RegistryBuilder.<TlsSocketStrategy>create()
                        .register(URIScheme.HTTPS.id, tlsSocketStrategy)
                        .build());
    }

    public PoolingHttpClientConnectionManager build() {
        final TlsSocketStrategy tlsSocketStrategyCopy;
        if (tlsSocketStrategy != null) {
            tlsSocketStrategyCopy = tlsSocketStrategy;
        } else {
            if (systemProperties) {
                tlsSocketStrategyCopy = DefaultClientTlsStrategy.createSystemDefault();
            } else {
                tlsSocketStrategyCopy = DefaultClientTlsStrategy.createDefault();
            }
        }

        final PoolingHttpClientConnectionManager poolingmgr = new PoolingHttpClientConnectionManager(
                createConnectionOperator(schemePortResolver, dnsResolver, tlsSocketStrategyCopy),
                poolConcurrencyPolicy,
                poolReusePolicy,
                null,
                connectionFactory);
        poolingmgr.setSocketConfigResolver(socketConfigResolver);
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
