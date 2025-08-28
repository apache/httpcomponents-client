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

import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Timeout;

public class ClassicClientCallTimeoutExample {

    public static void main(final String[] args) throws Exception {

        // Non-deprecated: set connect/socket timeouts via ConnectionConfig
        final PoolingHttpClientConnectionManager cm =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(
                                ConnectionConfig.custom()
                                        .setConnectTimeout(Timeout.ofSeconds(10))
                                        .setSocketTimeout(Timeout.ofSeconds(10))
                                        .build())
                        .build();

        try (final CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            // ---- Expected TIMEOUT (hard call deadline) ----
            final HttpGet slow = new HttpGet("https://httpbin.org/delay/5");
            slow.setConfig(RequestConfig.custom()
                    .setRequestTimeout(Timeout.ofSeconds(2))      // hard end-to-end cap
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3)) // don't hang on pool lease
                    .build());

            final HttpClientResponseHandler<String> handler = (ClassicHttpResponse response) -> {
                return response.getCode() + " " + response.getReasonPhrase();
            };

            System.out.println("Executing (expected timeout): " + slow.getPath());
            try {
                client.execute(slow, handler); // will throw by design
                System.out.println("UNEXPECTED: completed");
            } catch (final IOException ex) {
                System.out.println("As expected: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }

            // ---- Expected SUCCESS within budget (use HTTP to avoid TLS variance) ----
            final HttpGet fast = new HttpGet("http://httpbin.org/delay/1"); // HTTP on purpose
            fast.setConfig(RequestConfig.custom()
                    .setRequestTimeout(Timeout.ofSeconds(8))          // generous end-to-end budget
                    .setConnectionRequestTimeout(Timeout.ofSeconds(2)) // quick fail if pool stuck
                    .build());

            System.out.println("Executing (expected success): " + fast.getPath());
            final String ok = client.execute(fast, handler);
            System.out.println("OK: " + ok);
        }
    }
}
