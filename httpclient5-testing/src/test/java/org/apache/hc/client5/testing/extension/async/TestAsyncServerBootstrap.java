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

package org.apache.hc.client5.testing.extension.async;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

public class TestAsyncServerBootstrap {

    static final class HandlerEntry<T> {

        final String hostname;
        final String uriPattern;
        final T handler;

        public HandlerEntry(final String hostname, final String uriPattern, final T handler) {
            this.hostname = hostname;
            this.uriPattern = uriPattern;
            this.handler = handler;
        }

        public HandlerEntry(final String uriPattern, final T handler) {
            this(null, uriPattern, handler);
        }

    }

    private final URIScheme scheme;
    private final ServerProtocolLevel serverProtocolLevel;

    private final List<HandlerEntry<Supplier<AsyncServerExchangeHandler>>> handlerList;
    private Timeout timeout;
    private HttpProcessor httpProcessor;
    private Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator;

    public TestAsyncServerBootstrap(final URIScheme scheme, final ServerProtocolLevel serverProtocolLevel) {
        this.scheme = scheme != null ? scheme : URIScheme.HTTP;
        this.serverProtocolLevel = serverProtocolLevel != null ? serverProtocolLevel : ServerProtocolLevel.STANDARD;
        this.handlerList = new ArrayList<>();
    }

    public ServerProtocolLevel getProtocolLevel() {
        return serverProtocolLevel;
    }

    public TestAsyncServerBootstrap register(final String uriPattern, final Supplier<AsyncServerExchangeHandler> supplier) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(supplier, "Exchange handler supplier");
        handlerList.add(new HandlerEntry<>(uriPattern, supplier));
        return this;
    }

    public TestAsyncServerBootstrap register(
            final String uriPattern,
            final AsyncServerRequestHandler<AsyncServerExchangeHandler> requestHandler) {
        return register(uriPattern, () -> new BasicServerExchangeHandler<>(requestHandler));
    }

    public TestAsyncServerBootstrap setTimeout(final Timeout timeout) {
        this.timeout = timeout;
        return this;
    }

    public TestAsyncServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    public TestAsyncServerBootstrap setExchangeHandlerDecorator(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) {
        this.exchangeHandlerDecorator = exchangeHandlerDecorator;
        return this;
    }

    public TestAsyncServer build() throws Exception {
        final H2TestServer server = new H2TestServer(
                IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build(),
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null,
                null,
                null);
        for (final HandlerEntry<Supplier<AsyncServerExchangeHandler>> entry: handlerList) {
            server.register(entry.uriPattern, entry.handler);
        }
        return new TestAsyncServer(
                server,
                serverProtocolLevel == ServerProtocolLevel.H2_ONLY ? H2Config.DEFAULT : null,
                serverProtocolLevel == ServerProtocolLevel.STANDARD ? Http1Config.DEFAULT : null,
                httpProcessor,
                exchangeHandlerDecorator);
    }

}
