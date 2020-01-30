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
package org.apache.hc.client5.testing.async;

import java.util.concurrent.Future;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class TestHttp1AsyncStatefulConnManagement extends AbstractIntegrationTestBase<CloseableHttpAsyncClient> {

    protected HttpAsyncClientBuilder clientBuilder;
    protected PoolingAsyncClientConnectionManager connManager;

    @Rule
    public ExternalResource connManagerResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .build();
        }

        @Override
        protected void after() {
            if (connManager != null) {
                connManager.close();
                connManager = null;
            }
        }

    };

    @Rule
    public ExternalResource clientBuilderResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            clientBuilder = HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(TIMEOUT)
                            .setConnectionRequestTimeout(TIMEOUT)
                            .build())
                    .setConnectionManager(connManager);
        }

    };

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

    @Override
    public HttpHost start() throws Exception {
        return super.start(null, Http1Config.DEFAULT);
    }

    @Test
    public void testStatefulConnections() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) throws HttpException {
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setBody("Whatever", ContentType.TEXT_PLAIN);
                        return response;
                    }
                };
            }

        });

        final UserTokenHandler userTokenHandler = new UserTokenHandler() {

            @Override
            public Object getUserToken(final HttpRoute route, final HttpContext context) {
                return context.getAttribute("user");
            }

        };
        clientBuilder.setUserTokenHandler(userTokenHandler);
        final HttpHost target = start();

        final int workerCount = 2;
        final int requestCount = 5;

        final HttpContext[] contexts = new HttpContext[workerCount];
        final HttpWorker[] workers = new HttpWorker[workerCount];
        for (int i = 0; i < contexts.length; i++) {
            final HttpClientContext context = HttpClientContext.create();
            contexts[i] = context;
            workers[i] = new HttpWorker(
                    "user" + i,
                    context, requestCount, target, httpclient);
        }

        for (final HttpWorker worker : workers) {
            worker.start();
        }
        for (final HttpWorker worker : workers) {
            worker.join(LONG_TIMEOUT.toMilliseconds());
        }
        for (final HttpWorker worker : workers) {
            final Exception ex = worker.getException();
            if (ex != null) {
                throw ex;
            }
            Assert.assertEquals(requestCount, worker.getCount());
        }

        for (final HttpContext context : contexts) {
            final String state0 = (String) context.getAttribute("r0");
            Assert.assertNotNull(state0);
            for (int r = 1; r < requestCount; r++) {
                Assert.assertEquals(state0, context.getAttribute("r" + r));
            }
        }

    }

    static class HttpWorker extends Thread {

        private final String uid;
        private final HttpClientContext context;
        private final int requestCount;
        private final HttpHost target;
        private final CloseableHttpAsyncClient httpclient;

        private volatile Exception exception;
        private volatile int count;

        public HttpWorker(
                final String uid,
                final HttpClientContext context,
                final int requestCount,
                final HttpHost target,
                final CloseableHttpAsyncClient httpclient) {
            super();
            this.uid = uid;
            this.context = context;
            this.requestCount = requestCount;
            this.target = target;
            this.httpclient = httpclient;
            this.count = 0;
        }

        public int getCount() {
            return count;
        }

        public Exception getException() {
            return exception;
        }

        @Override
        public void run() {
            try {
                context.setAttribute("user", uid);
                for (int r = 0; r < requestCount; r++) {
                    final SimpleHttpRequest httpget = SimpleHttpRequests.get(target, "/");
                    final Future<SimpleHttpResponse> future = httpclient.execute(httpget, null);
                    future.get();

                    count++;
                    final EndpointDetails endpointDetails = context.getEndpointDetails();
                    final String connuid = Integer.toHexString(System.identityHashCode(endpointDetails));
                    context.setAttribute("r" + r, connuid);
                }

            } catch (final Exception ex) {
                exception = ex;
            }
        }

    }

    @Test
    public void testRouteSpecificPoolRecylcing() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) throws HttpException {
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setBody("Whatever", ContentType.TEXT_PLAIN);
                        return response;
                    }
                };
            }

        });

        // This tests what happens when a maxed connection pool needs
        // to kill the last idle connection to a route to build a new
        // one to the same route.
        final UserTokenHandler userTokenHandler = new UserTokenHandler() {

            @Override
            public Object getUserToken(final HttpRoute route, final HttpContext context) {
                return context.getAttribute("user");
            }

        };
        clientBuilder.setUserTokenHandler(userTokenHandler);

        final HttpHost target = start();
        final int maxConn = 2;
        // We build a client with 2 max active // connections, and 2 max per route.
        connManager.setMaxTotal(maxConn);
        connManager.setDefaultMaxPerRoute(maxConn);

        // Bottom of the pool : a *keep alive* connection to Route 1.
        final HttpContext context1 = new BasicHttpContext();
        context1.setAttribute("user", "stuff");

        final Future<SimpleHttpResponse> future1 = httpclient.execute(SimpleHttpRequests.get(target, "/"), context1, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);
        Assert.assertEquals(200, response1.getCode());

        // The ConnPoolByRoute now has 1 free connection, out of 2 max
        // The ConnPoolByRoute has one RouteSpcfcPool, that has one free connection
        // for [localhost][stuff]

        Thread.sleep(100);

        // Send a very simple HTTP get (it MUST be simple, no auth, no proxy, no 302, no 401, ...)
        // Send it to another route. Must be a keepalive.
        final HttpContext context2 = new BasicHttpContext();

        final Future<SimpleHttpResponse> future2 = httpclient.execute(SimpleHttpRequests.get(
                new HttpHost(target.getSchemeName(), "127.0.0.1", target.getPort()),"/"), context2, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);
        Assert.assertEquals(200, response2.getCode());

        // ConnPoolByRoute now has 2 free connexions, out of its 2 max.
        // The [localhost][stuff] RouteSpcfcPool is the same as earlier
        // And there is a [127.0.0.1][null] pool with 1 free connection

        Thread.sleep(100);

        // This will put the ConnPoolByRoute to the targeted state :
        // [localhost][stuff] will not get reused because this call is [localhost][null]
        // So the ConnPoolByRoute will need to kill one connection (it is maxed out globally).
        // The killed conn is the oldest, which means the first HTTPGet ([localhost][stuff]).
        // When this happens, the RouteSpecificPool becomes empty.
        final HttpContext context3 = new BasicHttpContext();
        final Future<SimpleHttpResponse> future3 = httpclient.execute(SimpleHttpRequests.get(target, "/"), context3, null);
        final HttpResponse response3 = future3.get();
        Assert.assertNotNull(response3);
        Assert.assertEquals(200, response3.getCode());
    }

}
