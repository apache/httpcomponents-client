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
package org.apache.hc.client5.testing.sync;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;

/**
 * Client protocol handling tests.
 */
public class TestClientRequestExecution extends LocalServerTestBase {

    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setCode(HttpStatus.SC_OK);
            final StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    private static class FaultyHttpRequestExecutor extends HttpRequestExecutor {

        private static final String MARKER = "marker";

        private final String failureMsg;

        public FaultyHttpRequestExecutor(final String failureMsg) {
            this.failureMsg = failureMsg;
        }

        @Override
        public ClassicHttpResponse execute(
                final ClassicHttpRequest request,
                final HttpClientConnection conn,
                final HttpContext context) throws IOException, HttpException {

            final ClassicHttpResponse response = super.execute(request, conn, context);
            final Object marker = context.getAttribute(MARKER);
            if (marker == null) {
                context.setAttribute(MARKER, Boolean.TRUE);
                throw new IOException(failureMsg);
            }
            return response;
        }

    }

    @Test
    public void testAutoGeneratedHeaders() throws Exception {
        this.server.registerHandler("*", new SimpleService());

        final HttpRequestInterceptor interceptor = new HttpRequestInterceptor() {

            @Override
            public void process(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException, IOException {
                request.addHeader("my-header", "stuff");
            }

        };

        final HttpRequestRetryStrategy requestRetryStrategy = new HttpRequestRetryStrategy() {

            @Override
            public boolean retryRequest(
                    final HttpRequest request,
                    final IOException exception,
                    final int executionCount,
                    final HttpContext context) {
                return true;
            }

            @Override
            public boolean retryRequest(
                    final HttpResponse response,
                    final int executionCount,
                    final HttpContext context) {
                return false;
            }

            @Override
            public TimeValue getRetryInterval(
                    final HttpResponse response,
                    final int executionCount,
                    final HttpContext context) {
                return TimeValue.ofSeconds(1L);
            }

        };

        this.httpclient = this.clientBuilder
            .addRequestInterceptorFirst(interceptor)
            .setRequestExecutor(new FaultyHttpRequestExecutor("Oppsie"))
            .setRetryStrategy(requestRetryStrategy)
            .build();

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpGet httpget = new HttpGet("/");

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());

        final Header[] myheaders = reqWrapper.getHeaders("my-header");
        Assert.assertNotNull(myheaders);
        Assert.assertEquals(1, myheaders.length);
    }

    @Test(expected=IOException.class)
    public void testNonRepeatableEntity() throws Exception {
        this.server.registerHandler("*", new SimpleService());

        final HttpRequestRetryStrategy requestRetryStrategy = new HttpRequestRetryStrategy() {

            @Override
            public boolean retryRequest(
                    final HttpRequest request,
                    final IOException exception,
                    final int executionCount,
                    final HttpContext context) {
                return true;
            }

            @Override
            public boolean retryRequest(
                    final HttpResponse response,
                    final int executionCount,
                    final HttpContext context) {
                return false;
            }

            @Override
            public TimeValue getRetryInterval(
                    final HttpResponse response,
                    final int executionCount,
                    final HttpContext context) {
                return TimeValue.ofSeconds(1L);
            }

        };

        this.httpclient = this.clientBuilder
            .setRequestExecutor(new FaultyHttpRequestExecutor("a message showing that this failed"))
            .setRetryStrategy(requestRetryStrategy)
            .build();

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final HttpPost httppost = new HttpPost("/");
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1, null));
        this.httpclient.execute(target, httppost, context);
    }

    @Test
    public void testNonCompliantURI() throws Exception {
        this.server.registerHandler("*", new SimpleService());

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "{{|boom|}}");
        final ClassicHttpResponse response = this.httpclient.execute(target, request, context);
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());

        final HttpRequest reqWrapper = context.getRequest();

        Assert.assertEquals("{{|boom|}}", reqWrapper.getRequestUri());
    }

    @Test
    public void testRelativeRequestURIWithFragment() throws Exception {
        this.server.registerHandler("*", new SimpleService());

        final HttpHost target = start();

        final HttpGet httpget = new HttpGet("/stuff#blahblah");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = this.httpclient.execute(target, httpget, context);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        EntityUtils.consume(response.getEntity());

        final HttpRequest request = context.getRequest();
        Assert.assertEquals("/stuff", request.getRequestUri());
    }

    @Test
    public void testAbsoluteRequestURIWithFragment() throws Exception {
        this.server.registerHandler("*", new SimpleService());

        final HttpHost target = start();

        final URI uri = new URIBuilder()
            .setHost(target.getHostName())
            .setPort(target.getPort())
            .setScheme(target.getSchemeName())
            .setPath("/stuff")
            .setFragment("blahblah")
            .build();

        final HttpGet httpget = new HttpGet(uri);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = this.httpclient.execute(httpget, context);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        EntityUtils.consume(response.getEntity());

        final HttpRequest request = context.getRequest();
        Assert.assertEquals("/stuff", request.getRequestUri());

        final RedirectLocations redirectLocations = context.getRedirectLocations();
        final URI location = URIUtils.resolve(uri, target, redirectLocations.getAll());
        Assert.assertEquals(uri, location);
    }

    @Test
    public void testRequestCancellation() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        final HttpHost target = start();

        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {

            for (int i = 0; i < 20; i++) {
                final HttpGet httpget = new HttpGet("/random/1000");

                executorService.schedule(new Runnable() {

                    @Override
                    public void run() {
                        httpget.cancel();
                    }
                }, 1, TimeUnit.MILLISECONDS);

                try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget)) {
                    EntityUtils.consume(response.getEntity());
                } catch (final Exception ignore) {
                }
            }

            final Random rnd = new Random();
            for (int i = 0; i < 20; i++) {
                final HttpGet httpget = new HttpGet("/random/1000");

                executorService.schedule(new Runnable() {

                    @Override
                    public void run() {
                        httpget.cancel();
                    }
                }, rnd.nextInt(200), TimeUnit.MILLISECONDS);

                try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget)) {
                    EntityUtils.consume(response.getEntity());
                } catch (final Exception ignore) {
                }

            }

            for (int i = 0; i < 5; i++) {
                final HttpGet httpget = new HttpGet("/random/1000");
                try (final ClassicHttpResponse response = this.httpclient.execute(target, httpget)) {
                    EntityUtils.consume(response.getEntity());
                }
            }

        } finally {
            executorService.shutdownNow();
        }
    }

}
