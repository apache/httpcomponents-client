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
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.ClientH2PrefaceHandler;
import org.apache.hc.core5.http2.impl.nio.ClientH2StreamMultiplexerFactory;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;

/**
 * Minimal {@link IOEventHandlerFactory} for starting HTTP/2 client protocol
 * inside a CONNECT tunnel session.
 * <p>
 * Unlike {@link H2AsyncClientProtocolStarter}, this factory does not
 * install push consumer handling, frame/header logging listeners, or
 * exception callbacks. Those concerns belong to the outer proxy
 * connection, not the tunneled target connection.
 * </p>
 *
 * @since 5.7
 */
final class H2TunnelProtocolStarter implements IOEventHandlerFactory {

    private final H2Config h2Config;
    private final CharCodingConfig charCodingConfig;

    H2TunnelProtocolStarter(
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig) {
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
    }

    @Override
    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        final ClientH2StreamMultiplexerFactory multiplexerFactory = new ClientH2StreamMultiplexerFactory(
                HttpProcessorBuilder.create().build(),
                null,
                h2Config,
                charCodingConfig,
                null);
        return new ClientH2PrefaceHandler(ioSession, multiplexerFactory, false, null);
    }

}
