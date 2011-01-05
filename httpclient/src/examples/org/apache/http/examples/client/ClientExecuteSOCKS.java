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

package org.apache.http.examples.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

/**
 * How to send a request via SOCKS proxy using {@link HttpClient}.
 *
 * @since 4.1
 */
public class ClientExecuteSOCKS {

    public static void main(String[] args)throws Exception {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            httpclient.getParams().setParameter("socks.host", "mysockshost");
            httpclient.getParams().setParameter("socks.port", 1234);
            httpclient.getConnectionManager().getSchemeRegistry().register(
                    new Scheme("http", 80, new MySchemeSocketFactory()));

            HttpHost target = new HttpHost("www.apache.org", 80, "http");
            HttpGet req = new HttpGet("/");

            System.out.println("executing request to " + target + " via SOCKS proxy");
            HttpResponse rsp = httpclient.execute(target, req);
            HttpEntity entity = rsp.getEntity();

            System.out.println("----------------------------------------");
            System.out.println(rsp.getStatusLine());
            Header[] headers = rsp.getAllHeaders();
            for (int i = 0; i<headers.length; i++) {
                System.out.println(headers[i]);
            }
            System.out.println("----------------------------------------");

            if (entity != null) {
                System.out.println(EntityUtils.toString(entity));
            }

        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
    }

    static class MySchemeSocketFactory implements SchemeSocketFactory {

        public Socket createSocket(final HttpParams params) throws IOException {
            if (params == null) {
                throw new IllegalArgumentException("HTTP parameters may not be null");
            }
            String proxyHost = (String) params.getParameter("socks.host");
            Integer proxyPort = (Integer) params.getParameter("socks.port");

            InetSocketAddress socksaddr = new InetSocketAddress(proxyHost, proxyPort);
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
            return new Socket(proxy);
        }

        public Socket connectSocket(
                final Socket socket,
                final InetSocketAddress remoteAddress,
                final InetSocketAddress localAddress,
                final HttpParams params)
                    throws IOException, UnknownHostException, ConnectTimeoutException {
            if (remoteAddress == null) {
                throw new IllegalArgumentException("Remote address may not be null");
            }
            if (params == null) {
                throw new IllegalArgumentException("HTTP parameters may not be null");
            }
            Socket sock;
            if (socket != null) {
                sock = socket;
            } else {
                sock = createSocket(params);
            }
            if (localAddress != null) {
                sock.setReuseAddress(HttpConnectionParams.getSoReuseaddr(params));
                sock.bind(localAddress);
            }
            int timeout = HttpConnectionParams.getConnectionTimeout(params);
            try {
                sock.connect(remoteAddress, timeout);
            } catch (SocketTimeoutException ex) {
                throw new ConnectTimeoutException("Connect to " + remoteAddress.getHostName() + "/"
                        + remoteAddress.getAddress() + " timed out");
            }
            return sock;
        }

        public boolean isSecure(final Socket sock) throws IllegalArgumentException {
            return false;
        }

    }

}