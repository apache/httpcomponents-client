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
package org.apache.hc.client5.http.websocket.client.impl.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.frame.FrameHeaderBits;
import org.junit.jupiter.api.Test;

final class Http1UpgradeProtocolExtensionTest {

    @Test
    void pmce_rejectedWhenDisabled() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .enablePerMessageDeflate(false)
                .build();
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate"));
    }

    @Test
    void pmce_rejectedWhenClientWindowBitsNotOffered() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerServerNoContextTakeover(false)
                .offerClientNoContextTakeover(false)
                .offerClientMaxWindowBits(null)
                .offerServerMaxWindowBits(null)
                .build();
        // RFC 7692 section 7.1.2.2: client_max_window_bits may appear in a response only
        // when the offer included it.
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate; client_max_window_bits=15"));
    }

    @Test
    void pmce_acceptsUninvitedNoContextTakeover() {
        // RFC 7692 sections 7.1.1.1 and 7.1.1.2: the server may include either
        // no-context-takeover parameter in the response even when the offer did not.
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerServerNoContextTakeover(false)
                .offerClientNoContextTakeover(false)
                .build();
        final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(cfg,
                "permessage-deflate; server_no_context_takeover; client_no_context_takeover");
        assertFalse(chain.isEmpty());
    }

    @Test
    void pmce_acceptsUninvitedServerMaxWindowBits() {
        // RFC 7692 section 7.1.2.1: the server may constrain its own window uninvited.
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerServerMaxWindowBits(null)
                .build();
        final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(cfg,
                "permessage-deflate; server_max_window_bits=12");
        assertFalse(chain.isEmpty());
    }

    @Test
    void headerValue_combinesRepeatedHeaders() {
        // RFC 6455 section 9.1: Sec-WebSocket-Extensions may be split across multiple
        // header fields; all instances must be taken into account.
        final BasicHttpResponse response = new BasicHttpResponse(101);
        response.addHeader("Sec-WebSocket-Extensions", "permessage-deflate");
        response.addHeader("Sec-WebSocket-Extensions", "x-unknown");
        assertEquals("permessage-deflate, x-unknown",
                Http1UpgradeProtocol.headerValue(response, "Sec-WebSocket-Extensions"));
        // The combined value must flow into validation: the unknown extension is rejected.
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg,
                        Http1UpgradeProtocol.headerValue(response, "Sec-WebSocket-Extensions")));
    }

    @Test
    void pmce_rejectsDuplicateServerNoContextTakeover() {
        // RFC 7692 section 7.1: a parameter name must not appear more than once in a response.
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg,
                        "permessage-deflate; server_no_context_takeover; server_no_context_takeover"));
        assertTrue(ex.getMessage().startsWith("Duplicate permessage-deflate parameter"), ex.getMessage());
    }

    @Test
    void pmce_rejectsDuplicateClientNoContextTakeover() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg,
                        "permessage-deflate; client_no_context_takeover; client_no_context_takeover"));
        assertTrue(ex.getMessage().startsWith("Duplicate permessage-deflate parameter"), ex.getMessage());
    }

    @Test
    void pmce_rejectsDuplicateServerMaxWindowBits() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg,
                        "permessage-deflate; server_max_window_bits=12; server_max_window_bits=10"));
        assertTrue(ex.getMessage().startsWith("Duplicate permessage-deflate parameter"), ex.getMessage());
    }

    @Test
    void pmce_rejectsDuplicateClientMaxWindowBits() {
        // client_max_window_bits can no longer be offered, so the first occurrence is already
        // rejected as "not offered"; a syntactically invalid duplicated response must still fail.
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg,
                        "permessage-deflate; client_max_window_bits=15; client_max_window_bits=12"));
    }

    @Test
    void pmce_rejectedOnUnknownOrDuplicate() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom().build();
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate; unknown=1"));
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate, permessage-deflate"));
    }

    @Test
    void pmce_acceptsServerWindowBitsBelow15() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerServerMaxWindowBits(15)
                .build();
        final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(cfg,
                "permessage-deflate; server_max_window_bits=12");
        assertFalse(chain.isEmpty());
        assertEquals(FrameHeaderBits.RSV1, chain.rsvMask());
    }

    @Test
    void pmce_validNegotiation_buildsChain() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerServerMaxWindowBits(15)
                .offerClientNoContextTakeover(true)
                .offerServerNoContextTakeover(true)
                .build();
        final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(cfg,
                "permessage-deflate; client_no_context_takeover; server_no_context_takeover; server_max_window_bits=15");
        assertFalse(chain.isEmpty());
        assertEquals(FrameHeaderBits.RSV1, chain.rsvMask());
    }
}
