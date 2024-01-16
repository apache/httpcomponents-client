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

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;

/**
 * This example demonstrates how to make HttpClient negotiate or force a particular
 * version of HTTP protocol during the TLS handshake.
 * <p/>
 * Please note that protocol version policy setting also applies to non-HTTPS
 * connections.
 */
public class AsyncClientHttpVersionPolicy {

    public static void main(final String[] args) throws Exception {
        final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                .setTlsConfigResolver(httphost -> TlsConfig.custom()
//                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
//                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                        .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                        .build())
                .build();
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .build()) {

            client.start();

            final HttpHost target = new HttpHost("https", "httpbin.org");
            final HttpClientContext clientContext = HttpClientContext.create();

            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            System.out.println("Executing request " + request);
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    clientContext,
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(request + "->" + new StatusLine(response));
                            System.out.println("HTTP protocol " + clientContext.getProtocolVersion());
                            final SSLSession sslSession = clientContext.getSSLSession();
                            if (sslSession != null) {
                                System.out.println("SSL protocol " + sslSession.getProtocol());
                                System.out.println("SSL cipher suite " + sslSession.getCipherSuite());
                            }
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

            System.out.println("Shutting down");
            client.close(CloseMode.GRACEFUL);
        }
    }

}
