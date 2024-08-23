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

package org.apache.hc.client5.testing.extension.sync;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

public class TestServerBootstrap {

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

    private final List<HandlerEntry<HttpRequestHandler>> handlerList;
    private Timeout timeout;
    private HttpProcessor httpProcessor;
    private Decorator<HttpServerRequestHandler> exchangeHandlerDecorator;

    public TestServerBootstrap(final URIScheme scheme) {
        this.scheme = scheme != null ? scheme : URIScheme.HTTP;
        this.handlerList = new ArrayList<>();
    }

    public TestServerBootstrap register(final String uriPattern, final HttpRequestHandler requestHandler) {
        return register(null, uriPattern, requestHandler);
    }

    public TestServerBootstrap register(final String hostname, final String uriPattern, final HttpRequestHandler requestHandler) {
        Args.notNull(uriPattern, "URI pattern");
        Args.notNull(requestHandler, "Exchange handler");
        handlerList.add(new HandlerEntry<>(hostname, uriPattern, requestHandler));
        return this;
    }

    public TestServerBootstrap setTimeout(final Timeout timeout) {
        this.timeout = timeout;
        return this;
    }

    public TestServerBootstrap setHttpProcessor(final HttpProcessor httpProcessor) {
        this.httpProcessor = httpProcessor;
        return this;
    }

    public TestServerBootstrap setExchangeHandlerDecorator(final Decorator<HttpServerRequestHandler> exchangeHandlerDecorator) {
        this.exchangeHandlerDecorator = exchangeHandlerDecorator;
        return this;
    }

    public TestServer build() throws Exception {
        final ClassicTestServer server = new ClassicTestServer(
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null,
                SocketConfig.custom()
                        .setSoTimeout(timeout)
                        .build());
        for (final HandlerEntry<HttpRequestHandler> entry: handlerList) {
            if (entry.hostname != null) {
                server.register(entry.hostname, entry.uriPattern, entry.handler);
            } else {
                server.register(entry.uriPattern, entry.handler);
            }
        }
        return new TestServer(
                server,
                Http1Config.DEFAULT,
                httpProcessor,
                exchangeHandlerDecorator);
    }

}
