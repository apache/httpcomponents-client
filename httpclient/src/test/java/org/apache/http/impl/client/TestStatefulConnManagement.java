/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.http.impl.client;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultRequestDirector}
 */
public class TestStatefulConnManagement extends ServerTestBase {

    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_OK);
            StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    @Test
    public void testStatefulConnections() throws Exception {

        int workerCount = 5;
        int requestCount = 5;

        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpHost target = new HttpHost("localhost", port);

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 10);

        ThreadSafeClientConnManager mgr = new ThreadSafeClientConnManager(supportedSchemes);
        mgr.setMaxTotal(workerCount);
        mgr.setDefaultMaxPerRoute(workerCount);


        DefaultHttpClient client = new DefaultHttpClient(mgr, params);

        HttpContext[] contexts = new HttpContext[workerCount];
        HttpWorker[] workers = new HttpWorker[workerCount];
        for (int i = 0; i < contexts.length; i++) {
            HttpContext context = new BasicHttpContext();
            context.setAttribute("user", Integer.valueOf(i));
            contexts[i] = context;
            workers[i] = new HttpWorker(context, requestCount, target, client);
        }

        client.setUserTokenHandler(new UserTokenHandler() {

            public Object getUserToken(final HttpContext context) {
                Integer id = (Integer) context.getAttribute("user");
                return id;
            }

        });


        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join(10000);
        }
        for (int i = 0; i < workers.length; i++) {
            Exception ex = workers[i].getException();
            if (ex != null) {
                throw ex;
            }
            Assert.assertEquals(requestCount, workers[i].getCount());
        }

        for (int i = 0; i < contexts.length; i++) {
            HttpContext context = contexts[i];
            Integer id = (Integer) context.getAttribute("user");

            for (int r = 0; r < requestCount; r++) {
                Integer state = (Integer) context.getAttribute("r" + r);
                Assert.assertNotNull(state);
                Assert.assertEquals(id, state);
            }
        }

    }

    static class HttpWorker extends Thread {

        private final HttpContext context;
        private final int requestCount;
        private final HttpHost target;
        private final HttpClient httpclient;

        private volatile Exception exception;
        private volatile int count;

        public HttpWorker(
                final HttpContext context,
                int requestCount,
                final HttpHost target,
                final HttpClient httpclient) {
            super();
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
                for (int r = 0; r < this.requestCount; r++) {
                    HttpGet httpget = new HttpGet("/");
                    HttpResponse response = this.httpclient.execute(
                            this.target,
                            httpget,
                            this.context);
                    this.count++;

                    ManagedClientConnection conn = (ManagedClientConnection) this.context.getAttribute(
                            ExecutionContext.HTTP_CONNECTION);

                    this.context.setAttribute("r" + r, conn.getState());

                    EntityUtils.consume(response.getEntity());
                }

            } catch (Exception ex) {
                this.exception = ex;
            }
        }

    }

    @Test
    public void testRouteSpecificPoolRecylcing() throws Exception {
        // This tests what happens when a maxed connection pool needs
        // to kill the last idle connection to a route to build a new
        // one to the same route.

        int maxConn = 2;

        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        // We build a client with 2 max active // connections, and 2 max per route.
        ThreadSafeClientConnManager connMngr = new ThreadSafeClientConnManager(supportedSchemes);
        connMngr.setMaxTotal(maxConn);
        connMngr.setDefaultMaxPerRoute(maxConn);

        DefaultHttpClient client = new DefaultHttpClient(connMngr);

        client.setUserTokenHandler(new UserTokenHandler() {

            public Object getUserToken(final HttpContext context) {
                return context.getAttribute("user");
            }

        });

        // Bottom of the pool : a *keep alive* connection to Route 1.
        HttpContext context1 = new BasicHttpContext();
        context1.setAttribute("user", "stuff");
        HttpResponse response1 = client.execute(
                new HttpHost("localhost", port), new HttpGet("/"), context1);
        EntityUtils.consume(response1.getEntity());

        // The ConnPoolByRoute now has 1 free connection, out of 2 max
        // The ConnPoolByRoute has one RouteSpcfcPool, that has one free connection
        // for [localhost][stuff]

        Thread.sleep(100);

        // Send a very simple HTTP get (it MUST be simple, no auth, no proxy, no 302, no 401, ...)
        // Send it to another route. Must be a keepalive.
        HttpContext context2 = new BasicHttpContext();
        HttpResponse response2 = client.execute(
                new HttpHost("127.0.0.1", port), new HttpGet("/"), context2);
        EntityUtils.consume(response2.getEntity());
        // ConnPoolByRoute now has 2 free connexions, out of its 2 max.
        // The [localhost][stuff] RouteSpcfcPool is the same as earlier
        // And there is a [127.0.0.1][null] pool with 1 free connection

        Thread.sleep(100);

        // This will put the ConnPoolByRoute to the targeted state :
        // [localhost][stuff] will not get reused because this call is [localhost][null]
        // So the ConnPoolByRoute will need to kill one connection (it is maxed out globally).
        // The killed conn is the oldest, which means the first HTTPGet ([localhost][stuff]).
        // When this happens, the RouteSpecificPool becomes empty.
        HttpContext context3 = new BasicHttpContext();
        HttpResponse response3 = client.execute(
                new HttpHost("localhost", port), new HttpGet("/"), context3);

        // If the ConnPoolByRoute did not behave coherently with the RouteSpecificPool
        // this may fail. Ex : if the ConnPool discared the route pool because it was empty,
        // but still used it to build the request3 connection.
        EntityUtils.consume(response3.getEntity());

    }

}
