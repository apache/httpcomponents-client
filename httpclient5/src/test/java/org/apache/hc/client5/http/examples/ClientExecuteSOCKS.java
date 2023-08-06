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

package org.apache.hc.client5.http.examples;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * How to send a request via SOCKS proxy.
 *
 * @since 4.1
 */
public class ClientExecuteSOCKS {

    public static void main(final String[] args)throws Exception {
        final InetSocketAddress proxyAddress = new InetSocketAddress("mysockshost", 1234);
        final PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(new SocksProxySSLConnectionSocketFactory(proxyAddress))
                .build();


        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            final HttpGet request = new HttpGet("https://ifconfig.me/ip");

            System.out.println("Executing request " + request.getMethod() + " " + request.getUri() +
                    " via SOCKS proxy " + proxyAddress);
            httpclient.execute(request, response -> {
                System.out.println("----------------------------------------");
                System.out.println(request + "->" + new StatusLine(response));
                System.out.println("IP: " + EntityUtils.toString(response.getEntity()));
                return null;
            });
        }
    }

    private static class SocksProxySSLConnectionSocketFactory extends SSLConnectionSocketFactory {

        private final InetSocketAddress proxyAddress;

        public SocksProxySSLConnectionSocketFactory(InetSocketAddress proxyAddress) {
            super(SSLContexts.createDefault(), HttpsSupport.getDefaultHostnameVerifier());
            this.proxyAddress = proxyAddress;
        }

        @Override
        public Socket createSocket(HttpContext context) {
            return new Socket(new Proxy(Proxy.Type.SOCKS, proxyAddress));
        }
    }

}
