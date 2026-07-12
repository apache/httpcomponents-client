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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class WebSocketExtensionRegistryTest {

    @Test
    void registersAndNegotiatesExtensions() throws Exception {
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(new WebSocketExtensionFactory() {
                    @Override
                    public String getName() {
                        return "x-test";
                    }

                    @Override
                    public WebSocketExtension create(final WebSocketExtensionData request, final boolean server) {
                        return new WebSocketExtension() {
                            @Override
                            public String getName() {
                                return "x-test";
                            }

                            @Override
                            public boolean usesRsv1() {
                                return false;
                            }

                            @Override
                            public boolean usesRsv2() {
                                return false;
                            }

                            @Override
                            public boolean usesRsv3() {
                                return false;
                            }

                            @Override
                            public ByteBuffer decode(final WebSocketFrameType type, final boolean fin, final ByteBuffer payload) {
                                return payload;
                            }

                            @Override
                            public ByteBuffer encode(final WebSocketFrameType type, final boolean fin, final ByteBuffer payload) {
                                return payload;
                            }

                            @Override
                            public WebSocketExtensionData getResponseData() {
                                return new WebSocketExtensionData("x-test", Collections.emptyMap());
                            }
                        };
                    }
                });

        final WebSocketExtensionNegotiation negotiation = registry.negotiate(
                Collections.singletonList(new WebSocketExtensionData("x-test", Collections.emptyMap())),
                true);
        assertNotNull(negotiation);
        assertEquals(1, negotiation.getExtensions().size());
        assertEquals("x-test", negotiation.getResponseData().get(0).getName());
    }

    @Test
    void defaultRegistryContainsPerMessageDeflate() throws Exception {
        final WebSocketExtensionRegistry registry = WebSocketExtensionRegistry.createDefault();
        final WebSocketExtensionNegotiation negotiation = registry.negotiate(
                Collections.singletonList(new WebSocketExtensionData("permessage-deflate", Collections.emptyMap())),
                true);
        assertEquals(1, negotiation.getExtensions().size());
    }

    @Test
    void duplicateOffersAreDeduplicated() throws Exception {
        final WebSocketExtensionRegistry registry = WebSocketExtensionRegistry.createDefault();
        // A client may offer the same extension more than once; accepting both would claim RSV1
        // twice and later break the frame reader with an IllegalStateException.
        final WebSocketExtensionNegotiation negotiation = registry.negotiate(
                Arrays.asList(
                        new WebSocketExtensionData("permessage-deflate", Collections.emptyMap()),
                        new WebSocketExtensionData("permessage-deflate", Collections.emptyMap())),
                true);
        assertEquals(1, negotiation.getExtensions().size(), "duplicate offers must collapse to one extension");
        assertEquals(1, negotiation.getResponseData().size(), "response must list the extension once");
    }

    @Test
    void negotiateClosesCreatedExtensionsWhenLaterFactoryThrows() {
        final AtomicInteger closed = new AtomicInteger();
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(closingFactory("ext-a", closed, null))
                .register(throwingFactory("ext-b", new WebSocketException("boom")));

        final WebSocketException ex = assertThrows(WebSocketException.class, () ->
                registry.negotiate(Arrays.asList(
                        new WebSocketExtensionData("ext-a", Collections.emptyMap()),
                        new WebSocketExtensionData("ext-b", Collections.emptyMap())),
                        true));

        assertEquals("boom", ex.getMessage());
        assertEquals(1, closed.get(), "the already-created extension must be closed exactly once");
    }

    @Test
    void negotiateSuppressesCloseFailureOnOriginalException() {
        final WebSocketException primary = new WebSocketException("primary");
        final IllegalStateException closeFailure = new IllegalStateException("close failed");
        final WebSocketExtensionRegistry registry = new WebSocketExtensionRegistry()
                .register(closingFactory("ext-a", new AtomicInteger(), closeFailure))
                .register(throwingFactory("ext-b", primary));

        final WebSocketException ex = assertThrows(WebSocketException.class, () ->
                registry.negotiate(Arrays.asList(
                        new WebSocketExtensionData("ext-a", Collections.emptyMap()),
                        new WebSocketExtensionData("ext-b", Collections.emptyMap())),
                        true));

        assertSame(primary, ex, "the original negotiation failure must be preserved");
        assertEquals(1, ex.getSuppressed().length, "the close failure must be attached as suppressed");
        assertSame(closeFailure, ex.getSuppressed()[0]);
    }

    private static WebSocketExtensionFactory closingFactory(
            final String name, final AtomicInteger closed, final RuntimeException closeFailure) {
        return new WebSocketExtensionFactory() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public WebSocketExtension create(final WebSocketExtensionData request, final boolean server) {
                return new WebSocketExtension() {
                    @Override
                    public String getName() {
                        return name;
                    }

                    @Override
                    public void close() {
                        closed.incrementAndGet();
                        if (closeFailure != null) {
                            throw closeFailure;
                        }
                    }
                };
            }
        };
    }

    private static WebSocketExtensionFactory throwingFactory(final String name, final WebSocketException failure) {
        return new WebSocketExtensionFactory() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public WebSocketExtension create(final WebSocketExtensionData request, final boolean server)
                    throws WebSocketException {
                throw failure;
            }
        };
    }
}
