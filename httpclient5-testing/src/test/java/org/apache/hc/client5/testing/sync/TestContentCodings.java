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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test case for how Content Codings are processed. By default, we want to do the right thing and
 * require no intervention from the user of HttpClient, but we still want to let clients do their
 * own thing if they so wish.
 */
public abstract class TestContentCodings {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources;

    protected TestContentCodings(final URIScheme scheme) {
        this.testResources = new TestClientResources(scheme, TIMEOUT);
    }

    public URIScheme scheme() {
        return testResources.scheme();
    }

    public ClassicTestServer startServer() throws IOException {
        return testResources.startServer(null, null, null);
    }

    public CloseableHttpClient startClient(final Consumer<HttpClientBuilder> clientCustomizer) throws Exception {
        return testResources.startClient(clientCustomizer);
    }

    public CloseableHttpClient startClient() throws Exception {
        return testResources.startClient(builder -> {});
    }

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    /**
     * Test for when we don't get an entity back; e.g. for a 204 or 304 response; nothing blows
     * up with the new behaviour.
     *
     * @throws Exception
     *             if there was a problem
     */
    @Test
    public void testResponseWithNoContent() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("*", new HttpRequestHandler() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setCode(HttpStatus.SC_NO_CONTENT);
            }
        });

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        client.execute(target, request, response -> {
            Assertions.assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
            Assertions.assertNull(response.getEntity());
            return null;
        });
    }

    /**
     * Test for when we are handling content from a server that has correctly interpreted RFC2616
     * to return RFC1950 streams for {@code deflate} content coding.
     *
     * @throws Exception
     */
    @Test
    public void testDeflateSupportForServerReturningRfc1950Stream() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createDeflateEncodingRequestHandler(entityText, false));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        client.execute(target, request, response -> {
            Assertions.assertEquals(entityText, EntityUtils.toString(response.getEntity()),
                    "The entity text is correctly transported");
            return null;
        });
    }

    /**
     * Test for when we are handling content from a server that has incorrectly interpreted RFC2616
     * to return RFC1951 streams for {@code deflate} content coding.
     *
     * @throws Exception
     */
    @Test
    public void testDeflateSupportForServerReturningRfc1951Stream() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createDeflateEncodingRequestHandler(entityText, true));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        client.execute(target, request, response -> {
            Assertions.assertEquals(entityText, EntityUtils.toString(response.getEntity()),
                    "The entity text is correctly transported");
            return null;
        });
    }

    /**
     * Test for a server returning gzipped content.
     *
     * @throws Exception
     */
    @Test
    public void testGzipSupport() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createGzipEncodingRequestHandler(entityText));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        client.execute(target, request, response -> {
            Assertions.assertEquals(entityText, EntityUtils.toString(response.getEntity()),
                    "The entity text is correctly transported");
            return null;
        });
    }

    /**
     * Try with a bunch of client threads, to check that it's thread-safe.
     *
     * @throws Exception
     *             if there was a problem
     */
    @Test
    public void testThreadSafetyOfContentCodings() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createGzipEncodingRequestHandler(entityText));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();
        final PoolingHttpClientConnectionManager connManager = testResources.connManager();

        /*
         * Create a load of workers which will access the resource. Half will use the default
         * gzip behaviour; half will require identity entity.
         */
        final int clients = 10;

        connManager.setMaxTotal(clients);

        final ExecutorService executor = Executors.newFixedThreadPool(clients);

        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(clients);

        final List<WorkerTask> workers = new ArrayList<>();

        for (int i = 0; i < clients; ++i) {
            workers.add(new WorkerTask(client, target, i % 2 == 0, startGate, endGate));
        }

        for (final WorkerTask workerTask : workers) {

            /* Set them all in motion, but they will block until we call startGate.countDown(). */
            executor.execute(workerTask);
        }

        startGate.countDown();

        /* Wait for the workers to complete. */
        endGate.await();

        for (final WorkerTask workerTask : workers) {
            if (workerTask.isFailed()) {
                Assertions.fail("A worker failed");
            }
            Assertions.assertEquals(entityText, workerTask.getText());
        }
    }

    @Test
    public void testHttpEntityWriteToForGzip() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createGzipEncodingRequestHandler(entityText));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        client.execute(target, request, response -> {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.getEntity().writeTo(out);
            Assertions.assertEquals(entityText, out.toString("utf-8"));
            return null;
        });

    }

    @Test
    public void testHttpEntityWriteToForDeflate() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createDeflateEncodingRequestHandler(entityText, true));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        client.execute(target, request, response -> {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.getEntity().writeTo(out);
            Assertions.assertEquals(entityText, out.toString("utf-8"));
            return out;
        });
    }

    @Test
    public void gzipResponsesWorkWithBasicResponseHandler() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createGzipEncodingRequestHandler(entityText));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        final String response = client.execute(target, request, new BasicHttpClientResponseHandler());
        Assertions.assertEquals(entityText, response, "The entity text is correctly transported");
    }

    @Test
    public void deflateResponsesWorkWithBasicResponseHandler() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        final ClassicTestServer server = startServer();
        server.registerHandler("*", createDeflateEncodingRequestHandler(entityText, false));

        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpGet request = new HttpGet("/some-resource");
        final String response = client.execute(target, request, new BasicHttpClientResponseHandler());
        Assertions.assertEquals(entityText, response, "The entity text is correctly transported");
    }

    /**
     * Creates a new {@link HttpRequestHandler} that will attempt to provide a deflate stream
     * Content-Coding.
     *
     * @param entityText
     *            the non-null String entity text to be returned by the server
     * @param rfc1951
     *            if true, then the stream returned will be a raw RFC1951 deflate stream, which
     *            some servers return as a result of misinterpreting the HTTP 1.1 RFC. If false,
     *            then it will return an RFC2616 compliant deflate encoded zlib stream.
     * @return a non-null {@link HttpRequestHandler}
     */
    private HttpRequestHandler createDeflateEncodingRequestHandler(
            final String entityText, final boolean rfc1951) {
        return new HttpRequestHandler() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity(entityText));
                response.addHeader("Content-Type", "text/plain");
                final Iterator<HeaderElement> it = MessageSupport.iterate(request, "Accept-Encoding");
                while (it.hasNext()) {
                    final HeaderElement element = it.next();
                    if ("deflate".equalsIgnoreCase(element.getName())) {
                        response.addHeader("Content-Encoding", "deflate");

                            /* Gack. DeflaterInputStream is Java 6. */
                        // response.setEntity(new InputStreamEntity(new DeflaterInputStream(new
                        // ByteArrayInputStream(
                        // entityText.getBytes("utf-8"))), -1));
                        final byte[] uncompressed = entityText.getBytes(StandardCharsets.UTF_8);
                        final Deflater compressor = new Deflater(Deflater.DEFAULT_COMPRESSION, rfc1951);
                        compressor.setInput(uncompressed);
                        compressor.finish();
                        final byte[] output = new byte[100];
                        final int compressedLength = compressor.deflate(output);
                        final byte[] compressed = new byte[compressedLength];
                        System.arraycopy(output, 0, compressed, 0, compressedLength);
                        response.setEntity(new InputStreamEntity(
                                new ByteArrayInputStream(compressed), compressedLength, null));
                        return;
                    }
                }
            }
        };
    }

    /**
     * Returns an {@link HttpRequestHandler} implementation that will attempt to provide a gzip
     * Content-Encoding.
     *
     * @param entityText
     *            the non-null String entity to be returned by the server
     * @return a non-null {@link HttpRequestHandler}
     */
    private HttpRequestHandler createGzipEncodingRequestHandler(final String entityText) {
        return new HttpRequestHandler() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity(entityText));
                response.addHeader("Content-Type", "text/plain");
                response.addHeader("Content-Type", "text/plain");
                final Iterator<HeaderElement> it = MessageSupport.iterate(request, "Accept-Encoding");
                while (it.hasNext()) {
                    final HeaderElement element = it.next();
                    if ("gzip".equalsIgnoreCase(element.getName())) {
                        response.addHeader("Content-Encoding", "gzip");

                        /*
                         * We have to do a bit more work with gzip versus deflate, since
                         * Gzip doesn't appear to have an equivalent to DeflaterInputStream in
                         * the JDK.
                         *
                         * UPDATE: DeflaterInputStream is Java 6 anyway, so we have to do a bit
                         * of work there too!
                         */
                        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        final OutputStream out = new GZIPOutputStream(bytes);

                        final ByteArrayInputStream uncompressed = new ByteArrayInputStream(
                                entityText.getBytes(StandardCharsets.UTF_8));

                        final byte[] buf = new byte[60];

                        int n;
                        while ((n = uncompressed.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }

                        out.close();

                        final byte[] arr = bytes.toByteArray();
                        response.setEntity(new InputStreamEntity(new ByteArrayInputStream(arr),
                                arr.length, null));

                        return;
                    }
                }
            }
        };
    }

    /**
     * Sub-ordinate task passed off to a different thread to be executed.
     *
     * @author jabley
     *
     */
    class WorkerTask implements Runnable {

        private final CloseableHttpClient client;
        private final HttpHost target;
        private final HttpGet request;
        private final CountDownLatch startGate;
        private final CountDownLatch endGate;

        private boolean failed;
        private String text;

        WorkerTask(final CloseableHttpClient client, final HttpHost target, final boolean identity, final CountDownLatch startGate, final CountDownLatch endGate) {
            this.client = client;
            this.target = target;
            this.request = new HttpGet("/some-resource");
            if (identity) {
                request.addHeader("Accept-Encoding", "identity");
            }
            this.startGate = startGate;
            this.endGate = endGate;
        }

        /**
         * Returns the text of the HTTP entity.
         *
         * @return a String - may be null.
         */
        public String getText() {
            return this.text;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            try {
                startGate.await();
                try {
                    text = client.execute(target, request, response ->
                            EntityUtils.toString(response.getEntity()));
                } catch (final Exception e) {
                    failed = true;
                } finally {
                    endGate.countDown();
                }
            } catch (final InterruptedException ignore) {
            }
        }

        /**
         * Returns true if this task failed, otherwise false.
         *
         * @return a flag
         */
        boolean isFailed() {
            return this.failed;
        }
    }
}
