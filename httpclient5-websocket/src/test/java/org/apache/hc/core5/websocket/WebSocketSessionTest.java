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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketSessionTest {

    @Test
    void writesTextAndCloseFrames() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final WebSocketSession session = new WebSocketSession(
                WebSocketConfig.DEFAULT,
                new ByteArrayInputStream(new byte[0]),
                out,
                null,
                null,
                Collections.emptyList());

        session.sendText("hello");
        final int afterText = out.size();
        assertTrue(afterText > 0);

        session.close(1000, "done");
        final int afterClose = out.size();
        assertTrue(afterClose > afterText);

        session.close(1000, "done");
        assertEquals(afterClose, out.size(), "close should be sent once");
    }

    @Test
    void decodeTextValidatesUtf8() throws Exception {
        final ByteBuffer payload = StandardCharsets.UTF_8.encode("ok");
        assertEquals("ok", WebSocketSession.decodeText(payload));
    }

    @Test
    void decodeTextRejectsInvalidUtf8() {
        final byte[] invalid = new byte[] {(byte) 0xC3, (byte) 0x28};
        final WebSocketProtocolException ex = Assertions.assertThrows(
                WebSocketProtocolException.class,
                () -> WebSocketSession.decodeText(ByteBuffer.wrap(invalid)));
        assertEquals(1007, ex.closeCode);
    }
}
