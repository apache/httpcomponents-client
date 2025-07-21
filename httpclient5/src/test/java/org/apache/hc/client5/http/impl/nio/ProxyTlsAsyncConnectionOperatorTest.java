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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.nio.ManagedAsyncClientConnection;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests for {@link ProxyTlsAsyncConnectionOperator}.
 */
class ProxyTlsAsyncConnectionOperatorTest {

    private static Lookup<TlsStrategy> registryWith(final TlsStrategy strategy) {
        return RegistryBuilder.<TlsStrategy>create()
                .register(URIScheme.HTTPS.id, strategy)
                .build();
    }

    /* --------------------------------------------------------------
     * Happy path – outer handshake succeeds
     * -------------------------------------------------------------- */
    @Test
    void upgrade_passesThroughOnSuccess() throws Exception {

        /* ----- mocks ----- */
        final TlsStrategy tls = mock(TlsStrategy.class);
        final ManagedAsyncClientConnection conn = mock(ManagedAsyncClientConnection.class,
                withSettings().extraInterfaces(TransportSecurityLayer.class));
        @SuppressWarnings("unchecked") final FutureCallback<ManagedAsyncClientConnection> userCb = mock(FutureCallback.class);

        /* tls.upgrade -> immediately call cb.completed(...) */
        doAnswer(inv -> {
            final FutureCallback<TransportSecurityLayer> cb = inv.getArgument(4);
            cb.completed((TransportSecurityLayer) inv.getArgument(0));
            return null;
        }).when(tls).upgrade(any(TransportSecurityLayer.class), any(), any(), any(), any());

        final ProxyTlsAsyncConnectionOperator op =
                new ProxyTlsAsyncConnectionOperator(registryWith(tls));

        final HttpHost target = new HttpHost("https", "example.com", 443);

        assertDoesNotThrow(() ->
                op.upgrade(conn, target, null, null,
                        HttpCoreContext.create(), userCb));

        verify(tls, times(1))
                .upgrade(any(TransportSecurityLayer.class), eq(target), any(), any(), any());
        verify(userCb).completed(conn);
    }

    @Test
    void upgrade_performsInnerTlsWhenProxyIsSecure() throws Exception {

        final TlsStrategy tls = mock(TlsStrategy.class);

        final ManagedAsyncClientConnection conn = mock(
                ManagedAsyncClientConnection.class,
                withSettings().extraInterfaces(TransportSecurityLayer.class));

        // Mark the proxy hop as already secured by TLS
        when(conn.getTlsDetails())
                .thenReturn(new TlsDetails(mock(SSLSession.class), null));

        @SuppressWarnings("unchecked") final FutureCallback<ManagedAsyncClientConnection> userCb = mock(FutureCallback.class);

        // tls.upgrade(...) -> immediately complete
        doAnswer(inv -> {
            final FutureCallback<TransportSecurityLayer> cb = inv.getArgument(4);
            cb.completed(inv.getArgument(0));
            return null;
        }).when(tls).upgrade(any(TransportSecurityLayer.class), any(), any(), any(), any());

        final ProxyTlsAsyncConnectionOperator op =
                new ProxyTlsAsyncConnectionOperator(registryWith(tls));

        final HttpHost target = new HttpHost("https", "example.com", 443);

        assertDoesNotThrow(() ->
                op.upgrade(conn, target, null, null,
                        HttpCoreContext.create(), userCb));

        /* proxy already over TLS → only one inner-handshake call */
        verify(tls, times(1))
                .upgrade(any(TransportSecurityLayer.class), eq(target), any(), any(), any());
        verify(userCb).completed(conn);
    }

}
