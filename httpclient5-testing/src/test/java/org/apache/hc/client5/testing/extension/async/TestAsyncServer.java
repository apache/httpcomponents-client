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

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.util.TimeValue;

public class TestAsyncServer {

    private final H2TestServer server;
    private final H2Config h2Config;
    private final Http1Config http1Config;
    private final HttpProcessor httpProcessor;
    private final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator;

    TestAsyncServer(
            final H2TestServer server,
            final H2Config h2Config,
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) {
        this.server = server;
        this.h2Config = h2Config;
        this.http1Config = http1Config;
        this.httpProcessor = httpProcessor;
        this.exchangeHandlerDecorator = exchangeHandlerDecorator;
    }

    public Future<ListenerEndpoint> listen(final InetSocketAddress address) {
        return server.listen(address);
    }

    public Set<ListenerEndpoint> getEndpoints() {
        return server.getEndpoints();
    }

    public IOReactorStatus getStatus() {
        return server.getStatus();
    }

    public void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        server.awaitShutdown(waitTime);
    }

    public void initiateShutdown() {
        server.initiateShutdown();
    }

    public void shutdown(final TimeValue graceTime) {
        server.shutdown(graceTime);
    }

    public void close() throws Exception {
        server.close();
    }

    public InetSocketAddress start() throws Exception {
        if (http1Config != null) {
            server.configure(http1Config);
        } else {
            server.configure(h2Config);
        }
        server.configure(exchangeHandlerDecorator);
        server.configure(httpProcessor);
        return server.start();
    }

}
