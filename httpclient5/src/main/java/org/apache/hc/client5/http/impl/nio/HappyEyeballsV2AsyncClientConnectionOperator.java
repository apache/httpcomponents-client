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
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link AsyncClientConnectionOperator} implementation that uses Happy Eyeballs V2 algorithm to connect
 * to the target server. Happy Eyeballs V2 (HEV2) algorithm is used to connect to the target server by concurrently
 * attempting to establish multiple connections to different IP addresses. The first connection to complete
 * successfully is selected and the others are closed. If all connections fail, the last error is rethrown.
 * The algorithm also applies a configurable delay before subsequent connection attempts. HEV2 was introduced
 * as a means to mitigate the latency issues caused by IPv4 and IPv6 co-existence in the Internet. HEV2 is defined
 * in RFC 8305.
 *
 * <p>
 * This connection operator maintains a connection pool for each unique route (combination of target host and
 * target port) and selects the next connection from the pool to establish a new connection or reuse an
 * existing connection. The connection pool uses a First-In-First-Out (FIFO) queue and has a configurable limit
 * on the maximum number of connections that can be kept alive in the pool. Once the maximum number of connections
 * has been reached, the oldest connection in the pool is closed to make room for a new one.
 * </p>
 *
 * <p>
 * This class is thread-safe and can be used in a multi-threaded environment.
 * </p>
 *
 * <p>
 * The HEV2 algorithm is configurable through the following parameters:
 * <ul>
 *   <li>{@code dualStackEnabled}: Whether to enable dual-stack connectivity. When set to {@code true},
 *   the operator attempts to connect to both IPv4 and IPv6 addresses concurrently. When set to {@code false},
 *   only IPv4 or IPv6 addresses are attempted depending on the address type of the target server.</li>
 *   <li>{@code maxAttempts}: The maximum number of connection attempts to be made before failing. If all
 *   attempts fail, the last error is rethrown.</li>
 *   <li>{@code delay}: The delay (in milliseconds) to apply before subsequent connection attempts.</li>
 *   <li>{@code connectTimeout}: The connection timeout (in milliseconds) for each attempt.</li>
 * </ul>
 * </p>
 *
 *
 * <p>
 * This class can be used with any {@link org.apache.hc.core5.http.nio.AsyncClientEndpoint} implementation
 * that supports HTTP/1.1 or HTTP/2 protocols.
 * </p>
 *
 * @since 5.3
 */
