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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * Demonstrates enabling Virtual Threads for the classic {@link CloseableHttpClient}.
 * <p>
 * <strong>Requirements:</strong> run on JDK&nbsp;21 or newer. The feature is disabled by default;
 * you enable it via the builder. When enabled, the client performs the transport layer
 * (connection lease, send, receive, entity streaming) on Virtual Threads while preserving the
 * classic blocking API and error semantics.
 * </p>
 * <p>
 * Notes:
 * <ul>
 *   <li>You can choose a thread-name prefix (useful for diagnostics).</li>
 *   <li>For production, prefer reusing a single {@code CloseableHttpClient} and a connection pool.</li>
 * </ul>
 * </p>
 * @since 5.6
 */
public class ClientClassicWithVirtualThreads {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .useVirtualThreads()
                .virtualThreadNamePrefix("hc-vt-")
                .virtualThreadsRunHandler()
                .build()) {

            final HttpGet httpget = new HttpGet("http://httpbin.org/get");

            httpclient.execute(httpget, response -> {

                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // keep output minimal; comment out next line if you don't want body echoed
                            System.out.println(line);
                        }
                    } finally {
                        EntityUtils.consume(entity);
                    }
                }
                return null;
            });
        }
    }
}
