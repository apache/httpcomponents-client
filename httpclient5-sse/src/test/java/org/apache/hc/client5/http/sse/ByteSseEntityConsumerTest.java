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
package org.apache.hc.client5.http.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.sse.impl.ByteSseEntityConsumer;
import org.apache.hc.client5.http.sse.impl.SseCallbacks;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

class ByteSseEntityConsumerTest {

    static final class Cb implements SseCallbacks {
        boolean opened;
        String id, type, data;
        Long retry;

        @Override
        public void onOpen() {
            opened = true;
        }

        @Override
        public void onEvent(final String id, final String type, final String data) {
            this.id = id;
            this.type = type;
            this.data = data;
        }

        @Override
        public void onRetry(final long retryMs) {
            retry = retryMs;
        }
    }


    @Test
    void handlesBomCrLfAndDispatch() throws Exception {
        final Cb cb = new Cb();
        final ByteSseEntityConsumer c = new ByteSseEntityConsumer(cb);

        c.streamStart(ContentType.parse("text/event-stream"));

        // UTF-8 BOM + CRLF split across two chunks
        final byte[] p1 = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        final byte[] p2 = "event: ping\r\nid: 1\r\ndata: hi\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        c.consume(ByteBuffer.wrap(p1));
        c.consume(ByteBuffer.wrap(p2));
        c.streamEnd(null);

        assertTrue(cb.opened);
        assertEquals("1", cb.id);
        assertEquals("ping", cb.type);
        assertEquals("hi", cb.data);
    }

    @Test
    void emitsRetry() throws Exception {
        final Cb cb = new Cb();
        final ByteSseEntityConsumer c = new ByteSseEntityConsumer(cb);
        c.streamStart(ContentType.parse("text/event-stream"));

        final byte[] p = "retry: 2500\n\n".getBytes(StandardCharsets.UTF_8);
        c.consume(ByteBuffer.wrap(p));
        c.streamEnd(null);

        assertEquals(Long.valueOf(2500L), cb.retry);
    }
}
