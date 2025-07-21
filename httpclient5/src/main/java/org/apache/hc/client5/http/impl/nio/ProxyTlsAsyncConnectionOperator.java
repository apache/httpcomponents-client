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
package org.apache.hc.client5.http.impl.nio;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection-operator that enables <em>double-TLS</em> tunnelling for the
 * <strong>reactor / async</strong> transport.
 *
 * <pre>
 *   client ── TLS#1 ──► HTTPS-proxy   CONNECT   origin ── TLS#2 ──►
 * </pre>
 *
 * <p>The first hop (client → proxy) may already be protected by TLS when the
 * proxy itself speaks HTTPS.  If {@link ManagedAsyncClientConnection#getTlsDetails()}
 * returns non-{@code null}, this operator skips the standard upgrade path and
 * immediately starts a <em>second</em> TLS handshake to the target host inside
 * the tunnel.  For plain-HTTP proxies the logic falls back to
 * {@link DefaultAsyncClientConnectionOperator#upgrade(ManagedAsyncClientConnection, HttpHost, NamedEndpoint, Object, HttpContext, FutureCallback)} unchanged.</p>
 *
 * @since 5.6
 */
public final class ProxyTlsAsyncConnectionOperator extends DefaultAsyncClientConnectionOperator {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyTlsAsyncConnectionOperator.class);

    private final TlsStrategy tlsStrategy;

    /**
     * Builds an operator that uses the system-default DNS resolver and
     * scheme-port resolver.
     *
     * @param tlsLookup registry that <em>must</em> contain a {@link TlsStrategy}
     *                  under the {@code "https"} scheme.
     */
    public ProxyTlsAsyncConnectionOperator(final Lookup<TlsStrategy> tlsLookup) {
        this(tlsLookup, null, SystemDefaultDnsResolver.INSTANCE);
    }

    /**
     * Full-control constructor.
     *
     * @param tlsLookup          registry containing the TLS strategy.
     * @param schemePortResolver optional custom scheme-port resolver.
     * @param dnsResolver        optional custom DNS resolver.
     */
    public ProxyTlsAsyncConnectionOperator(final Lookup<TlsStrategy> tlsLookup,
                                           final SchemePortResolver schemePortResolver,
                                           final DnsResolver dnsResolver) {
        super(tlsLookup, schemePortResolver, dnsResolver);
        this.tlsStrategy = tlsLookup.lookup(URIScheme.HTTPS.id);
    }

    @Override
    public void upgrade(final ManagedAsyncClientConnection connection,
                        final HttpHost endpointHost,
                        final NamedEndpoint endpointName,
                        final Object attachment,
                        final HttpContext context,
                        final FutureCallback<ManagedAsyncClientConnection> callback) {

        final NamedEndpoint tlsName =
                endpointName != null ? endpointName : endpointHost;

        if (connection.getTlsDetails() != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} proxy hop is already TLS, starting inner TLS to {}",
                        ConnPoolSupport.getId(connection), tlsName);
            }
            startInnerTls(connection, tlsName, attachment, callback);
            return;
        }

        /* Plain HTTP proxy → standard upgrade path */
        super.upgrade(connection, endpointHost, endpointName, attachment, context, callback);
    }

    /**
     * Initiates TLS#2 inside the CONNECT tunnel.
     */
    private void startInnerTls(final ManagedAsyncClientConnection connection,
                               final NamedEndpoint tlsName,
                               final Object attachment,
                               final FutureCallback<ManagedAsyncClientConnection> callback) {
        try {
            tlsStrategy.upgrade(
                    connection,
                    tlsName,
                    attachment,
                    Timeout.ofMinutes(1),
                    new FutureCallback<TransportSecurityLayer>() {
                        @Override
                        public void completed(final TransportSecurityLayer result) {
                            if (callback != null) {
                                callback.completed(connection);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            if (callback != null) {
                                callback.failed(ex);
                            }
                        }

                        @Override
                        public void cancelled() {
                            if (callback != null) {
                                callback.cancelled();
                            }
                        }
                    });
        } catch (final Exception ex) {
            if (callback != null) {
                callback.failed(ex);
            }
        }
    }
}