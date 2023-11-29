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
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestHttpAsyncMinimalTlsHandshake extends AbstractIntegrationTestBase {

    public TestHttpAsyncMinimalTlsHandshake() {
        super(URIScheme.HTTPS);
    }

    protected MinimalHttpAsyncClient startMinimalClient(
            final Consumer<PoolingAsyncClientConnectionManagerBuilder> connManagerCustomizer) throws Exception {
        return startMinimalClient(
                Http1Config.DEFAULT,
                H2Config.DEFAULT,
                connManagerCustomizer);
    }

    @Test
    public void testSuccessfulTlsHandshake() throws Exception {
        startServer(Http1Config.DEFAULT, null, null);
        final HttpHost target = targetHost();

        final int maxConnNo = 2;
        final MinimalHttpAsyncClient client = startMinimalClient(builder -> builder
                        .setMaxConnPerRoute(maxConnNo)
                        .setMaxConnTotal(maxConnNo));

        for (int i = 0; i < maxConnNo + 1; i++) {
            final Future<AsyncClientEndpoint> endpointLease = client.lease(target, null);
            final AsyncClientEndpoint endpoint = endpointLease.get(5, TimeUnit.SECONDS);
            endpoint.releaseAndDiscard();
        }
    }

    @Test
    public void testTlsHandshakeFailure() throws Exception {
        startServer(Http1Config.DEFAULT, null, null);
        final HttpHost target = targetHost();

        final int maxConnNo = 2;
        final MinimalHttpAsyncClient client = startMinimalClient(builder -> builder
                .setTlsStrategy(new DefaultClientTlsStrategy(SSLContexts.createDefault()))
                .setMaxConnPerRoute(maxConnNo)
                .setMaxConnTotal(maxConnNo));

        for (int i = 0; i < maxConnNo + 1; i++) {
            final Future<AsyncClientEndpoint> endpointLease = client.lease(target, null);
            final ExecutionException executionException = Assertions.assertThrows(ExecutionException.class,
                    () -> endpointLease.get(5, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(SSLException.class, executionException.getCause());
        }
    }

}