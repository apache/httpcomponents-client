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

/**
 * Client-side WebSocket support built on top of Apache HttpClient.
 *
 * <p>This package provides the public API for establishing and using
 * WebSocket connections according to RFC&nbsp;6455. WebSocket sessions
 * are created by upgrading an HTTP request and are backed internally
 * by the non-blocking I/O reactor used by the HttpClient async APIs.</p>
 *
 * <h2>Core abstractions</h2>
 * <ul>
 *   <li>{@link org.apache.hc.client5.http.websocket.api.WebSocket WebSocket} –
 *     application view of a single WebSocket connection, used to send
 *     text and binary messages and initiate the close handshake.</li>
 *   <li>{@link org.apache.hc.client5.http.websocket.api.WebSocketListener WebSocketListener} –
 *     callback interface that receives inbound messages, pings, pongs,
 *     errors, and close notifications.</li>
 *   <li>{@link org.apache.hc.client5.http.websocket.api.WebSocketClientConfig WebSocketClientConfig} –
 *     immutable configuration for timeouts, maximum frame and message
 *     sizes, auto-pong behaviour, and buffer management.</li>
 *   <li>{@link org.apache.hc.client5.http.websocket.client.CloseableWebSocketClient CloseableWebSocketClient} –
 *     high-level client for establishing WebSocket connections.</li>
 *   <li>{@link org.apache.hc.client5.http.websocket.client.WebSocketClients WebSocketClients} and
 *     {@link org.apache.hc.client5.http.websocket.client.WebSocketClientBuilder WebSocketClientBuilder} –
 *     factory and builder for creating and configuring WebSocket clients.</li>
 * </ul>
 *
 * <h2>Threading model</h2>
 * <p>Outbound operations on {@code WebSocket} are thread-safe and may be
 * invoked from arbitrary application threads. Inbound callbacks on
 * {@code WebSocketListener} are normally executed on I/O dispatcher
 * threads; listeners should avoid long blocking operations.</p>
 *
 * <h2>Close handshake</h2>
 * <p>The implementation follows the close handshake defined in RFC&nbsp;6455.
 * Applications should initiate shutdown via
 * {@link org.apache.hc.client5.http.websocket.api.WebSocket#close(int, String)}
 * and treat receipt of a close frame as a terminal event. The configured
 * {@code closeWaitTimeout} controls how long the client will wait for the
 * peer's close frame before the underlying connection is closed.</p>
 *
 * <p>Classes in {@code org.apache.hc.core5.websocket} subpackages and
 * {@code org.apache.hc.client5.http.websocket.transport} are internal
 * implementation details and are not intended for direct use.</p>
 *
 * @since 5.7
 */
package org.apache.hc.client5.http.websocket;
