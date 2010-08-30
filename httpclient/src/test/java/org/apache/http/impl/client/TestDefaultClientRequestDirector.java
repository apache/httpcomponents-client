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
 */

package org.apache.http.impl.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionReleaseTrigger;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.conn.ClientConnAdapterMockup;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.message.BasicHeader;
import org.apache.http.mockup.SocketFactoryMockup;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultRequestDirector}
 */
public class TestDefaultClientRequestDirector extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        localServer = new LocalTestServer(null, null);
        localServer.registerDefaultHandlers();
        localServer.start();
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
        Assert.assertTrue("cause should be InterruptedException, was: " + throwableRef.get().getCause(),
                throwableRef.get().getCause() instanceof InterruptedException);
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

        SingleClientConnManager conMan = new SingleClientConnManager(registry);
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

        SingleClientConnManager conMan = new SingleClientConnManager(registry);
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
        final DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
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
        Assert.assertTrue("cause should be InterruptedException, was: " + throwableRef.get().getCause(),
                throwableRef.get().getCause() instanceof InterruptedException);
    }


    /**
     * Tests that if a socket fails to connect, the allocated connection is
     * properly released back to the connection manager.
     */
    @Test
    public void testSocketConnectFailureReleasesConnection() throws Exception {
        final ConnMan2 conMan = new ConnMan2();
        final DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
        final HttpContext context = new BasicHttpContext();
        final HttpGet httpget = new HttpGet("http://www.example.com/a");

        try {
            client.execute(httpget, context);
            Assert.fail("expected IOException");
        } catch(IOException expected) {}

        Assert.assertNotNull(conMan.allocatedConnection);
        Assert.assertSame(conMan.allocatedConnection, conMan.releasedConnection);
    }

    @Test
    public void testRequestFailureReleasesConnection() throws Exception {
        this.localServer.register("*", new ThrowingService());

        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        ConnMan3 conMan = new ConnMan3(registry);
        DefaultHttpClient client = new DefaultHttpClient(conMan, new BasicHttpParams());
        HttpGet httpget = new HttpGet("/a");

        try {
            client.execute(getServerHttp(), httpget);
            Assert.fail("expected IOException");
        } catch (IOException expected) {}

        Assert.assertNotNull(conMan.allocatedConnection);
        Assert.assertSame(conMan.allocatedConnection, conMan.releasedConnection);
    }

    private static class ThrowingService implements HttpRequestHandler {
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            throw new IOException();
        }
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

    private static class ConnMan4 extends ThreadSafeClientConnManager {
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

                        return new ClientConnAdapterMockup(ConnMan4.this);
                    }
                };
            } else {
                return super.requestConnection(route, state);
            }
        }
    }


    private static class ConnMan3 extends SingleClientConnManager {
        private ManagedClientConnection allocatedConnection;
        private ManagedClientConnection releasedConnection;

        public ConnMan3(SchemeRegistry schreg) {
            super(schreg);
        }

        @Override
        public ManagedClientConnection getConnection(HttpRoute route, Object state) {
            allocatedConnection = super.getConnection(route, state);
            return allocatedConnection;
        }

        @Override
        public void releaseConnection(ManagedClientConnection conn, long validDuration, TimeUnit timeUnit) {
            releasedConnection = conn;
            super.releaseConnection(conn, validDuration, timeUnit);
        }


    }

    static class ConnMan2 implements ClientConnectionManager {

        private ManagedClientConnection allocatedConnection;
        private ManagedClientConnection releasedConnection;

        public ConnMan2() {
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

            return new ClientConnectionRequest() {

                public void abortRequest() {
                    throw new UnsupportedOperationException("just a mockup");
                }

                public ManagedClientConnection getConnection(
                        long timeout, TimeUnit unit)
                        throws InterruptedException,
                        ConnectionPoolTimeoutException {
                    allocatedConnection = new ClientConnAdapterMockup(ConnMan2.this) {
                        @Override
                        public void open(HttpRoute route, HttpContext context,
                                HttpParams params) throws IOException {
                            throw new ConnectException();
                        }
                    };
                    return allocatedConnection;
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
            this.releasedConnection = conn;
        }

        public void shutdown() {
            throw new UnsupportedOperationException("just a mockup");
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

                    return new ClientConnAdapterMockup(ConMan.this);
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

    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_OK);
            StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    @Test
    public void testDefaultHostAtClientLevel() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpHost target = new HttpHost("localhost", port);

        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(ClientPNames.DEFAULT_HOST, target);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = client.execute(httpget);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testDefaultHostAtRequestLevel() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpHost target1 = new HttpHost("whatever", 80);
        HttpHost target2 = new HttpHost("localhost", port);

        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(ClientPNames.DEFAULT_HOST, target1);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);
        httpget.getParams().setParameter(ClientPNames.DEFAULT_HOST, target2);

        HttpResponse response = client.execute(httpget);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    private static class FaultyHttpRequestExecutor extends HttpRequestExecutor {

        private static final String MARKER = "marker";

        private final String failureMsg;

        public FaultyHttpRequestExecutor(String failureMsg) {
            this.failureMsg = failureMsg;
        }

        @Override
        public HttpResponse execute(
                final HttpRequest request,
                final HttpClientConnection conn,
                final HttpContext context) throws IOException, HttpException {

            HttpResponse response = super.execute(request, conn, context);
            Object marker = context.getAttribute(MARKER);
            if (marker == null) {
                context.setAttribute(MARKER, Boolean.TRUE);
                throw new IOException(failureMsg);
            }
            return response;
        }

    }

    private static class FaultyHttpClient extends DefaultHttpClient {

        private final String failureMsg;

        public FaultyHttpClient() {
            this("Oppsie");
        }

        public FaultyHttpClient(String failureMsg) {
            this.failureMsg = failureMsg;
        }

        @Override
        protected HttpRequestExecutor createRequestExecutor() {
            return new FaultyHttpRequestExecutor(failureMsg);
        }

    }

    @Test
    public void testAutoGeneratedHeaders() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        FaultyHttpClient client = new FaultyHttpClient();

        client.addRequestInterceptor(new HttpRequestInterceptor() {

            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                request.addHeader("my-header", "stuff");
            }

        }) ;

        client.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception,
                    int executionCount,
                    final HttpContext context) {
                return true;
            }

        });

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = client.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Assert.assertTrue(reqWrapper instanceof RequestWrapper);
        Header[] myheaders = reqWrapper.getHeaders("my-header");
        Assert.assertNotNull(myheaders);
        Assert.assertEquals(1, myheaders.length);
    }

    @Test(expected=ClientProtocolException.class)
    public void testNonRepeatableEntity() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        String failureMsg = "a message showing that this failed";

        FaultyHttpClient client = new FaultyHttpClient(failureMsg);
        client.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception,
                    int executionCount,
                    final HttpContext context) {
                return true;
            }

        });
        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpPost httppost = new HttpPost(s);
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));

        try {
            client.execute(getServerHttp(), httppost, context);
        } catch (ClientProtocolException ex) {
            Assert.assertTrue(ex.getCause() instanceof NonRepeatableRequestException);
            NonRepeatableRequestException nonRepeat = (NonRepeatableRequestException)ex.getCause();
            Assert.assertTrue(nonRepeat.getCause() instanceof IOException);
            Assert.assertEquals(failureMsg, nonRepeat.getCause().getMessage());
            throw ex;
        }
    }

}
