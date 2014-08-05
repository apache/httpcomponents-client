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

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.http.HttpHost;
import org.apache.http.config.Lookup;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestHttpClientConnectionOperator {

    private ManagedHttpClientConnection conn;
    private Socket socket;
    private ConnectionSocketFactory plainSocketFactory;
    private LayeredConnectionSocketFactory sslSocketFactory;
    private Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    private SchemePortResolver schemePortResolver;
    private DnsResolver dnsResolver;
    private DefaultHttpClientConnectionOperator connectionOperator;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        conn = Mockito.mock(ManagedHttpClientConnection.class);
        socket = Mockito.mock(Socket.class);
        plainSocketFactory = Mockito.mock(ConnectionSocketFactory.class);
        sslSocketFactory = Mockito.mock(LayeredConnectionSocketFactory.class);
        socketFactoryRegistry = Mockito.mock(Lookup.class);
        schemePortResolver = Mockito.mock(SchemePortResolver.class);
        dnsResolver = Mockito.mock(DnsResolver.class);
        connectionOperator = new DefaultHttpClientConnectionOperator(
                socketFactoryRegistry, schemePortResolver, dnsResolver);
    }

    @Test
    public void testConnect() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {127, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(host)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.<Socket>any(),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(socket);

        final SocketConfig socketConfig = SocketConfig.custom()
            .setSoKeepAlive(true)
            .setSoReuseAddress(true)
            .setSoTimeout(5000)
            .setTcpNoDelay(true)
            .setSoLinger(50)
            .build();
        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        connectionOperator.connect(conn, host, localAddress, 1000, socketConfig, context);

        Mockito.verify(socket).setKeepAlive(true);
        Mockito.verify(socket).setReuseAddress(true);
        Mockito.verify(socket).setSoTimeout(5000);
        Mockito.verify(socket).setSoLinger(true, 50);
        Mockito.verify(socket).setTcpNoDelay(true);

        Mockito.verify(plainSocketFactory).connectSocket(
                1000,
                socket,
                host,
                new InetSocketAddress(ip1, 80),
                localAddress,
                context);
        Mockito.verify(conn, Mockito.times(2)).bind(socket);
    }

    @Test(expected=ConnectTimeoutException.class)
    public void testConnectTimeout() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(host)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.<Socket>any(),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenThrow(new SocketTimeoutException());

        connectionOperator.connect(conn, host, null, 1000, SocketConfig.DEFAULT, context);
    }

    @Test(expected=HttpHostConnectException.class)
    public void testConnectFailure() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(host)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.<Socket>any(),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenThrow(new ConnectException());

        connectionOperator.connect(conn, host, null, 1000, SocketConfig.DEFAULT, context);
    }

    @Test
    public void testConnectFailover() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost");
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetAddress ip1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        final InetAddress ip2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});

        Mockito.when(dnsResolver.resolve("somehost")).thenReturn(new InetAddress[] { ip1, ip2 });
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(host)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.<Socket>any(),
                Mockito.<HttpHost>any(),
                Mockito.eq(new InetSocketAddress(ip1, 80)),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenThrow(new ConnectException());
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.<Socket>any(),
                Mockito.<HttpHost>any(),
                Mockito.eq(new InetSocketAddress(ip2, 80)),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(socket);

        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        connectionOperator.connect(conn, host, localAddress, 1000, SocketConfig.DEFAULT, context);

        Mockito.verify(plainSocketFactory).connectSocket(
                1000,
                socket,
                host,
                new InetSocketAddress(ip2, 80),
                localAddress,
                context);
        Mockito.verify(conn, Mockito.times(3)).bind(socket);
    }

    @Test
    public void testConnectExplicitAddress() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 0});
        final InetAddress ip = InetAddress.getByAddress(new byte[] {127, 0, 0, 23});
        final HttpHost host = new HttpHost(ip);

        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);
        Mockito.when(schemePortResolver.resolve(host)).thenReturn(80);
        Mockito.when(plainSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(plainSocketFactory.connectSocket(
                Mockito.anyInt(),
                Mockito.<Socket>any(),
                Mockito.<HttpHost>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<InetSocketAddress>any(),
                Mockito.<HttpContext>any())).thenReturn(socket);

        final InetSocketAddress localAddress = new InetSocketAddress(local, 0);
        connectionOperator.connect(conn, host, localAddress, 1000, SocketConfig.DEFAULT, context);

        Mockito.verify(plainSocketFactory).connectSocket(
                1000,
                socket,
                host,
                new InetSocketAddress(ip, 80),
                localAddress,
                context);
        Mockito.verify(dnsResolver, Mockito.never()).resolve(Mockito.anyString());
        Mockito.verify(conn, Mockito.times(2)).bind(socket);
    }

    @Test
    public void testUpgrade() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost", -1, "https");

        Mockito.when(socketFactoryRegistry.lookup("https")).thenReturn(sslSocketFactory);
        Mockito.when(schemePortResolver.resolve(host)).thenReturn(443);
        Mockito.when(sslSocketFactory.createSocket(Mockito.<HttpContext>any())).thenReturn(socket);
        Mockito.when(sslSocketFactory.createLayeredSocket(
                Mockito.<Socket>any(),
                Mockito.eq("somehost"),
                Mockito.eq(443),
                Mockito.<HttpContext>any())).thenReturn(socket);

        connectionOperator.upgrade(conn, host, context);

        Mockito.verify(conn).bind(socket);
    }

    @Test(expected=UnsupportedSchemeException.class)
    public void testUpgradeUpsupportedScheme() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost", -1, "httpsssss");
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);

        connectionOperator.upgrade(conn, host, context);
    }

    @Test(expected=UnsupportedSchemeException.class)
    public void testUpgradeNonLayeringScheme() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpHost host = new HttpHost("somehost", -1, "http");
        Mockito.when(socketFactoryRegistry.lookup("http")).thenReturn(plainSocketFactory);

        connectionOperator.upgrade(conn, host, context);
    }

}
