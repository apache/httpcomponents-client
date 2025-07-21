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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit-tests for {@link ProxyTlsConnectionOperator}.
 */
class ProxyTlsConnectionOperatorTest {

    @Test
    void upgrade_layersSecondTls_andBinds() throws Exception {
        /* -------- test doubles -------- */
        final TlsSocketStrategy tlsStrategy = Mockito.mock(TlsSocketStrategy.class);
        final ManagedHttpClientConnection conn = Mockito.mock(ManagedHttpClientConnection.class);
        final Socket raw = Mockito.mock(Socket.class);
        final SSLSocket layered = Mockito.mock(SSLSocket.class);

        when(conn.getSocket()).thenReturn(raw);
        when(tlsStrategy.upgrade(
                same(raw), eq("example.com"), eq(443),
                isNull(), any()))
                .thenReturn(layered);

        final Lookup<TlsSocketStrategy> lookup = RegistryBuilder.<TlsSocketStrategy>create()
                .register(URIScheme.HTTPS.id, tlsStrategy)
                .build();

        final ProxyTlsConnectionOperator op = new ProxyTlsConnectionOperator(lookup);

        final HttpHost endpoint = new HttpHost("https", "example.com", 443);

        op.upgrade(conn, endpoint, null, null, HttpCoreContext.create());

        verify(tlsStrategy).upgrade(
                same(raw), eq("example.com"), eq(443),
                isNull(), any());
        verify(conn).bind(layered, raw);
    }

    @Test
    void upgrade_throwsWhenSocketMissing() throws Exception {
        final TlsSocketStrategy tlsStrategy = Mockito.mock(TlsSocketStrategy.class);
        final ManagedHttpClientConnection conn = Mockito.mock(ManagedHttpClientConnection.class);
        when(conn.getSocket()).thenReturn(null);

        final Lookup<TlsSocketStrategy> lookup = RegistryBuilder.<TlsSocketStrategy>create()
                .register(URIScheme.HTTPS.id, tlsStrategy)
                .build();

        final ProxyTlsConnectionOperator op = new ProxyTlsConnectionOperator(lookup);
        final HttpHost endpoint = new HttpHost("https", "example.com", 443);

        assertThrows(ConnectionClosedException.class,
                () -> op.upgrade(conn, endpoint, null, null, HttpCoreContext.create()));
    }
}
