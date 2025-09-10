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
package org.apache.hc.client5.http.websocket.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

final class WebSocketClientConfigTest {

    @Test
    void builderDefaultsAndCustom() {
        final WebSocketClientConfig def = WebSocketClientConfig.custom().build();
        assertTrue(def.isAutoPong());
        assertTrue(def.getMaxFrameSize() > 0);
        assertTrue(def.getMaxMessageSize() > 0);

        final WebSocketClientConfig cfg = WebSocketClientConfig.custom()
                .setAutoPong(false)
                .setMaxFrameSize(1024)
                .setMaxMessageSize(2048)
                .setConnectTimeout(Timeout.ofSeconds(3))
                .build();

        assertFalse(cfg.isAutoPong());
        assertEquals(1024, cfg.getMaxFrameSize());
        assertEquals(2048, cfg.getMaxMessageSize());
        assertEquals(Timeout.ofSeconds(3), cfg.getConnectTimeout());
    }
}
