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
package org.apache.hc.core5.websocket.server;

import java.lang.reflect.Field;
import java.net.SocketAddress;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebSocketH2ServerBootstrapTest {

    @Test
    void setH2ConfigDisablesPush() {
        final WebSocketH2ServerBootstrap bootstrap = WebSocketH2ServerBootstrap.bootstrap();
        bootstrap.setH2Config(H2Config.custom().setPushEnabled(true).build());

        final H2Config cfg = getField(bootstrap, "h2Config", H2Config.class);
        Assertions.assertNotNull(cfg);
        Assertions.assertFalse(cfg.isPushEnabled());
    }

    @Test
    void setH2ConfigNullUsesDefault() {
        final WebSocketH2ServerBootstrap bootstrap = WebSocketH2ServerBootstrap.bootstrap();
        bootstrap.setH2Config(null);

        final H2Config cfg = getField(bootstrap, "h2Config", H2Config.class);
        Assertions.assertNotNull(cfg);
        Assertions.assertFalse(cfg.isPushEnabled());
    }

    @Test
    void setHttp1ConfigNullUsesDefault() {
        final WebSocketH2ServerBootstrap bootstrap = WebSocketH2ServerBootstrap.bootstrap();
        bootstrap.setHttp1Config(null);
        final Http1Config cfg = getField(bootstrap, "http1Config", Http1Config.class);
        Assertions.assertSame(Http1Config.DEFAULT, cfg);
    }

    @Test
    void createUsesHttpsWhenTlsStrategyProvided() {
        final WebSocketH2ServerBootstrap bootstrap = WebSocketH2ServerBootstrap.bootstrap()
                .setTlsStrategy(new StubTlsStrategy());
        bootstrap.register("/", WebSocketH2ServerBootstrapTest::noopHandler);
        final WebSocketH2Server server = bootstrap.create();
        final URIScheme scheme = getField(server, "scheme", URIScheme.class);
        Assertions.assertEquals(URIScheme.HTTPS, scheme);
    }

    @Test
    void createUsesHttpWhenNoTlsStrategy() {
        final WebSocketH2ServerBootstrap bootstrap = WebSocketH2ServerBootstrap.bootstrap();
        bootstrap.register("/", WebSocketH2ServerBootstrapTest::noopHandler);
        final WebSocketH2Server server = bootstrap.create();
        final URIScheme scheme = getField(server, "scheme", URIScheme.class);
        Assertions.assertEquals(URIScheme.HTTP, scheme);
    }

    private static final class StubTlsStrategy implements TlsStrategy {
        @Override
        public boolean upgrade(final TransportSecurityLayer sessionLayer,
                               final HttpHost host,
                               final SocketAddress localAddress,
                               final SocketAddress remoteAddress,
                               final Object attachment,
                               final Timeout handshakeTimeout) {
            return true;
        }

        @Override
        public void upgrade(final TransportSecurityLayer sessionLayer,
                            final NamedEndpoint endpoint,
                            final Object attachment,
                            final Timeout handshakeTimeout,
                            final FutureCallback<TransportSecurityLayer> callback) {
            if (callback != null) {
                callback.completed(sessionLayer);
            }
        }
    }

    private static <T> T getField(final Object target, final String name, final Class<T> type) {
        try {
            final Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (final Exception ex) {
            throw new AssertionError("Failed to access field " + name, ex);
        }
    }

    private static WebSocketHandler noopHandler() {
        return new WebSocketHandler() { };
    }
}
