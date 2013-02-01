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
 *
 */

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionReleaseTrigger;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRequestDirector;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.message.BasicHeader;
import org.apache.http.mockup.SocketFactoryMockup;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *  Tests for Abort handling.
 */
public class TestAbortHandling extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        this.localServer = new LocalTestServer(null, null);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        this.httpclient = new DefaultHttpClient();
    }

    @Test
    public void testAbortRetry_HTTPCLIENT_1120() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        final CountDownLatch wait = new CountDownLatch(1);

        this.localServer.register("*", new HttpRequestHandler(){
            public void handle(HttpRequest request, HttpResponse response,
                    HttpContext context) throws HttpException, IOException {
                try {
                    wait.countDown(); // trigger abort
                    Thread.sleep(2000); // allow time for abort to happen
                    response.setStatusCode(HttpStatus.SC_OK);
                    StringEntity entity = new StringEntity("Whatever");
                    response.setEntity(entity);
                } catch (Exception e) {
                    response.setStatusCode(HttpStatus.SC_REQUEST_TIMEOUT);
                }
            }});

        String s = "http://localhost:" + port + "/path";
        final HttpGet httpget = new HttpGet(s);

        Thread t = new Thread() {
             @Override
            public void run(){
                 try {
                    wait.await();
                } catch (InterruptedException e) {
                }
                 httpget.abort();
             }
        };

        t.start();

        HttpContext context = new BasicHttpContext();
        try {
            this.httpclient.execute(getServerHttp(), httpget, context);
        } catch (IllegalStateException e) {
        } catch (IOException e) {
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
        Assert.assertNotNull("Request should exist",reqWrapper);
        Assert.assertEquals(1,((RequestWrapper) reqWrapper).getExecCount());
    }

    /**
     * Tests that if abort is called on an {@link AbortableHttpRequest} while
     * {@link DefaultRequestDirector} is allocating a connection, that the
     * connection is properly aborted.
     */
    @Test
    public void testAbortInAllocate() throws Exception {
        CountDownLatch connLatch = new CountDownLatch(1);
        CountDownLatch awaitLatch = new CountDownLatch(1);
        final ConMan conMan = new ConMan(connLatch, awaitLatch);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        final DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("http://www.example.com/a");

        new Thread(new Runnable() {
            public void run() {
                try {
                    client.execute(httpget, context);
                } catch(Throwable t) {
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
    }

    /**
     * Tests that an abort called after the connection has been retrieved
     * but before a release trigger is set does still abort the request.
     */
    @Test
    public void testAbortAfterAllocateBeforeRequest() throws Exception {
        this.localServer.register("*", new BasicService());

        CountDownLatch releaseLatch = new CountDownLatch(1);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        PoolingClientConnectionManager conMan = new PoolingClientConnectionManager(registry);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        final DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new CustomGet("a", releaseLatch);

        new Thread(new Runnable() {
            public void run() {
                try {
                    client.execute(getServerHttp(), httpget, context);
                } catch(Throwable t) {
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
        this.localServer.register("*", new BasicService());

        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        PoolingClientConnectionManager conMan = new PoolingClientConnectionManager(registry);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("a");

        new Thread(new Runnable() {
            public void run() {
                try {
                    try {
                        if(!startLatch.await(1, TimeUnit.SECONDS))
                            throw new RuntimeException("Took too long to start!");
                    } catch(InterruptedException interrupted) {
                        throw new RuntimeException("Never started!", interrupted);
                    }
                    client.execute(getServerHttp(), httpget, context);
                } catch(Throwable t) {
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
        final int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new BasicRedirectService(port));

        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        CountDownLatch connLatch = new CountDownLatch(1);
        CountDownLatch awaitLatch = new CountDownLatch(1);
        ConnMan4 conMan = new ConnMan4(registry, connLatch, awaitLatch);
        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>();
        final CountDownLatch getLatch = new CountDownLatch(1);
        final DefaultHttpClient client  = new DefaultHttpClient(conMan, new BasicHttpParams());
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("a");

        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpHost host = new HttpHost("127.0.0.1", port);
                    client.execute(host, httpget, context);
                } catch(Throwable t) {
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
    }


    /**
     * Tests that if a socket fails to connect, the allocated connection is
     * properly released back to the connection manager.
     */
    @Test
    public void testSocketConnectFailureReleasesConnection() throws Exception {
        ManagedClientConnection conn = Mockito.mock(ManagedClientConnection.class);
        Mockito.doThrow(new ConnectException()).when(conn).open(
                Mockito.any(HttpRoute.class),
                Mockito.any(HttpContext.class),
                Mockito.any(HttpParams.class));
        ClientConnectionRequest connrequest = Mockito.mock(ClientConnectionRequest.class);
        Mockito.when(connrequest.getConnection(
                Mockito.anyInt(), Mockito.any(TimeUnit.class))).thenReturn(conn);
        ClientConnectionManager connmgr = Mockito.mock(ClientConnectionManager.class);

        SchemeRegistry schemeRegistry = SchemeRegistryFactory.createDefault();

        Mockito.when(connmgr.requestConnection(
                Mockito.any(HttpRoute.class), Mockito.any())).thenReturn(connrequest);
        Mockito.when(connmgr.getSchemeRegistry()).thenReturn(schemeRegistry);

        final DefaultHttpClient client = new DefaultHttpClient(connmgr, new BasicHttpParams());
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("http://www.example.com/a");

        try {
            client.execute(httpget, context);
            Assert.fail("expected IOException");
        } catch(IOException expected) {}

        Mockito.verify(conn).abortConnection();
    }

    private static class BasicService implements HttpRequestHandler {
        public void handle(final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(200);
            response.setEntity(new StringEntity("Hello World"));
        }
    }

   private static class BasicRedirectService implements HttpRequestHandler {
        private int statuscode = HttpStatus.SC_SEE_OTHER;
        private int port;

        public BasicRedirectService(int port) {
            this.port = port;
        }

        public void handle(final HttpRequest request,
                final HttpResponse response, final HttpContext context)
                throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            response.setStatusLine(ver, this.statuscode);
            response.addHeader(new BasicHeader("Location", "http://localhost:"
                    + this.port + "/newlocation/"));
            response.addHeader(new BasicHeader("Connection", "close"));
        }
    }

    private static class ConnMan4 extends PoolingClientConnectionManager {
        private final CountDownLatch connLatch;
        private final CountDownLatch awaitLatch;

        public ConnMan4(SchemeRegistry schreg,
                CountDownLatch connLatch, CountDownLatch awaitLatch) {
            super(schreg);
            this.connLatch = connLatch;
            this.awaitLatch = awaitLatch;
        }

        @Override
        public ClientConnectionRequest requestConnection(HttpRoute route, Object state) {
            // If this is the redirect route, stub the return value
            // so-as to pretend the host is waiting on a slot...
            if(route.getTargetHost().getHostName().equals("localhost")) {
                final Thread currentThread = Thread.currentThread();

                return new ClientConnectionRequest() {

                    public void abortRequest() {
                        currentThread.interrupt();
                    }

                    public ManagedClientConnection getConnection(
                            long timeout, TimeUnit tunit)
                            throws InterruptedException,
                            ConnectionPoolTimeoutException {
                        connLatch.countDown(); // notify waiter that we're getting a connection

                        // zero usually means sleep forever, but CountDownLatch doesn't interpret it that way.
                        if(timeout == 0)
                            timeout = Integer.MAX_VALUE;

                        if(!awaitLatch.await(timeout, tunit))
                            throw new ConnectionPoolTimeoutException();

                        return Mockito.mock(ManagedClientConnection.class);
                    }
                };
            } else {
                return super.requestConnection(route, state);
            }
        }
    }


    static class ConMan implements ClientConnectionManager {
        private final CountDownLatch connLatch;
        private final CountDownLatch awaitLatch;

        public ConMan(CountDownLatch connLatch, CountDownLatch awaitLatch) {
            this.connLatch = connLatch;
            this.awaitLatch = awaitLatch;
        }

        public void closeIdleConnections(long idletime, TimeUnit tunit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        public void closeExpiredConnections() {
            throw new UnsupportedOperationException("just a mockup");
        }

        public ManagedClientConnection getConnection(HttpRoute route) {
            throw new UnsupportedOperationException("just a mockup");
        }

        public ManagedClientConnection getConnection(HttpRoute route,
                long timeout, TimeUnit tunit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        public ClientConnectionRequest requestConnection(
                final HttpRoute route,
                final Object state) {

            final Thread currentThread = Thread.currentThread();

            return new ClientConnectionRequest() {

                public void abortRequest() {
                    currentThread.interrupt();
                }

                public ManagedClientConnection getConnection(
                        long timeout, TimeUnit tunit)
                        throws InterruptedException,
                        ConnectionPoolTimeoutException {
                    connLatch.countDown(); // notify waiter that we're getting a connection

                    // zero usually means sleep forever, but CountDownLatch doesn't interpret it that way.
                    if(timeout == 0)
                        timeout = Integer.MAX_VALUE;

                    if(!awaitLatch.await(timeout, tunit))
                        throw new ConnectionPoolTimeoutException();

                    return Mockito.mock(ManagedClientConnection.class);
                }
            };
        }

        public HttpParams getParams() {
            throw new UnsupportedOperationException("just a mockup");
        }

        public SchemeRegistry getSchemeRegistry() {
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", 80, new SocketFactoryMockup(null)));
            return registry;
        }

        public void releaseConnection(ManagedClientConnection conn, long validDuration, TimeUnit timeUnit) {
            throw new UnsupportedOperationException("just a mockup");
        }

        public void shutdown() {
            throw new UnsupportedOperationException("just a mockup");
        }
    }

    private static class CustomGet extends HttpGet {
        private final CountDownLatch releaseTriggerLatch;

        public CustomGet(String uri, CountDownLatch releaseTriggerLatch) {
            super(uri);
            this.releaseTriggerLatch = releaseTriggerLatch;
        }

        @Override
        public void setReleaseTrigger(ConnectionReleaseTrigger releaseTrigger) throws IOException {
            try {
                if(!releaseTriggerLatch.await(1, TimeUnit.SECONDS))
                    throw new RuntimeException("Waited too long...");
            } catch(InterruptedException ie) {
                throw new RuntimeException(ie);
            }

            super.setReleaseTrigger(releaseTrigger);
        }

    }

}
