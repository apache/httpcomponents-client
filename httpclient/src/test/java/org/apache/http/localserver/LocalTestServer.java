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

package org.apache.http.localserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.apache.http.util.Asserts;

/**
 * Local HTTP server for tests that require one.
 * Based on the <code>ElementalHttpServer</code> example in HttpCore.
 */
public class LocalTestServer {

    public final static String ORIGIN = "LocalTestServer/1.1";

    /**
     * The local address to bind to.
     * The host is an IP number rather than "localhost" to avoid surprises
     * on hosts that map "localhost" to an IPv6 address or something else.
     * The port is 0 to let the system pick one.
     */
    public final static InetSocketAddress TEST_SERVER_ADDR =
        new InetSocketAddress("127.0.0.1", 0);

    /** The request handler registry. */
    private final UriHttpRequestHandlerMapper handlerRegistry;

    private final HttpService httpservice;

    /** Optional SSL context */
    private final SSLContext sslcontext;

    /** Optional flag whether to force SSL context */
    private final boolean forceSSLAuth;

    /** The server socket, while being served. */
    private volatile ServerSocket servicedSocket;

    /** The request listening thread, while listening. */
    private volatile ListenerThread listenerThread;

    /** Set of active worker threads */
    private final Set<Worker> workers;

    /** The number of connections this accepted. */
    private final AtomicInteger acceptedConnections = new AtomicInteger(0);

    private volatile int timeout;

    /**
     * Creates a new test server.
     *
     * @param proc      the HTTP processors to be used by the server, or
     *                  <code>null</code> to use a
     *                  {@link #newProcessor default} processor
     * @param reuseStrat the connection reuse strategy to be used by the
     *                  server, or <code>null</code> to use
     *                  {@link #newConnectionReuseStrategy() default}
     *                  strategy.
     * @param responseFactory the response factory to be used by the
     *                  server, or <code>null</code> to use
     *                  {@link #newHttpResponseFactory() default} factory.
     * @param expectationVerifier the expectation verifier. May be
     *                  <code>null</code>.
     * @param sslcontext optional SSL context if the server is to leverage
     *                   SSL/TLS transport security
     * @param forceSSLAuth whether or not the server needs to enforce client auth
     */
    public LocalTestServer(
            final HttpProcessor proc,
            final ConnectionReuseStrategy reuseStrat,
            final HttpResponseFactory responseFactory,
            final HttpExpectationVerifier expectationVerifier,
            final SSLContext sslcontext,
            final boolean forceSSLAuth) {
        super();
        this.handlerRegistry = new UriHttpRequestHandlerMapper();
        this.workers = Collections.synchronizedSet(new HashSet<Worker>());
        this.httpservice = new HttpService(
            proc != null ? proc : newProcessor(),
            reuseStrat != null ? reuseStrat: newConnectionReuseStrategy(),
            responseFactory != null ? responseFactory: newHttpResponseFactory(),
            handlerRegistry,
            expectationVerifier);
        this.sslcontext = sslcontext;
        this.forceSSLAuth = forceSSLAuth;
    }

    public LocalTestServer(
            final HttpProcessor proc,
            final ConnectionReuseStrategy reuseStrat) {
        this(proc, reuseStrat, null, null, null, false);
    }

    /**
     * Creates a new test server with SSL/TLS encryption.
     *
     * @param sslcontext SSL context
     * @param forceSSLAuth whether or not the server needs to enforce client auth
     */
    public LocalTestServer(final SSLContext sslcontext, final boolean forceSSLAuth) {
        this(null, null, null, null, sslcontext, forceSSLAuth);
    }

    /**
     * Creates a new test server with SSL/TLS encryption.
     *
     * @param sslcontext SSL context
     */
    public LocalTestServer(final SSLContext sslcontext) {
        this(null, null, null, null, sslcontext, false);
    }

    /**
     * Obtains an HTTP protocol processor with default interceptors.
     *
     * @return  a protocol processor for server-side use
     */
    protected HttpProcessor newProcessor() {
        return new ImmutableHttpProcessor(
                new HttpResponseInterceptor[] {
                        new ResponseDate(),
                        new ResponseServer(ORIGIN),
                        new ResponseContent(),
                        new ResponseConnControl()
                });
    }

    protected ConnectionReuseStrategy newConnectionReuseStrategy() {
        return DefaultConnectionReuseStrategy.INSTANCE;
    }

    protected HttpResponseFactory newHttpResponseFactory() {
        return DefaultHttpResponseFactory.INSTANCE;
    }

    /**
     * Returns the number of connections this test server has accepted.
     */
    public int getAcceptedConnectionCount() {
        return acceptedConnections.get();
    }

    /**
     * {@link #register Registers} a set of default request handlers.
     * <pre>
     * URI pattern      Handler
     * -----------      -------
     * /echo/*          {@link EchoHandler EchoHandler}
     * /random/*        {@link RandomHandler RandomHandler}
     * </pre>
     */
    public void registerDefaultHandlers() {
        handlerRegistry.register("/echo/*", new EchoHandler());
        handlerRegistry.register("/random/*", new RandomHandler());
    }

