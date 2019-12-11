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
package org.apache.hc.client5.testing.external;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class HttpAsyncClientCompatibilityTest {

    public static void main(final String... args) throws Exception {
        final HttpAsyncClientCompatibilityTest[] tests = new HttpAsyncClientCompatibilityTest[] {
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1,
                        new HttpHost("http", "localhost", 8080), null, null),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1,
                        new HttpHost("http", "test-httpd", 8080), new HttpHost("localhost", 8888), null),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1,
                        new HttpHost("http", "test-httpd", 8080), new HttpHost("localhost", 8889),
                        new UsernamePasswordCredentials("squid", "nopassword".toCharArray())),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1,
                        new HttpHost("https", "localhost", 8443), null, null),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1,
                        new HttpHost("https", "test-httpd", 8443), new HttpHost("localhost", 8888), null),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1,
                        new HttpHost("https", "test-httpd", 8443), new HttpHost("localhost", 8889),
                        new UsernamePasswordCredentials("squid", "nopassword".toCharArray())),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_2_0,
                        new HttpHost("http", "localhost", 8080), null, null),
                new HttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_2_0,
                        new HttpHost("https", "localhost", 8443), null, null)
        };
        for (final HttpAsyncClientCompatibilityTest test: tests) {
            try {
                test.execute();
            } finally {
                test.shutdown();
            }
        }
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpVersion protocolVersion;
    private final HttpHost target;
    private final HttpHost proxy;
    private final BasicCredentialsProvider credentialsProvider;
    private final PoolingAsyncClientConnectionManager connManager;
    private final CloseableHttpAsyncClient client;

    HttpAsyncClientCompatibilityTest(
            final HttpVersion protocolVersion,
            final HttpHost target,
            final HttpHost proxy,
            final Credentials proxyCreds) throws Exception {
        this.protocolVersion = protocolVersion;
        this.target = target;
        this.proxy = proxy;
        this.credentialsProvider = new BasicCredentialsProvider();
        final RequestConfig requestConfig = RequestConfig.custom()
                .setProxy(proxy)
                .build();
        if (proxy != null && proxyCreds != null) {
            this.credentialsProvider.setCredentials(new AuthScope(proxy), proxyCreds);
        }
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(getClass().getResource("/test-ca.keystore"), "nopassword".toCharArray()).build();
        this.connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setTlsStrategy(new DefaultClientTlsStrategy(sslContext))
                .build();
        this.client = HttpAsyncClients.custom()
                .setVersionPolicy(this.protocolVersion == HttpVersion.HTTP_2 ? HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.FORCE_HTTP_1)
                .setConnectionManager(this.connManager)
                .setProxy(this.proxy)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    void shutdown() throws Exception {
        client.close();
    }

    enum TestResult {OK, NOK}

    private void logResult(final TestResult result, final HttpRequest request, final String message) {
        final StringBuilder buf = new StringBuilder();
        buf.append(result);
        if (buf.length() == 2) {
            buf.append(" ");
        }
        buf.append(": ").append(protocolVersion).append(" ").append(target);
        if (proxy != null) {
            buf.append(" via ").append(proxy);
        }
        buf.append(": ");
        buf.append(request.getMethod()).append(" ").append(request.getRequestUri());
        if (message != null && !TextUtils.isBlank(message)) {
            buf.append(" -> ").append(message);
        }
        System.out.println(buf.toString());
    }

    void execute() throws Exception {

        client.start();
        // Initial ping
        {
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final SimpleHttpRequest options = SimpleHttpRequests.OPTIONS.create(target, "*");
            final Future<SimpleHttpResponse> future = client.execute(options, context, null);
            try {
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final int code = response.getCode();
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, options, Objects.toString(response.getFirstHeader("server")));
                } else {
                    logResult(TestResult.NOK, options, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, options, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, options, "(time out)");
            }
        }
        // Basic GET requests
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final String[] requestUris = new String[] {"/", "/news.html", "/status.html"};
            for (final String requestUri: requestUris) {
                final SimpleHttpRequest httpGet = SimpleHttpRequests.GET.create(target, requestUri);
                final Future<SimpleHttpResponse> future = client.execute(httpGet, context, null);
                try {
                    final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                    final int code = response.getCode();
                    if (code == HttpStatus.SC_OK) {
                        logResult(TestResult.OK, httpGet, "200");
                    } else {
                        logResult(TestResult.NOK, httpGet, "(status " + code + ")");
                    }
                } catch (final ExecutionException ex) {
                    final Throwable cause = ex.getCause();
                    logResult(TestResult.NOK, httpGet, "(" + cause.getMessage() + ")");
                } catch (final TimeoutException ex) {
                    logResult(TestResult.NOK, httpGet, "(time out)");
                }
            }
        }
        // Wrong target auth scope
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            credentialsProvider.setCredentials(
                    new AuthScope("http", "otherhost", -1, "Restricted Files", null),
                    new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final SimpleHttpRequest httpGetSecret = SimpleHttpRequests.GET.create(target, "/private/big-secret.txt");
            final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
            try {
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final int code = response.getCode();
                if (code == HttpStatus.SC_UNAUTHORIZED) {
                    logResult(TestResult.OK, httpGetSecret, "401 (wrong target auth scope)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, httpGetSecret, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, httpGetSecret, "(time out)");
            }
        }
        // Wrong target credentials
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            credentialsProvider.setCredentials(
                    new AuthScope(target),
                    new UsernamePasswordCredentials("testuser", "wrong password".toCharArray()));
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final SimpleHttpRequest httpGetSecret = SimpleHttpRequests.GET.create(target, "/private/big-secret.txt");
            final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
            try {
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final int code = response.getCode();
                if (code == HttpStatus.SC_UNAUTHORIZED) {
                    logResult(TestResult.OK, httpGetSecret, "401 (wrong target creds)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, httpGetSecret, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, httpGetSecret, "(time out)");
            }
        }
        // Correct target credentials
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            credentialsProvider.setCredentials(
                    new AuthScope(target),
                    new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final SimpleHttpRequest httpGetSecret = SimpleHttpRequests.GET.create(target, "/private/big-secret.txt");
            final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
            try {
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final int code = response.getCode();
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, httpGetSecret, "200 (correct target creds)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, httpGetSecret, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, httpGetSecret, "(time out)");
            }
        }
        // Correct target credentials (no keep-alive)
        if (protocolVersion.lessEquals(HttpVersion.HTTP_1_1))
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            credentialsProvider.setCredentials(
                    new AuthScope(target),
                    new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final SimpleHttpRequest httpGetSecret = SimpleHttpRequests.GET.create(target, "/private/big-secret.txt");
            httpGetSecret.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
            try {
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final int code = response.getCode();
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, httpGetSecret, "200 (correct target creds / no keep-alive)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, httpGetSecret, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, httpGetSecret, "(time out)");
            }
        }
    }

}
