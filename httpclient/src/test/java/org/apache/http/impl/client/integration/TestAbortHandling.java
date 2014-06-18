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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *  Tests for Abort handling.
 */
@SuppressWarnings("static-access") // test code
public class TestAbortHandling extends LocalServerTestBase {

    @Test
    public void testAbortRetry_HTTPCLIENT_1120() throws Exception {
        final CountDownLatch wait = new CountDownLatch(1);

        this.serverBootstrap.registerHandler("*", new HttpRequestHandler() {
            @Override
            public void handle(final HttpRequest request, final HttpResponse response,
                               final HttpContext context) throws HttpException, IOException {
                try {
                    wait.countDown(); // trigger abort
                    Thread.sleep(2000); // allow time for abort to happen
                    response.setStatusCode(HttpStatus.SC_OK);
                    final StringEntity entity = new StringEntity("Whatever");
                    response.setEntity(entity);
                } catch (final Exception e) {
                    response.setStatusCode(HttpStatus.SC_REQUEST_TIMEOUT);
                }
            }
        });

        final HttpHost target = start();
        final HttpGet httpget = new HttpGet("/");

        final Thread t = new Thread() {
             @Override
            public void run(){
                 try {
                    wait.await();
                } catch (final InterruptedException e) {
                }
                 httpget.abort();
             }
        };

        t.start();

        final HttpClientContext context = HttpClientContext.create();
        try {
            this.httpclient.execute(target, httpget, context);
        } catch (final IllegalStateException e) {
        } catch (final IOException e) {
        }

        final HttpRequest reqWrapper = context.getRequest();
        Assert.assertNotNull("Request should exist",reqWrapper);
    }

