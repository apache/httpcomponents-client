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

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.ExternalResource;

@EnableRuleMigrationSupport
public class TestHttpAsyncMinimalTls extends AbstractServerTestBase {

    public TestHttpAsyncMinimalTls() {
        super(URIScheme.HTTPS);
    }

    MinimalHttpAsyncClient httpclient;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void after() {
            if (httpclient != null) {
                httpclient.close(CloseMode.GRACEFUL);
                httpclient = null;
            }
        }

    };

    @Test
    public void testSuccessfulTlsHandshake() throws Exception {
        final int maxConnNo = 2;

        httpclient = HttpAsyncClients.createMinimal(
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build(),
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                        .setMaxConnPerRoute(maxConnNo)
                        .setMaxConnTotal(maxConnNo)
                        .build());

        server.start(Http1Config.DEFAULT);
        final Future<ListenerEndpoint> endpointFuture = server.listen(new InetSocketAddress(0));

        final ListenerEndpoint listenerEndpoint = endpointFuture.get();
        final InetSocketAddress address = (InetSocketAddress) listenerEndpoint.getAddress();
        final HttpHost target = new HttpHost(scheme.name(), "localhost", address.getPort());
        httpclient.start();

        for (int i = 0; i < maxConnNo + 1; i++) {
            final Future<AsyncClientEndpoint> endpointLease = httpclient.lease(target, null);
            final AsyncClientEndpoint endpoint = endpointLease.get(5, TimeUnit.SECONDS);
            endpoint.releaseAndDiscard();
        }
    }

    @Test
    public void testTlsHandshakeFailure() throws Exception {
        final int maxConnNo = 2;

        httpclient = HttpAsyncClients.createMinimal(
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build(),
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(new DefaultClientTlsStrategy(SSLContexts.createDefault()))
                        .setMaxConnPerRoute(2)
                        .setMaxConnTotal(2)
                        .build());

        server.start(Http1Config.DEFAULT);
        final Future<ListenerEndpoint> endpointFuture = server.listen(new InetSocketAddress(0));

        final ListenerEndpoint listenerEndpoint = endpointFuture.get();
        final InetSocketAddress address = (InetSocketAddress) listenerEndpoint.getAddress();
        final HttpHost target = new HttpHost(scheme.name(), "localhost", address.getPort());
        httpclient.start();

        for (int i = 0; i < maxConnNo + 1; i++) {
            final Future<AsyncClientEndpoint> endpointLease = httpclient.lease(target, null);
            final ExecutionException executionException = Assert.assertThrows(ExecutionException.class,
                    () -> endpointLease.get(5, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(SSLException.class, executionException.getCause());
        }
    }

}