    /**
     * Registers a handler with the local registry.
     *
     * @param pattern   the URL pattern to match
     * @param handler   the handler to apply
     */
    public void register(final String pattern, final HttpRequestHandler handler) {
        handlerRegistry.register(pattern, handler);
    }

    /**
     * Unregisters a handler from the local registry.
     *
     * @param pattern   the URL pattern
     */
    public void unregister(final String pattern) {
        handlerRegistry.unregister(pattern);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    /**
     * Starts this test server.
     */
    public void start() throws Exception {
        Asserts.check(servicedSocket == null, "Already running");
        final ServerSocket ssock;
        if (sslcontext != null) {
            final SSLServerSocketFactory sf = sslcontext.getServerSocketFactory();
            final SSLServerSocket sslsock = (SSLServerSocket) sf.createServerSocket();
            if (forceSSLAuth) {
                sslsock.setNeedClientAuth(true);
            } else {
                sslsock.setWantClientAuth(true);
            }
            ssock = sslsock;
        } else {
            ssock = new ServerSocket();
        }

        ssock.setReuseAddress(true); // probably pointless for port '0'
        ssock.bind(TEST_SERVER_ADDR);
        servicedSocket = ssock;

        listenerThread = new ListenerThread();
        listenerThread.setDaemon(false);
        listenerThread.start();
    }

    /**
     * Stops this test server.
     */
    public void stop() throws Exception {
        if (servicedSocket == null) {
            return; // not running
        }
        final ListenerThread t = listenerThread;
        if (t != null) {
            t.shutdown();
        }
        synchronized (workers) {
            for (final Worker worker : workers) {
                worker.shutdown();
            }
        }
    }

    public void awaitTermination(final long timeMs) throws InterruptedException {
        if (listenerThread != null) {
            listenerThread.join(timeMs);
        }
    }

    @Override
    public String toString() {
        final ServerSocket ssock = servicedSocket; // avoid synchronization
        final StringBuilder sb = new StringBuilder(80);
        sb.append("LocalTestServer/");
        if (ssock == null) {
            sb.append("stopped");
        } else {
            sb.append(ssock.getLocalSocketAddress());
        }
        return sb.toString();
    }

    /**
     * Obtains the local address the server is listening on
     *
     * @return the service address
     */
    public InetSocketAddress getServiceAddress() {
        final ServerSocket ssock = servicedSocket; // avoid synchronization
        Asserts.check(ssock != null, "Not running");
        return (InetSocketAddress) ssock.getLocalSocketAddress();
    }

    /**
     * Creates an instance of {@link DefaultBHttpServerConnection} to be used
     * in the Worker thread.
     * <p>
     * This method can be overridden in a super class in order to provide
     * a different implementation of the {@link DefaultBHttpServerConnection}.
     *
     * @return DefaultBHttpServerConnection.
     */
    protected DefaultBHttpServerConnection createHttpServerConnection() {
      return new DefaultBHttpServerConnection(8 * 1024);
    }

    /**
     * The request listener.
     * Accepts incoming connections and launches a service thread.
     */
    class ListenerThread extends Thread {

        private volatile Exception exception;

        ListenerThread() {
            super();
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    final Socket socket = servicedSocket.accept();
                    acceptedConnections.incrementAndGet();
                    final DefaultBHttpServerConnection conn = createHttpServerConnection();
                    conn.bind(socket);
                    conn.setSocketTimeout(timeout);
                    // Start worker thread
                    final Worker worker = new Worker(conn);
                    workers.add(worker);
                    worker.setDaemon(true);
                    worker.start();
                }
            } catch (final Exception ex) {
                this.exception = ex;
            } finally {
                try {
                    servicedSocket.close();
                } catch (final IOException ignore) {
                }
            }
        }

        public void shutdown() {
            interrupt();
            try {
                servicedSocket.close();
            } catch (final IOException ignore) {
            }
        }

        public Exception getException() {
            return this.exception;
        }

    }

    class Worker extends Thread {

        private final HttpServerConnection conn;

        private volatile Exception exception;

        public Worker(final HttpServerConnection conn) {
            this.conn = conn;
        }

        @Override
        public void run() {
            final HttpContext context = new BasicHttpContext();
            try {
                while (this.conn.isOpen() && !Thread.interrupted()) {
                    httpservice.handleRequest(this.conn, context);
                }
            } catch (final Exception ex) {
                this.exception = ex;
            } finally {
                workers.remove(this);
                try {
                    this.conn.shutdown();
                } catch (final IOException ignore) {
                }
            }
        }

        public void shutdown() {
            interrupt();
            try {
                this.conn.shutdown();
            } catch (final IOException ignore) {
            }
        }

        public Exception getException() {
            return this.exception;
        }

    }

}
