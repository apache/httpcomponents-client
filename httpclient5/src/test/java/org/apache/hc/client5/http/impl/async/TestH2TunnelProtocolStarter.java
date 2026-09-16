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
package org.apache.hc.client5.http.impl.async;

import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.ClientH2PrefaceHandler;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestH2TunnelProtocolStarter {

    @Test
    void testCreatesMinimalH2HandlerWithoutPushOrLogging() {
        final H2TunnelProtocolStarter starter = new H2TunnelProtocolStarter(
                H2Config.DEFAULT, CharCodingConfig.DEFAULT);
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getId()).thenReturn("test-tunnel");

        final IOEventHandler handler = starter.createHandler(ioSession, null);

        Assertions.assertNotNull(handler);
        Assertions.assertInstanceOf(ClientH2PrefaceHandler.class, handler);
    }

    @Test
    void testDefaultsWhenNullConfig() {
        final H2TunnelProtocolStarter starter = new H2TunnelProtocolStarter(null, null);
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getId()).thenReturn("test-tunnel-null");

        final IOEventHandler handler = starter.createHandler(ioSession, null);

        Assertions.assertNotNull(handler);
        Assertions.assertInstanceOf(ClientH2PrefaceHandler.class, handler);
    }

    @Test
    void testCustomH2ConfigIsRespected() {
        final H2Config customConfig = H2Config.custom()
                .setMaxFrameSize(32768)
                .setInitialWindowSize(128 * 1024)
                .setPushEnabled(false)
                .build();
        final H2TunnelProtocolStarter starter = new H2TunnelProtocolStarter(
                customConfig, CharCodingConfig.DEFAULT);
        final ProtocolIOSession ioSession = Mockito.mock(ProtocolIOSession.class);
        Mockito.when(ioSession.getId()).thenReturn("test-tunnel-custom");

        final IOEventHandler handler = starter.createHandler(ioSession, null);

        Assertions.assertNotNull(handler);
        Assertions.assertInstanceOf(ClientH2PrefaceHandler.class, handler);
    }

}
