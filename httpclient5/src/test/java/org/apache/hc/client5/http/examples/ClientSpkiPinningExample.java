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

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SpkiPinningClientTlsStrategy;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * Example: using SPKI pinning with the classic (blocking) client.
 *
 * <p><strong>Warning:</strong> Replace the pins with real values for your hosts and always ship a
 * backup pin. Keep PKI + hostname verification enabled.</p>
 */
public class ClientSpkiPinningExample {

    public static void main(final String[] args) throws Exception {
        final SSLContext sslContext = SSLContexts.createDefault();

        final SpkiPinningClientTlsStrategy pinning = SpkiPinningClientTlsStrategy
                .newBuilder(sslContext)
                // Replace with real host(s) and real pins (sha256/<base64(SPKI)>)
                .add("example.com",
                        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // backup
                .add("*.example.net",
                        "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
                .build();

        final PoolingHttpClientConnectionManager cm =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setTlsSocketStrategy(pinning) // classic path
                        .build();

        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            final HttpGet httpget = new HttpGet("https://example.com/");
            System.out.println("Executing: " + httpget.getMethod() + " " + httpget.getUri());

            httpclient.execute(httpget, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + " -> " + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
                return null;
            });
        }
    }
}
