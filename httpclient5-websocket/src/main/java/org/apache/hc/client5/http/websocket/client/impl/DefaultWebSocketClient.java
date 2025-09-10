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
package org.apache.hc.client5.http.websocket.client.impl;

import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2MultiplexingRequester;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.reactor.IOSession;

@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Internal
public class DefaultWebSocketClient extends InternalWebSocketClientBase {

    public DefaultWebSocketClient(
            final HttpAsyncRequester requester,
            final ManagedConnPool<HttpHost, IOSession> connPool,
            final WebSocketClientConfig defaultConfig,
            final ThreadFactory threadFactory,
            final H2MultiplexingRequester h2Requester) {
        super(requester, connPool, defaultConfig, threadFactory, h2Requester);
    }
}
