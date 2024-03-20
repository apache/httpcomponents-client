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
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.ssl.ConscryptClientTlsStrategy;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ReflectionUtils;
import org.apache.hc.core5.util.Timeout;

import java.util.concurrent.ScheduledExecutorService;

/**
 * A builder for creating instances of {@link HappyEyeballsV2AsyncClientConnectionOperator}.
 *
 * <p>This builder provides a fluent API for configuring various options of the
 * {@link HappyEyeballsV2AsyncClientConnectionOperator}. Once all the desired options have been set,
 * the {@link #build()} method can be called to create an instance of the connection operator.
 *
 * <p>The following options can be configured using this builder:
 * <ul>
 *     <li>The TLS strategy to be used for establishing TLS connections</li>
 *     <li>The connection operator to be used for creating connections</li>
 *     <li>The DNS resolver to be used for resolving hostnames</li>
 *     <li>The timeout for establishing a connection</li>
 *     <li>The delay between resolution of hostnames and connection establishment attempts</li>
 *     <li>The minimum and maximum delays between connection establishment attempts</li>
 *     <li>The delay between subsequent connection establishment attempts</li>
 *     <li>The number of connections to be established with the first address family in the list</li>
 *     <li>The preferred address family to be used for establishing connections</li>
 * </ul>
 *
 * <p>If no options are explicitly set using this builder, default options will be used for each option.
 *
 * <p>This class is not thread-safe.
 *
 * @see HappyEyeballsV2AsyncClientConnectionOperator
 * @since 5.3
 */
public class HappyEyeballsV2AsyncClientConnectionOperatorBuilder {

    private AsyncClientConnectionOperator connectionOperator;
    private DnsResolver dnsResolver;
    private TlsStrategy tlsStrategy;
    private Timeout timeout;
    private Timeout minimumConnectionAttemptDelay;
    private Timeout maximumConnectionAttemptDelay;
    private Timeout connectionAttemptDelay;
    private Timeout resolutionDelay;
    private int firstAddressFamilyCount;
    private HappyEyeballsV2AsyncClientConnectionOperator.AddressFamily addressFamily;
    private ScheduledExecutorService scheduler;


    public static HappyEyeballsV2AsyncClientConnectionOperatorBuilder create() {
        return new HappyEyeballsV2AsyncClientConnectionOperatorBuilder();
    }


    HappyEyeballsV2AsyncClientConnectionOperatorBuilder() {
        super();
    }


    private boolean systemProperties;

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final HappyEyeballsV2AsyncClientConnectionOperatorBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    /**
     * Sets the {@link AsyncClientConnectionOperator} to use for establishing connections.
     *
     * @param connectionOperator the {@link AsyncClientConnectionOperator} to use
     * @return this {@link HappyEyeballsV2AsyncClientConnectionOperatorBuilder} instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withConnectionOperator(
            final AsyncClientConnectionOperator connectionOperator) {
        this.connectionOperator = connectionOperator;
        return this;
    }

    /**
     * Sets the {@link DnsResolver} to use for resolving host names to IP addresses.
     *
     * @param dnsResolver the {@link DnsResolver} to use
     * @return this builder instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Sets the {@link TlsStrategy} to use for creating TLS connections.
     *
     * @param tlsStrategy the {@link TlsStrategy} to use
     * @return this {@link HappyEyeballsV2AsyncClientConnectionOperatorBuilder} instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withTlsStrategyLookup(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Set the timeout to use for connection attempts.
     *
     * @param timeout the timeout to use for connection attempts
     * @return this builder
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withTimeout(final Timeout timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the minimum delay between connection attempts. The actual delay may be longer if a resolution delay has been
     * specified, in which case the minimum connection attempt delay is added to the resolution delay.
     *
     * @param minimumConnectionAttemptDelay the minimum delay between connection attempts
     * @return this builder instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withMinimumConnectionAttemptDelay(
            final Timeout minimumConnectionAttemptDelay) {
        this.minimumConnectionAttemptDelay = minimumConnectionAttemptDelay;
        return this;
    }

    /**
     * Sets the maximum delay between two connection attempts.
     *
     * @param maximumConnectionAttemptDelay the maximum delay between two connection attempts
     * @return the builder instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withMaximumConnectionAttemptDelay(
            final Timeout maximumConnectionAttemptDelay) {
        this.maximumConnectionAttemptDelay = maximumConnectionAttemptDelay;
        return this;
    }

    /**
     * Sets the delay between two connection attempts.
     *
     * @param connectionAttemptDelay the delay between two connection attempts
     * @return the builder instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withConnectionAttemptDelay(
            final Timeout connectionAttemptDelay) {
        this.connectionAttemptDelay = connectionAttemptDelay;
        return this;
    }

    /**
     * Sets the delay before attempting to resolve the next address in the list.
     *
     * @param resolutionDelay the delay before attempting to resolve the next address in the list
     * @return the builder instance
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withResolutionDelay(final Timeout resolutionDelay) {
        this.resolutionDelay = resolutionDelay;
        return this;
    }

    /**
     * Sets the number of first address families to try before falling back to the other address families.
     *
     * @param firstAddressFamilyCount the number of first address families to try
     * @return this builder
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withFirstAddressFamilyCount(
            final int firstAddressFamilyCount) {
        this.firstAddressFamilyCount = firstAddressFamilyCount;
        return this;
    }

    /**
     * Sets the preferred address family to use for connections.
     *
     * @param addressFamily the preferred address family
     * @return this builder
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withAddressFamily(final HappyEyeballsV2AsyncClientConnectionOperator.AddressFamily addressFamily) {
        this.addressFamily = addressFamily;
        return this;
    }

    /**
     * Sets the {@link ScheduledExecutorService} for the {@link HappyEyeballsV2AsyncClientConnectionOperator}.
     *
     * @param scheduler The ScheduledExecutorService to set.
     * @return this builder
     */
    public HappyEyeballsV2AsyncClientConnectionOperatorBuilder withScheduler(final ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    /**
     * Builds a {@link HappyEyeballsV2AsyncClientConnectionOperator} with the specified parameters.
     *
     * @return the {@link HappyEyeballsV2AsyncClientConnectionOperator} instance built with the specified parameters.
     * @throws IllegalArgumentException if the connection operator is null.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator build() {
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


        connectionOperator = Args.notNull(connectionOperator, "Connection operator");

        return new HappyEyeballsV2AsyncClientConnectionOperator(
                RegistryBuilder.<TlsStrategy>create()
                        .register(URIScheme.HTTPS.getId(), tlsStrategyCopy)
                        .build(),
                connectionOperator,
                dnsResolver,
                timeout,
                resolutionDelay,
                minimumConnectionAttemptDelay,
                maximumConnectionAttemptDelay,
                connectionAttemptDelay,
                firstAddressFamilyCount,
                addressFamily,
                scheduler
        );
    }
}
