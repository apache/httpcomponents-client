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

import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test cases for state-ful connections.
 */
class TestStatefulConnManagement extends AbstractIntegrationTestBase {

    public static final Timeout LONG_TIMEOUT = Timeout.ofMinutes(3);

    public TestStatefulConnManagement() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD);
    }

    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) {
            response.setCode(HttpStatus.SC_OK);
            final StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    @Test
    void testStatefulConnections() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("*", new SimpleService()));
        final HttpHost target = startServer();

        final int workerCount = 5;
        final int requestCount = 5;

        final UserTokenHandler userTokenHandler = (route, context) -> {
            final String id = (String) context.getAttribute("user");
            return id;
        };

        configureClient(builder -> builder
                .setUserTokenHandler(userTokenHandler));
        final TestClient client = client();

        final PoolingHttpClientConnectionManager connectionManager = client.getConnectionManager();
        connectionManager.setMaxTotal(workerCount);
        connectionManager.setDefaultMaxPerRoute(workerCount);

        final HttpClientContext[] contexts = new HttpClientContext[workerCount];
        final HttpWorker[] workers = new HttpWorker[workerCount];
        for (int i = 0; i < contexts.length; i++) {
            final HttpClientContext context = HttpClientContext.create();
            contexts[i] = context;
            workers[i] = new HttpWorker(
                    "user" + i,
                    context, requestCount, target, client);
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
            Assertions.assertEquals(requestCount, worker.getCount());
        }

        for (final HttpContext context : contexts) {
            final String state0 = (String) context.getAttribute("r0");
            Assertions.assertNotNull(state0);
            for (int r = 1; r < requestCount; r++) {
                Assertions.assertEquals(state0, context.getAttribute("r" + r));
            }
        }

    }

    static class HttpWorker extends Thread {

        private final String uid;
        private final HttpClientContext context;
        private final int requestCount;
        private final HttpHost target;
        private final CloseableHttpClient httpclient;

        private volatile Exception exception;
        private volatile int count;

        public HttpWorker(
                final String uid,
                final HttpClientContext context,
                final int requestCount,
                final HttpHost target,
                final CloseableHttpClient httpclient) {
            super();
            this.uid = uid;
            this.context = context;
            this.requestCount = requestCount;
            this.target = target;
            this.httpclient = httpclient;
            this.count = 0;
        }

        public int getCount() {
            return this.count;
        }

        public Exception getException() {
            return this.exception;
        }

        @Override
        public void run() {
            try {
                this.context.setAttribute("user", this.uid);
                for (int r = 0; r < this.requestCount; r++) {
                    final HttpGet httpget = new HttpGet("/");
                    this.httpclient.execute(this.target, httpget, this.context, response -> {
                        EntityUtils.consume(response.getEntity());
                        return null;
                    });
                    this.count++;

                    final EndpointDetails endpointDetails = this.context.getEndpointDetails();
                    final String connuid = Integer.toHexString(System.identityHashCode(endpointDetails));
                    this.context.setAttribute("r" + r, connuid);
                }

            } catch (final Exception ex) {
                this.exception = ex;
            }
        }

    }

    @Test
    void testRouteSpecificPoolRecylcing() throws Exception {
        configureServer(bootstrap -> bootstrap.register("*", new SimpleService()));
        final HttpHost target = startServer();

        // This tests what happens when a maxed connection pool needs
        // to kill the last idle connection to a route to build a new
        // one to the same route.

        final int maxConn = 2;

        configureClient(builder -> builder
                .setUserTokenHandler((route, context) -> context.getAttribute("user")));
        final TestClient client = client();

        final PoolingHttpClientConnectionManager connectionManager = client.getConnectionManager();
        connectionManager.setMaxTotal(maxConn);
        connectionManager.setDefaultMaxPerRoute(maxConn);

        // Bottom of the pool : a *keep alive* connection to Route 1.
        final HttpContext context1 = HttpClientContext.create();
        context1.setAttribute("user", "stuff");
        client.execute(target, new HttpGet("/"), context1, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        // The ConnPoolByRoute now has 1 free connection, out of 2 max
        // The ConnPoolByRoute has one RouteSpcfcPool, that has one free connection
        // for [localhost][stuff]

        Thread.sleep(100);

        // Send a very simple HTTP get (it MUST be simple, no auth, no proxy, no 302, no 401, ...)
        // Send it to another route. Must be a keepalive.
        final HttpContext context2 = HttpClientContext.create();
        client.execute(new HttpHost("127.0.0.1", target.getPort()), new HttpGet("/"), context2, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });
        // ConnPoolByRoute now has 2 free connexions, out of its 2 max.
        // The [localhost][stuff] RouteSpcfcPool is the same as earlier
        // And there is a [127.0.0.1][null] pool with 1 free connection

        Thread.sleep(100);

        // This will put the ConnPoolByRoute to the targeted state :
        // [localhost][stuff] will not get reused because this call is [localhost][null]
        // So the ConnPoolByRoute will need to kill one connection (it is maxed out globally).
        // The killed conn is the oldest, which means the first HTTPGet ([localhost][stuff]).
        // When this happens, the RouteSpecificPool becomes empty.
        final HttpContext context3 = HttpClientContext.create();
        client.execute(target, new HttpGet("/"), context3, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        // If the ConnPoolByRoute did not behave coherently with the RouteSpecificPool
        // this may fail. Ex : if the ConnPool discared the route pool because it was empty,
        // but still used it to build the request3 connection.

    }

}
