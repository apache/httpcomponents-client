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

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link PoolingHttpClientConnectionManagerBuilder} tests.
 */
@SuppressWarnings({"boxing","static-access","resource"}) // test code
public class TestPoolingHttpClientConnectionManagerBuilder {

    @Mock
    private ManagedHttpClientConnection conn;
    @Mock
    private Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    @Mock
    private ConnectionSocketFactory plainSocketFactory;
    @Mock
    private ConnectionSocketFactory sslSocketFactory;
    @Mock
    private Socket socket;
    @Mock
    private SchemePortResolver schemePortResolver;
    @Mock
    private DnsResolver dnsResolver;
    @Mock
    private Future<PoolEntry<HttpRoute, ManagedHttpClientConnection>> future;
    @Mock
    private StrictConnPool<HttpRoute, ManagedHttpClientConnection> pool;

    private PoolingHttpClientConnectionManager mgr;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDefaultManager() throws Exception {
        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry = new PoolEntry<>(route, TimeValue.NEG_ONE_MILLISECOND);
        entry.assignConnection(conn);

        Mockito.when(future.isCancelled()).thenReturn(Boolean.FALSE);
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);

        Mockito.when(conn.isOpen()).thenReturn(true);
        Mockito.when(future.isCancelled()).thenReturn(false);
        Mockito.when(future.get(1, TimeUnit.SECONDS)).thenReturn(entry);
        Mockito.when(pool.lease(
                Mockito.eq(route),
                Mockito.eq(null),
                Mockito.<Timeout>any(),
                Mockito.<FutureCallback<PoolEntry<HttpRoute, ManagedHttpClientConnection>>>eq(null)))
                .thenReturn(future);

        mgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionSocketFactoryRegistry(socketFactoryRegistry)
                .setSchemePortResolver(schemePortResolver)
                .setDnsResolver(dnsResolver)
                .setManagedConnPool(pool)
                .build();

        final LeaseRequest connRequest1 = mgr.lease("some-id", route, null);
        final ConnectionEndpoint endpoint1 = connRequest1.get(Timeout.ofSeconds(1));
        Assert.assertNotNull(endpoint1);
        Assert.assertNotSame(conn, endpoint1);

        mgr.release(endpoint1, null, TimeValue.NEG_ONE_MILLISECOND);

        Mockito.verify(pool).release(entry, true);
    }

}
