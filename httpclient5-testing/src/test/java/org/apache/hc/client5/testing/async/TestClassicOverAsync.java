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

import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.input.ProxyInputStream;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.testing.Result;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class TestClassicOverAsync extends AbstractClassicOverAsyncIntegrationTestBase {

    /**
     * Mocks an InputStream that records whether it has been closed.
     */
    private static final class MockInputStream extends ProxyInputStream {

        boolean closed;

        public MockInputStream(final InputStream proxy) {
            super(proxy);
        }

        @Override
        public synchronized void close() throws IOException {
            super.close();
            closed = true;
        }

    }

    /**
     * Mocks an HttpEntity that records whether it has been closed.
     */
    private static final class MockHttpEntityWrapper extends HttpEntityWrapper {

        boolean closed;
        final boolean streaming;
        final InputStream content;

        public MockHttpEntityWrapper(final HttpEntity wrappedEntity, final boolean streaming) throws UnsupportedOperationException, IOException {
            super(wrappedEntity);
            this.streaming = streaming;
            this.content = new MockInputStream(wrappedEntity.getContent());
        }

        @Override
        public synchronized void close() throws IOException {
            super.close();
            closed = true;
        }

        @Override
        public synchronized InputStream getContent() throws IOException {
            return content;
        }

        @Override
        public boolean isStreaming() {
            return streaming;
        }

    }

    public TestClassicOverAsync(final URIScheme scheme,
                                final ClientProtocolLevel clientProtocolLevel,
                                final ServerProtocolLevel serverProtocolLevel) {
        super(scheme, clientProtocolLevel, serverProtocolLevel);
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialGetRequests(final int contentSize) throws Exception {
        configureServer(bootstrap ->
                bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final CloseableHttpClient client = startClient();

        final int n = 10;
        for (int i = 0; i < n; i++) {
            client.execute(
                    ClassicRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/random/" + contentSize)
                            .build(),
                    response -> {
                        Assertions.assertEquals(200, response.getCode());
                        final byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                        Assertions.assertNotNull(bytes);
                        Assertions.assertEquals(contentSize, bytes.length);
                        return null;
                    });
        }
    }

    /**
     * Tests that the response entity is the client when an AssertionError is thrown from the response handler.
     * The content stream is <strong>not closed</strong> because the entity is non-streaming.
     */
    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialGetRequestsAssertionError(final int contentSize) throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();
        final CloseableHttpClient client = startClient();
        final int n = 10;
        final String detailMessage = "Simulated unit test failure";
        for (int i = 0; i < n; i++) {
            final AtomicReference<MockHttpEntityWrapper> entityRef = new AtomicReference<>();
            final AtomicReference<MockInputStream> contentRef = new AtomicReference<>();
            try {
                entityRef.set(null);
                client.execute(
                    ClassicRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/random/" + contentSize)
                            .build(),
                    response -> {
                        final MockHttpEntityWrapper entity = new MockHttpEntityWrapper(response.getEntity(), false);
                        entityRef.set(entity);
                        contentRef.set((MockInputStream) entity.getContent());
                        response.setEntity(entityRef.get());
                        throw new AssertionError(detailMessage);
                    });
                Assertions.fail("AssertionError expected from execute()");
            } catch (final AssertionError e) {
                // Note that we can't use Assertions.assertThrows() because it doesn't catch AssertionError.
                Assertions.assertEquals(detailMessage, e.getMessage());
            }
            Assertions.assertNotNull(entityRef.get());
            Assertions.assertTrue(entityRef.get().closed);
            Assertions.assertFalse(contentRef.get().closed);
        }
    }

    /**
     * Tests that the response entity is the client when an HttpException is thrown from the response handler.
     * The content stream is <strong>not closed</strong> because the entity is non-streaming.
     */
    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialGetRequestsHttpException(final int contentSize) throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();
        final CloseableHttpClient client = startClient();
        final int n = 10;
        final String detailMessage = "Simulated HttpException failure";
        for (int i = 0; i < n; i++) {
            final AtomicReference<MockHttpEntityWrapper> entityRef = new AtomicReference<>();
            final AtomicReference<MockInputStream> contentRef = new AtomicReference<>();
            final ClientProtocolException e = Assertions.assertThrows(ClientProtocolException.class, () -> {
                client.execute(
                        ClassicRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/random/" + contentSize)
                                .build(),
                        response -> {
                            final MockHttpEntityWrapper entity = new MockHttpEntityWrapper(response.getEntity(), false);
                            entityRef.set(entity);
                            contentRef.set((MockInputStream) entity.getContent());
                            response.setEntity(entityRef.get());
                            throw new HttpException(detailMessage);
                        });
                });
            // If an HttpException is thrown from the handler, and the entity is non-streaming, the stream is left open.
            Assertions.assertEquals(detailMessage, e.getCause().getMessage());
            Assertions.assertTrue(entityRef.get().closed);
            Assertions.assertFalse(contentRef.get().closed);
        }
    }

    /**
     * Tests that the response entity and its content are closed by the client.
     */
    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialGetRequestsStreaming(final int contentSize) throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();
        final CloseableHttpClient client = startClient();
        final int n = 10;
        for (int i = 0; i < n; i++) {
            final AtomicReference<MockInputStream> contentRef = new AtomicReference<>();
            final MockHttpEntityWrapper result = (MockHttpEntityWrapper) client.execute(
                    ClassicRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/random/" + contentSize)
                            .build(),
                    response -> {
                        Assertions.assertEquals(200, response.getCode());
                        final MockHttpEntityWrapper entity = new MockHttpEntityWrapper(response.getEntity(), true);
                        contentRef.set((MockInputStream) entity.getContent());
                        response.setEntity(entity);
                        return response.getEntity();
                    });
            // The plain use case where the entity and its input stream are closed.
            Assertions.assertTrue(result.closed);
            Assertions.assertTrue(contentRef.get().closed);
        }
    }


    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testConcurrentGetRequests(final int contentSize) throws Exception {
        configureServer(bootstrap ->
                bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final CloseableHttpClient client = startClient();

        final int n = 10;

        final ExecutorService executorService = Executors.newFixedThreadPool(n);
        final CountDownLatch countDownLatch = new CountDownLatch(n);
        final Queue<Future<Result<byte[]>>> resultQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < n; i++) {
            resultQueue.add(executorService.submit(() -> {
                final ClassicHttpRequest request = ClassicRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/" + contentSize)
                        .build();
                try {
                    return client.execute(
                            request,
                            response -> {
                                final byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                                countDownLatch.countDown();
                                return new Result<>(request, response, bytes);
                            });
                } catch (final RuntimeException | IOException ex) {
                    countDownLatch.countDown();
                    return new Result<>(request, ex);
                }
            }));
        }
        Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertEquals(n, resultQueue.size());
        for (final Future<Result<byte[]>> future : resultQueue) {
            final Result<byte[]> result = future.get();
            Assertions.assertNotNull(result.response);
            Assertions.assertEquals(200, result.response.getCode());
            Assertions.assertNotNull(result.content);
            Assertions.assertEquals(contentSize, result.content.length);
        }

        executorService.shutdownNow();
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialPostRequests(final int contentSize) throws Exception {
        configureServer(bootstrap ->
                bootstrap.register("/echo/*", AsyncEchoHandler::new));
        final HttpHost target = startServer();

        final CloseableHttpClient client = startClient();

        final int n = 10;
        for (int i = 0; i < n; i++) {
            final byte[] temp = new byte[contentSize];
            new Random(System.currentTimeMillis()).nextBytes(temp);
            client.execute(
                    ClassicRequestBuilder.post()
                            .setHttpHost(target)
                            .setPath("/echo/")
                            .setEntity(new ByteArrayEntity(temp, ContentType.DEFAULT_BINARY))
                            .build(),
                    response -> {
                        Assertions.assertEquals(200, response.getCode());
                        final byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                        Assertions.assertNotNull(bytes);
                        Assertions.assertArrayEquals(temp, bytes);
                        return null;
                    });
        }
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testConcurrentPostRequests(final int contentSize) throws Exception {
        configureServer(bootstrap ->
                bootstrap.register("/echo/*", AsyncEchoHandler::new));
        final HttpHost target = startServer();

        final CloseableHttpClient client = startClient();

        final int n = 10;

        final ExecutorService executorService = Executors.newFixedThreadPool(n);
        final CountDownLatch countDownLatch = new CountDownLatch(n);
        final Queue<Future<Result<byte[]>>> resultQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < n; i++) {
            final byte[] temp = new byte[contentSize];
            new Random(System.currentTimeMillis()).nextBytes(temp);
            resultQueue.add(executorService.submit(() -> {
                final ClassicHttpRequest request = ClassicRequestBuilder.post()
                        .setHttpHost(target)
                        .setPath("/echo/")
                        .setEntity(new ByteArrayEntity(temp, ContentType.DEFAULT_BINARY))
                        .build();
                try {
                    return client.execute(
                            request,
                            response -> {
                                final byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                                countDownLatch.countDown();
                                return new Result<>(request, response, bytes);
                            });
                } catch (final RuntimeException | IOException ex) {
                    countDownLatch.countDown();
                    return new Result<>(request, ex);
                }
            }));
        }
        Assertions.assertTrue(countDownLatch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertEquals(n, resultQueue.size());
        for (final Future<Result<byte[]>> future : resultQueue) {
            final Result<byte[]> result = future.get();
            if (result.exception != null) {
                Assertions.fail(result.exception);
            }
            Assertions.assertNotNull(result.response);
            Assertions.assertEquals(200, result.response.getCode());
            Assertions.assertNotNull(result.content);
            Assertions.assertEquals(contentSize, result.content.length);
        }

        executorService.shutdownNow();
    }

}