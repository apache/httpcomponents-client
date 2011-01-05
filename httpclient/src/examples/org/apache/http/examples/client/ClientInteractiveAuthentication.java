/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.http.examples.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * A simple example that uses HttpClient to execute an HTTP request against
 * a target site that requires user authentication.
 */
public class ClientInteractiveAuthentication {

    public static void main(String[] args) throws Exception {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            // Create local execution context
            HttpContext localContext = new BasicHttpContext();

            HttpGet httpget = new HttpGet("http://localhost/test");

            boolean trying = true;
            while (trying) {
                System.out.println("executing request " + httpget.getRequestLine());
                HttpResponse response = httpclient.execute(httpget, localContext);

                System.out.println("----------------------------------------");
                System.out.println(response.getStatusLine());

                // Consume response content
                HttpEntity entity = response.getEntity();
                EntityUtils.consume(entity);

                int sc = response.getStatusLine().getStatusCode();

                AuthState authState = null;
                if (sc == HttpStatus.SC_UNAUTHORIZED) {
                    // Target host authentication required
                    authState = (AuthState) localContext.getAttribute(ClientContext.TARGET_AUTH_STATE);
                }
                if (sc == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                    // Proxy authentication required
                    authState = (AuthState) localContext.getAttribute(ClientContext.PROXY_AUTH_STATE);
                }

                if (authState != null) {
                    System.out.println("----------------------------------------");
                    AuthScope authScope = authState.getAuthScope();
                    System.out.println("Please provide credentials");
                    System.out.println(" Host: " + authScope.getHost() + ":" + authScope.getPort());
                    System.out.println(" Realm: " + authScope.getRealm());


                    BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

                    System.out.print("Enter username: ");
                    String user = console.readLine();
                    System.out.print("Enter password: ");
                    String password = console.readLine();

                    if (user != null && user.length() > 0) {
                        Credentials creds = new UsernamePasswordCredentials(user, password);
                        httpclient.getCredentialsProvider().setCredentials(authScope, creds);
                        trying = true;
                    } else {
                        trying = false;
                    }
                } else {
                    trying = false;
                }
            }

        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }
    }
}
