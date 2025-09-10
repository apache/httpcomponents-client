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
package org.apache.hc.core5.websocket.server;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.util.Args;

class WebSocketHttpService extends HttpService {

    WebSocketHttpService(
            final HttpProcessor processor,
            final HttpServerRequestHandler requestHandler,
            final Http1Config http1Config,
            final ConnectionReuseStrategy connReuseStrategy,
            final Http1StreamListener streamListener) {
        super(
                Args.notNull(processor, "HTTP processor"),
                Args.notNull(requestHandler, "Request handler"),
                http1Config != null ? http1Config : Http1Config.DEFAULT,
                connReuseStrategy != null ? connReuseStrategy : DefaultConnectionReuseStrategy.INSTANCE,
                streamListener);
    }

    @Override
    public void handleRequest(
            final HttpServerConnection conn,
            final HttpContext localContext) throws IOException, HttpException {
        if (localContext != null) {
            localContext.setAttribute(WebSocketContextKeys.CONNECTION, conn);
        }
        super.handleRequest(conn, localContext);
    }
}
