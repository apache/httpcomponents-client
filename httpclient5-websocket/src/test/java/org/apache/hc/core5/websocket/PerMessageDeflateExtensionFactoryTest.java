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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PerMessageDeflateExtensionFactoryTest {

    @Test
    void createsExtension() {
        final PerMessageDeflateExtensionFactory factory = new PerMessageDeflateExtensionFactory();
        assertEquals("permessage-deflate", factory.getName());
        final WebSocketExtension ext = factory.create(
                new WebSocketExtensionData("permessage-deflate", Collections.emptyMap()), true);
        assertNotNull(ext);
        assertEquals("permessage-deflate", ext.getName());
    }

    @Test
    void rejectsUnsupportedWindowBits() {
        final Map<String, String> params = new LinkedHashMap<>();
        params.put("client_max_window_bits", "12");
        final WebSocketExtension ext = new PerMessageDeflateExtensionFactory().create(
                new WebSocketExtensionData("permessage-deflate", params), true);
        assertNull(ext);
    }

    @Test
    void echoesNegotiatedWindowBitsWhenRequested() {
        final Map<String, String> params = new LinkedHashMap<>();
        params.put("client_max_window_bits", "15");
        params.put("server_max_window_bits", "15");
        final WebSocketExtension ext = new PerMessageDeflateExtensionFactory().create(
                new WebSocketExtensionData("permessage-deflate", params), true);
        assertNotNull(ext);
        final WebSocketExtensionData response = ext.getResponseData();
        assertEquals("15", response.getParameters().get("client_max_window_bits"));
        assertEquals("15", response.getParameters().get("server_max_window_bits"));
    }
}
