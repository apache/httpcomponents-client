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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link HappyEyeballsV2AsyncClientConnectionOperator}.
 *
 * @since 5.3
 */
final class HappyEyeballsV2AsyncClientConnectionOperatorTest {

    private DnsResolver dnsResolver;

    private ConnectionInitiator connectionInitiator;

    private IOSession ioSession;

    private Lookup<TlsStrategy> tlsStrategyLookup;          // <-- added

    private HappyEyeballsV2AsyncClientConnectionOperator operator;

    @BeforeEach
    void setUp() {
        dnsResolver = mock(DnsResolver.class);
        connectionInitiator = mock(ConnectionInitiator.class);
        ioSession = mock(IOSession.class);
        tlsStrategyLookup = mock(Lookup.class);             // <-- added
        operator = new HappyEyeballsV2AsyncClientConnectionOperator(
                dnsResolver, 10, tlsStrategyLookup);        // <-- changed ctor
    }

    @AfterEach
    void tearDown() {
        operator.shutdown(CloseMode.IMMEDIATE);
    }

    /**
     * Tests connection to a resolved host address.
     */
    @Test
    void testConnectResolvedHost() throws Exception {
        final InetAddress ipv4 = InetAddress.getByName("127.0.0.1");
        final HttpHost host = new HttpHost("http", ipv4, 80);
        final SocketAddress localAddress = new InetSocketAddress(0);
        final Timeout timeout = Timeout.ofSeconds(30);
        final Object attachment = new Object();

        final Future<IOSession> connectFuture = mock(Future.class);
        when(connectFuture.get()).thenReturn(ioSession);
        when(connectionInitiator.connect(eq(host), any(InetSocketAddress.class), eq(localAddress), eq(timeout), eq(attachment), any(FutureCallback.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(5, FutureCallback.class).completed(ioSession);
                    return connectFuture;
                });

        final Future<ManagedAsyncClientConnection> future = operator.connect(connectionInitiator, host, localAddress, timeout, attachment, null);
        final ManagedAsyncClientConnection conn = future.get();

        assertNotNull(conn);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());

        final ArgumentCaptor<InetSocketAddress> addrCaptor = ArgumentCaptor.forClass(InetSocketAddress.class);
        verify(connectionInitiator).connect(eq(host), addrCaptor.capture(), eq(localAddress), eq(timeout), eq(attachment), any(FutureCallback.class));

        final InetSocketAddress capturedAddr = addrCaptor.getValue();
        assertEquals(ipv4, capturedAddr.getAddress());
        assertEquals(80, capturedAddr.getPort());
    }

    /**
     * Tests connection to an unresolved host with DNS resolution and sorting.
     */
    @Test
    void testConnectUnresolvedHost() throws Exception {
        final HttpHost host = new HttpHost("http", "example.com", 80);
        final SocketAddress localAddress = new InetSocketAddress(0);
        final Timeout timeout = Timeout.ofSeconds(30);
        final Object attachment = new Object();

        final InetAddress ipv6 = InetAddress.getByName("::1");
        final InetAddress ipv4 = InetAddress.getByName("127.0.0.1");
        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[]{ipv4, ipv6});

        final Future<IOSession> connectFuture = mock(Future.class);
        when(connectFuture.get()).thenReturn(ioSession);
        when(connectionInitiator.connect(eq(host), any(InetSocketAddress.class), eq(localAddress), eq(timeout), eq(attachment), any(FutureCallback.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(5, FutureCallback.class).completed(ioSession);
                    return connectFuture;
                });

        final Future<ManagedAsyncClientConnection> future = operator.connect(connectionInitiator, host, localAddress, timeout, attachment, null);
        final ManagedAsyncClientConnection conn = future.get(1, TimeUnit.SECONDS);

        assertNotNull(conn);
        verify(dnsResolver).resolve("example.com");

        // Verify staggered calls (first succeeds quickly, so only one connect)
        verify(connectionInitiator, times(1)).connect(eq(host), any(InetSocketAddress.class), eq(localAddress), eq(timeout), eq(attachment), any(FutureCallback.class));
    }

    /**
     * Tests failure when no addresses are available.
     */
    @Test
    void testConnectNoAddresses() throws Exception {
        final HttpHost host = new HttpHost("http", "example.com", 80);
        final SocketAddress localAddress = new InetSocketAddress(0);
        final Timeout timeout = Timeout.ofSeconds(30);

        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[0]);

        final Future<ManagedAsyncClientConnection> future = operator.connect(connectionInitiator, host, localAddress, timeout, null, null);

        final ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(UnknownHostException.class, ex.getCause());
        assertEquals("No addresses", ex.getCause().getMessage());
    }

    /**
     * Tests DNS failure.
     */
    @Test
    void testConnectDnsFailure() throws Exception {
        final HttpHost host = new HttpHost("http", "invalidhost", 80);
        final SocketAddress localAddress = new InetSocketAddress(0);
        final Timeout timeout = Timeout.ofSeconds(30);

        doThrow(new UnknownHostException("Invalid host")).when(dnsResolver).resolve("invalidhost");

        final Future<ManagedAsyncClientConnection> future = operator.connect(connectionInitiator, host, localAddress, timeout, null, null);

        final ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(UnknownHostException.class, ex.getCause());
        assertEquals("Invalid host", ex.getCause().getMessage());
    }

    /**
     * Tests shutdown behavior.
     */
    @Test
    void testShutdown() {
        operator.shutdown(CloseMode.GRACEFUL);
        // No exceptions expected.
    }

    /**
     * Tests address sorting logic.
     */
    @Test
    void testSortByRFC6724() throws Exception {
        final List<InetSocketAddress> addrs = new ArrayList<>();
        addrs.add(new InetSocketAddress(InetAddress.getByName("::1"), 80));       // Loopback IPv6
        addrs.add(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 80)); // Loopback IPv4

        HappyEyeballsV2AsyncClientConnectionOperator.sortByRFC6724(addrs);

        // Expect IPv6 loopback first (higher precedence).
        // NOTE: if IPv6 is disabled on the test host, this assertion can fail.
        assertEquals(InetAddress.getByName("::1"), addrs.get(0).getAddress());
    }

    /**
     * Tests legacy upgrade method (no-op).
     */
    @Test
    void testUpgrade() {
        final ManagedAsyncClientConnection conn = mock(ManagedAsyncClientConnection.class);
        final HttpHost host = new HttpHost("https", "localhost", 443);
        final Object attachment = new Object();

        operator.upgrade(conn, host, attachment);

        verify(conn, never()).setSocketTimeout(any(Timeout.class)); // No-op
    }
}
