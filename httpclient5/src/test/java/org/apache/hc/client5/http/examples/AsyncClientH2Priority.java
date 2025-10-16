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
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.priority.PriorityValue;

/**
 * Demonstrates sending the HTTP/2 {@code Priority} request header using per-request configuration.
 *
 * <p><strong>How it works</strong>:
 * <ul>
 *   <li>Call {@code enablePriorityHeader()} on the H2 client builder to register
 *       {@link org.apache.hc.client5.http.protocol.H2RequestPriority}.</li>
 *   <li>For each request, set a priority on the {@link RequestConfig} via
 *       {@link RequestConfig.Builder#setH2Priority(PriorityValue)} and attach it to the
 *       {@link HttpClientContext} passed to {@code execute}.</li>
 * </ul>
 *
 * <p><strong>Notes</strong>:
 * <ul>
 *   <li>If a {@code Priority} header is already present on the request, it is preserved.</li>
 *   <li>If the configured value encodes protocol defaults, the header is omitted.</li>
 *   <li>Applies to HTTP/2+ only; HTTP/1.1 requests are unaffected.</li>
 * </ul>
 *
 * @since 5.6
 */
@Experimental
public class AsyncClientH2Priority {

    public static void main(final String[] args) throws Exception {
        try (CloseableHttpAsyncClient client = H2AsyncClientBuilder.create()
                .setH2Config(H2Config.custom()
                        .setPushEnabled(false)
                        .build())
                .build()) {

            client.start();

            // --- Request 1: non-default priority -> header sent (e.g., "u=0, i")
            final HttpClientContext ctx1 = HttpClientContext.create();
            ctx1.setRequestConfig(RequestConfig.custom()
                    .setH2Priority(PriorityValue.of(0, true))
                    .build());

            final SimpleHttpRequest req1 = SimpleRequestBuilder.get("https://nghttp2.org/httpbin/headers").build();
            final Future<SimpleHttpResponse> f1 = client.execute(req1, ctx1, null);
            final SimpleHttpResponse r1 = f1.get();
            System.out.println("[/httpbin/headers] -> " + r1.getCode());
            System.out.println("Negotiated protocol (req1): " + ctx1.getProtocolVersion());
            System.out.println(r1.getBodyText());

            // --- Request 2: defaults -> header omitted
            final HttpClientContext ctx2 = HttpClientContext.create();
            ctx2.setRequestConfig(RequestConfig.custom()
                    .setH2Priority(PriorityValue.defaults())
                    .build());

            final SimpleHttpRequest req2 = SimpleRequestBuilder.get("https://nghttp2.org/httpbin/user-agent").build();
            final SimpleHttpResponse r2 = client.execute(req2, ctx2, null).get();
            System.out.println("[/httpbin/user-agent] -> " + r2.getCode());
            System.out.println("Negotiated protocol (req2): " + ctx2.getProtocolVersion());
            System.out.println(r2.getBodyText());

            // --- Request 3: user-provided header -> preserved (no overwrite)
            final HttpClientContext ctx3 = HttpClientContext.create();
            ctx3.setRequestConfig(RequestConfig.custom()
                    .setH2Priority(PriorityValue.of(5, false))
                    .build());
            final SimpleHttpRequest req3 = SimpleRequestBuilder.get("https://nghttp2.org/httpbin/headers").build();
            req3.addHeader("Priority", "u=2");
            final SimpleHttpResponse r3 = client.execute(req3, ctx3, null).get();
            System.out.println("[/httpbin/headers with user header] -> " + r3.getCode());
            System.out.println("Negotiated protocol (req3): " + ctx3.getProtocolVersion());
            System.out.println(r3.getBodyText());
        }
    }
}
