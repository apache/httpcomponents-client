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

import java.net.InetSocketAddress;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * How to send a request via SOCKS proxy.
 *
 * @since 4.1
 */
public class ClientExecuteSOCKS {

    public static void main(final String[] args)throws Exception {
        final InetSocketAddress socksaddr = new InetSocketAddress("localhost", 1080);
        final PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSocksProxyAddress(socksaddr)
                        .build())
                .build();
        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            final HttpHost target = new HttpHost("http", "httpbin.org", 80);
            final HttpGet request = new HttpGet("/get");

            System.out.println("Executing request " + request.getMethod() + " " + request.getUri() +
                    " via SOCKS proxy " + socksaddr);
            httpclient.execute(target, request, response -> {
                System.out.println("----------------------------------------");
                System.out.println(request + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
                return null;
            });
        }
    }

}
