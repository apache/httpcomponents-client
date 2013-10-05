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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * A simple example that uses HttpClient to execute an HTTP request
 * over a secure connection tunneled through an authenticating proxy.
 */
public class ClientProxyAuthentication {

    public static void main(String[] args) throws Exception {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope("localhost", 8080),
                new UsernamePasswordCredentials("username", "password"));
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider).build();
        try {
            HttpHost target = new HttpHost("www.verisign.com", 443, "https");
            HttpHost proxy = new HttpHost("localhost", 8080);

            RequestConfig config = RequestConfig.custom()
                .setProxy(proxy)
                .build();
            HttpGet httpget = new HttpGet("/");
            httpget.setConfig(config);

            System.out.println("Executing request " + httpget.getRequestLine() + " to " + target + " via " + proxy);

            CloseableHttpResponse response = httpclient.execute(target, httpget);
            try {
                System.out.println("----------------------------------------");
                System.out.println(response.getStatusLine());
                EntityUtils.consume(response.getEntity());
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
    }
}