    @Test
    public void testAbortInAllocate() throws Exception {
        final CountDownLatch connLatch = new CountDownLatch(1);
        final CountDownLatch awaitLatch = new CountDownLatch(1);
        final ConMan conMan = new ConMan(connLatch, awaitLatch);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        this.clientBuilder.setConnectionManager(conMan);
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("http://www.example.com/a");

        start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpclient.execute(httpget, context);
                } catch(final Throwable t) {
                    throwableRef.set(t);
                } finally {
                    getLatch.countDown();
                }
            }
        }).start();

        Assert.assertTrue("should have tried to get a connection", connLatch.await(1, TimeUnit.SECONDS));

        httpget.abort();

        Assert.assertTrue("should have finished get request", getLatch.await(1, TimeUnit.SECONDS));
        Assert.assertTrue("should be instanceof IOException, was: " + throwableRef.get(),
                throwableRef.get() instanceof IOException);
        Assert.assertTrue("cause should be InterruptedException, was: " + throwableRef.get().getCause(),
                throwableRef.get().getCause() instanceof InterruptedException);
    }

    /**
     * Tests that an abort called after the connection has been retrieved
     * but before a release trigger is set does still abort the request.
     */
    @Test
    public void testAbortAfterAllocateBeforeRequest() throws Exception {
        this.serverBootstrap.registerHandler("*", new BasicService());

        final CountDownLatch releaseLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new CustomGet("a", releaseLatch);

        final HttpHost target = start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpclient.execute(target, httpget, context);
                } catch(final Throwable t) {
                    throwableRef.set(t);
                } finally {
                    getLatch.countDown();
                }
            }
        }).start();

        Thread.sleep(100); // Give it a little time to proceed to release...

        httpget.abort();

        releaseLatch.countDown();

        Assert.assertTrue("should have finished get request", getLatch.await(1, TimeUnit.SECONDS));
        Assert.assertTrue("should be instanceof IOException, was: " + throwableRef.get(),
                throwableRef.get() instanceof IOException);
    }

    /**
     * Tests that an abort called completely before execute
     * still aborts the request.
     */
    @Test
    public void testAbortBeforeExecute() throws Exception {
        this.serverBootstrap.registerHandler("*", new BasicService());

        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("a");

        final HttpHost target = start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        if(!startLatch.await(1, TimeUnit.SECONDS)) {
                            throw new RuntimeException("Took too long to start!");
                        }
                    } catch(final InterruptedException interrupted) {
                        throw new RuntimeException("Never started!", interrupted);
                    }
                    httpclient.execute(target, httpget, context);
                } catch(final Throwable t) {
                    throwableRef.set(t);
                } finally {
                    getLatch.countDown();
                }
            }
        }).start();

        httpget.abort();
        startLatch.countDown();

        Assert.assertTrue("should have finished get request", getLatch.await(1, TimeUnit.SECONDS));
        Assert.assertTrue("should be instanceof IOException, was: " + throwableRef.get(),
                throwableRef.get() instanceof IOException);
    }

    /**
     * Tests that an abort called after a redirect has found a new host
     * still aborts in the correct place (while trying to get the new
     * host's route, not while doing the subsequent request).
     */
    @Test
    public void testAbortAfterRedirectedRoute() throws Exception {
        final UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
        this.serverBootstrap.setHandlerMapper(reqistry);

        final CountDownLatch connLatch = new CountDownLatch(1);
        final CountDownLatch awaitLatch = new CountDownLatch(1);
        final ConnMan4 conMan = new ConnMan4(connLatch, awaitLatch);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        this.clientBuilder.setConnectionManager(conMan);
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("a");

        final HttpHost target = start();
        reqistry.register("*", new BasicRedirectService(target.getPort()));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final HttpHost host = new HttpHost("127.0.0.1", target.getPort());
                    httpclient.execute(host, httpget, context);
                } catch(final Throwable t) {
                    throwableRef.set(t);
                } finally {
                    getLatch.countDown();
                }
            }
        }).start();

        Assert.assertTrue("should have tried to get a connection", connLatch.await(1, TimeUnit.SECONDS));

        httpget.abort();

        Assert.assertTrue("should have finished get request", getLatch.await(1, TimeUnit.SECONDS));
        Assert.assertTrue("should be instanceof IOException, was: " + throwableRef.get(),
                throwableRef.get() instanceof IOException);
        Assert.assertTrue("cause should be InterruptedException, was: " + throwableRef.get().getCause(),
                throwableRef.get().getCause() instanceof InterruptedException);
    }


    /**
     * Tests that if a socket fails to connect, the allocated connection is
     * properly released back to the connection manager.
     */
    @Test
    public void testSocketConnectFailureReleasesConnection() throws Exception {
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final ConnectionRequest connrequest = Mockito.mock(ConnectionRequest.class);
        Mockito.when(connrequest.get(
                Mockito.anyInt(), Mockito.any(TimeUnit.class))).thenReturn(conn);
        final HttpClientConnectionManager connmgr = Mockito.mock(HttpClientConnectionManager.class);
        Mockito.doThrow(new ConnectException()).when(connmgr).connect(
                Mockito.any(HttpClientConnection.class),
                Mockito.any(HttpRoute.class),
                Mockito.anyInt(),
                Mockito.any(HttpContext.class));

        Mockito.when(connmgr.requestConnection(
                Mockito.any(HttpRoute.class), Mockito.any())).thenReturn(connrequest);

        final HttpClient client = HttpClients.custom().setConnectionManager(connmgr).build();
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("http://www.example.com/a");

        try {
            client.execute(httpget, context);
            Assert.fail("expected IOException");
        } catch(final IOException expected) {}

        Mockito.verify(connmgr).releaseConnection(conn, null, 0, TimeUnit.MILLISECONDS);
    }

    private static class BasicService implements HttpRequestHandler {
        @Override
        public void handle(final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(200);
            response.setEntity(new StringEntity("Hello World"));
        }
    }

   private static class BasicRedirectService implements HttpRequestHandler {
        private final int statuscode = HttpStatus.SC_SEE_OTHER;
        private final int port;

        public BasicRedirectService(final int port) {
            this.port = port;
        }

        @Override
        public void handle(final HttpRequest request,
                final HttpResponse response, final HttpContext context)
                throws HttpException, IOException {
            final ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            response.setStatusLine(ver, this.statuscode);
            response.addHeader(new BasicHeader("Location", "http://localhost:"
                    + this.port + "/newlocation/"));
            response.addHeader(new BasicHeader("Connection", "close"));
        }
    }

    private static class ConnMan4 extends PoolingHttpClientConnectionManager {
        private final CountDownLatch connLatch;
        private final CountDownLatch awaitLatch;

        public ConnMan4(final CountDownLatch connLatch, final CountDownLatch awaitLatch) {
            super();
            this.connLatch = connLatch;
            this.awaitLatch = awaitLatch;
        }

        @Override
        public ConnectionRequest requestConnection(final HttpRoute route, final Object state) {
            // If this is the redirect route, stub the return value
            // so-as to pretend the host is waiting on a slot...
            if(route.getTargetHost().getHostName().equals("localhost")) {
                final Thread currentThread = Thread.currentThread();

                return new ConnectionRequest() {

                    @Override
                    public boolean cancel() {
                        currentThread.interrupt();
                        return true;
                    }

                    @Override
                    public HttpClientConnection get(
                            final long timeout,
                            final TimeUnit tunit) throws InterruptedException, ConnectionPoolTimeoutException {
                        connLatch.countDown(); // notify waiter that we're getting a connection

                        // zero usually means sleep forever, but CountDownLatch doesn't interpret it that way.
                        if(!awaitLatch.await(timeout > 0 ? timeout : Integer.MAX_VALUE, tunit)) {
                            throw new ConnectionPoolTimeoutException();
                        }

                        return Mockito.mock(HttpClientConnection.class);
                    }
                };
            } else {
                return super.requestConnection(route, state);
            }
        }
    }


    static class ConMan implements HttpClientConnectionManager {
        private final CountDownLatch connLatch;
        private final CountDownLatch awaitLatch;

        public ConMan(final CountDownLatch connLatch, final CountDownLatch awaitLatch) {
            this.connLatch = connLatch;
            this.awaitLatch = awaitLatch;
        }

        @Override
        public void closeIdleConnections(final long idletime, final TimeUnit tunit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        @Override
        public void closeExpiredConnections() {
            throw new UnsupportedOperationException("just a mockup");
        }

        public HttpClientConnection getConnection(final HttpRoute route,
                final long timeout, final TimeUnit tunit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        @Override
        public ConnectionRequest requestConnection(
                final HttpRoute route,
                final Object state) {

            final Thread currentThread = Thread.currentThread();

            return new ConnectionRequest() {

                @Override
                public boolean cancel() {
                    currentThread.interrupt();
                    return true;
                }

                @Override
                public HttpClientConnection get(
                        final long timeout,
                        final TimeUnit tunit) throws InterruptedException, ConnectionPoolTimeoutException {
                    connLatch.countDown(); // notify waiter that we're getting a connection

                    // zero usually means sleep forever, but CountDownLatch doesn't interpret it that way.
                    if(!awaitLatch.await(timeout > 0 ? timeout : Integer.MAX_VALUE, tunit)) {
                        throw new ConnectionPoolTimeoutException();
                    }

                    return Mockito.mock(HttpClientConnection.class);
                }

            };
        }

        @Override
        public void shutdown() {
        }

        public void close() {
        }

        @Override
        public void releaseConnection(
                final HttpClientConnection conn,
                final Object newState,
                final long validDuration, final TimeUnit timeUnit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        @Override
        public void connect(
                final HttpClientConnection conn,
                final HttpRoute route,
                final int connectTimeout,
                final HttpContext context) throws IOException {
            throw new UnsupportedOperationException("just a mockup");
        }

        @Override
        public void upgrade(
                final HttpClientConnection conn,
                final HttpRoute route,
                final HttpContext context) throws IOException {
            throw new UnsupportedOperationException("just a mockup");
        }

        @Override
        public void routeComplete(
                final HttpClientConnection conn,
                final HttpRoute route,
                final HttpContext context) throws IOException {
            throw new UnsupportedOperationException("just a mockup");
        }

        public void connect(
                final HttpClientConnection conn,
                final HttpHost host,
                final InetSocketAddress localAddress,
                final int connectTimeout,
                final HttpContext context) {
            throw new UnsupportedOperationException("just a mockup");
        }

        public void upgrade(
                final HttpClientConnection conn,
                final HttpHost host,
                final HttpContext context) {
            throw new UnsupportedOperationException("just a mockup");
        }
    }

    private static class CustomGet extends HttpGet {
        private final CountDownLatch releaseTriggerLatch;

        public CustomGet(final String uri, final CountDownLatch releaseTriggerLatch) {
            super(uri);
            this.releaseTriggerLatch = releaseTriggerLatch;
        }

        @Override
        public void setCancellable(final Cancellable cancellable) {
            try {
                if(!releaseTriggerLatch.await(1, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Waited too long...");
                }
            } catch(final InterruptedException ie) {
                throw new RuntimeException(ie);
            }

            super.setCancellable(cancellable);
        }

    }

}
