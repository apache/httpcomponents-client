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
import java.util.concurrent.Future;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

public abstract class AbstractIntegrationTestBase<T extends CloseableHttpAsyncClient> extends AbstractServerTestBase {

    public AbstractIntegrationTestBase(final URIScheme scheme) {
        super(scheme);
    }

    public AbstractIntegrationTestBase() {
        super(URIScheme.HTTP);
    }

    protected T httpclient;

    protected abstract T createClient() throws Exception;

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

    public abstract HttpHost start() throws Exception;

    public final HttpHost start(
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator,
            final Http1Config h1Config) throws Exception {
        server.start(httpProcessor, exchangeHandlerDecorator, h1Config);
        final Future<ListenerEndpoint> endpointFuture = server.listen(new InetSocketAddress(0));
        httpclient = createClient();
        httpclient.start();
        final ListenerEndpoint endpoint = endpointFuture.get();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost(scheme.name(), "localhost", address.getPort());
    }

    public final HttpHost start(
            final HttpProcessor httpProcessor,
            final Http1Config h1Config) throws Exception {
        return start(httpProcessor, null, h1Config);
    }

    public final HttpHost start(
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator,
            final H2Config h2Config) throws Exception {
        server.start(httpProcessor, exchangeHandlerDecorator, h2Config);
        final Future<ListenerEndpoint> endpointFuture = server.listen(new InetSocketAddress(0));
        httpclient = createClient();
        httpclient.start();
        final ListenerEndpoint endpoint = endpointFuture.get();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost(scheme.name(), "localhost", address.getPort());
    }


    public final HttpHost start(
            final HttpProcessor httpProcessor,
            final H2Config h2Config) throws Exception {
        return start(httpProcessor, null, h2Config);
    }

}
