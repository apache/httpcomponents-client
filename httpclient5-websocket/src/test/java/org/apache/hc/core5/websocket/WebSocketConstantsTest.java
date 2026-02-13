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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebSocketConstantsTest {

    @Test
    void exposesStandardHeaderNames() {
        assertEquals("Sec-WebSocket-Key", WebSocketConstants.SEC_WEBSOCKET_KEY);
        assertEquals("Sec-WebSocket-Version", WebSocketConstants.SEC_WEBSOCKET_VERSION);
        assertEquals("Sec-WebSocket-Accept", WebSocketConstants.SEC_WEBSOCKET_ACCEPT);
        assertEquals("Sec-WebSocket-Protocol", WebSocketConstants.SEC_WEBSOCKET_PROTOCOL);
        assertEquals("Sec-WebSocket-Extensions", WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS);
        assertEquals("258EAFA5-E914-47DA-95CA-C5AB0DC85B11", WebSocketConstants.WEBSOCKET_GUID);
    }
}
