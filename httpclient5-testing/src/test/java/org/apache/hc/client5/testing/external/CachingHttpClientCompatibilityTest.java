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

import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;

public class CachingHttpClientCompatibilityTest {

    public static void main(final String... args) throws Exception {
        final CachingHttpClientCompatibilityTest[] tests = new CachingHttpClientCompatibilityTest[] {
                new CachingHttpClientCompatibilityTest(
                        new HttpHost("http", "localhost", 8080))
        };
        for (final CachingHttpClientCompatibilityTest test: tests) {
            try {
                test.execute();
            } finally {
                test.shutdown();
            }
        }
    }

    private final HttpHost target;
    private final PoolingHttpClientConnectionManager connManager;
    private final CloseableHttpClient client;

    CachingHttpClientCompatibilityTest(final HttpHost target) throws Exception {
        this.target = target;
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(getClass().getResource("/test-ca.keystore"), "nopassword".toCharArray()).build();
        this.connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                .build();
        this.client = CachingHttpClients.custom()
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

    enum TestResult {OK, NOK}

    private void logResult(final TestResult result, final HttpRequest request, final String message) {
        final StringBuilder buf = new StringBuilder();
        buf.append(result);
        if (buf.length() == 2) {
            buf.append(" ");
        }
        buf.append(": ").append(target);
        buf.append(": ");
        buf.append(request.getMethod()).append(" ").append(request.getRequestUri());
        if (message != null && !TextUtils.isBlank(message)) {
            buf.append(" -> ").append(message);
        }
        System.out.println(buf);
    }

    void execute() throws InterruptedException {

        // Initial ping
        {
            final HttpCacheContext context = HttpCacheContext.create();
            final HttpOptions options = new HttpOptions("*");
            try (final ClassicHttpResponse response = client.executeOpen(target, options, context)) {
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
        // GET from cache
        {
            connManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND);

            final String[] links = {"/", "/css/hc-maven.css", "/images/logos/httpcomponents.png"};

            final HttpCacheContext context = HttpCacheContext.create();
            for (final String link: links) {
                final HttpGet httpGet1 = new HttpGet(link);
                try (ClassicHttpResponse response = client.executeOpen(target, httpGet1, context)) {
                    final int code = response.getCode();
                    final CacheResponseStatus cacheResponseStatus = context.getCacheResponseStatus();
                    EntityUtils.consume(response.getEntity());
                    if (code == HttpStatus.SC_OK && cacheResponseStatus == CacheResponseStatus.CACHE_MISS) {
                        logResult(TestResult.OK, httpGet1, "200, " + cacheResponseStatus);
                    } else {
                        logResult(TestResult.NOK, httpGet1, "(status " + code + ", " + cacheResponseStatus + ")");
                    }
                } catch (final Exception ex) {
                    logResult(TestResult.NOK, httpGet1, "(" + ex.getMessage() + ")");
                }
                final HttpGet httpGet2 = new HttpGet(link);
                try (ClassicHttpResponse response = client.executeOpen(target, httpGet2, context)) {
                    final int code = response.getCode();
                    final CacheResponseStatus cacheResponseStatus = context.getCacheResponseStatus();
                    EntityUtils.consume(response.getEntity());
                    if (code == HttpStatus.SC_OK && cacheResponseStatus == CacheResponseStatus.CACHE_HIT) {
                        logResult(TestResult.OK, httpGet2, "200, " + cacheResponseStatus);
                    } else {
                        logResult(TestResult.NOK, httpGet2, "(status " + code + ", " + cacheResponseStatus + ")");
                    }
                } catch (final Exception ex) {
                    logResult(TestResult.NOK, httpGet2, "(" + ex.getMessage() + ")");
                }

                Thread.sleep(2000);

                final HttpGet httpGet3 = new HttpGet(link);
                httpGet3.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=0");
                try (ClassicHttpResponse response = client.executeOpen(target, httpGet3, context)) {
                    final int code = response.getCode();
                    final CacheResponseStatus cacheResponseStatus = context.getCacheResponseStatus();
                    EntityUtils.consume(response.getEntity());
                    if (code == HttpStatus.SC_OK && cacheResponseStatus == CacheResponseStatus.VALIDATED) {
                        logResult(TestResult.OK, httpGet3, "200, " + cacheResponseStatus);
                    } else {
                        logResult(TestResult.NOK, httpGet3, "(status " + code + ", " + cacheResponseStatus + ")");
                    }
                } catch (final Exception ex) {
                    logResult(TestResult.NOK, httpGet3, "(" + ex.getMessage() + ")");
                }
            }
        }
    }

}
