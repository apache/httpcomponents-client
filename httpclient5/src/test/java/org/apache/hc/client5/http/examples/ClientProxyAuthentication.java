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

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * A simple example that uses HttpClient to execute an HTTP request
 * over a secure connection tunneled through an authenticating proxy.
 */
public class ClientProxyAuthentication {

    public static void main(final String[] args) throws Exception {
        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope("localhost", 8888), "squid", "squid".toCharArray())
                .add(new AuthScope("httpbin.org", 80), "user", "passwd".toCharArray())
                .build();
        final HttpHost target = new HttpHost("http", "httpbin.org", 80);
        final HttpHost proxy = new HttpHost("localhost", 8888);
        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setProxy(proxy)
                .setDefaultCredentialsProvider(credsProvider)
                .build()) {

            final RequestConfig config = RequestConfig.custom()
                .build();
            final HttpGet httpget = new HttpGet("/basic-auth/user/passwd");
            httpget.setConfig(config);

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri() + " to " + target +
                    " via " + proxy);

            httpclient.execute(target, httpget, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
                return null;
            });
        }
    }
}
