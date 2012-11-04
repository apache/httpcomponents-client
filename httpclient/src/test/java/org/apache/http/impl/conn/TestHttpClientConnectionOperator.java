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

package org.apache.http.impl.conn;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpInetSocketAddress;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeLayeredSocketFactory;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHttpClientConnectionOperator {

    private DefaultClientConnection conn;
    private Socket socket;
    private SchemeSocketFactory plainSocketFactory;
    private SchemeLayeredSocketFactory sslSocketFactory;
    private SchemeRegistry schemeRegistry;
    private DnsResolver dnsResolver;
    private HttpClientConnectionOperator connectionOperator;

    @Before
    public void setup() throws Exception {
        conn = Mockito.mock(DefaultClientConnection.class);
        socket = Mockito.mock(Socket.class);
        plainSocketFactory = Mockito.mock(SchemeSocketFactory.class);
        sslSocketFactory = Mockito.mock(SchemeLayeredSocketFactory.class);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpParams>any())).thenReturn(socket);
        Mockito.when(sslSocketFactory.createSocket(Mockito.<HttpParams>any())).thenReturn(socket);
        dnsResolver = Mockito.mock(DnsResolver.class);

        Scheme http = new Scheme("http", 80, plainSocketFactory);
        Scheme https = new Scheme("https", 443, sslSocketFactory);
        schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(http);
        schemeRegistry.register(https);

        connectionOperator = new HttpClientConnectionOperator(schemeRegistry, dnsResolver);
    }

    @Test
    public void testConnect() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();
        HttpHost host = new HttpHost("somehost");
        InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        InetAddress ip1 = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        InetAddress ip2 = InetAddress.getByAddress(new byte[] {127, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.<Socket>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpParams>any())).thenReturn(socket);

        connectionOperator.connect(conn, host, local, context, params);

        Mockito.verify(plainSocketFactory).connectSocket(socket,
                new InetSocketAddress(ip1, 80),
                new InetSocketAddress(local, 0), params);
        Mockito.verify(conn).opening(socket, host);
        Mockito.verify(conn).openCompleted(false, params);
    }

    @Test(expected=ConnectTimeoutException.class)
    public void testConnectFailure() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();
        HttpHost host = new HttpHost("somehost");
        InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.<Socket>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpParams>any())).thenThrow(new ConnectTimeoutException());

        connectionOperator.connect(conn, host, local, context, params);
    }

    @Test
    public void testConnectFailover() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();
        HttpHost host = new HttpHost("somehost");
        InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.<Socket>any(),
                Mockito.eq(new HttpInetSocketAddress(host, ip1, 80)),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpParams>any())).thenThrow(new ConnectTimeoutException());
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.<Socket>any(),
                Mockito.eq(new HttpInetSocketAddress(host, ip2, 80)),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpParams>any())).thenReturn(socket);

        connectionOperator.connect(conn, host, local, context, params);

        Mockito.verify(plainSocketFactory).connectSocket(socket,
                new HttpInetSocketAddress(host, ip2, 80),
                new InetSocketAddress(local, 0), params);
        Mockito.verify(conn, Mockito.times(2)).opening(socket, host);
        Mockito.verify(conn).openCompleted(false, params);
    }

    @Test
    public void testUpgrade() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();
        HttpHost host = new HttpHost("somehost", -1, "https");

        Mockito.when(sslSocketFactory.createLayeredSocket(
                Mockito.<Socket>any(),
                Mockito.eq("somehost"),
                Mockito.eq(443),
                Mockito.<HttpParams>any())).thenReturn(socket);

        connectionOperator.upgrade(conn, host, context, params);

        Mockito.verify(conn).update(socket, host, false, params);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUpgradeNonLayeringScheme() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpParams params = new BasicHttpParams();
        HttpHost host = new HttpHost("somehost", -1, "http");

        connectionOperator.upgrade(conn, host, context, params);
    }

}