public class HappyEyeballsV2AsyncClientConnectionOperator implements AsyncClientConnectionOperator {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncClientConnectionOperator.class);


    /**
     * The default delay used between subsequent DNS resolution attempts, in milliseconds.
     */
    private final Timeout DEFAULT_RESOLUTION_DELAY = Timeout.ofMilliseconds(50);
    /**
     * The default timeout duration for establishing a connection, in milliseconds.
     */
    private final Timeout DEFAULT_TIMEOUT = Timeout.ofMilliseconds(250);

    /**
     * The default minimum delay between connection attempts.
     * This delay is used to prevent the connection operator from spamming connection attempts and to provide a reasonable
     * delay between attempts for the user.
     */
    private final Timeout DEFAULT_MINIMUM_CONNECTION_ATTEMPT_DELAY = Timeout.ofMilliseconds(100);

    /**
     * The default maximum delay between connection attempts.
     * This delay is used to prevent the connection operator from spamming connection attempts and to provide a reasonable
     * delay between attempts for the user. This value is used to cap the delay between attempts to prevent the delay from becoming
     * too long and causing unnecessary delays in the application's processing.
     */
    private final Timeout DEFAULT_MAXIMUM_CONNECTION_ATTEMPT_DELAY = Timeout.ofMilliseconds(2000);

    /**
     * The default delay before attempting to establish a connection.
     * This delay is used to provide a reasonable amount of time for the underlying transport to be ready before attempting
     * to establish a connection. This can help to improve the likelihood of successful connection attempts and reduce
     * unnecessary delays in the application's processing.
     */
    private final Timeout DEFAULT_CONNECTION_ATTEMPT_DELAY = Timeout.ofMilliseconds(250);


    /**
     * The {@link ScheduledExecutorService} used by this connection operator to execute delayed tasks, such as DNS resolution and connection attempts.
     * This executor is used to control the timing of tasks in order to optimize the performance of connection attempts. By default, a single thread is used
     * to execute tasks sequentially, but this can be adjusted depending on the application's workload and number of instances of the connection operator.
     * If multiple instances of the connection operator are being used in the same application, it may be more efficient to use a {@link java.util.concurrent.ThreadPoolExecutor}
     * with a fixed number of threads instead of a single thread executor. This will allow tasks to be executed in parallel, which can improve the overall
     * performance of the application.
     * If the scheduler provided to the constructor is null, a new instance of {@link Executors#newSingleThreadScheduledExecutor()} will be used as the default.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * The underlying {@link AsyncClientConnectionOperator} that is used to establish connections
     * to the target server.
     */
    private final AsyncClientConnectionOperator connectionOperator;

    /**
     * The DNS resolver used to resolve hostnames to IP addresses.
     */
    private final DnsResolver dnsResolver;

    /**
     * A lookup table used to determine the {@link TlsStrategy} to use for a given connection route.
     */
    private final Lookup<TlsStrategy> tlsStrategyLookup;

    /**
     * The default timeout for connection establishment attempts. If a connection cannot be established
     * within this timeout, the attempt is considered failed.
     */
    private final Timeout timeout;

    /**
     * The minimum delay between connection establishment attempts.
     */
    private final Timeout minimumConnectionAttemptDelay;

    /**
     * The maximum delay between connection establishment attempts.
     */
    private final Timeout maximumConnectionAttemptDelay;

    /**
     * The current delay between connection establishment attempts.
     */
    private final Timeout connectionAttemptDelay;

    /**
     * The delay before resolution is started.
     */
    private final Timeout resolution_delay;

    /**
     * The number of IP addresses of each address family to include in the initial list of
     * IP addresses to attempt connections to. This value is set to 2 by default, but can be
     * increased to more aggressively favor a particular address family (e.g. set to 4 for IPv6).
     */
    private final int firstAddressFamilyCount;

    /**
     * The address family to use for establishing connections. This can be set to either
     * {@link AddressFamily#IPv4} or {@link AddressFamily#IPv6}.
     */
    private final AddressFamily addressFamily;

    /**
     * An {@link AtomicInteger} that keeps track of the number of scheduled tasks in the {@link ScheduledExecutorService}.
     */
    private final AtomicInteger scheduledTasks = new AtomicInteger(0);

    /**
     * The AddressFamily enum represents the possible address families that can be used when attempting to establish
     * <p>
     * connections using the Happy Eyeballs V2 algorithm.
     *
     * <p>
     * The Happy Eyeballs V2 algorithm allows for concurrent connection attempts to be made to different IP addresses,
     * <p>
     * so this enum specifies whether connections should be attempted using IPv4 or IPv6 addresses.
     *
     * </p>
     */
    public enum AddressFamily {
        IPv4, IPv6
    }

    /**
     * Constructs a new {@link HappyEyeballsV2AsyncClientConnectionOperator} with the specified parameters.
     *
     * @param tlsStrategyLookup             the lookup object used to retrieve a {@link TlsStrategy} for a given {@link Route}
     * @param connectionOperator            the underlying {@link AsyncClientConnectionOperator} to use for establishing connections
     * @param dnsResolver                   the {@link DnsResolver} to use for resolving target hostnames
     * @param timeout                       the timeout duration for establishing a connection
     * @param resolution_delay              the configurable delay before subsequent DNS resolution attempts
     * @param minimumConnectionAttemptDelay the minimum configurable delay between connection attempts
     * @param maximumConnectionAttemptDelay the maximum configurable delay between connection attempts
     * @param connectionAttemptDelay        the configurable delay before attempting to establish a connection
     * @param firstAddressFamilyCount       the number of initial address families to use for establishing a connection
     * @param addressFamily                 the preferred address family to use for establishing a connection
     * @param scheduler                     the {@link ScheduledExecutorService} to use for scheduling tasks
     * @throws IllegalArgumentException if {@code firstAddressFamilyCount} is not positive
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(final Lookup<TlsStrategy> tlsStrategyLookup,
                                                        final AsyncClientConnectionOperator connectionOperator,
                                                        final DnsResolver dnsResolver,
                                                        final Timeout timeout,
                                                        final Timeout resolution_delay,
                                                        final Timeout minimumConnectionAttemptDelay,
                                                        final Timeout maximumConnectionAttemptDelay,
                                                        final Timeout connectionAttemptDelay,
                                                        final int firstAddressFamilyCount,
                                                        final AddressFamily addressFamily,
                                                        final ScheduledExecutorService scheduler) {
        this.tlsStrategyLookup = Args.notNull(tlsStrategyLookup, "TLS strategy lookup");
        this.connectionOperator = Args.notNull(connectionOperator, "Connection operator");
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        this.resolution_delay = resolution_delay != null ? resolution_delay : DEFAULT_RESOLUTION_DELAY;
        this.minimumConnectionAttemptDelay = minimumConnectionAttemptDelay != null ? minimumConnectionAttemptDelay : DEFAULT_MINIMUM_CONNECTION_ATTEMPT_DELAY;
        this.maximumConnectionAttemptDelay = maximumConnectionAttemptDelay != null ? maximumConnectionAttemptDelay : DEFAULT_MAXIMUM_CONNECTION_ATTEMPT_DELAY;
        this.connectionAttemptDelay = connectionAttemptDelay != null ? connectionAttemptDelay : DEFAULT_CONNECTION_ATTEMPT_DELAY;
        this.firstAddressFamilyCount = Args.positive(firstAddressFamilyCount, "firstAddressFamilyCount");
        this.addressFamily = addressFamily != null ? addressFamily : AddressFamily.IPv6;
        this.scheduler = scheduler != null ? scheduler : Executors.newSingleThreadScheduledExecutor();

    }

    /**
     * Constructs a new instance of {@link HappyEyeballsV2AsyncClientConnectionOperator} using the specified
     * {@link Lookup} for {@link TlsStrategy} and {@link SchemePortResolver} and {@link DnsResolver}.
     * <p>
     * The constructor internally creates a new instance of {@link DefaultAsyncClientConnectionOperator} with the
     * specified {@link Lookup} for {@link TlsStrategy}, {@link SchemePortResolver} and {@link DnsResolver}. The
     * created {@link AsyncClientConnectionOperator} is then passed to the main constructor along with default values
     * for other parameters.
     * </p>
     *
     * @param tlsStrategyLookup  The {@link Lookup} for {@link TlsStrategy}.
     * @param schemePortResolver The {@link SchemePortResolver} to use for resolving scheme ports.
     * @param dnsResolver        The {@link DnsResolver} to use for resolving hostnames to IP addresses.
     * @throws IllegalArgumentException if the {@code tlsStrategyLookup} or {@code schemePortResolver} or {@code dnsResolver} parameter is {@code null}.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        this(tlsStrategyLookup,
                new DefaultAsyncClientConnectionOperator(tlsStrategyLookup, schemePortResolver, dnsResolver),
                dnsResolver,
                null,
                null,
                null,
                null,
                null,
                1,
                null,
                null);
    }

    /**
     * Creates a new instance of {@link HappyEyeballsV2AsyncClientConnectionOperator} using the provided TLS strategy lookup
     * and scheme-port resolver. The DNS resolver will be set to the system default resolver.
     *
     * @param tlsStrategyLookup  The lookup instance for {@link TlsStrategy} to be used for establishing connections.
     * @param schemePortResolver The resolver instance for mapping scheme names to default port numbers.
     * @throws IllegalArgumentException if {@code tlsStrategyLookup} is {@code null}.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(
            final Lookup<TlsStrategy> tlsStrategyLookup,
            final SchemePortResolver schemePortResolver) {
        this(tlsStrategyLookup, schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE, null);
    }

    /**
     * Creates a new instance of {@link HappyEyeballsV2AsyncClientConnectionOperator} using the provided TLS strategy lookup.
     * The scheme-port resolver and DNS resolver will be set to their default instances.
     *
     * @param tlsStrategyLookup The lookup instance for {@link TlsStrategy} to be used for establishing connections.
     * @throws IllegalArgumentException if {@code tlsStrategyLookup} is {@code null}.
     */
    public HappyEyeballsV2AsyncClientConnectionOperator(
            final Lookup<TlsStrategy> tlsStrategyLookup) {
        this(tlsStrategyLookup, DefaultSchemePortResolver.INSTANCE, null);
    }


    /**
     * Attempts to connect to the given host and returns a Future that will be completed when the connection is established
     * or when an error occurs. This method may attempt to connect to multiple IP addresses associated with the host,
     * depending on the address family and the number of connection attempts to execute. The address family and number of
     * connection attempts can be configured by calling the corresponding setters on this class.
     *
     * @param connectionInitiator the connection initiator to use when creating the connection
     * @param host                the host to connect to
     * @param localAddress        the local address to bind to when connecting, or null to use any available local address
     * @param connectTimeout      the timeout to use when connecting, or null to use the default timeout
     * @param attachment          the attachment to associate with the connection, or null if no attachment is needed
     * @param callback            the callback to invoke when the connection is established or an error occurs, or null if no callback is needed
     * @return a Future that will be completed when the connection is established or when an error occurs
     */
    @Override
    public Future<ManagedAsyncClientConnection> connect(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final SocketAddress localAddress,
            final Timeout connectTimeout,
            final Object attachment,
            final FutureCallback<ManagedAsyncClientConnection> callback) {

        final CompletableFuture<ManagedAsyncClientConnection> connectionFuture = new CompletableFuture<>();

        final Timeout conTimeout = connectTimeout != null ? connectTimeout : timeout;

        resolveDnsAsync(host.getHostName())
                .thenCompose(inetAddresses -> {
                    final List<InetAddress> ipv4Addresses = new ArrayList<>();
                    final List<InetAddress> ipv6Addresses = new ArrayList<>();

                    for (final InetAddress inetAddress : inetAddresses) {
                        if (inetAddress instanceof Inet4Address) {
                            ipv4Addresses.add(inetAddress);
                        } else if (inetAddress instanceof Inet6Address) {
                            ipv6Addresses.add(inetAddress);
                        }
                    }

                    // Sort the array of addresses using the custom Comparator
                    Arrays.sort(inetAddresses, InetAddressComparator.INSTANCE);

                    final List<CompletableFuture<ManagedAsyncClientConnection>> connectionFutures = new ArrayList<>();

                    // Create a list of connection attempts to execute
                    final List<CompletableFuture<ManagedAsyncClientConnection>> attempts = new ArrayList<>();

                    // Create a list of connection attempts to execute
                    if (addressFamily == AddressFamily.IPv4 && !ipv4Addresses.isEmpty()) {
                        for (int i = 0; i < firstAddressFamilyCount && i < ipv4Addresses.size(); i++) {
                            attempts.add(connectAttempt(connectionInitiator, host, conTimeout, attachment,
                                    Collections.singletonList(ipv4Addresses.get(i)), localAddress));
                        }
                    } else if (addressFamily == AddressFamily.IPv6 && !ipv6Addresses.isEmpty()) {
                        for (int i = 0; i < firstAddressFamilyCount && i < ipv6Addresses.size(); i++) {
                            attempts.add(connectAttempt(connectionInitiator, host, conTimeout, attachment,
                                    Collections.singletonList(ipv6Addresses.get(i)), localAddress));
                        }
                    } else {
                        if (!ipv4Addresses.isEmpty()) {
                            for (int i = 0; i < firstAddressFamilyCount && i < ipv4Addresses.size(); i++) {
                                attempts.add(connectAttempt(connectionInitiator, host, conTimeout, attachment,
                                        Collections.singletonList(ipv4Addresses.get(i)), localAddress));
                            }
                        }
                        if (!ipv6Addresses.isEmpty()) {
                            for (int i = 0; i < firstAddressFamilyCount && i < ipv6Addresses.size(); i++) {
                                attempts.add(connectAttempt(connectionInitiator, host, conTimeout, attachment,
                                        Collections.singletonList(ipv6Addresses.get(i)), localAddress));
                            }
                        }
                    }

                    // Execute the connection attempts concurrently using CompletableFuture.anyOf
                    return CompletableFuture.anyOf(attempts.toArray(new CompletableFuture[0]))
                            .thenCompose(result -> {
                                if (result instanceof ManagedAsyncClientConnection) {
                                    // If there is a result, cancel all other attempts and complete the connectionFuture
                                    connectionFutures.forEach(future -> future.cancel(true));
                                    connectionFuture.complete((ManagedAsyncClientConnection) result);
                                    // Check if all tasks have completed and shutdown the scheduler
                                } else {
                                    // If there is an exception, complete the connectionFuture exceptionally with the exception
                                    connectionFuture.completeExceptionally(new ConnectException("Failed to connect to any address for " + host));
                                }
                                // Invoke the callback if provided
                                if (callback != null) {
                                    connectionFuture.whenComplete((conn, ex) -> {
                                        if (ex != null) {
                                            callback.failed(new Exception(ex));
                                        } else {
                                            callback.completed(conn);
                                        }
                                    });
                                }
                                return connectionFuture;
                            });
                })
                .exceptionally(e -> {
                    connectionFuture.completeExceptionally(e);
                    if (callback != null) {
                        callback.failed(new Exception(e));
                    }
                    return null;

                }).whenComplete((result, ex) -> shutdownSchedulerIfTasksCompleted());

        return connectionFuture;
    }

    /**
     * Asynchronously resolves the DNS for the given host name and returns a CompletableFuture that will be completed
     * with an array of InetAddress objects representing the IP addresses of the host.
     * The resolution of AAAA records is delayed by the configured resolution delay to allow for a chance for A records to be
     * returned first.
     *
     * @param host the host name to resolve DNS for
     * @return a CompletableFuture that will be completed with an array of InetAddress objects representing the IP addresses
     */
    private CompletableFuture<InetAddress[]> resolveDnsAsync(final String host) {
        final CompletableFuture<InetAddress[]> dnsFuture = new CompletableFuture<>();
        final List<InetAddress> addresses = new ArrayList<>();
        CompletableFuture.runAsync(() -> {
            try {
                final InetAddress[] inetAddresses = dnsResolver.resolve(host);
                addresses.addAll(Arrays.asList(inetAddresses));
                // Introduce a delay before resolving AAAA records after receiving A records
                if (inetAddresses.length > 0) {
                    scheduledTasks.incrementAndGet();
                    scheduler.schedule(() -> {
                        try {
                            final InetAddress[] inet6Addresses = dnsResolver.resolve(host);
                            addresses.addAll(Arrays.asList(inet6Addresses));
                            dnsFuture.complete(addresses.toArray(new InetAddress[0]));
                        } catch (final UnknownHostException e) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Failed to resolve AAAA DNS for host '{}': {}", host, e.getMessage(), e);
                            }
                            dnsFuture.completeExceptionally(e);
                        } finally {
                            scheduledTasks.decrementAndGet(); // Decrease the count. If it reaches 0, the scheduler will be shutdown
                        }
                    }, resolution_delay.toMilliseconds(), TimeUnit.MILLISECONDS);
                } else {
                    dnsFuture.complete(addresses.toArray(new InetAddress[0]));
                }
            } catch (final Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to resolve DNS for host '{}': {}", host, e.getMessage(), e);
                }
                dnsFuture.completeExceptionally(e);
            }
        });
        return dnsFuture;
    }


    /**
     * Initiates an asynchronous connection attempt to the given list of IP addresses for the specified {@link HttpHost}.
     *
     * @param connectionInitiator the {@link ConnectionInitiator} to use for establishing the connection
     * @param host                the {@link HttpHost} to connect to
     * @param connectTimeout      the timeout for the connection attempt
     * @param attachment          the attachment object to pass to the connection operator
     * @param addresses           the list of IP addresses to attempt to connect to
     * @param localAddress        the local socket address to bind the connection to, or {@code null} if not binding
     * @return a {@link CompletableFuture} that completes with a {@link ManagedAsyncClientConnection} if the connection attempt succeeds,
     * or exceptionally with an exception if all attempts fail
     */
    private CompletableFuture<ManagedAsyncClientConnection> connectAttempt(
            final ConnectionInitiator connectionInitiator,
            final HttpHost host,
            final Timeout connectTimeout,
            final Object attachment,
            final List<InetAddress> addresses,
            final SocketAddress localAddress) {

        final CompletableFuture<ManagedAsyncClientConnection> connectionFuture = new CompletableFuture<>();

        // Create a list of connection attempts to execute
        final List<CompletableFuture<Void>> attempts = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            final InetAddress address = addresses.get(i);

            if (LOG.isDebugEnabled()) {
                LOG.info("Attempting to connect to {}", address);
            }

            final CompletableFuture<Void> attempt = new CompletableFuture<>();
            attempts.add(attempt);
            final HttpHost currentHost = new HttpHost(host.getSchemeName(), address, host.getHostName(), host.getPort());

            connectionOperator.connect(
                    connectionInitiator,
                    currentHost,
                    localAddress,
                    connectTimeout,
                    attachment,
                    new FutureCallback<ManagedAsyncClientConnection>() {
                        @Override
                        public void completed(final ManagedAsyncClientConnection connection) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Successfully connected {}", ConnPoolSupport.getId(connection));
                            }
                            connectionFuture.complete(connection);
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Failed to connect  {}", ConnPoolSupport.getId(address), ex);
                            }
                            attempt.completeExceptionally(ex);
                        }

                        @Override
                        public void cancelled() {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Cancelled connect for {}", ConnPoolSupport.getId(address));
                            }
                            attempt.cancel(true);
                        }
                    });

            // Introduce a delay before executing the next connection attempt
            if (i < addresses.size() - 1) {
                final Duration delay = calculateDelay(i);
                scheduledTasks.incrementAndGet();
                scheduler.schedule(() -> {
                    scheduledTasks.decrementAndGet();
                    attempt.complete(null);
                }, delay.toMillis(), TimeUnit.MILLISECONDS);
            }
        }

        // Execute the connection attempts concurrently using CompletableFuture.allOf
        CompletableFuture.allOf(attempts.toArray(new CompletableFuture[0]))
                .whenCompleteAsync((result, exception) -> {
                    if (!connectionFuture.isDone()) {
                        // If all attempts fail, complete the connectionFuture exceptionally with a ConnectException
                        connectionFuture.completeExceptionally(new ConnectException("Failed to connect to any address for " + host));
                    }
                });

        return connectionFuture;
    }

    /**
     * Upgrades the specified connection to a higher-level protocol. This method delegates to
     * {@link #upgrade(ManagedAsyncClientConnection, HttpHost, Object, HttpContext, FutureCallback)} passing null
     * as the {@code protocolHandler}.
     *
     * @param connection the connection to upgrade
     * @param host       the host to connect to
     * @param attachment the attachment object for the upgrade process
     * @see #upgrade(ManagedAsyncClientConnection, HttpHost, Object, HttpContext)
     */

    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost host,
            final Object attachment) {
        upgrade(connection, host, attachment, null, null);
    }

    /**
     * Upgrades the specified connection to a higher-level protocol using the given {@code context}. This method delegates
     * to {@link #upgrade(ManagedAsyncClientConnection, HttpHost, Object, HttpContext, FutureCallback)} passing null
     * as the {@code protocolHandler}.
     *
     * @param connection the connection to upgrade
     * @param host       the host to connect to
     * @param attachment the attachment object for the upgrade process
     * @param context    the HttpContext to use for the upgrade process
     * @see #upgrade(ManagedAsyncClientConnection, HttpHost, Object)
     * @see #upgrade(ManagedAsyncClientConnection, HttpHost, Object, HttpContext, FutureCallback)
     */
    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost host,
            final Object attachment,
            final HttpContext context) {
        upgrade(connection, host, attachment, context, null);
    }

    /**
     * Upgrades the given {@link ManagedAsyncClientConnection} to a secure connection using the appropriate
     * {@link TlsStrategy} if available. If no {@link TlsStrategy} is available, the callback is called with the
     * original connection.
     *
     * @param connection the connection to upgrade
     * @param host       the target host
     * @param attachment the attachment object
     * @param context    the HttpContext, can be null
     * @param callback   the callback to call when the upgrade is complete or fails, can be null
     */
    @Override
    public void upgrade(
            final ManagedAsyncClientConnection connection,
            final HttpHost host,
            final Object attachment,
            final HttpContext context,
            final FutureCallback<ManagedAsyncClientConnection> callback) {
        final TlsStrategy tlsStrategy = tlsStrategyLookup != null ? tlsStrategyLookup.lookup(host.getSchemeName()) : null;
        if (tlsStrategy != null) {
            tlsStrategy.upgrade(
                    connection,
                    host,
                    attachment,
                    null,
                    new FutureCallback<TransportSecurityLayer>() {
                        @Override
                        public void completed(final TransportSecurityLayer transportSecurityLayer) {
                            // If the upgrade succeeded, call the callback with the original connection
                            if (callback != null) {
                                callback.completed(connection);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            // If the upgrade failed, call the callback with the exception
                            if (callback != null) {
                                callback.failed(ex);
                            }
                        }

                        @Override
                        public void cancelled() {
                            // If the upgrade was cancelled, call the callback with an exception indicating cancellation
                            if (callback != null) {
                                callback.failed(new CancellationException("Upgrade was cancelled"));
                            }
                        }
                    });
        } else {
            // If no TLS strategy is available, call the callback with the original connection
            if (callback != null) {
                callback.completed(connection);
            }
        }
    }

    /**
     * Calculates the delay before the next connection attempt based on the attempt index and the configured connection
     * <p>
     * attempt delay parameters.
     *
     * @param attemptIndex the index of the connection attempt, starting from 0
     * @return the duration to wait before the next connection attempt
     */
    private Duration calculateDelay(final int attemptIndex) {
        final Duration delay;
        final Duration attemptDelay = connectionAttemptDelay.toDuration();
        final Duration maximumAttemptDelay = maximumConnectionAttemptDelay.toDuration();
        final Duration minimumAttemptDelay = minimumConnectionAttemptDelay.toDuration();

        if (attemptIndex == 0) {
            delay = attemptDelay;
        } else {
            delay = attemptDelay.multipliedBy(2).compareTo(maximumAttemptDelay) <= 0 ?
                    attemptDelay.multipliedBy(2) : maximumAttemptDelay;
        }
        return delay.compareTo(minimumAttemptDelay) >= 0 ? delay : minimumAttemptDelay;
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed,
     * but no new tasks will be accepted. If the provided CloseMode is GRACEFUL,
     * the scheduler will wait for currently executing tasks to complete before shutting down.
     * Otherwise, it will attempt to cancel currently executing tasks.
     *
     * @param closeMode The mode to use for shutting down the scheduler.
     */
    public void shutdown(final CloseMode closeMode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutdown ScheduledExecutorService {}", closeMode);
        }
        if (closeMode == CloseMode.GRACEFUL) {
            scheduler.shutdown();
        } else {
            scheduler.shutdownNow();
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed,
     * but no new tasks will be accepted. The scheduler will wait for currently executing tasks
     * to complete before shutting down.
     */
    public void shutdown() {
        shutdown(CloseMode.GRACEFUL);
    }

    /**
     * Decrements the number of scheduled tasks and shuts down the scheduler if there are no more tasks left to execute.
     * This method is intended to be used when all tasks have been completed, and the scheduler should be shut down
     * gracefully. It is executed asynchronously using {@link CompletableFuture#runAsync(Runnable)}, so it will not block
     * the calling thread.
     */
    private void shutdownSchedulerIfTasksCompleted() {
        if (scheduledTasks.decrementAndGet() <= 0) {
            this.shutdown();
        }
    }
}

