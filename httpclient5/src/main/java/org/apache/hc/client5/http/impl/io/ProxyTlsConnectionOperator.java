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
package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.NamedEndpoint;

/**
 * Connection-operator that supports “double-TLS” proxy tunnelling for the
 * <strong>classic / blocking</strong> transport.
 *
 * <pre>
 *   client ── TLS#1 ──► HTTPS-proxy  CONNECT  origin ── TLS#2 ──►
 * </pre>
 *
 * <p>The operator lets the default implementation build the plain tunnel,
 * then layers a second TLS handshake on the already-encrypted socket and
 * re-binds it to the pooled connection.</p>
 *
 * @since 5.6
 */
public final class ProxyTlsConnectionOperator extends DefaultHttpClientConnectionOperator {

    private final TlsSocketStrategy tlsStrategy;

    /**
     * System-defaults constructor.
     */
    public ProxyTlsConnectionOperator(final Lookup<TlsSocketStrategy> tlsLookup) {
        this(tlsLookup, null, SystemDefaultDnsResolver.INSTANCE);
    }

    /**
     * Full-control constructor.
     */
    public ProxyTlsConnectionOperator(final Lookup<TlsSocketStrategy> tlsLookup,
                                      final SchemePortResolver schemePortResolver,
                                      final DnsResolver dnsResolver) {
        super(schemePortResolver, dnsResolver, tlsLookup);
        this.tlsStrategy = tlsLookup.lookup(URIScheme.HTTPS.id);
        if (this.tlsStrategy == null) {
            throw new IllegalArgumentException(
                    "Lookup must contain a TlsSocketStrategy for scheme 'https'");
        }
    }

    @Override
    public void upgrade(final ManagedHttpClientConnection conn,
                        final HttpHost endpointHost,
                        final NamedEndpoint endpointName,
                        final Object attachment,
                        final HttpContext context) throws IOException {

        final Socket raw = conn.getSocket();
        if (raw == null) {
            throw new ConnectionClosedException("Connection already closed");
        }

        /* Layer TLS#2 on top of the proxy TLS session */
        final SSLSocket layered = tlsStrategy.upgrade(
                raw,
                endpointHost.getHostName(),
                endpointHost.getPort(),
                attachment,
                context);

        /* Re-bind so the pool sees the secure socket */
        conn.bind(layered, raw);
    }
}
