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
import org.apache.hc.client5.http.config.RedirectMethodPolicy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;

/**
 * Demonstrates how to control 301/302 redirect method rewriting in the
 * <b>async</b> client using {@link RedirectMethodPolicy}.
 * <p>
 * The example executes the same JSON POST twice against a 301 redirecting URL:
 * once with the default browser-compatible policy (resulting in a GET without body),
 * and once with {@link RedirectMethodPolicy#PRESERVE_METHOD} (resulting in a POST with body).
 * </p>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>When preserving the method, the {@code AsyncEntityProducer} must be repeatable.</li>
 *   <li>303 is always followed with GET; 307/308 always preserve method/body.</li>
 *   <li>Redirect safety rules (e.g., stripping {@code Authorization} across authorities) still apply.</li>
 * </ul>
 *
 * <h3>How to run</h3>
 * <pre>{@code
 * $ mvn -q -DskipTests exec:java -Dexec.mainClass=org.apache.hc.client5.http.examples.AsyncClientRedirectPreserveMethod
 * }</pre>
 *
 * @see RequestConfig#setRedirectMethodPolicy(RedirectMethodPolicy)
 * @see RedirectMethodPolicy
 * @since 5.6
 */
public class AsyncClientRedirectPreserveMethod {

    private static String redirectUrl() {
        // httpbin: redirect to /anything with status 301
        return "https://httpbin.org/redirect-to?url=/anything&status_code=301";
    }

    private static void runOnce(
            final CloseableHttpAsyncClient client,
            final String label) throws Exception {

        final SimpleHttpRequest req = SimpleRequestBuilder.post(redirectUrl())
                .setBody("{\"hello\":\"world\"}", ContentType.APPLICATION_JSON)
                .build();

        System.out.println("\n[" + label + "] Executing " + req);
        final Future<SimpleHttpResponse> f = client.execute(
                SimpleRequestProducer.create(req),
                SimpleResponseConsumer.create(),
                new FutureCallback<SimpleHttpResponse>() {
                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        System.out.println("[" + label + "] " + new StatusLine(response));
                        final String body = response.getBodyText();
                        System.out.println(body != null ? body : "");
                    }

                    @Override
                    public void failed(final Exception ex) {
                        System.out.println("[" + label + "] failed: " + ex);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println("[" + label + "] cancelled");
                    }
                });
        f.get();
    }

    public static void main(final String[] args) throws Exception {
        final RequestConfig browserCompat = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setRedirectMethodPolicy(RedirectMethodPolicy.BROWSER_COMPAT)
                .build();

        final RequestConfig preserveMethod = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setRedirectMethodPolicy(RedirectMethodPolicy.PRESERVE_METHOD)
                .build();

        try (CloseableHttpAsyncClient clientDefault = HttpAsyncClients.custom()
                .setDefaultRequestConfig(browserCompat)
                .build();
             CloseableHttpAsyncClient clientPreserve = HttpAsyncClients.custom()
                     .setDefaultRequestConfig(preserveMethod)
                     .build()) {

            System.out.println("== Async client redirect demo ==");
            System.out.println("URL: " + redirectUrl());
            System.out.println("Sending POST with JSON body...\n");

            clientDefault.start();
            clientPreserve.start();

            runOnce(clientDefault, "Default (BROWSER_COMPAT: POST→GET)");
            runOnce(clientPreserve, "Opt-in (PRESERVE_METHOD: keep POST)");

            System.out.println("\nShutting down");
            clientDefault.close(CloseMode.GRACEFUL);
            clientPreserve.close(CloseMode.GRACEFUL);
        }
    }
}
