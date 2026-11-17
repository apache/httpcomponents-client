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
package org.apache.hc.client5.http.impl.async;

import static org.mockito.ArgumentMatchers.any;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestMinimalHttpAsyncClient {

    @Test
    @SuppressWarnings("unchecked")
    void testCancellationReachesConnectAfterSynchronousLease() throws Exception {
        // The pool hands out a new endpoint synchronously, so the lease callback starts the connect
        // before lease returns. Cancelling the request must still cancel that connect.
        final AsyncClientConnectionManager manager = Mockito.mock(AsyncClientConnectionManager.class);
        final AsyncConnectionEndpoint endpoint = Mockito.mock(AsyncConnectionEndpoint.class);
        final Future<AsyncConnectionEndpoint> connectFuture = Mockito.mock(Future.class);
        Mockito.when(endpoint.isConnected()).thenReturn(false);
        Mockito.when(manager.lease(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            invocation.getArgument(4, FutureCallback.class).completed(endpoint);
            return CompletableFuture.completedFuture(endpoint);
        });
        Mockito.when(manager.connect(any(), any(), any(), any(), any(), any())).thenReturn(connectFuture);

        final MinimalHttpAsyncClient client = HttpAsyncClients.createMinimal(manager);
        client.start();
        try {
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get("http://somehost/").build(), null);
            Mockito.verify(manager).connect(any(), any(), any(), any(), any(), any());

            future.cancel(true);
            Mockito.verify(connectFuture).cancel(true);
        } finally {
            client.close(CloseMode.IMMEDIATE);
        }
    }

}
