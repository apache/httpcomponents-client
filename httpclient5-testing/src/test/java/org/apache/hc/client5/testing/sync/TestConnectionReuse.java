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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestConnectionReuse {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    @Test
    public void testReuseOfPersistentConnections() throws Exception {
        final ClassicTestServer server =  testResources.startServer(  null, null, null);
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = testResources.startClient(
                builder -> builder
                        .setMaxConnTotal(5)
                        .setMaxConnPerRoute(5),
                builder -> {
                }
        );

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(
                    client,
                    target,
                    new URI("/random/2000"),
                    10, false);
        }

        for (final WorkerThread worker : workers) {
            worker.start();
        }
        for (final WorkerThread worker : workers) {
            worker.join(10000);
            final Exception ex = worker.getException();
            if (ex != null) {
                throw ex;
            }
        }

        // Expect leased connections to be returned
        Assertions.assertEquals(0, connManager.getTotalStats().getLeased());
        // Expect some connection in the pool
        Assertions.assertTrue(connManager.getTotalStats().getAvailable() > 0);
    }

    @Test
    public void testReuseOfPersistentConnectionsWithStreamedRequestAndResponse() throws Exception {
        final ClassicTestServer server =  testResources.startServer(  null, null, null);
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = testResources.startClient(
                builder -> builder
                        .setMaxConnTotal(5)
                        .setMaxConnPerRoute(5),
                builder -> {
                }
        );

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            final List<ClassicHttpRequest> requests = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                final HttpPost post = new HttpPost(new URI("/random/2000"));
                // non-repeatable
                post.setEntity(new InputStreamEntity(
                        new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)),
                        ContentType.APPLICATION_OCTET_STREAM));
                requests.add(post);
            }
            workers[i] = new WorkerThread(client, target, false, requests);
        }

        for (final WorkerThread worker : workers) {
            worker.start();
        }
        for (final WorkerThread worker : workers) {
            worker.join(10000);
            final Exception ex = worker.getException();
            if (ex != null) {
                throw ex;
            }
        }

        // Expect leased connections to be returned
        Assertions.assertEquals(0, connManager.getTotalStats().getLeased());
        // Expect some connection in the pool
        Assertions.assertTrue(connManager.getTotalStats().getAvailable() > 0);
    }

    private static class AlwaysCloseConn implements HttpResponseInterceptor {

        @Override
        public void process(
                final HttpResponse response,
                final EntityDetails entityDetails,
                final HttpContext context) throws HttpException, IOException {
            response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
        }

    }

    @Test
    public void testReuseOfClosedConnections() throws Exception {
        final HttpProcessor httpproc = HttpProcessors.customServer(null)
                .add(new AlwaysCloseConn())
                .build();
        final ClassicTestServer server =  testResources.startServer(  null, httpproc, null);
        final HttpHost target = targetHost();

        final CloseableHttpClient client = testResources.startClient(
                builder -> builder
                        .setMaxConnTotal(5)
                        .setMaxConnPerRoute(5),
                builder -> {
                }
        );

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(
                    client,
                    target,
                    new URI("/random/2000"),
                    10, false);
        }

        for (final WorkerThread worker : workers) {
            worker.start();
        }
        for (final WorkerThread worker : workers) {
            worker.join(10000);
            final Exception ex = worker.getException();
            if (ex != null) {
                throw ex;
            }
        }

        // Expect leased connections to be returned
        Assertions.assertEquals(0, connManager.getTotalStats().getLeased());
        // Expect zero connections in the pool
        Assertions.assertEquals(0, connManager.getTotalStats().getAvailable());
    }

    @Test
    public void testReuseOfAbortedConnections() throws Exception {
        final ClassicTestServer server =  testResources.startServer(  null, null, null);
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = testResources.startClient(
                builder -> builder
                        .setMaxConnTotal(5)
                        .setMaxConnPerRoute(5),
                builder -> {
                }
        );

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(
                    client,
                    target,
                    new URI("/random/2000"),
                    10, true);
        }

        for (final WorkerThread worker : workers) {
            worker.start();
        }
        for (final WorkerThread worker : workers) {
            worker.join(10000);
            final Exception ex = worker.getException();
            if (ex != null) {
                throw ex;
            }
        }

        // Expect leased connections to be returned
        Assertions.assertEquals(0, connManager.getTotalStats().getLeased());
        // Expect some connections in the pool
        Assertions.assertTrue(connManager.getTotalStats().getAvailable() > 0);
    }

    @Test
    public void testKeepAliveHeaderRespected() throws Exception {
        final HttpProcessor httpproc = HttpProcessors.customServer(null)
                .add(new ResponseKeepAlive())
                .build();
        final ClassicTestServer server =  testResources.startServer(  null, httpproc, null);
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = targetHost();

        final CloseableHttpClient client = testResources.startClient(
                builder -> builder
                        .setMaxConnTotal(1)
                        .setMaxConnPerRoute(1),
                builder -> {
                }
        );

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();


        client.execute(target, new HttpGet("/random/2000"), response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());

        client.execute(target, new HttpGet("/random/2000"), response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());

        // Now sleep for 1.1 seconds and let the timeout do its work
        Thread.sleep(1100);
        client.execute(target, new HttpGet("/random/2000"), response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());

        // Do another request just under the 1 second limit & make
        // sure we reuse that connection.
        Thread.sleep(500);
        client.execute(target, new HttpGet("/random/2000"), response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        // Expect leased connections to be returned
        Assertions.assertEquals(0, connManager.getTotalStats().getLeased());
        Assertions.assertEquals(1, connManager.getTotalStats().getAvailable());
    }

    private static class WorkerThread extends Thread {

        private final HttpHost target;
        private final CloseableHttpClient httpclient;
        private final boolean forceClose;
        private final List<ClassicHttpRequest> requests;

        private volatile Exception exception;

        public WorkerThread(
                final CloseableHttpClient httpclient,
                final HttpHost target,
                final URI requestURI,
                final int repetitions,
                final boolean forceClose) {
            super();
            this.httpclient = httpclient;
            this.target = target;
            this.forceClose = forceClose;
            this.requests = new ArrayList<>(repetitions);
            for (int i = 0; i < repetitions; i++) {
                requests.add(new HttpGet(requestURI));
            }
        }

        public WorkerThread(
                final CloseableHttpClient httpclient,
                final HttpHost target,
                final boolean forceClose,
                final List<ClassicHttpRequest> requests) {
            super();
            this.httpclient = httpclient;
            this.target = target;
            this.forceClose = forceClose;
            this.requests = requests;
        }

        @Override
        public void run() {
            try {
                for (final ClassicHttpRequest request : requests) {
                    this.httpclient.execute(this.target, request, response -> {
                        if (this.forceClose) {
                            response.close();
                        } else {
                            EntityUtils.consume(response.getEntity());
                        }
                        return null;
                    });
                }
            } catch (final Exception ex) {
                this.exception = ex;
            }
        }

        public Exception getException() {
            return exception;
        }

    }

    // A very basic keep-alive header interceptor, to add Keep-Alive: timeout=1
    // if there is no Connection: close header.
    private static class ResponseKeepAlive implements HttpResponseInterceptor {
        @Override
        public void process(
                final HttpResponse response,
                final EntityDetails entityDetails,
                final HttpContext context) throws HttpException, IOException {
            final Header connection = response.getFirstHeader(HttpHeaders.CONNECTION);
            if(connection != null) {
                if(!connection.getValue().equalsIgnoreCase("Close")) {
                    response.addHeader(HeaderElements.KEEP_ALIVE, "timeout=1");
                }
            }
        }
    }

}
