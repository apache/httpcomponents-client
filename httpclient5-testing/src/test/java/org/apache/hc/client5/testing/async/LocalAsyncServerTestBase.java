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

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.H2TlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.Http2TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

public abstract class LocalAsyncServerTestBase {

    protected final URIScheme scheme;

    public LocalAsyncServerTestBase(final URIScheme scheme) {
        this.scheme = scheme;
    }

    public LocalAsyncServerTestBase() {
        this(URIScheme.HTTP);
    }

    protected Http2TestServer server;
    protected PoolingAsyncClientConnectionManager connManager;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            server = new Http2TestServer(
                    IOReactorConfig.DEFAULT,
                    scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null);
            server.register("/echo/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    return new AsyncEchoHandler();
                }

            });
            server.register("/random/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    return new AsyncRandomHandler();
                }

            });
        }

        @Override
        protected void after() {
            if (server != null) {
                server.shutdown(TimeValue.ofSeconds(5));
                server = null;
            }
        }

    };

    @Rule
    public ExternalResource connManagerResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(new H2TlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .build();
        }

        @Override
        protected void after() {
            if (connManager != null) {
                connManager.close();
                connManager = null;
            }
        }

    };

}
