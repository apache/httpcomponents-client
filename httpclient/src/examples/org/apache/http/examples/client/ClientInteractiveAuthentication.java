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

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.RouteInfo;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * A simple example that uses HttpClient to execute an HTTP request against
 * a target site that requires user authentication.
 */
public class ClientInteractiveAuthentication {

    public static void main(String[] args) throws Exception {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider).build();
        try {
            // Create local execution context
            HttpClientContext localContext = HttpClientContext.create();

            HttpGet httpget = new HttpGet("http://localhost/test");

            boolean trying = true;
            while (trying) {
                System.out.println("executing request " + httpget.getRequestLine());
                CloseableHttpResponse response = httpclient.execute(httpget, localContext);
                try {
                    System.out.println("----------------------------------------");
                    System.out.println(response.getStatusLine());

                    // Consume response content
                    HttpEntity entity = response.getEntity();
                    EntityUtils.consume(entity);

                    int sc = response.getStatusLine().getStatusCode();

                    RouteInfo route = localContext.getHttpRoute();
                    AuthState authState = null;
                    HttpHost authhost = null;
                    if (sc == HttpStatus.SC_UNAUTHORIZED) {
                        // Target host authentication required
                        authState = localContext.getTargetAuthState();
                        authhost = route.getTargetHost();
                    }
                    if (sc == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                        // Proxy authentication required
                        authState = localContext.getProxyAuthState();
                        authhost = route.getProxyHost();
                    }

                    if (authState != null) {
                        System.out.println("----------------------------------------");
                        AuthScheme authscheme = authState.getAuthScheme();
                        System.out.println("Please provide credentials for " +
                                authscheme.getRealm() + "@" + authhost.toHostString());

                        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

                        System.out.print("Enter username: ");
                        String user = console.readLine();
                        System.out.print("Enter password: ");
                        String password = console.readLine();

                        if (user != null && user.length() > 0) {
                            Credentials creds = new UsernamePasswordCredentials(user, password);
                            credsProvider.setCredentials(new AuthScope(authhost), creds);
                            trying = true;
                        } else {
                            trying = false;
                        }
                    } else {
                        trying = false;
                    }
                } finally {
                    response.close();
                }
            }
        } finally {
            httpclient.close();
        }
    }
}
