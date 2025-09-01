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
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.CharBuffer;

import org.apache.hc.client5.http.sse.impl.SseCallbacks;
import org.apache.hc.client5.http.sse.impl.SseEntityConsumer;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

class SseEntityConsumerTest {

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
    void parsesLinesAndFlushesOnEndOfStream() throws Exception {
        final Cb cb = new Cb();
        final SseEntityConsumer c = new SseEntityConsumer(cb);

        c.streamStart(ContentType.parse("text/event-stream"));
        c.data(CharBuffer.wrap("id: 9\nevent: t\ndata: v\n"), false);
        c.data(CharBuffer.wrap("\n"), true); // end -> flush

        assertTrue(cb.opened);
        assertEquals("9", cb.id);
        assertEquals("t", cb.type);
        assertEquals("v", cb.data);
    }

    @Test
    void rejectsWrongContentType() {
        final Cb cb = new Cb();
        final SseEntityConsumer c = new SseEntityConsumer(cb);
        try {
            c.streamStart(ContentType.APPLICATION_JSON);
            fail("Should have thrown");
        } catch (final Exception expected) { /* ok */ }
    }
}
