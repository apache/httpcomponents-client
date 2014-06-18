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

package org.apache.http.impl.client.integration;

import java.io.IOException;
import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.localserver.RandomHandler;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestConnectionReuse extends LocalServerTestBase {

    @Test
    public void testReuseOfPersistentConnections() throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer(LocalServerTestBase.ORIGIN))
            .add(new ResponseContent())
            .add(new ResponseConnControl()).build();

        this.serverBootstrap.setHttpProcessor(httpproc)
                .registerHandler("/random/*", new RandomHandler());

        this.connManager.setMaxTotal(5);
        this.connManager.setDefaultMaxPerRoute(5);

        final HttpHost target = start();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(
                    this.httpclient,
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

        // Expect some connection in the pool
        Assert.assertTrue(this.connManager.getTotalStats().getAvailable() > 0);
    }

    private static class AlwaysCloseConn implements HttpResponseInterceptor {

        @Override
        public void process(
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        }

    }

    @Test
    public void testReuseOfClosedConnections() throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer(LocalServerTestBase.ORIGIN))
            .add(new ResponseContent())
            .add(new AlwaysCloseConn()).build();

        this.serverBootstrap.setHttpProcessor(httpproc)
                .registerHandler("/random/*", new RandomHandler());

        this.connManager.setMaxTotal(5);
        this.connManager.setDefaultMaxPerRoute(5);

        final HttpHost target = start();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(
                    this.httpclient,
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

        // Expect zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());
    }

    @Test
    public void testReuseOfAbortedConnections() throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer(LocalServerTestBase.ORIGIN))
            .add(new ResponseContent())
            .add(new ResponseConnControl()).build();

        this.serverBootstrap.setHttpProcessor(httpproc)
                .registerHandler("/random/*", new RandomHandler());

        this.connManager.setMaxTotal(5);
        this.connManager.setDefaultMaxPerRoute(5);

        final HttpHost target = start();

        final WorkerThread[] workers = new WorkerThread[10];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(
                    this.httpclient,
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

        // Expect zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());
    }

    @Test
    public void testKeepAliveHeaderRespected() throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
                .add(new ResponseServer(LocalServerTestBase.ORIGIN))
            .add(new ResponseContent())
            .add(new ResponseConnControl())
            .add(new ResponseKeepAlive()).build();

        this.serverBootstrap.setHttpProcessor(httpproc)
                .registerHandler("/random/*", new RandomHandler());

        this.connManager.setMaxTotal(1);
        this.connManager.setDefaultMaxPerRoute(1);

        final HttpHost target = start();

        HttpResponse response = this.httpclient.execute(target, new HttpGet("/random/2000"));
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());

        response = this.httpclient.execute(target, new HttpGet("/random/2000"));
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());

        // Now sleep for 1.1 seconds and let the timeout do its work
        Thread.sleep(1100);
        response = this.httpclient.execute(target, new HttpGet("/random/2000"));
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());

        // Do another request just under the 1 second limit & make
        // sure we reuse that connection.
        Thread.sleep(500);
        response = this.httpclient.execute(target, new HttpGet("/random/2000"));
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());
    }

    private static class WorkerThread extends Thread {

        private final URI requestURI;
        private final HttpHost target;
        private final HttpClient httpclient;
        private final int repetitions;
        private final boolean forceClose;

        private volatile Exception exception;

        public WorkerThread(
                final HttpClient httpclient,
                final HttpHost target,
                final URI requestURI,
                final int repetitions,
                final boolean forceClose) {
            super();
            this.httpclient = httpclient;
            this.requestURI = requestURI;
            this.target = target;
            this.repetitions = repetitions;
            this.forceClose = forceClose;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < this.repetitions; i++) {
                    final HttpGet httpget = new HttpGet(this.requestURI);
                    final HttpResponse response = this.httpclient.execute(
                            this.target,
                            httpget);
                    if (this.forceClose) {
                        httpget.abort();
                    } else {
                        EntityUtils.consume(response.getEntity());
                    }
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
        public void process(final HttpResponse response, final HttpContext context)
                throws HttpException, IOException {
            final Header connection = response.getFirstHeader(HTTP.CONN_DIRECTIVE);
            if(connection != null) {
                if(!connection.getValue().equalsIgnoreCase("Close")) {
                    response.addHeader(HTTP.CONN_KEEP_ALIVE, "timeout=1");
                }
            }
        }
    }

}
