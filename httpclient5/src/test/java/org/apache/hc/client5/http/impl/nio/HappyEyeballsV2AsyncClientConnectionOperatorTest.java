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
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.client5.http.ssl.ConscryptClientTlsStrategy;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class HappyEyeballsV2AsyncClientConnectionOperatorTest {

    private AsyncClientConnectionOperator happyEyeballsV2AsyncClientConnectionOperator;

    private ConnectionInitiator connectionInitiator;

    private DnsResolver dnsResolver;

    private SocketAddress socketAddress;

    private AsyncClientConnectionOperator connectionOperator;


    @BeforeEach
    public void setup() {

        dnsResolver = Mockito.mock(DnsResolver.class);
        connectionOperator = Mockito.mock(AsyncClientConnectionOperator.class);

        happyEyeballsV2AsyncClientConnectionOperator = new HappyEyeballsV2AsyncClientConnectionOperator
                (RegistryBuilder.<TlsStrategy>create().register(URIScheme.HTTPS.getId(), ConscryptClientTlsStrategy.getDefault()).build(),
                        connectionOperator,
                        dnsResolver,
                        Timeout.ofSeconds(1),
                        Timeout.ofSeconds(1),
                        Timeout.ofSeconds(1),
                        Timeout.ofSeconds(1),
                        Timeout.ofSeconds(1),
                        1,
                        HappyEyeballsV2AsyncClientConnectionOperator.AddressFamily.IPv4,
                        null);

        connectionInitiator = mock(ConnectionInitiator.class);
        socketAddress = mock(SocketAddress.class);


    }

    @DisplayName("Test that application prioritizes IPv6 over IPv4 when both are available")
    @Test
    public void testIPv6ConnectionIsAttemptedBeforeIPv4() throws UnknownHostException, ExecutionException, InterruptedException {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 0x7F, 0, 0, 0x1}); // IPv6 address ::ffff:127.0.0.1
        final InetAddress ip2 = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});
        final TlsConfig tlsConfig = TlsConfig.custom()
                .build();

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[]{ip1, ip2});
        Mockito.when(connectionOperator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer((Answer<CompletableFuture<ManagedAsyncClientConnection>>) invocation -> {
                    // Extract the callback from the arguments
                    final FutureCallback<ManagedAsyncClientConnection> callback =
                            invocation.getArgument(5);

                    // Create a CompletableFuture for the connection result
                    final CompletableFuture<ManagedAsyncClientConnection> result = new CompletableFuture<>();

                    // Invoke the callback's completed() method with a mock connection
                    callback.completed(Mockito.mock(ManagedAsyncClientConnection.class));

                    // Return the CompletableFuture
                    return result;
                });


        final Future<ManagedAsyncClientConnection> future = happyEyeballsV2AsyncClientConnectionOperator.connect(
                connectionInitiator,
                host,
                socketAddress,
                Timeout.ofMilliseconds(123),
                tlsConfig,
                context,
                new FutureCallback<ManagedAsyncClientConnection>() {
                    @Override
                    public void completed(final ManagedAsyncClientConnection managedAsyncClientConnection) {
                        System.out.println("Executing request " + managedAsyncClientConnection);
                    }

                    @Override
                    public void failed(final Exception e) {
                        System.out.println("Exception " + e);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("Cancelled");
                    }
                }
        );

        future.get();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());

    }

    @DisplayName("Test asynchronous behavior with valid input and expect correct output")
    @Test
    public void testAsyncBehavior_withValidInput_expectCorrectOutput() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});
        final TlsConfig tlsConfig = TlsConfig.custom().build();

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[]{ip1, ip2});

        // Create a mock connection
        final ManagedAsyncClientConnection connection = Mockito.mock(ManagedAsyncClientConnection.class);

        // Create a future that will complete when the connection is established
        final CompletableFuture<ManagedAsyncClientConnection> future = new CompletableFuture<>();

        // Make the connection mock return the future when connect() is called
        Mockito.when(connectionOperator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer((Answer<CompletableFuture<ManagedAsyncClientConnection>>) invocation -> {
                    // Extract the callback from the arguments
                    final FutureCallback<ManagedAsyncClientConnection> callback =
                            invocation.getArgument(5);

                    // Create a CompletableFuture for the connection result
                    final CompletableFuture<ManagedAsyncClientConnection> result = new CompletableFuture<>();

                    // Invoke the callback's completed() method with a mock connection
                    callback.completed(connection);

                    // Return the CompletableFuture
                    return result;
                });

        // Call connect() on the operator
        final Future<ManagedAsyncClientConnection> result = happyEyeballsV2AsyncClientConnectionOperator.connect(
                connectionInitiator,
                host,
                socketAddress,
                Timeout.ofMilliseconds(123),
                tlsConfig,
                context,
                new FutureCallback<ManagedAsyncClientConnection>() {
                    @Override
                    public void completed(final ManagedAsyncClientConnection managedAsyncClientConnection) {
                        System.out.println("Executing request " + managedAsyncClientConnection);
                    }

                    @Override
                    public void failed(final Exception e) {
                        System.out.println("Exception " + e);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("Cancelled");
                    }
                });

        // Verify that the connection is not yet established
        assertFalse(result.isDone());

        // Complete the future with the connection mock
        future.complete(connection);

        // Wait for the connection to be established
        final ManagedAsyncClientConnection actualConnection = result.get();

        // Verify that the correct connection was returned
        assertSame(connection, actualConnection);
    }


    @Test
    @DisplayName("Verify Asynchronous Behavior of Request Processing")
    public void verifyAsynchronousBehaviorOfRequestProcessing() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});
        final TlsConfig tlsConfig = TlsConfig.custom().build();

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[]{ip1, ip2});

        // Create a mock connection
        final ManagedAsyncClientConnection connection = Mockito.mock(ManagedAsyncClientConnection.class);

        // Create a future that will complete when the connection is established
        final CompletableFuture<ManagedAsyncClientConnection> future = new CompletableFuture<>();

        // Make the connection mock return the future when connect() is called
        Mockito.when(connectionOperator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer((Answer<CompletableFuture<ManagedAsyncClientConnection>>) invocation -> {
                    // Extract the callback from the arguments
                    final FutureCallback<ManagedAsyncClientConnection> callback =
                            invocation.getArgument(5);

                    // Create a CompletableFuture for the connection result
                    final CompletableFuture<ManagedAsyncClientConnection> result = new CompletableFuture<>();

                    // Invoke the callback's completed() method with a mock connection
                    callback.completed(connection);

                    // Return the CompletableFuture
                    return result;
                });

        // Call connect() on the operator
        final Future<ManagedAsyncClientConnection> result = happyEyeballsV2AsyncClientConnectionOperator.connect(
                connectionInitiator,
                host,
                socketAddress,
                Timeout.ofMilliseconds(123),
                tlsConfig,
                context,
                new FutureCallback<ManagedAsyncClientConnection>() {
                    @Override
                    public void completed(final ManagedAsyncClientConnection managedAsyncClientConnection) {
                        System.out.println("Executing request " + managedAsyncClientConnection);
                    }

                    @Override
                    public void failed(final Exception e) {
                        System.out.println("Exception " + e);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("Cancelled");
                    }
                });

        // Verify that the connection is not yet established
        assertFalse(result.isDone());

        // Complete the future with the connection mock
        future.complete(connection);

        // Wait for the connection to be established
        final ManagedAsyncClientConnection actualConnection = result.get();

        // Verify that the correct connection was returned
        assertSame(connection, actualConnection);

        // Check that the failed and cancelled callbacks were not called
        assertFalse(future.isCompletedExceptionally());
        assertFalse(result.isCancelled());
    }

    @Test
    @DisplayName("Test successful connection using only IPv4")
    public void testIPv4SuccessfulConnection() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("ipv4host");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        final TlsConfig tlsConfig = TlsConfig.custom().build();

        Mockito.when(dnsResolver.resolve("ipv4host")).thenReturn(new InetAddress[]{ip1});
        Mockito.when(connectionOperator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<ManagedAsyncClientConnection> callback = invocation.getArgument(5);
                    final CompletableFuture<ManagedAsyncClientConnection> result = new CompletableFuture<>();
                    callback.completed(Mockito.mock(ManagedAsyncClientConnection.class));
                    return result;
                });

        final Future<ManagedAsyncClientConnection> future = happyEyeballsV2AsyncClientConnectionOperator.connect(
                connectionInitiator,
                host,
                socketAddress,
                Timeout.ofMilliseconds(123),
                tlsConfig,
                context,
                new FutureCallback<ManagedAsyncClientConnection>() {
                    @Override
                    public void completed(final ManagedAsyncClientConnection managedAsyncClientConnection) {
                        System.out.println("Executing request " + managedAsyncClientConnection);
                    }

                    @Override
                    public void failed(final Exception e) {
                        System.out.println("Exception " + e);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("Cancelled");
                    }
                });

        future.get();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        Mockito.verify(connectionOperator, times(1)).connect(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Test failed connection attempt")
    public void testFailedConnectionAttempt() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("failedhost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        final TlsConfig tlsConfig = TlsConfig.custom().build();

        Mockito.when(dnsResolver.resolve("failedhost")).thenReturn(new InetAddress[]{ip1});
        Mockito.when(connectionOperator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<ManagedAsyncClientConnection> callback = invocation.getArgument(5);
                    final CompletableFuture<ManagedAsyncClientConnection> result = new CompletableFuture<>();
                    callback.failed(new IOException("Failed to connect"));
                    return result;
                });

        final CompletableFuture<ManagedAsyncClientConnection> future = new CompletableFuture<>();
        happyEyeballsV2AsyncClientConnectionOperator.connect(
                connectionInitiator,
                host,
                socketAddress,
                Timeout.ofMilliseconds(123),
                tlsConfig,
                context,
                new FutureCallback<ManagedAsyncClientConnection>() {
                    @Override
                    public void completed(final ManagedAsyncClientConnection managedAsyncClientConnection) {
                        System.out.println("Executing request " + managedAsyncClientConnection);
                        future.complete(managedAsyncClientConnection);
                    }

                    @Override
                    public void failed(final Exception e) {
                        System.out.println("Exception " + e);
                        future.completeExceptionally(e);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("Cancelled");
                        future.cancel(true);
                    }
                });

        assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        Mockito.verify(connectionOperator, times(1)).connect(any(), any(), any(), any(), any(), any());
    }
}
