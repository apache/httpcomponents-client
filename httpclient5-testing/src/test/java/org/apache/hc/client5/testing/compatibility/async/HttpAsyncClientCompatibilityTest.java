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
package org.apache.hc.client5.testing.compatibility.async;

import java.util.concurrent.Future;

import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.async.HttpAsyncClientResource;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class HttpAsyncClientCompatibilityTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpVersionPolicy versionPolicy;
    private final HttpHost target;
    @RegisterExtension
    private final HttpAsyncClientResource clientResource;
    private final BasicCredentialsProvider credentialsProvider;

    public HttpAsyncClientCompatibilityTest(
            final HttpVersionPolicy versionPolicy,
            final HttpHost target,
            final HttpHost proxy,
            final Credentials proxyCreds) throws Exception {
        this.versionPolicy = versionPolicy;
        this.target = target;
        this.clientResource = new HttpAsyncClientResource(versionPolicy);
        this.clientResource.configure(builder -> builder.setProxy(proxy));
        this.credentialsProvider = new BasicCredentialsProvider();
        if (proxy != null && proxyCreds != null) {
            this.credentialsProvider.setCredentials(new AuthScope(proxy), proxyCreds);
        }
    }

    CloseableHttpAsyncClient client() {
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

    void assertProtocolVersion(final HttpClientContext context) {
        switch (versionPolicy) {
            case FORCE_HTTP_1:
                Assertions.assertEquals(HttpVersion.HTTP_1_1, context.getProtocolVersion());
                break;
            case FORCE_HTTP_2:
            case NEGOTIATE:
                Assertions.assertEquals(HttpVersion.HTTP_2, context.getProtocolVersion());
                break;
            default:
                throw new IllegalStateException("Unexpected version policy: " + versionPolicy);
        }
    }

    @Test
    void test_sequential_gets() throws Exception {
        final CloseableHttpAsyncClient client = client();
        final HttpClientContext context = context();

        final String[] requestUris = new String[] {"/111", "/222", "/333"};
        for (final String requestUri: requestUris) {
            final SimpleHttpRequest httpGet = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(requestUri)
                    .build();
            final Future<SimpleHttpResponse> future = client.execute(httpGet, context, null);
            final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            assertProtocolVersion(context);
        }
    }

    @Test
    void test_auth_failure_wrong_auth_scope() throws Exception {
        addCredentials(
                new AuthScope("http", "otherhost", -1, "Restricted Files", null),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
        final CloseableHttpAsyncClient client = client();
        final HttpClientContext context = context();

        final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/private/big-secret.txt")
                .build();
        final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        assertProtocolVersion(context);
    }

    @Test
    void test_auth_failure_wrong_auth_credentials() throws Exception {
        addCredentials(
                new AuthScope(target),
                new UsernamePasswordCredentials("testuser", "wrong password".toCharArray()));
        final CloseableHttpAsyncClient client = client();
        final HttpClientContext context = context();

        final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/private/big-secret.txt")
                .build();
        final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        assertProtocolVersion(context);
    }

    @Test
    void test_auth_success() throws Exception {
        addCredentials(
                new AuthScope(target),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
        final CloseableHttpAsyncClient client = client();
        final HttpClientContext context = context();

        final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/private/big-secret.txt")
                .build();
        final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        assertProtocolVersion(context);
    }

}
