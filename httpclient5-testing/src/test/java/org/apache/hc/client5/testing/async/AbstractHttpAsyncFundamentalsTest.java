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

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public abstract class AbstractHttpAsyncFundamentalsTest<T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase<T> {

    public AbstractHttpAsyncFundamentalsTest(final URIScheme scheme) {
        super(scheme);
    }

    @Test
    public void testSequenctialGetRequests() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.get(target, "/random/2048"), null);
            final SimpleHttpResponse response = future.get();
            Assert.assertThat(response, CoreMatchers.notNullValue());
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBodyText();
            Assert.assertThat(body, CoreMatchers.notNullValue());
            Assert.assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test
    public void testSequenctialHeadRequests() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.head(target, "/random/2048"), null);
            final SimpleHttpResponse response = future.get();
            Assert.assertThat(response, CoreMatchers.notNullValue());
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBodyText();
            Assert.assertThat(body, CoreMatchers.nullValue());
        }
    }

    @Test
    public void testSequenctialPostRequests() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final byte[] b1 = new byte[1024];
            final Random rnd = new Random(System.currentTimeMillis());
            rnd.nextBytes(b1);
            final Future<Message<HttpResponse, byte[]>> future = httpclient.execute(
                    new BasicRequestProducer(Method.GET, target, "/echo/",
                            AsyncEntityProducers.create(b1, ContentType.APPLICATION_OCTET_STREAM)),
                    new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), HttpClientContext.create(), null);
            final Message<HttpResponse, byte[]> responseMessage = future.get();
            Assert.assertThat(responseMessage, CoreMatchers.notNullValue());
            final HttpResponse response = responseMessage.getHead();
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final byte[] b2 = responseMessage.getBody();
            Assert.assertThat(b1, CoreMatchers.equalTo(b2));
        }
    }

    @Test
    public void testConcurrentPostRequests() throws Exception {
        final HttpHost target = start();
        final byte[] b1 = new byte[1024];
        final Random rnd = new Random(System.currentTimeMillis());
        rnd.nextBytes(b1);

        final int reqCount = 20;

        final Queue<Future<Message<HttpResponse, byte[]>>> queue = new LinkedList<>();
        for (int i = 0; i < reqCount; i++) {
            final Future<Message<HttpResponse, byte[]>> future = httpclient.execute(
                    new BasicRequestProducer(Method.POST, target, "/echo/",
                            AsyncEntityProducers.create(b1, ContentType.APPLICATION_OCTET_STREAM)),
                    new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), HttpClientContext.create(), null);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, byte[]>> future = queue.remove();
            final Message<HttpResponse, byte[]> responseMessage = future.get();
            Assert.assertThat(responseMessage, CoreMatchers.notNullValue());
            final HttpResponse response = responseMessage.getHead();
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final byte[] b2 = responseMessage.getBody();
            Assert.assertThat(b1, CoreMatchers.equalTo(b2));
        }
    }

    @Test
    public void testRequestExecutionFromCallback() throws Exception {
        final HttpHost target = start();
        final int requestNum = 50;
        final AtomicInteger count = new AtomicInteger(requestNum);
        final Queue<SimpleHttpResponse> resultQueue = new ConcurrentLinkedQueue<>();
        final CountDownLatch countDownLatch = new CountDownLatch(requestNum);

        final FutureCallback<SimpleHttpResponse> callback = new FutureCallback<SimpleHttpResponse>() {

            @Override
            public void completed(final SimpleHttpResponse result) {
                try {
                    resultQueue.add(result);
                    if (count.decrementAndGet() > 0) {
                        httpclient.execute(SimpleHttpRequests.get(target, "/random/2048"), this);
                    }
                } finally {
                    countDownLatch.countDown();
                }
            }

            @Override
            public void failed(final Exception ex) {
                countDownLatch.countDown();
            }

            @Override
            public void cancelled() {
                countDownLatch.countDown();
            }
        };

        final int threadNum = 5;
        final ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
        for (int i = 0; i < threadNum; i++) {
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    if (!Thread.currentThread().isInterrupted()) {
                        httpclient.execute(SimpleHttpRequests.get(target, "/random/2048"), callback);
                    }
                }

            });
        }

        Assert.assertThat(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()), CoreMatchers.equalTo(true));

        executorService.shutdownNow();
        executorService.awaitTermination(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        for (;;) {
            final SimpleHttpResponse response = resultQueue.poll();
            if (response == null) {
                break;
            }
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
        }
    }

    @Test
    public void testBadRequest() throws Exception {
        final HttpHost target = start();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/random/boom"), null);
        final SimpleHttpResponse response = future.get();
        Assert.assertThat(response, CoreMatchers.notNullValue());
        Assert.assertThat(response.getCode(), CoreMatchers.equalTo(400));
    }

}
