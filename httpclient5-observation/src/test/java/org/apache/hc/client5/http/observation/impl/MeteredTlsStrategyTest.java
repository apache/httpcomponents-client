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

package org.apache.hc.client5.http.observation.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.ObservingOptions;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class MeteredTlsStrategyTest {

    private static final class DummyTSL implements TransportSecurityLayer {
        // no-op
        public void startTls(final Object attachment, final Timeout handshakeTimeout, final FutureCallback<TransportSecurityLayer> callback) {
        }

        public void close() {
        }

        public boolean isOpen() {
            return true;
        }

        public void shutdown() {
        }

        public String getId() {
            return "dummy";
        }

        @Override
        public void startTls(final SSLContext sslContext, final NamedEndpoint endpoint, final SSLBufferMode sslBufferMode, final SSLSessionInitializer initializer, final SSLSessionVerifier verifier, final Timeout handshakeTimeout) throws UnsupportedOperationException {

        }

        @Override
        public TlsDetails getTlsDetails() {
            return null;
        }
    }

    private static final class NE implements NamedEndpoint {
        private final String host;
        private final int port;

        NE(final String host, final int port) {
            this.host = host;
            this.port = port;
        }

        public String getHostName() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }

    private static final class OkTls implements TlsStrategy {
        @Override
        public void upgrade(final TransportSecurityLayer sessionLayer,
                            final NamedEndpoint endpoint,
                            final Object attachment,
                            final Timeout handshakeTimeout,
                            final FutureCallback<TransportSecurityLayer> callback) {
            // immediately complete
            if (callback != null) {
                callback.completed(sessionLayer);
            }
        }

        @Deprecated
        @Override
        public boolean upgrade(final TransportSecurityLayer sessionLayer,
                               final HttpHost host,
                               final SocketAddress localAddress,
                               final SocketAddress remoteAddress,
                               final Object attachment,
                               final Timeout handshakeTimeout) {
            return true;
        }
    }

    private static final class FailTls implements TlsStrategy {
        @Override
        public void upgrade(final TransportSecurityLayer sessionLayer,
                            final NamedEndpoint endpoint,
                            final Object attachment,
                            final Timeout handshakeTimeout,
                            final FutureCallback<TransportSecurityLayer> callback) {
            if (callback != null) {
                callback.failed(new RuntimeException("boom"));
            }
        }

        @Deprecated
        @Override
        public boolean upgrade(final TransportSecurityLayer sessionLayer,
                               final HttpHost host,
                               final SocketAddress localAddress,
                               final SocketAddress remoteAddress,
                               final Object attachment,
                               final Timeout handshakeTimeout) {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void recordsOkOutcome_newApi() {
        final MeterRegistry reg = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("tls").build();
        final MeteredTlsStrategy m = new MeteredTlsStrategy(new OkTls(), reg, mc, ObservingOptions.DEFAULT);

        final TransportSecurityLayer tsl = new DummyTSL();
        m.upgrade(tsl, new NE("sni.local", 443), null, Timeout.ofSeconds(5), new FutureCallback<TransportSecurityLayer>() {
            public void completed(final TransportSecurityLayer result) {
            }

            public void failed(final Exception ex) {
            }

            public void cancelled() {
            }
        });

        assertTrue(reg.find("tls.tls.handshake").timer().count() >= 1L);
        assertTrue(reg.find("tls.tls.handshakes").counter().count() >= 1.0d);
    }

    @SuppressWarnings("deprecation")
    @Test
    void recordsErrorOutcome_bothApis() {
        final MeterRegistry reg = new SimpleMeterRegistry();
        final MetricConfig mc = MetricConfig.builder().prefix("tls2").build();
        final MeteredTlsStrategy m = new MeteredTlsStrategy(new FailTls(), reg, mc, ObservingOptions.DEFAULT);

        final TransportSecurityLayer tsl = new DummyTSL();
        // new API
        try {
            m.upgrade(tsl, new NE("sni.local", 443), null, Timeout.ofSeconds(5), new FutureCallback<TransportSecurityLayer>() {
                public void completed(final TransportSecurityLayer result) {
                }

                public void failed(final Exception ex) {
                }

                public void cancelled() {
                }
            });
        } catch (final RuntimeException ignore) {
            // delegate calls failed via callback, no throw expected
        }

        // deprecated API
        try {
            m.upgrade(tsl, new HttpHost("https", "sni.local", 443), null, null, null, Timeout.ofSeconds(5));
        } catch (final RuntimeException ignore) {
            // expected throw
        }

        assertTrue(reg.find("tls2.tls.handshake").timer().count() >= 2L); // once per API path
        assertTrue(reg.find("tls2.tls.handshakes").counter().count() >= 2.0d);
    }
}
