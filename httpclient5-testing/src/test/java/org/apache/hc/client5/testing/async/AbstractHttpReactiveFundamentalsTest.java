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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.testing.reactive.Reactive3TestUtils;
import org.apache.hc.core5.testing.reactive.Reactive3TestUtils.StreamDescription;
import org.apache.hc.core5.testing.reactive.ReactiveEchoProcessor;
import org.apache.hc.core5.testing.reactive.ReactiveRandomProcessor;
import org.apache.hc.core5.util.TextUtils;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;

public abstract class AbstractHttpReactiveFundamentalsTest<T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase {

    public AbstractHttpReactiveFundamentalsTest(final URIScheme scheme) {
        super(scheme);
    }

    abstract protected H2TestServer startServer() throws Exception;

    abstract protected T startClient() throws Exception;

    @Test
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testSequentialGetRequests() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor()));
        final HttpHost target = targetHost();
        final T client = startClient();
        for (int i = 0; i < 3; i++) {
            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

            client.execute(AsyncRequestBuilder.get(target + "/random/2048").build(), consumer, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();
            assertThat(response, CoreMatchers.notNullValue());
            assertThat(response.getHead().getCode(), CoreMatchers.equalTo(200));

            final String body = publisherToString(response.getBody());
            assertThat(body, CoreMatchers.notNullValue());
            assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test
    @Timeout(value = 2000, unit = MILLISECONDS)
    public void testSequentialHeadRequests() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor()));
        final HttpHost target = targetHost();
        final T client = startClient();
        for (int i = 0; i < 3; i++) {
            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

            client.execute(AsyncRequestBuilder.head(target + "/random/2048").build(), consumer, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();
            assertThat(response, CoreMatchers.notNullValue());
            assertThat(response.getHead().getCode(), CoreMatchers.equalTo(200));

            final String body = publisherToString(response.getBody());
            assertThat(body, CoreMatchers.nullValue());
        }
    }

    @Test
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testSequentialPostRequests() throws Exception {
        final H2TestServer server = startServer();
        server.register("/echo/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveEchoProcessor()));
        final HttpHost target = targetHost();
        final T client = startClient();
        for (int i = 0; i < 3; i++) {
            final byte[] b1 = new byte[1024];
            final Random rnd = new Random(System.currentTimeMillis());
            rnd.nextBytes(b1);
            final Flowable<ByteBuffer> publisher = Flowable.just(ByteBuffer.wrap(b1));
            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
            final AsyncRequestProducer request = AsyncRequestBuilder.post(target + "/echo/")
                .setEntity(new ReactiveEntityProducer(publisher, -1, ContentType.APPLICATION_OCTET_STREAM, null))
                .build();

            client.execute(request, consumer, HttpClientContext.create(), null);

            final Future<Message<HttpResponse, Publisher<ByteBuffer>>> responseFuture = consumer.getResponseFuture();
            final Message<HttpResponse, Publisher<ByteBuffer>> responseMessage = responseFuture.get();
            assertThat(responseMessage, CoreMatchers.notNullValue());
            final HttpResponse response = responseMessage.getHead();
            assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final byte[] b2 = publisherToByteArray(responseMessage.getBody());
            assertThat(b1, CoreMatchers.equalTo(b2));
        }
    }

    @Test
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testConcurrentPostRequests() throws Exception {
        final H2TestServer server = startServer();
        server.register("/echo/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveEchoProcessor()));
        final HttpHost target = targetHost();
        final T client = startClient();

        final int reqCount = 500;
        final int maxSize = 128 * 1024;
        final Map<Long, StreamingTestCase> testCases = StreamingTestCase.generate(reqCount, maxSize);
        final BlockingQueue<StreamDescription> responses = new ArrayBlockingQueue<>(reqCount);

        for (final StreamingTestCase testCase : testCases.values()) {
            final ReactiveEntityProducer producer = new ReactiveEntityProducer(testCase.stream, testCase.length,
                    ContentType.APPLICATION_OCTET_STREAM, null);
            final AsyncRequestProducer request = AsyncRequestBuilder.post(target + "/echo/")
                    .setEntity(producer)
                    .build();

            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer(new FutureCallback<Message<HttpResponse, Publisher<ByteBuffer>>>() {
                @Override
                public void completed(final Message<HttpResponse, Publisher<ByteBuffer>> result) {
                    final Flowable<ByteBuffer> flowable = Flowable.fromPublisher(result.getBody())
                            .observeOn(Schedulers.io()); // Stream the data on an RxJava scheduler, not a client thread
                    Reactive3TestUtils.consumeStream(flowable).subscribe(responses::add);
                }
                @Override
                public void failed(final Exception ex) { }
                @Override
                public void cancelled() { }
            });
            client.execute(request, consumer, HttpClientContext.create(), null);
        }

        for (int i = 0; i < reqCount; i++) {
            final StreamDescription streamDescription = responses.take();
            final StreamingTestCase streamingTestCase = testCases.get(streamDescription.length);
            final long expectedLength = streamingTestCase.length;
            final long actualLength = streamDescription.length;
            Assertions.assertEquals(expectedLength, actualLength);

            final String expectedHash = streamingTestCase.expectedHash.get();
            final String actualHash = TextUtils.toHexString(streamDescription.md.digest());
            Assertions.assertEquals(expectedHash, actualHash);
        }
    }

    @Test
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testRequestExecutionFromCallback() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor()));
        final HttpHost target = targetHost();
        final T client = startClient();
        final int requestNum = 50;
        final AtomicInteger count = new AtomicInteger(requestNum);
        final Queue<Message<HttpResponse, Publisher<ByteBuffer>>> resultQueue = new ConcurrentLinkedQueue<>();
        final CountDownLatch countDownLatch = new CountDownLatch(requestNum);

        final FutureCallback<Message<HttpResponse, Publisher<ByteBuffer>>> callback = new FutureCallback<Message<HttpResponse, Publisher<ByteBuffer>>>() {
            @Override
            public void completed(final Message<HttpResponse, Publisher<ByteBuffer>> result) {
                try {
                    resultQueue.add(result);
                    if (count.decrementAndGet() > 0) {
                        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer(this);
                        client.execute(AsyncRequestBuilder.get(target + "/random/2048").build(), consumer, null);
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
            executorService.execute(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer(callback);
                    client.execute(AsyncRequestBuilder.get(target + "/random/2048").build(), consumer, null);
                }
            });
        }

        assertThat(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()), CoreMatchers.equalTo(true));

        executorService.shutdownNow();
        executorService.awaitTermination(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        for (;;) {
            final Message<HttpResponse, Publisher<ByteBuffer>> response = resultQueue.poll();
            if (response == null) {
                break;
            }
            assertThat(response.getHead().getCode(), CoreMatchers.equalTo(200));
        }
    }

    @Test
    public void testBadRequest() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor()));
        final HttpHost target = targetHost();
        final T client = startClient();
        final AsyncRequestProducer request = AsyncRequestBuilder.get(target + "/random/boom").build();
        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

        client.execute(request, consumer, null);

        final Future<Message<HttpResponse, Publisher<ByteBuffer>>> future = consumer.getResponseFuture();
        final HttpResponse response = future.get().getHead();
        assertThat(response, CoreMatchers.notNullValue());
        assertThat(response.getCode(), CoreMatchers.equalTo(400));
    }

    static String publisherToString(final Publisher<ByteBuffer> publisher) throws Exception {
        final byte[] bytes = publisherToByteArray(publisher);
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static byte[] publisherToByteArray(final Publisher<ByteBuffer> publisher) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (WritableByteChannel channel = Channels.newChannel(baos)) {
            final List<ByteBuffer> bufs = Flowable.fromPublisher(publisher)
                .toList()
                .blockingGet();
            if (bufs.isEmpty()) {
                return null;
            }
            for (final ByteBuffer buf : bufs) {
                channel.write(buf);
            }
        }
        return baos.toByteArray();
    }

    private static final class StreamingTestCase {
        final long length;
        final AtomicReference<String> expectedHash;
        final Flowable<ByteBuffer> stream;

        StreamingTestCase(final long length, final AtomicReference<String> expectedHash, final Flowable<ByteBuffer> stream) {
            this.length = length;
            this.expectedHash = expectedHash;
            this.stream = stream;
        }

        static Map<Long, StreamingTestCase> generate(final int numTestCases, final int maxSize) {
            final Map<Long, StreamingTestCase> testCases = new LinkedHashMap<>();
            int testCaseNum = 0;
            while (testCases.size() < numTestCases) {
                final long seed = 198723L * testCaseNum++;
                final int length = 1 + new Random(seed).nextInt(maxSize);
                final AtomicReference<String> expectedHash = new AtomicReference<>();
                final Flowable<ByteBuffer> stream = Reactive3TestUtils.produceStream(length, expectedHash);
                final StreamingTestCase streamingTestCase = new StreamingTestCase(length, expectedHash, stream);
                testCases.put((long) length, streamingTestCase);
            }
            return testCases;
        }
    }
}
