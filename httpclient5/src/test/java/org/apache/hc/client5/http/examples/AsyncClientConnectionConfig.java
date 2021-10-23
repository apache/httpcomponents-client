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

import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * This example demonstrates how to use connection configuration on a per-route or a per-host
 * basis.
 */
public class AsyncClientConnectionConfig {

    public static void main(final String[] args) throws Exception {
        final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                .setConnectionConfigResolver(route -> {
                    // Use different settings for all secure (TLS) connections
                    final HttpHost targetHost = route.getTargetHost();
                    if (route.isSecure()) {
                        return ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMinutes(2))
                                .setSocketTimeout(Timeout.ofMinutes(2))
                                .setValidateAfterInactivity(TimeValue.ofMinutes(1))
                                .setTimeToLive(TimeValue.ofHours(1))
                                .build();
                    } else {
                        return ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMinutes(1))
                                .setSocketTimeout(Timeout.ofMinutes(1))
                                .setValidateAfterInactivity(TimeValue.ofSeconds(15))
                                .setTimeToLive(TimeValue.ofMinutes(15))
                                .build();
                    }
                })
                .setTlsConfigResolver(host -> {
                    // Use different settings for specific hosts
                    if (host.getSchemeName().equalsIgnoreCase("httpbin.org")) {
                        return TlsConfig.custom()
                                .setSupportedProtocols(TLS.V_1_3)
                                .setHandshakeTimeout(Timeout.ofSeconds(10))
                                .build();
                    } else {
                        return TlsConfig.DEFAULT;
                    }
                })
                .build();
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .build()) {

            client.start();

            for (final URIScheme uriScheme : URIScheme.values()) {
                final SimpleHttpRequest request = SimpleRequestBuilder.get()
                        .setHttpHost(new HttpHost(uriScheme.id, "httpbin.org"))
                        .setPath("/headers")
                        .build();

                System.out.println("Executing request " + request);
                final Future<SimpleHttpResponse> future = client.execute(
                        SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(),
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse response) {
                                System.out.println(request + "->" + new StatusLine(response));
                                System.out.println(response.getBody());
                            }

                            @Override
                            public void failed(final Exception ex) {
                                System.out.println(request + "->" + ex);
                            }

                            @Override
                            public void cancelled() {
                                System.out.println(request + " cancelled");
                            }

                        });
                future.get();
            }

            System.out.println("Shutting down");
            client.close(CloseMode.GRACEFUL);
        }
    }

}
