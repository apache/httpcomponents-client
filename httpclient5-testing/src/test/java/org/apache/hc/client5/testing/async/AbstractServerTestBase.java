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

import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.testing.reactive.ReactiveEchoProcessor;
import org.apache.hc.core5.testing.reactive.ReactiveRandomProcessor;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

public abstract class AbstractServerTestBase {

    public static final Timeout TIMEOUT = Timeout.ofSeconds(30);
    public static final Timeout LONG_TIMEOUT = Timeout.ofSeconds(60);

    protected final URIScheme scheme;

    public AbstractServerTestBase(final URIScheme scheme) {
        this.scheme = scheme;
    }

    public AbstractServerTestBase() {
        this(URIScheme.HTTP);
    }

    protected H2TestServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            server = new H2TestServer(
                    IOReactorConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build(),
                    scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null);
            server.register("/echo/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    if (isReactive()) {
                        return new ReactiveServerExchangeHandler(new ReactiveEchoProcessor());
                    } else {
                        return new AsyncEchoHandler();
                    }
                }

            });
            server.register("/random/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    if (isReactive()) {
                        return new ReactiveServerExchangeHandler(new ReactiveRandomProcessor());
                    } else {
                        return new AsyncRandomHandler();
                    }
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

    protected boolean isReactive() {
        return false;
    }
}
