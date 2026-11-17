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

import static org.mockito.ArgumentMatchers.any;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultAsyncClientConnectionOperatorTest {

    @Test
    void testCancelDuringTlsHandshakeClosesSession() throws Exception {
        final TlsStrategy tlsStrategy = Mockito.mock(TlsStrategy.class);
        final DefaultAsyncClientConnectionOperator operator = new DefaultAsyncClientConnectionOperator(
                RegistryBuilder.<TlsStrategy>create().register("https", tlsStrategy).build(), null, null);

        final IOSession ioSession = Mockito.mock(IOSession.class);
        final ConnectionInitiator connectionInitiator = Mockito.mock(ConnectionInitiator.class);
        Mockito.when(connectionInitiator.connect(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final FutureCallback<IOSession> callback = invocation.getArgument(5);
                    callback.completed(ioSession);
                    return CompletableFuture.completedFuture(ioSession);
                });

        // The handshake never completes: the mocked strategy does not call back.
        final HttpHost target = new HttpHost("https", InetAddress.getLoopbackAddress(), "somehost", 443);
        final Future<ManagedAsyncClientConnection> future = operator.connect(
                connectionInitiator, target, null, null, Timeout.ofSeconds(30), null, null, null);
        Mockito.verify(tlsStrategy).upgrade(any(), any(), any(), any(), any());

        future.cancel(true);
        Mockito.verify(ioSession).close(CloseMode.IMMEDIATE);
    }

}
