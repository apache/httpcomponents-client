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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.Timeout;


/**
 * Demonstrates the usage of the {@link PoolingHttpClientConnectionManager} to perform a warm-up
 * of connections synchronously and execute an HTTP request using the Apache HttpClient 5 sync API.
 *
 * <p>The warm-up process initializes a specified number of connections to a target host,
 * ensuring they are ready for use before actual requests are made. The example then performs an
 * HTTP GET request to a target server and logs the response details.</p>
 *
 * <p>Key steps include:</p>
 * <ul>
 *     <li>Creating a {@link PoolingHttpClientConnectionManager} instance with TLS configuration.</li>
 *     <li>Calling {@link PoolingHttpClientConnectionManager#warmUp(HttpHost, Timeout)} to prepare
 *     the connection pool for the specified target host.</li>
 *     <li>Executing an HTTP GET request using {@link CloseableHttpClient}.</li>
 *     <li>Handling the HTTP response and logging protocol, SSL details, and response body.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * java WarmUpTest
 * </pre>
 *
 * <p>Dependencies: Ensure the required Apache HttpClient libraries are on the classpath.</p>
 *
 * @since 5.5
 */
public class WarmUpTest {

    public static void main(final String[] args) throws Exception {

        // Target host for warm-up and execution
        final HttpHost targetHost = new HttpHost("http", "httpbin.org", 80);
        final Timeout timeout = Timeout.ofSeconds(10);

        // Create a connection manager with warm-up support
        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(10);
        connectionManager.setDefaultMaxPerRoute(5);

        // Warm up connections to the target host
        System.out.println("Warming up connections...");
        connectionManager.warmUp(targetHost, timeout);
        System.out.println("Warm-up completed successfully.");

        // Create an HttpClient using the warmed-up connection manager
        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build()) {

            // Define the HTTP GET request
            final HttpGet httpget = new HttpGet("http://httpbin.org/get");
            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

            // Execute the request in a loop
            for (int i = 0; i < 3; i++) {
                httpclient.execute(httpget, response -> {
                    System.out.println("----------------------------------------");
                    System.out.println(httpget + " -> " + new StatusLine(response));
                    final String content = EntityUtils.toString(response.getEntity());
                    System.out.println("Response content: " + content);
                    return null;
                });
            }
        }
    }
}
