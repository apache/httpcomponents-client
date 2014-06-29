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
package org.apache.http.impl.client;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("boxing") // test code
public class TestFutureRequestExecutionService {

    private HttpServer localServer;
    private String uri;
    private FutureRequestExecutionService httpAsyncClientWithFuture;

    private final AtomicBoolean blocked = new AtomicBoolean(false);

    @Before
    public void before() throws Exception {
            this.localServer = ServerBootstrap.bootstrap()
                    .registerHandler("/wait", new HttpRequestHandler() {

                @Override
                public void handle(
                        final HttpRequest request, final HttpResponse response,
                        final HttpContext context) throws HttpException, IOException {
                    try {
                        while(blocked.get()) {
                            Thread.sleep(10);
                        }
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    response.setStatusCode(200);
                }
            }).create();

            this.localServer.start();
            uri = "http://localhost:" + this.localServer.getLocalPort() + "/wait";
            final HttpClient httpClient = HttpClientBuilder.create()
                    .setMaxConnPerRoute(5)
                    .build();
            final ExecutorService executorService = Executors.newFixedThreadPool(5);
            httpAsyncClientWithFuture = new FutureRequestExecutionService(httpClient, executorService);
    }

    @After
    public void after() throws Exception {
        blocked.set(false); // any remaining requests should unblock
        this.localServer.stop();
        httpAsyncClientWithFuture.close();
    }

    @Test
    public void shouldExecuteSingleCall() throws InterruptedException, ExecutionException {
        final HttpRequestFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(
            new HttpGet(uri), HttpClientContext.create(), new OkidokiHandler());
        Assert.assertTrue("request should have returned OK", task.get().booleanValue());
    }

    @Test(expected=CancellationException.class)
    public void shouldCancel() throws InterruptedException, ExecutionException {
        final HttpRequestFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(
            new HttpGet(uri), HttpClientContext.create(), new OkidokiHandler());
        task.cancel(true);
        task.get();
    }

    @Test(expected=TimeoutException.class)
    public void shouldTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        blocked.set(true);
        final HttpRequestFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(
            new HttpGet(uri), HttpClientContext.create(), new OkidokiHandler());
        task.get(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void shouldExecuteMultipleCalls() throws Exception {
        final int reqNo = 100;
        final Queue<Future<Boolean>> tasks = new LinkedList<Future<Boolean>>();
        for(int i = 0; i < reqNo; i++) {
            final Future<Boolean> task = httpAsyncClientWithFuture.execute(
                    new HttpGet(uri), HttpClientContext.create(), new OkidokiHandler());
            tasks.add(task);
        }
        for (final Future<Boolean> task : tasks) {
            final Boolean b = task.get();
            Assert.assertNotNull(b);
            Assert.assertTrue("request should have returned OK", b.booleanValue());
        }
    }

    @Test
    public void shouldExecuteMultipleCallsAndCallback() throws Exception {
        final int reqNo = 100;
        final Queue<Future<Boolean>> tasks = new LinkedList<Future<Boolean>>();
        final CountDownLatch latch = new CountDownLatch(reqNo);
        for(int i = 0; i < reqNo; i++) {
            final Future<Boolean> task = httpAsyncClientWithFuture.execute(
                    new HttpGet(uri), HttpClientContext.create(),
                    new OkidokiHandler(), new CountingCallback(latch));
            tasks.add(task);
        }
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        for (final Future<Boolean> task : tasks) {
            final Boolean b = task.get();
            Assert.assertNotNull(b);
            Assert.assertTrue("request should have returned OK", b.booleanValue());
        }
    }

    private final class CountingCallback implements FutureCallback<Boolean> {

        private final CountDownLatch latch;

        CountingCallback(final CountDownLatch latch) {
            super();
            this.latch = latch;
        }

        @Override
        public void failed(final Exception ex) {
            latch.countDown();
        }

        @Override
        public void completed(final Boolean result) {
            latch.countDown();
        }

        @Override
        public void cancelled() {
            latch.countDown();
        }
    }


    private final class OkidokiHandler implements ResponseHandler<Boolean> {
        @Override
        public Boolean handleResponse(
                final HttpResponse response) throws ClientProtocolException, IOException {
            return response.getStatusLine().getStatusCode() == 200;
        }
    }

}
