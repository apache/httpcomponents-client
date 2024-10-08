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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClients;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class CachingHttpAsyncClientCompatibilityTest {

    public static void main(final String... args) throws Exception {
        final CachingHttpAsyncClientCompatibilityTest[] tests = new CachingHttpAsyncClientCompatibilityTest[] {
                new CachingHttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_1_1, new HttpHost("http", "localhost", 8080)),
                new CachingHttpAsyncClientCompatibilityTest(
                        HttpVersion.HTTP_2_0, new HttpHost("http", "localhost", 8080))
        };
        for (final CachingHttpAsyncClientCompatibilityTest test: tests) {
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
    private final PoolingAsyncClientConnectionManager connManager;
    private final CloseableHttpAsyncClient client;

    CachingHttpAsyncClientCompatibilityTest(final HttpVersion protocolVersion, final HttpHost target) throws Exception {
        this.protocolVersion = protocolVersion;
        this.target = target;
        this.connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setTlsStrategy(new DefaultClientTlsStrategy(SSLContexts.custom()
                        .loadTrustMaterial(getClass().getResource("/test-ca.keystore"), "nopassword".toCharArray())
                        .build()))
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(this.protocolVersion == HttpVersion.HTTP_2 ?
                                HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.FORCE_HTTP_1)
                        .build())
                .build();
        this.client = CachingHttpAsyncClients.custom()
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(20480)
                        .setHeuristicCachingEnabled(true)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .setConnectionManager(this.connManager)
                .build();
    }

    void shutdown() throws Exception {
        client.close();
    }

    enum TestResult { OK, NOK }

    private void logResult(final TestResult result,
                           final HttpRequest request,
                           final HttpResponse response,
                           final String message) {
        final StringBuilder buf = new StringBuilder();
        buf.append(result);
        if (buf.length() == 2) {
            buf.append(" ");
        }
        buf.append(": ");
        if (response != null && response.getVersion() != null) {
            buf.append(response.getVersion()).append(" ");
        } else {
            buf.append(protocolVersion).append(" ");
        }
        buf.append(target);
        buf.append(": ");
        buf.append(request.getMethod()).append(" ").append(request.getRequestUri());
        if (message != null && !TextUtils.isBlank(message)) {
            buf.append(" -> ").append(message);
        }
        System.out.println(buf);
    }

    void execute() throws InterruptedException {

        client.start();
        // Initial ping
        {
            final HttpCacheContext context = HttpCacheContext.create();
            final SimpleHttpRequest options = SimpleRequestBuilder.options()
                    .setHttpHost(target)
                    .setPath("*")
                    .build();
            final Future<SimpleHttpResponse> future = client.execute(options, context, null);
            try {
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                final int code = response.getCode();
                if (code == HttpStatus.SC_OK) {
                    logResult(TestResult.OK, options, response, Objects.toString(response.getFirstHeader("server")));
                } else {
                    logResult(TestResult.NOK, options, response, "(status " + code + ")");
                }
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                logResult(TestResult.NOK, options, null, "(" + cause.getMessage() + ")");
            } catch (final TimeoutException ex) {
                logResult(TestResult.NOK, options, null, "(time out)");
            }
        }

        // GET from cache
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);
            final HttpCacheContext context = HttpCacheContext.create();

            final String[] links = {"/", "/css/hc-maven.css", "/images/logos/httpcomponents.png"};

            for (final String link: links) {
                final SimpleHttpRequest httpGet1 = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath(link)
                        .build();
                final Future<SimpleHttpResponse> linkFuture1 = client.execute(httpGet1, context, null);
                try {
                    final SimpleHttpResponse response = linkFuture1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                    final int code = response.getCode();
                    final CacheResponseStatus cacheResponseStatus = context.getCacheResponseStatus();
                    if (code == HttpStatus.SC_OK && cacheResponseStatus == CacheResponseStatus.CACHE_MISS) {
                        logResult(TestResult.OK, httpGet1, response, "200, " + cacheResponseStatus);
                    } else {
                        logResult(TestResult.NOK, httpGet1, response, "(status " + code + ", " + cacheResponseStatus + ")");
                    }
                } catch (final ExecutionException ex) {
                    final Throwable cause = ex.getCause();
                    logResult(TestResult.NOK, httpGet1, null, "(" + cause.getMessage() + ")");
                } catch (final TimeoutException ex) {
                    logResult(TestResult.NOK, httpGet1, null, "(time out)");
                }

                final SimpleHttpRequest httpGet2 = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath(link)
                        .build();
                final Future<SimpleHttpResponse> linkFuture2 = client.execute(httpGet2, context, null);
                try {
                    final SimpleHttpResponse response = linkFuture2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                    final int code = response.getCode();
                    final CacheResponseStatus cacheResponseStatus = context.getCacheResponseStatus();
                    if (code == HttpStatus.SC_OK && cacheResponseStatus == CacheResponseStatus.CACHE_HIT) {
                        logResult(TestResult.OK, httpGet2, response, "200, " + cacheResponseStatus);
                    } else {
                        logResult(TestResult.NOK, httpGet2, response, "(status " + code + ", " + cacheResponseStatus + ")");
                    }
                } catch (final ExecutionException ex) {
                    final Throwable cause = ex.getCause();
                    logResult(TestResult.NOK, httpGet2, null, "(" + cause.getMessage() + ")");
                } catch (final TimeoutException ex) {
                    logResult(TestResult.NOK, httpGet2, null, "(time out)");
                }

                Thread.sleep(2000);

                final SimpleHttpRequest httpGet3 = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath(link)
                        .setHeader(HttpHeaders.CACHE_CONTROL, "max-age=0")
                        .build();
                final Future<SimpleHttpResponse> linkFuture3 = client.execute(httpGet3, context, null);
                try {
                    final SimpleHttpResponse response = linkFuture3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                    final int code = response.getCode();
                    final CacheResponseStatus cacheResponseStatus = context.getCacheResponseStatus();
                    if (code == HttpStatus.SC_OK && cacheResponseStatus == CacheResponseStatus.VALIDATED) {
                        logResult(TestResult.OK, httpGet3, response, "200, " + cacheResponseStatus);
                    } else {
                        logResult(TestResult.NOK, httpGet3, response, "(status " + code + ", " + cacheResponseStatus + ")");
                    }
                } catch (final ExecutionException ex) {
                    final Throwable cause = ex.getCause();
                    logResult(TestResult.NOK, httpGet3, null, "(" + cause.getMessage() + ")");
                } catch (final TimeoutException ex) {
                    logResult(TestResult.NOK, httpGet3, null, "(time out)");
                }
            }
        }
    }

}
