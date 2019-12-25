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

import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.DigestScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * An example of how HttpClient can authenticate multiple requests
 * using the same Digest scheme. After the initial request / response exchange
 * all subsequent requests sharing the same execution context can re-use
 * the last Digest nonce value to authenticate with the server.
 */
public class ClientPreemptiveDigestAuthentication {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {

            final HttpHost target = new HttpHost("http", "httpbin.org", 80);

            final HttpClientContext localContext = HttpClientContext.create();
            final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(target),
                    new UsernamePasswordCredentials("user", "passwd".toCharArray()));
            localContext.setCredentialsProvider(credentialsProvider);

            final HttpGet httpget = new HttpGet("http://httpbin.org/digest-auth/auth/user/passwd");

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());
            for (int i = 0; i < 3; i++) {
                try (final CloseableHttpResponse response = httpclient.execute(target, httpget, localContext)) {
                    System.out.println("----------------------------------------");
                    System.out.println(response.getCode() + " " + response.getReasonPhrase());
                    EntityUtils.consume(response.getEntity());

                    final AuthExchange authExchange = localContext.getAuthExchange(target);
                    if (authExchange != null) {
                        final AuthScheme authScheme = authExchange.getAuthScheme();
                        if (authScheme instanceof DigestScheme) {
                            final DigestScheme digestScheme = (DigestScheme) authScheme;
                            System.out.println("Nonce: " + digestScheme.getNonce() +
                                    "; count: " + digestScheme.getNounceCount());
                        }
                    }
                }
            }
        }
    }

}
