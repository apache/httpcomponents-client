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

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketHandshakeTest {

    @Test
    void createsExpectedAcceptKey() throws WebSocketException {
        final String key = "dGhlIHNhbXBsZSBub25jZQ==";
        final String expected = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
        Assertions.assertEquals(expected, WebSocketHandshake.createAcceptKey(key));
    }

    @Test
    void detectsWebSocketUpgradeRequest() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/chat");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");

        Assertions.assertTrue(WebSocketHandshake.isWebSocketUpgrade(request));
    }

    @Test
    void rejectsInvalidSecWebSocketKeyEncoding() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/chat");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "not-base64");

        Assertions.assertFalse(WebSocketHandshake.isWebSocketUpgrade(request));
    }

    @Test
    void rejectsInvalidSecWebSocketKeyLength() {
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/chat");
        request.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        request.addHeader(HttpHeaders.UPGRADE, "websocket");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION, "13");
        request.addHeader(WebSocketConstants.SEC_WEBSOCKET_KEY, "AQIDBA==");

        Assertions.assertFalse(WebSocketHandshake.isWebSocketUpgrade(request));
    }

    @Test
    void parsesSubprotocols() {
        final BasicHeader header = new BasicHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");
        Assertions.assertEquals(2, WebSocketHandshake.parseSubprotocols(header).size());
    }
}
