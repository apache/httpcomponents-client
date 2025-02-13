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

import static org.junit.Assume.assumeNotNull;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import javax.security.auth.Subject;

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
import org.apache.hc.client5.testing.Result;
import org.apache.hc.client5.testing.compatibility.spnego.SpnegoAuthenticationStrategy;
import org.apache.hc.client5.testing.compatibility.spnego.SpnegoTestUtil;
import org.apache.hc.client5.testing.compatibility.spnego.UseJaasCredentials;
import org.apache.hc.client5.testing.extension.async.HttpAsyncClientResource;
import org.apache.hc.client5.testing.util.SecurityUtils;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class HttpAsyncClientCompatibilityTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);
    static final Timeout LONG_TIMEOUT = Timeout.ofSeconds(30);

    private final HttpVersionPolicy versionPolicy;
    private final HttpHost target;
    @RegisterExtension
    private final HttpAsyncClientResource clientResource;
    private final HttpAsyncClientResource spnegoClientResource;
    private final BasicCredentialsProvider credentialsProvider;
    protected final Subject spnegoSubject;

    public HttpAsyncClientCompatibilityTest(
            final HttpVersionPolicy versionPolicy,
            final HttpHost target,
            final HttpHost proxy,
            final Credentials proxyCreds) throws Exception {
        this(versionPolicy, target, proxy, proxyCreds, null);
    }

    public HttpAsyncClientCompatibilityTest(
            final HttpVersionPolicy versionPolicy,
            final HttpHost target,
            final HttpHost proxy,
            final Credentials proxyCreds,
            final Subject spnegoSubject) throws Exception {
        this.versionPolicy = versionPolicy;
        this.target = target;
        this.clientResource = new HttpAsyncClientResource(versionPolicy);
        this.spnegoClientResource = new HttpAsyncClientResource(versionPolicy);
        this.clientResource.configure(builder -> builder.setProxy(proxy));
        this.spnegoClientResource.configure(builder -> builder.setProxy(proxy).setTargetAuthenticationStrategy(new SpnegoAuthenticationStrategy()).setDefaultAuthSchemeRegistry(SpnegoTestUtil.getSpnegoSchemeRegistry()));
        this.credentialsProvider = new BasicCredentialsProvider();
        if (proxy != null && proxyCreds != null) {
            this.credentialsProvider.setCredentials(new AuthScope(proxy), proxyCreds);
        }
        this.spnegoSubject = spnegoSubject;
    }

    CloseableHttpAsyncClient client() {
        return clientResource.client();
    }

    CloseableHttpAsyncClient spnegoClient() {
        return spnegoClientResource.client();
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
    void test_concurrent_gets() throws Exception {
        final CloseableHttpAsyncClient client = client();

        final String[] requestUris = new String[] {"/111", "/222", "/333"};
        final int n = 200;
        final Queue<Result<Void>> queue = new ConcurrentLinkedQueue<>();
        final CountDownLatch latch = new CountDownLatch(requestUris.length * n);

        for (int i = 0; i < n; i++) {
            for (final String requestUri: requestUris) {
                final SimpleHttpRequest request = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath(requestUri)
                        .build();
                final HttpClientContext context = context();
                client.execute(request, context, new FutureCallback<SimpleHttpResponse>() {

                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        queue.add(new Result<>(request, response, null));
                        latch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        queue.add(new Result<>(request, ex));
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        queue.add(new Result<>(request, new RequestNotExecutedException()));
                        latch.countDown();
                    }

                });
            }
        }
        Assertions.assertTrue(latch.await(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit()));
        Assertions.assertEquals(requestUris.length * n, queue.size());
        for (final Result<Void> result : queue) {
            if (result.isOK()) {
                Assertions.assertEquals(HttpStatus.SC_OK, result.response.getCode());
            }
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

    // This does not work.
    // Looks like by the time the SPNEGO negotiations happens, we're in another thread,
    // and Subject is no longer set. We could save the subject somewhere, or just document this.
    @Disabled
    @Test
    void test_spnego_auth_success_implicit() throws Exception {
        assumeNotNull(spnegoSubject);
        addCredentials(
                new AuthScope(target),
                new UseJaasCredentials());
        final CloseableHttpAsyncClient client = spnegoClient();
        final HttpClientContext context = context();
        final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/private_spnego/big-secret.txt")
                .build();

        final Future<SimpleHttpResponse> future = SecurityUtils.callAs(spnegoSubject, new Callable<Future<SimpleHttpResponse>>() {
            @Override
            public Future<SimpleHttpResponse> call() throws Exception {
                return client.execute(httpGetSecret, context, null);
            }
        });

        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        assertProtocolVersion(context);
    }

    @Test
    void test_spnego_auth_success() throws Exception {
        assumeNotNull(spnegoSubject);
        addCredentials(
                new AuthScope(target),
                SpnegoTestUtil.createCredentials(spnegoSubject));
        final CloseableHttpAsyncClient client = spnegoClient();
        final HttpClientContext context = context();
        final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/private_spnego/big-secret.txt")
                .build();

        final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);

        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        assertProtocolVersion(context);
    }
}
