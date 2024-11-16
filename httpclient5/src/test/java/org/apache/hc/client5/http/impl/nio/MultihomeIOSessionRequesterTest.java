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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MultihomeIOSessionRequesterTest {

    private DnsResolver dnsResolver;
    private ConnectionInitiator connectionInitiator;
    private MultihomeIOSessionRequester sessionRequester;
    private NamedEndpoint namedEndpoint;

    @BeforeEach
    void setUp() {
        dnsResolver = Mockito.mock(DnsResolver.class);
        connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        namedEndpoint = Mockito.mock(NamedEndpoint.class);
        sessionRequester = new MultihomeIOSessionRequester(dnsResolver);
    }

    @Test
    void testConnectWithMultipleAddresses() throws Exception {
        final InetAddress address1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        final InetAddress address2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(address1, 8080),
                new InetSocketAddress(address2, 8080)
        );

        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> callback = invocation.getArgument(5);
                    // Simulate a failure for the first connection attempt
                    final CompletableFuture<IOSession> future = new CompletableFuture<>();
                    callback.failed(new IOException("Simulated connection failure"));
                    future.completeExceptionally(new IOException("Simulated connection failure"));
                    return future;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator,
                namedEndpoint,
                null,
                Timeout.ofMilliseconds(500),
                null,
                null
        );

        assertTrue(future.isDone());
        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (final ExecutionException ex) {
            assertInstanceOf(IOException.class, ex.getCause());
            assertEquals("Simulated connection failure", ex.getCause().getMessage());
        }
    }

    @Test
    void testConnectSuccessfulAfterRetries() throws Exception {
        final InetAddress address1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        final InetAddress address2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        final List<InetSocketAddress> remoteAddresses = Arrays.asList(
                new InetSocketAddress(address1, 8080),
                new InetSocketAddress(address2, 8080)
        );

        Mockito.when(namedEndpoint.getHostName()).thenReturn("somehost");
        Mockito.when(namedEndpoint.getPort()).thenReturn(8080);
        Mockito.when(dnsResolver.resolve("somehost", 8080)).thenReturn(remoteAddresses);

        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> callback = invocation.getArgument(5);
                    final InetSocketAddress remoteAddress = invocation.getArgument(1);
                    final CompletableFuture<IOSession> future = new CompletableFuture<>();
                    if (remoteAddress.getAddress().equals(address1)) {
                        // Fail the first address
                        callback.failed(new IOException("Simulated connection failure"));
                        future.completeExceptionally(new IOException("Simulated connection failure"));
                    } else {
                        // Succeed for the second address
                        final IOSession mockSession = Mockito.mock(IOSession.class);
                        callback.completed(mockSession);
                        future.complete(mockSession);
                    }
                    return future;
                });

        final Future<IOSession> future = sessionRequester.connect(
                connectionInitiator,
                namedEndpoint,
                null,
                Timeout.ofMilliseconds(500),
                null,
                null
        );

        assertTrue(future.isDone());
        try {
            final IOSession session = future.get();
            assertNotNull(session);
        } catch (final ExecutionException ex) {
            fail("Did not expect an ExecutionException", ex);
        }
    }
}
