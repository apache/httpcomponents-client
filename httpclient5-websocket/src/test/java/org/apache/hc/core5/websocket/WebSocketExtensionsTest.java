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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

class WebSocketExtensionsTest {

    @Test
    void parsesExtensionsHeader() {
        final BasicHeader header = new BasicHeader(
                WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; client_max_window_bits=12, foo; bar=1");
        final List<WebSocketExtensionData> data = WebSocketExtensions.parse(header);
        assertEquals(2, data.size());
        assertEquals("permessage-deflate", data.get(0).getName());
        assertEquals("12", data.get(0).getParameters().get("client_max_window_bits"));
        assertEquals("foo", data.get(1).getName());
        final Map<String, String> params = data.get(1).getParameters();
        assertEquals("1", params.get("bar"));
    }

    @Test
    void dropsOfferWithDuplicateParameter() {
        final BasicHeader header = new BasicHeader(
                WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; client_no_context_takeover; client_no_context_takeover");
        assertTrue(WebSocketExtensions.parse(header).isEmpty(),
                "a repeated parameter makes the offer invalid and it must be dropped, not collapsed");
    }

    @Test
    void dropsOnlyTheOfferWithDuplicateParameter() {
        final BasicHeader header = new BasicHeader(
                WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; server_max_window_bits=10; server_max_window_bits=12, foo; bar=1");
        final List<WebSocketExtensionData> data = WebSocketExtensions.parse(header);
        assertEquals(1, data.size(), "the malformed permessage-deflate offer is dropped, the valid one kept");
        assertEquals("foo", data.get(0).getName());
    }

    @Test
    void combinesMultipleExtensionHeaders() {
        // RFC 6455: multiple Sec-WebSocket-Extensions fields are a single combined value, so a
        // client's fallback offer in a later field must still be examined.
        final List<WebSocketExtensionData> data = WebSocketExtensions.parse(Arrays.<Header>asList(
                new BasicHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS,
                        "permessage-deflate; server_max_window_bits=12"),
                new BasicHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS,
                        "permessage-deflate")).iterator());
        assertEquals(2, data.size(), "offers from every Sec-WebSocket-Extensions field must be combined");
        assertEquals("12", data.get(0).getParameters().get("server_max_window_bits"));
        assertTrue(data.get(1).getParameters().isEmpty(), "the fallback offer must be preserved");
    }
}
