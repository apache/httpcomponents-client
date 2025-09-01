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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.sse.impl.SseResponseConsumer;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

class SseResponseConsumerTest {

    static final class DummyEntity implements AsyncEntityConsumer<Void> {
        boolean started, ended, failed;

        @Override
        public void updateCapacity(final CapacityChannel channel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
            ended = true;
        }

        @Override
        public void streamStart(final EntityDetails entityDetails, final FutureCallback<Void> resultCallback) throws HttpException, IOException {
            started = true;
        }

        @Override
        public void failed(final Exception cause) {
            failed = true;
        }

        @Override
        public void releaseResources() {
        }


        @Override
        public Void getContent() {
            return null;
        }
    }

    @Test
    void passesThrough200AndStartsEntity() throws Exception {
        final DummyEntity ent = new DummyEntity();
        final AtomicLong hint = new AtomicLong(-1);
        final SseResponseConsumer c = new SseResponseConsumer(ent, hint::set);

        final HttpResponse rsp = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        c.consumeResponse(rsp, new TestEntityDetails("text/event-stream"), new HttpContext() {
            @Override
            public ProtocolVersion getProtocolVersion() {
                return null;
            }

            @Override
            public void setProtocolVersion(final ProtocolVersion version) {

            }

            @Override
            public Object getAttribute(final String id) {
                return null;
            }

            @Override
            public Object setAttribute(final String id, final Object obj) {
                return null;
            }

            @Override
            public Object removeAttribute(final String id) {
                return null;
            }
        }, new FutureCallback<Void>() {
            @Override
            public void completed(final Void result) {
            }

            @Override
            public void failed(final Exception ex) {
            }

            @Override
            public void cancelled() {
            }
        });

        assertTrue(ent.started);
        assertEquals(-1L, hint.get());
    }

    @Test
    void extractsRetryAfterAndThrowsOnNon200() {
        final DummyEntity ent = new DummyEntity();
        final AtomicLong hint = new AtomicLong(-1);
        final SseResponseConsumer c = new SseResponseConsumer(ent, hint::set);

        final BasicHttpResponse rsp = new BasicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "busy");
        rsp.addHeader(new BasicHeader(HttpHeaders.RETRY_AFTER, "3"));

        try {
            c.consumeResponse(rsp, new TestEntityDetails("text/event-stream"), new HttpContext() {
                @Override
                public ProtocolVersion getProtocolVersion() {
                    return null;
                }

                @Override
                public void setProtocolVersion(final ProtocolVersion version) {

                }

                @Override
                public Object getAttribute(final String id) {
                    return null;
                }

                @Override
                public Object setAttribute(final String id, final Object obj) {
                    return null;
                }

                @Override
                public Object removeAttribute(final String id) {
                    return null;
                }
            }, new FutureCallback<Void>() {
                @Override
                public void completed(final Void result) {
                }

                @Override
                public void failed(final Exception ex) {
                }

                @Override
                public void cancelled() {
                }
            });
            fail("Expected exception");
        } catch (final Exception expected) {
            assertEquals(3000L, hint.get());
        }
    }

    // Minimal EntityDetails stub for tests
    static final class TestEntityDetails implements EntityDetails {
        private final String ct;

        TestEntityDetails(final String ct) {
            this.ct = ct;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public String getContentType() {
            return ct;
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public boolean isChunked() {
            return true;
        }

        @Override
        public Set<String> getTrailerNames() {
            return Collections.<String>emptySet();
        }
    }
}
