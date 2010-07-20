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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.RequestAcceptEncoding;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test case for how Content Codings are processed. By default, we want to do the right thing and
 * require no intervention from the user of HttpClient, but we still want to let clients do their
 * own thing if they so wish.
 */
public class TestContentCodings extends ServerTestBase {

    /**
     * Test for when we don't get an entity back; e.g. for a 204 or 304 response; nothing blows
     * up with the new behaviour.
     *
     * @throws Exception
     *             if there was a problem
     */
    @Test
    public void testResponseWithNoContent() throws Exception {
        this.localServer.register("*", new HttpRequestHandler() {

            /**
             * {@inheritDoc}
             */
            public void handle(
                    HttpRequest request,
                    HttpResponse response,
                    HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }
        });

        DefaultHttpClient client = createHttpClient();

        HttpGet request = new HttpGet("/some-resource");
        HttpResponse response = client.execute(getServerHttp(), request);
        Assert.assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusLine().getStatusCode());
        Assert.assertNull(response.getEntity());

        client.getConnectionManager().shutdown();
    }

    /**
     * Test for when we are handling content from a server that has correctly interpreted RFC2616
     * to return RFC1950 streams for <code>deflate</code> content coding.
     *
     * @throws Exception
     * @see DeflateDecompressingEntity
     */
    @Test
    public void testDeflateSupportForServerReturningRfc1950Stream() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        this.localServer.register("*", createDeflateEncodingRequestHandler(entityText, false));

        DefaultHttpClient client = createHttpClient();

        HttpGet request = new HttpGet("/some-resource");
        HttpResponse response = client.execute(getServerHttp(), request);
        Assert.assertEquals("The entity text is correctly transported", entityText,
                EntityUtils.toString(response.getEntity()));

        client.getConnectionManager().shutdown();
    }

    /**
     * Test for when we are handling content from a server that has incorrectly interpreted RFC2616
     * to return RFC1951 streams for <code>deflate</code> content coding.
     *
     * @throws Exception
     * @see DeflateDecompressingEntity
     */
    @Test
    public void testDeflateSupportForServerReturningRfc1951Stream() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        this.localServer.register("*", createDeflateEncodingRequestHandler(entityText, true));

        DefaultHttpClient client = createHttpClient();
        HttpGet request = new HttpGet("/some-resource");
        HttpResponse response = client.execute(getServerHttp(), request);
        Assert.assertEquals("The entity text is correctly transported", entityText,
                EntityUtils.toString(response.getEntity()));

        client.getConnectionManager().shutdown();
    }

    /**
     * Test for a server returning gzipped content.
     *
     * @throws Exception
     */
    @Test
    public void testGzipSupport() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        this.localServer.register("*", createGzipEncodingRequestHandler(entityText));

        DefaultHttpClient client = createHttpClient();
        HttpGet request = new HttpGet("/some-resource");
        HttpResponse response = client.execute(getServerHttp(), request);
        Assert.assertEquals("The entity text is correctly transported", entityText,
                EntityUtils.toString(response.getEntity()));

        client.getConnectionManager().shutdown();
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

        this.localServer.register("*", createGzipEncodingRequestHandler(entityText));

        /*
         * Create a load of workers which will access the resource. Half will use the default
         * gzip behaviour; half will require identity entity.
         */
        int clients = 100;

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(schemeRegistry);
        cm.setMaxTotal(clients);

        final HttpClient httpClient = new DefaultHttpClient(cm);

        ExecutorService executor = Executors.newFixedThreadPool(clients);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(clients);

        List<WorkerTask> workers = new ArrayList<WorkerTask>();

        for (int i = 0; i < clients; ++i) {
            workers.add(new WorkerTask(httpClient, i % 2 == 0, startGate, endGate));
        }

        for (WorkerTask workerTask : workers) {

            /* Set them all in motion, but they will block until we call startGate.countDown(). */
            executor.execute(workerTask);
        }

        startGate.countDown();

        /* Wait for the workers to complete. */
        endGate.await();

        for (WorkerTask workerTask : workers) {
            if (workerTask.isFailed()) {
                Assert.fail("A worker failed");
            }
            Assert.assertEquals(entityText, workerTask.getText());
        }
    }

    /**
     * Checks that we can turn off the new Content-Coding support. The default is that it's on, but that is a change
     * to existing behaviour and might not be desirable in some situations.
     *
     * @throws Exception
     */
    @Test
    public void testCanBeDisabledAtRequestTime() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        /* Assume that server will see an Accept-Encoding header. */
        final boolean [] sawAcceptEncodingHeader = { true };

        this.localServer.register("*", new HttpRequestHandler() {

            /**
             * {@inheritDoc}
             */
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity(entityText));
                response.addHeader("Content-Type", "text/plain");
                Header[] acceptEncodings = request.getHeaders("Accept-Encoding");

                sawAcceptEncodingHeader[0] = acceptEncodings.length > 0;
            }

        });

        AbstractHttpClient client = createHttpClient();
        HttpGet request = new HttpGet("/some-resource");

        client.removeRequestInterceptorByClass(RequestAcceptEncoding.class);

        HttpResponse response = client.execute(getServerHttp(), request);

        Assert.assertFalse("The Accept-Encoding header was not there", sawAcceptEncodingHeader[0]);
        Assert.assertEquals("The entity isn't treated as gzip or zip content", entityText,
                EntityUtils.toString(response.getEntity()));

        client.getConnectionManager().shutdown();
    }

    /**
     * Test that the returned {@link HttpEntity} in the response correctly overrides
     * {@link HttpEntity#writeTo(OutputStream)} for gzip-encoding.
     *
     * @throws Exception
     */
    @Test
    public void testHttpEntityWriteToForGzip() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        this.localServer.register("*", createGzipEncodingRequestHandler(entityText));

        DefaultHttpClient client = createHttpClient();
        HttpGet request = new HttpGet("/some-resource");
        HttpResponse response = client.execute(getServerHttp(), request);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        response.getEntity().writeTo(out);

        Assert.assertEquals(entityText, out.toString("utf-8"));

        client.getConnectionManager().shutdown();
    }

    /**
     * Test that the returned {@link HttpEntity} in the response correctly overrides
     * {@link HttpEntity#writeTo(OutputStream)} for deflate-encoding.
     *
     * @throws Exception
     */
    @Test
    public void testHttpEntityWriteToForDeflate() throws Exception {
        final String entityText = "Hello, this is some plain text coming back.";

        this.localServer.register("*", createDeflateEncodingRequestHandler(entityText, true));

        DefaultHttpClient client = createHttpClient();
        HttpGet request = new HttpGet("/some-resource");
        HttpResponse response = client.execute(getServerHttp(), request);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        response.getEntity().writeTo(out);

        Assert.assertEquals(entityText, out.toString("utf-8"));

        client.getConnectionManager().shutdown();
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
            public void handle(
                    HttpRequest request,
                    HttpResponse response,
                    HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity(entityText));
                response.addHeader("Content-Type", "text/plain");
                Header[] acceptEncodings = request.getHeaders("Accept-Encoding");

                for (Header header : acceptEncodings) {
                    for (HeaderElement element : header.getElements()) {
                        if ("deflate".equalsIgnoreCase(element.getName())) {
                            response.addHeader("Content-Encoding", "deflate");

                            /* Gack. DeflaterInputStream is Java 6. */
                            // response.setEntity(new InputStreamEntity(new DeflaterInputStream(new
                            // ByteArrayInputStream(
                            // entityText.getBytes("utf-8"))), -1));
                            byte[] uncompressed = entityText.getBytes("utf-8");
                            Deflater compressor = new Deflater(Deflater.DEFAULT_COMPRESSION, rfc1951);
                            compressor.setInput(uncompressed);
                            compressor.finish();
                            byte[] output = new byte[100];
                            int compressedLength = compressor.deflate(output);
                            byte[] compressed = new byte[compressedLength];
                            System.arraycopy(output, 0, compressed, 0, compressedLength);
                            response.setEntity(new InputStreamEntity(
                                    new ByteArrayInputStream(compressed), compressedLength));
                            return;
                        }
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
            public void handle(
                    HttpRequest request,
                    HttpResponse response,
                    HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity(entityText));
                response.addHeader("Content-Type", "text/plain");
                Header[] acceptEncodings = request.getHeaders("Accept-Encoding");

                for (Header header : acceptEncodings) {
                    for (HeaderElement element : header.getElements()) {
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
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            OutputStream out = new GZIPOutputStream(bytes);

                            ByteArrayInputStream uncompressed = new ByteArrayInputStream(
                                    entityText.getBytes("utf-8"));

                            byte[] buf = new byte[60];

                            int n;
                            while ((n = uncompressed.read(buf)) != -1) {
                                out.write(buf, 0, n);
                            }

                            out.close();

                            byte[] arr = bytes.toByteArray();
                            response.setEntity(new InputStreamEntity(new ByteArrayInputStream(arr),
                                    arr.length));

                            return;
                        }
                    }
                }
            }
        };
    }

    private DefaultHttpClient createHttpClient() {
        return new ContentEncodingHttpClient();
    }

    /**
     * Sub-ordinate task passed off to a different thread to be executed.
     *
     * @author jabley
     *
     */
    class WorkerTask implements Runnable {

        /**
         * The {@link HttpClient} used to make requests.
         */
        private final HttpClient client;

        /**
         * The {@link HttpRequest} to be executed.
         */
        private final HttpGet request;

        /**
         * Flag indicating if there were failures.
         */
        private boolean failed = false;

        /**
         * The latch that this runnable instance should wait on.
         */
        private final CountDownLatch startGate;

        /**
         * The latch that this runnable instance should countdown on when the runnable is finished.
         */
        private final CountDownLatch endGate;

        /**
         * The text returned from the HTTP server.
         */
        private String text;

        WorkerTask(HttpClient client, boolean identity, CountDownLatch startGate, CountDownLatch endGate) {
            this.client = client;
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
        public void run() {
            try {
                startGate.await();
                try {
                    HttpResponse response = client.execute(TestContentCodings.this.getServerHttp(), request);
                    text = EntityUtils.toString(response.getEntity());
                } catch (Exception e) {
                    failed = true;
                } finally {
                    endGate.countDown();
                }
            } catch (InterruptedException e) {

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
