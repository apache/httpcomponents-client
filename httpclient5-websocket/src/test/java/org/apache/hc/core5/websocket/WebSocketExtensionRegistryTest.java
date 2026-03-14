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

import java.nio.ByteBuffer;
import java.util.Collections;

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
}
