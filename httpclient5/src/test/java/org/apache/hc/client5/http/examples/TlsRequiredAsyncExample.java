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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;

/**
 * Demonstrates the "TLS-required connections" mode for the async client.
 *
 * <p>
 * When {@code TlsRequired(true)} is enabled, the async client rejects execution when the
 * computed {@code HttpRoute} is not secure. This prevents accidental cleartext connections
 * such as {@code http://...} and disables cleartext upgrade mechanisms that start without TLS.
 * </p>
 *
 * <p>
 * The example triggers a rejection using {@code http://example.com/} and validates the failure
 * by unwrapping {@link ExecutionException#getCause()} and checking for
 * {@link UnsupportedSchemeException}.
 * </p>
 *
 * @since 5.7
 */
public final class TlsRequiredAsyncExample {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setTlsRequired(true)
                .build()) {

            client.start();

            // 1) Must fail fast with UnsupportedSchemeException
            final SimpleHttpRequest http = SimpleRequestBuilder.get("http://example.com/").build();
            final Future<SimpleHttpResponse> httpFuture =
                    client.execute(http, HttpClientContext.create(), null);

            try {
                final SimpleHttpResponse response = httpFuture.get();
                System.out.println("UNEXPECTED: http:// executed with status " + response.getCode());
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                if (cause instanceof UnsupportedSchemeException) {
                    System.out.println("OK (expected): " + cause.getMessage());
                } else {
                    throw ex;
                }
            }

            // 2) Allowed (may still fail if network/DNS blocked)
            final SimpleHttpRequest https = SimpleRequestBuilder.get("https://example.com/").build();
            final Future<SimpleHttpResponse> httpsFuture =
                    client.execute(https, HttpClientContext.create(), null);

            try {
                final SimpleHttpResponse response = httpsFuture.get();
                System.out.println("HTTPS OK: status=" + response.getCode());
            } catch (final ExecutionException ex) {
                System.err.println("HTTPS failed (network/env): " + ex.getCause());
            }
        }
    }

}
