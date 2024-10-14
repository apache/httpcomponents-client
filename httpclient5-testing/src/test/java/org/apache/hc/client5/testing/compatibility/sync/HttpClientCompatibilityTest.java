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
package org.apache.hc.client5.testing.compatibility.sync;

import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.sync.HttpClientResource;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class HttpClientCompatibilityTest {

    private final HttpHost target;
    @RegisterExtension
    private final HttpClientResource clientResource;
    private final CredentialsStore credentialsProvider;

    public HttpClientCompatibilityTest(final HttpHost target, final HttpHost proxy, final Credentials proxyCreds) throws Exception {
        this.target = target;
        this.clientResource = new HttpClientResource();
        this.clientResource.configure(builder -> builder.setProxy(proxy));
        this.credentialsProvider = new BasicCredentialsProvider();
        if (proxy != null && proxyCreds != null) {
            this.addCredentials(new AuthScope(proxy), proxyCreds);
        }
    }

    CloseableHttpClient client() {
        return clientResource.client();
    }

    HttpClientContext context() {
        return ContextBuilder.create()
                .useCredentialsProvider(credentialsProvider)
                .build();
    }

    void addCredentials(final AuthScope authScope, final Credentials credentials) {
        credentialsProvider.setCredentials(authScope, credentials);
    }

    @Test
    void test_options_ping() throws Exception {
        final CloseableHttpClient client = client();
        final HttpClientContext context = context();
        final HttpOptions options = new HttpOptions("*");
        try (ClassicHttpResponse response = client.executeOpen(target, options, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    void test_get() throws Exception {
        final CloseableHttpClient client = client();
        final HttpClientContext context = context();
        final String[] requestUris = new String[] { "/111", "/222", "/333" };
        for (final String requestUri: requestUris) {
            final ClassicHttpRequest request = ClassicRequestBuilder.get(requestUri)
                    .build();
            try (ClassicHttpResponse response = client.executeOpen(target, request, context)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                EntityUtils.consume(response.getEntity());
            }
        }
    }

    @Test
    void test_get_connection_close() throws Exception {
        final CloseableHttpClient client = client();
        final HttpClientContext context = context();
        final String[] requestUris = new String[] { "/111", "/222", "/333" };
        for (final String requestUri: requestUris) {
            final ClassicHttpRequest request = ClassicRequestBuilder.get(requestUri)
                    .addHeader(HttpHeaders.CONNECTION, "close")
                    .build();
            try (ClassicHttpResponse response = client.executeOpen(target, request, context)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                EntityUtils.consume(response.getEntity());
            }
        }
    }

    @Test
    void test_wrong_target_auth_scope() throws Exception {
        addCredentials(
                new AuthScope("http", "otherhost", -1, "Restricted Files", null),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));

        final CloseableHttpClient client = client();
        final HttpClientContext context = context();

        final ClassicHttpRequest request = new HttpGet("/private/big-secret.txt");
        try (ClassicHttpResponse response = client.executeOpen(target, request, context)) {
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    void test_wrong_target_credentials() throws Exception {
        addCredentials(
                new AuthScope(target),
                new UsernamePasswordCredentials("testuser", "wrong password".toCharArray()));

        final CloseableHttpClient client = client();
        final HttpClientContext context = context();

        final ClassicHttpRequest request = new HttpGet("/private/big-secret.txt");
        try (ClassicHttpResponse response = client.executeOpen(target, request, context)) {
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    void test_correct_target_credentials() throws Exception {
        addCredentials(
                new AuthScope(target),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
        final CloseableHttpClient client = client();
        final HttpClientContext context = context();

        final ClassicHttpRequest request = new HttpGet("/private/big-secret.txt");
        try (ClassicHttpResponse response = client.executeOpen(target, request, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    void test_correct_target_credentials_no_keep_alive() throws Exception {
        addCredentials(
                new AuthScope(target),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
        final CloseableHttpClient client = client();
        final HttpClientContext context = context();

        final ClassicHttpRequest request = ClassicRequestBuilder.get("/private/big-secret.txt")
                .addHeader(HttpHeaders.CONNECTION, "close")
                .build();
        try (ClassicHttpResponse response = client.executeOpen(target, request, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
        }
    }

}
