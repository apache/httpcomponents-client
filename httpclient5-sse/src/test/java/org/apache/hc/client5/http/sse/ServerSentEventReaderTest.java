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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hc.client5.http.sse.impl.ServerSentEventReader;
import org.junit.jupiter.api.Test;

class ServerSentEventReaderTest {

    static final class Capt implements ServerSentEventReader.Callback {
        String id, type, data, comment;
        Long retry;

        @Override
        public void onEvent(final String id, final String type, final String data) {
            this.id = id;
            this.type = type;
            this.data = data;
        }

        @Override
        public void onComment(final String comment) {
            this.comment = comment;
        }

        @Override
        public void onRetryChange(final long retryMs) {
            this.retry = retryMs;
        }
    }

    @Test
    void parsesMultiLineEventWithDefaults() {
        final Capt c = new Capt();
        final ServerSentEventReader r = new ServerSentEventReader(c);

        r.line("id: 42");
        r.line("data: hello");
        r.line("data: world");
        r.line(""); // dispatch

        assertEquals("42", c.id);
        assertEquals("message", c.type);
        assertEquals("hello\nworld", c.data);
    }

    @Test
    void parsesEventTypeAndCommentAndRetry() {
        final Capt c = new Capt();
        final ServerSentEventReader r = new ServerSentEventReader(c);

        r.line(": this is a comment");
        assertEquals("this is a comment", c.comment);

        r.line("event: update");
        r.line("retry: 1500");
        r.line("data: x");
        r.line(""); // dispatch

        assertEquals("update", c.type);
        assertEquals("x", c.data);
        assertEquals(Long.valueOf(1500L), c.retry);
    }

    @Test
    void ignoresIdWithNul() {
        final Capt c = new Capt();
        final ServerSentEventReader r = new ServerSentEventReader(c);

        r.line("id: a\u0000b");
        r.line("data: d");
        r.line("");

        assertNull(c.id);
        assertEquals("d", c.data);
    }
}
