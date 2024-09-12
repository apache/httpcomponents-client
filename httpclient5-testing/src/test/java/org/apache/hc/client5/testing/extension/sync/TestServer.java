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

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.classic.ClassicTestServer;

public class TestServer {

    private final ClassicTestServer server;
    private final Http1Config http1Config;
    private final HttpProcessor httpProcessor;
    private final Decorator<HttpServerRequestHandler> exchangeHandlerDecorator;

    TestServer(
            final ClassicTestServer server,
            final Http1Config http1Config,
            final HttpProcessor httpProcessor,
            final Decorator<HttpServerRequestHandler> exchangeHandlerDecorator) {
        this.server = server;
        this.http1Config = http1Config;
        this.httpProcessor = httpProcessor;
        this.exchangeHandlerDecorator = exchangeHandlerDecorator;
    }

    public void shutdown(final CloseMode closeMode) {
        server.shutdown(closeMode);
    }

    public InetSocketAddress start() throws IOException {
        server.configure(http1Config);
        server.configure(exchangeHandlerDecorator);
        server.configure(httpProcessor);
        server.start();
        return new InetSocketAddress(server.getInetAddress(), server.getPort());
    }

}
