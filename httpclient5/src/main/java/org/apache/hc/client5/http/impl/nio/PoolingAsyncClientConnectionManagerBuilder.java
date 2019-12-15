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
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.ssl.ConscryptClientTlsStrategy;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
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

    private int maxConnTotal = 0;
    private int maxConnPerRoute = 0;

    private TimeValue timeToLive;
    private TimeValue validateAfterInactivity;

    public static PoolingAsyncClientConnectionManagerBuilder create() {
        return new PoolingAsyncClientConnectionManagerBuilder();
    }

    PoolingAsyncClientConnectionManagerBuilder() {
        super();
    }

    /**
     * Assigns {@link TlsStrategy} instance for TLS connections.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setTlsStrategy(
            final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Assigns {@link DnsResolver} instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Assigns {@link SchemePortResolver} instance.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Assigns {@link PoolConcurrencyPolicy} value.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setPoolConcurrencyPolicy(final PoolConcurrencyPolicy poolConcurrencyPolicy) {
        this.poolConcurrencyPolicy = poolConcurrencyPolicy;
        return this;
    }

    /**
     * Assigns {@link PoolReusePolicy} value.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setConnPoolPolicy(final PoolReusePolicy poolReusePolicy) {
        this.poolReusePolicy = poolReusePolicy;
        return this;
    }

    /**
     * Assigns maximum total connection value.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setMaxConnTotal(final int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
        return this;
    }

    /**
     * Assigns maximum connection per route value.
     */
    public final PoolingAsyncClientConnectionManagerBuilder setMaxConnPerRoute(final int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }

    /**
     * Sets maximum time to live for persistent connections
     */
    public final PoolingAsyncClientConnectionManagerBuilder setConnectionTimeToLive(final TimeValue timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    /**
     * Sets period after inactivity after which persistent
     * connections must be checked to ensure they are still valid.
     *
     * @see org.apache.hc.core5.http.io.HttpClientConnection#isStale()
     */
    public final PoolingAsyncClientConnectionManagerBuilder setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final PoolingAsyncClientConnectionManagerBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
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
                    tlsStrategyCopy = DefaultClientTlsStrategy.getSystemDefault();
                } else {
                    tlsStrategyCopy = DefaultClientTlsStrategy.getDefault();
                }
            }
        }
        final PoolingAsyncClientConnectionManager poolingmgr = new PoolingAsyncClientConnectionManager(
                RegistryBuilder.<TlsStrategy>create()
                        .register("https", tlsStrategyCopy)
                        .build(),
                poolConcurrencyPolicy,
                poolReusePolicy,
                timeToLive,
                schemePortResolver,
                dnsResolver);
        poolingmgr.setValidateAfterInactivity(this.validateAfterInactivity);
        if (maxConnTotal > 0) {
            poolingmgr.setMaxTotal(maxConnTotal);
        }
        if (maxConnPerRoute > 0) {
            poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
        }
        return poolingmgr;
    }

}
