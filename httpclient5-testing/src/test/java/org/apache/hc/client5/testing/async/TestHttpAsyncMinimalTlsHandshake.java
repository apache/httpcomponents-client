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
package org.apache.hc.client5.testing.async;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHttpAsyncMinimalTlsHandshake extends AbstractIntegrationTestBase {

    public TestHttpAsyncMinimalTlsHandshake() {
        super(URIScheme.HTTPS, ClientProtocolLevel.MINIMAL, ServerProtocolLevel.STANDARD);
    }

    @Test
    void testSuccessfulTlsHandshake() throws Exception {
        final HttpHost target = startServer();

        final int maxConnNo = 2;
        final PoolingAsyncClientConnectionManager connectionManager = startClient().getConnectionManager();
        connectionManager.setDefaultMaxPerRoute(maxConnNo);
        connectionManager.setMaxTotal(maxConnNo);

        final MinimalHttpAsyncClient client = startClient().getImplementation();

        for (int i = 0; i < maxConnNo + 1; i++) {
            final Future<AsyncClientEndpoint> endpointLease = client.lease(target, null);
            final AsyncClientEndpoint endpoint = endpointLease.get(5, TimeUnit.SECONDS);
            endpoint.releaseAndDiscard();
        }
    }

    @Test
    void testTlsHandshakeFailure() throws Exception {
        final HttpHost target = startServer();

        configureClient(builder ->
                builder.setTlsStrategy(new DefaultClientTlsStrategy(SSLContexts.createDefault())));

        final int maxConnNo = 2;
        final PoolingAsyncClientConnectionManager connectionManager = startClient().getConnectionManager();
        connectionManager.setDefaultMaxPerRoute(maxConnNo);
        connectionManager.setMaxTotal(maxConnNo);

        final MinimalHttpAsyncClient client = startClient().getImplementation();

        for (int i = 0; i < maxConnNo + 1; i++) {
            final Future<AsyncClientEndpoint> endpointLease = client.lease(target, null);
            final ExecutionException executionException = Assertions.assertThrows(ExecutionException.class,
                    () -> endpointLease.get(5, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(SSLException.class, executionException.getCause());
        }
    }

}