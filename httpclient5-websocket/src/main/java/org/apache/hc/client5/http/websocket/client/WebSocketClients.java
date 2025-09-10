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
package org.apache.hc.client5.http.websocket.client;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;

/**
 * Static factory methods for {@link CloseableWebSocketClient} instances.
 *
 * <p>This is a convenience entry point for typical client creation
 * scenarios. For advanced configuration use
 * {@link WebSocketClientBuilder} directly.</p>
 *
 * <p>Clients created by these helpers own their I/O resources and must be
 * closed when no longer needed.</p>
 *
 * @since 5.7
 */
public final class WebSocketClients {

    private WebSocketClients() {
    }

    /**
     * Creates a new {@link WebSocketClientBuilder} instance for
     * custom client configuration.
     *
     * @return a new {@link WebSocketClientBuilder}.
     */
    public static WebSocketClientBuilder custom() {
        return WebSocketClientBuilder.create();
    }

    /**
     * Creates a {@link CloseableWebSocketClient} instance with
     * default configuration.
     *
     * @return a newly created {@link CloseableWebSocketClient}
     * using default settings.
     */
    public static CloseableWebSocketClient createDefault() {
        return custom().build();
    }

    /**
     * Creates a {@link CloseableWebSocketClient} instance using
     * the given default WebSocket configuration.
     *
     * @param defaultConfig default configuration applied to
     *                      WebSocket connections created by
     *                      the client; must not be {@code null}.
     * @return a newly created {@link CloseableWebSocketClient}.
     */
    public static CloseableWebSocketClient createWith(final WebSocketClientConfig defaultConfig) {
        return custom().defaultConfig(defaultConfig).build();
    }
}