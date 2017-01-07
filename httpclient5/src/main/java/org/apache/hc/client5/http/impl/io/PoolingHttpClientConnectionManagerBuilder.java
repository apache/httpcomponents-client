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

import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.util.TextUtils;

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

    private LayeredConnectionSocketFactory sslSocketFactory;
    private DnsResolver dnsResolver;

    private SocketConfig defaultSocketConfig;
    private ConnectionConfig defaultConnectionConfig;

    private boolean systemProperties;

    private int maxConnTotal = 0;
    private int maxConnPerRoute = 0;

    private long connTimeToLive = -1;
    private TimeUnit connTimeToLiveTimeUnit = TimeUnit.MILLISECONDS;
    private int validateAfterInactivity = 2000;

    public static PoolingHttpClientConnectionManagerBuilder create() {
        return new PoolingHttpClientConnectionManagerBuilder();
    }

    PoolingHttpClientConnectionManagerBuilder() {
        super();
    }

    /**
     * Assigns {@link LayeredConnectionSocketFactory} instance for SSL connections.
     */
    public final PoolingHttpClientConnectionManagerBuilder setSSLSocketFactory(
            final LayeredConnectionSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    /**
     * Assigns maximum total connection value.
     */
    public final PoolingHttpClientConnectionManagerBuilder setMaxConnTotal(final int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
        return this;
    }

    /**
     * Assigns maximum connection per route value.
     */
    public final PoolingHttpClientConnectionManagerBuilder setMaxConnPerRoute(final int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }

    /**
     * Assigns default {@link SocketConfig}.
     */
    public final PoolingHttpClientConnectionManagerBuilder setDefaultSocketConfig(final SocketConfig config) {
        this.defaultSocketConfig = config;
        return this;
    }

    /**
     * Assigns default {@link ConnectionConfig}.
     */
    public final PoolingHttpClientConnectionManagerBuilder setDefaultConnectionConfig(final ConnectionConfig config) {
        this.defaultConnectionConfig = config;
        return this;
    }

    /**
     * Sets maximum time to live for persistent connections
     *
     * @since 4.4
     */
    public final PoolingHttpClientConnectionManagerBuilder setConnectionTimeToLive(final long connTimeToLive, final TimeUnit connTimeToLiveTimeUnit) {
        this.connTimeToLive = connTimeToLive;
        this.connTimeToLiveTimeUnit = connTimeToLiveTimeUnit;
        return this;
    }

    /**
     * Sets period after inactivity in milliseconds after which persistent
     * connections must be checked to ensure they are still valid.
     *
     * @see org.apache.hc.core5.http.io.HttpClientConnection#isStale()
     */
    public final PoolingHttpClientConnectionManagerBuilder setValidateAfterInactivity(final int validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
        return this;
    }

    /**
     * Assigns {@link DnsResolver} instance.
     */
    public final PoolingHttpClientConnectionManagerBuilder setDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final PoolingHttpClientConnectionManagerBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

    public PoolingHttpClientConnectionManager build() {
        @SuppressWarnings("resource")
        final PoolingHttpClientConnectionManager poolingmgr = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslSocketFactory != null ? sslSocketFactory :
                                (systemProperties ?
                                        SSLConnectionSocketFactory.getSystemSocketFactory() :
                                        SSLConnectionSocketFactory.getSocketFactory()))
                        .build(),
                null,
                null,
                dnsResolver,
                connTimeToLive,
                connTimeToLiveTimeUnit != null ? connTimeToLiveTimeUnit : TimeUnit.MILLISECONDS);
        poolingmgr.setValidateAfterInactivity(this.validateAfterInactivity);
        if (defaultSocketConfig != null) {
            poolingmgr.setDefaultSocketConfig(defaultSocketConfig);
        }
        if (defaultConnectionConfig != null) {
            poolingmgr.setDefaultConnectionConfig(defaultConnectionConfig);
        }
        if (maxConnTotal > 0) {
            poolingmgr.setMaxTotal(maxConnTotal);
        }
        if (maxConnPerRoute > 0) {
            poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
        }
        return poolingmgr;
    }

}
