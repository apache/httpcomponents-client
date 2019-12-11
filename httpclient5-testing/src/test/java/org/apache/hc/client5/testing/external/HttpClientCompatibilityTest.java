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

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;

public class HttpClientCompatibilityTest {

    public static void main(final String... args) throws Exception {
        final HttpClientCompatibilityTest[] tests = new HttpClientCompatibilityTest[] {
                new HttpClientCompatibilityTest(
                        new HttpHost("http", "localhost", 8080), null, null),
                new HttpClientCompatibilityTest(
                        new HttpHost("http", "test-httpd", 8080), new HttpHost("localhost", 8888), null),
                new HttpClientCompatibilityTest(
                        new HttpHost("http", "test-httpd", 8080), new HttpHost("localhost", 8889),
                        new UsernamePasswordCredentials("squid", "nopassword".toCharArray())),
                new HttpClientCompatibilityTest(
                        new HttpHost("https", "localhost", 8443), null, null),
                new HttpClientCompatibilityTest(
                        new HttpHost("https", "test-httpd", 8443), new HttpHost("localhost", 8888), null),
                new HttpClientCompatibilityTest(
                        new HttpHost("https", "test-httpd", 8443), new HttpHost("localhost", 8889),
                        new UsernamePasswordCredentials("squid", "nopassword".toCharArray()))
        };
        for (final HttpClientCompatibilityTest test: tests) {
            try {
                test.execute();
            } finally {
                test.shutdown();
            }
        }
    }

    private final HttpHost target;
    private final HttpHost proxy;
    private final BasicCredentialsProvider credentialsProvider;
    private final PoolingHttpClientConnectionManager connManager;
    private final CloseableHttpClient client;

    HttpClientCompatibilityTest(
            final HttpHost target,
            final HttpHost proxy,
            final Credentials proxyCreds) throws Exception {
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
        this.connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(this.connManager)
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
        buf.append(": ").append(target);
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

    void execute() {

        // Initial ping
        {
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);
            final HttpOptions options = new HttpOptions("*");
            try (ClassicHttpResponse response = client.execute(target, options, context)) {
                final int code = response.getCode();
                EntityUtils.consume(response.getEntity());
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, options, Objects.toString(response.getFirstHeader("server")));
                } else {
                    logResult(TestResult.NOK, options, "(status " + code + ")");
                }
            } catch (final Exception ex) {
                logResult(TestResult.NOK, options, "(" + ex.getMessage() + ")");
            }
        }
        // Basic GET requests
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);
            final String[] requestUris = new String[] {"/", "/news.html", "/status.html"};
            for (final String requestUri: requestUris) {
                final HttpGet httpGet = new HttpGet(requestUri);
                try (ClassicHttpResponse response = client.execute(target, httpGet, context)) {
                    final int code = response.getCode();
                    EntityUtils.consume(response.getEntity());
                    if (code == HttpStatus.SC_OK) {
                        logResult(TestResult.OK, httpGet, "200");
                    } else {
                        logResult(TestResult.NOK, httpGet, "(status " + code + ")");
                    }
                } catch (final Exception ex) {
                    logResult(TestResult.NOK, httpGet, "(" + ex.getMessage() + ")");
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

            final HttpGet httpGetSecret = new HttpGet("/private/big-secret.txt");
            try (ClassicHttpResponse response = client.execute(target, httpGetSecret, context)) {
                final int code = response.getCode();
                EntityUtils.consume(response.getEntity());
                if (code == HttpStatus.SC_UNAUTHORIZED) {
                    logResult(TestResult.OK, httpGetSecret, "401 (wrong target auth scope)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final Exception ex) {
                logResult(TestResult.NOK, httpGetSecret, "(" + ex.getMessage() + ")");
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

            final HttpGet httpGetSecret = new HttpGet("/private/big-secret.txt");
            try (ClassicHttpResponse response = client.execute(target, httpGetSecret, context)) {
                final int code = response.getCode();
                EntityUtils.consume(response.getEntity());
                if (code == HttpStatus.SC_UNAUTHORIZED) {
                    logResult(TestResult.OK, httpGetSecret, "401 (wrong target creds)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final Exception ex) {
                logResult(TestResult.NOK, httpGetSecret, "(" + ex.getMessage() + ")");
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

            final HttpGet httpGetSecret = new HttpGet("/private/big-secret.txt");
            try (ClassicHttpResponse response = client.execute(target, httpGetSecret, context)) {
                final int code = response.getCode();
                EntityUtils.consume(response.getEntity());
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, httpGetSecret, "200 (correct target creds)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final Exception ex) {
                logResult(TestResult.NOK, httpGetSecret, "(" + ex.getMessage() + ")");
            }
        }
        // Correct target credentials (no keep-alive)
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            credentialsProvider.setCredentials(
                    new AuthScope(target),
                    new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()));
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);

            final HttpGet httpGetSecret = new HttpGet("/private/big-secret.txt");
            httpGetSecret.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            try (ClassicHttpResponse response = client.execute(target, httpGetSecret, context)) {
                final int code = response.getCode();
                EntityUtils.consume(response.getEntity());
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, httpGetSecret, "200 (correct target creds / no keep-alive)");
                } else {
                    logResult(TestResult.NOK, httpGetSecret, "(status " + code + ")");
                }
            } catch (final Exception ex) {
                logResult(TestResult.NOK, httpGetSecret, "(" + ex.getMessage() + ")");
            }
        }
    }

}
