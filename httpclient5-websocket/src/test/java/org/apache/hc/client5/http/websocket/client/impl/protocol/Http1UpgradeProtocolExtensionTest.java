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

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
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
    void pmce_rejectedWhenParametersNotOffered() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerServerNoContextTakeover(false)
                .offerClientNoContextTakeover(false)
                .offerClientMaxWindowBits(null)
                .offerServerMaxWindowBits(null)
                .build();
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate; server_no_context_takeover"));
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate; client_max_window_bits=15"));
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
    void pmce_rejectedOnUnsupportedClientWindowBits() {
        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .offerClientMaxWindowBits(15)
                .offerServerMaxWindowBits(15)
                .build();
        assertThrows(IllegalStateException.class, () ->
                Http1UpgradeProtocol.buildExtensionChain(cfg, "permessage-deflate; client_max_window_bits=12"));
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
                .offerClientMaxWindowBits(15)
                .offerServerMaxWindowBits(15)
                .offerClientNoContextTakeover(true)
                .offerServerNoContextTakeover(true)
                .build();
        final ExtensionChain chain = Http1UpgradeProtocol.buildExtensionChain(cfg,
                "permessage-deflate; client_no_context_takeover; server_no_context_takeover; client_max_window_bits=15");
        assertFalse(chain.isEmpty());
        assertEquals(FrameHeaderBits.RSV1, chain.rsvMask());
    }
}
