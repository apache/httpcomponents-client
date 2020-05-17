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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

/**
 * How to send a request via SOCKS proxy set with system properties.
 *
 * Usage will require both setting the system properties and forcing HttpClient to use the system properties.
 * It will replace the configured connection manager with a new SOCKS connection manager with configuration based on
 * the configuration of the HttpClient. This means that it is not possible to use your own connection manager when using
 * SOCKS.
 *
 * If either one of the required triggers (SOCKS properties or useSystemProperties) are missing, then the HttpClient
 * just runs with the configured connection manager, if any.
 *
 * @since 4.1
 */
public class ClientExecuteSOCKSFromProperties {

    public static void main(String[] args)throws Exception {

        // Setting these properties will create a new connection manager in the HttpClient that uses this SOCKS address.
        System.setProperty("socksProxyHost", "localhost");
        System.setProperty("socksProxyPort", "6667");

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .useSystemProperties() // this will trigger the usage of the SOCKS properties
                .build();

        try {

            HttpHost target = new HttpHost("httpbin.org", 80, "http");
            HttpGet request = new HttpGet("/");

            System.out.println("Executing request " + request + " to " + target + " via SOCKS proxy from properties");
            CloseableHttpResponse response = httpclient.execute(target, request);
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
