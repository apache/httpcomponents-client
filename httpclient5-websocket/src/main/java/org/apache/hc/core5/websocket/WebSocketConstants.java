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
package org.apache.hc.core5.websocket;

public final class WebSocketConstants {

    public static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    public static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    public static final String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
    public static final String SEC_WEBSOCKET_VERSION_LOWER = "sec-websocket-version";
    public static final String SEC_WEBSOCKET_PROTOCOL_LOWER = "sec-websocket-protocol";
    public static final String SEC_WEBSOCKET_EXTENSIONS_LOWER = "sec-websocket-extensions";
    /**
     * RFC 8441 extended CONNECT pseudo-header.
     */
    public static final String PSEUDO_PROTOCOL = ":protocol";

    private WebSocketConstants() {
    }
}
