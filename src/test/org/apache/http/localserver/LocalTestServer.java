/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.BindException;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;

/**
 * Local HTTP server for tests that require one.
 * Based on the <code>ElementalHttpServer</code> example in HttpCore.
 * 
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines to avoid 'svn diff' problems -->
 * @version $Revision$
 */
public class LocalTestServer {

    /**
     * The local network address to bind to.
     * This is an IP number rather than "localhost" to avoid surprises
     * on hosts that map "localhost" to an IPv6 address or something else.
     */
    public final static String TEST_SERVER_ADDR = "127.0.0.1";

    /** The suggested port number for a local test server. */
    public final static int TEST_SERVER_PORT = 39888;

    /** The request handler registry. */
    public HttpRequestHandlerRegistry handlerRegistry;

    /** The server-side connection re-use strategy. */
    public ConnectionReuseStrategy reuseStrategy;

    /**
     * The HTTP processor.
     * If the interceptors are thread safe and the list is not
     * modified during operation, the processor is thread safe.
     */
    public BasicHttpProcessor httpProcessor;

    /** The server parameters. */
    public HttpParams serverParams;

    /** The server socket, while being served. */
    protected ServerSocket servicedSocket;

    /** The request listening thread, while listening. */
    protected Thread listenerThread;


    /**
     * Creates a new test server.
     *
     * @param proc      the HTTP processors to be used by the server, or
     *                  <code>null</code> to use a
     *                  {@link #newProcessor default} processor
     * @param params    the parameters to be used by the server, or
     *                  <code>null</code> to use
     *                  {@link #newDefaultParams default} parameters
     */
    public LocalTestServer(BasicHttpProcessor proc, HttpParams params) {
        handlerRegistry = new HttpRequestHandlerRegistry();
        reuseStrategy = new DefaultConnectionReuseStrategy();
        httpProcessor = (proc != null) ? proc : newProcessor();
        serverParams = (params != null) ? params : newDefaultParams();
    }


    /**
     * Obtains an HTTP protocol processor with default interceptors.
     *
     * @return  a protocol processor for server-side use
     */
    public static BasicHttpProcessor newProcessor() {

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());

        return httpproc;
    }


    /**
     * Obtains a set of reasonable default parameters for a server.
     *
     * @return  default parameters
     */
    public static HttpParams newDefaultParams() {
        HttpParams params = new BasicHttpParams(null);
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT,
                             5000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE,
                             8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK,
                                 false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY,
                                 true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER,
                          "Jakarta-HttpComponents-LocalTestServer/1.1");
        return params;
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
    public void register(String pattern, HttpRequestHandler handler) {
        handlerRegistry.register(pattern, handler);
    }


    /**
     * Unregisters a handler from the local registry.
     *
     * @param pattern   the URL pattern
     */
    public void unregister(String pattern) {
        handlerRegistry.unregister(pattern);
    }


    /**
     * Specifies the connection re-use strategy.
     *
     * @param strategy  the re-use strategy
     */
    public void setReuseStrategy(ConnectionReuseStrategy strategy) {
        reuseStrategy = strategy;
    }


    /**
     * Starts this test server.
     * Since stopping and starting this server in quick succession may
     * leave a local port bound to an already closed server socket for
     * a while, the port number can not be guaranteed.
     * Use {@link #getServicePort getServicePort} to obtain the port number.
     *
     * @param port      the port number to try first,
     *                  0 or negative if you don't care
     */
    public void start(int port) throws Exception {
        if (servicedSocket != null)
            throw new IllegalStateException
                (this.toString() + " already running");

        if (port <= 0)
            port = TEST_SERVER_PORT;
        final int stop = port + 10;

        ServerSocket ssock = new ServerSocket();
        ssock.setReuseAddress(true);
        while (!ssock.isBound()) {
            try {
                InetSocketAddress addr = new
                    InetSocketAddress(TEST_SERVER_ADDR, port);
                ssock.bind(addr);

            } catch (BindException bx) {
                port++;                 // try next one
                if (port >= stop)
                    throw bx;           // no use trying any more
            }
        }
        servicedSocket = ssock;

        listenerThread = new Thread(new RequestListener());
        listenerThread.setDaemon(false);
        listenerThread.start();
    }

    /**
     * Stops this test server.
     */
    public void stop() throws Exception {
        if (servicedSocket == null)
            return; // not running

        try {
            servicedSocket.close();
        } catch (IOException iox) {
            System.out.println("error stopping " + this);
            iox.printStackTrace(System.out);
        } finally {
            servicedSocket = null;
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }


    public String toString() {
        ServerSocket ssock = servicedSocket; // avoid synchronization
        StringBuffer sb = new StringBuffer(80);
        sb.append("LocalTestServer/");
        if (ssock == null)
            sb.append("stopped");
        else
            sb.append(ssock.getLocalSocketAddress());
        return sb.toString();
    }


    /**
     * Obtains the port this server is servicing.
     *
     * @return  the service port
     */
    public int getServicePort() {
        ServerSocket ssock = servicedSocket; // avoid synchronization
        if (ssock == null)
            throw new IllegalStateException("not running");

        return ssock.getLocalPort();
    }


    /**
     * The request listener.
     * Accepts incoming connections and launches a service thread.
     */
    public class RequestListener implements Runnable {

        /** The workers launched from here. */
        private Set workerThreads =
            Collections.synchronizedSet(new HashSet());


        public void run() {

            try {
                while ((servicedSocket != null) &&
                       (listenerThread == Thread.currentThread()) &&
                       !Thread.interrupted()) {
                    try {
                        accept();
                    } catch (Exception e) {
                        ServerSocket ssock = servicedSocket;
                        if ((ssock != null) && !ssock.isClosed()) {
                            System.out.println
                                (LocalTestServer.this.toString() +
                                 " could not accept");
                            e.printStackTrace(System.out);
                        }
                        // otherwise ignore the exception silently
                        break;
                    }
                }
            } finally {
                cleanup();
            }
        }

        protected void accept() throws IOException {
            // Set up HTTP connection
            Socket socket = servicedSocket.accept();
            DefaultHttpServerConnection conn =
                new DefaultHttpServerConnection();
            conn.bind(socket, serverParams);

            // Set up the HTTP service
            HttpService httpService = new HttpService(
                httpProcessor, 
                new DefaultConnectionReuseStrategy(), 
                new DefaultHttpResponseFactory());
            httpService.setParams(serverParams);
            httpService.setHandlerResolver(handlerRegistry);

            // Start worker thread
            Thread t = new Thread(new Worker(httpService, conn));
            workerThreads.add(t);
            t.setDaemon(true);
            t.start();

        } // accept


        protected void cleanup() {
            Thread[] threads = (Thread[]) workerThreads.toArray(new Thread[0]);
            for (int i=0; i<threads.length; i++) {
                if (threads[i] != null)
                    threads[i].interrupt();
            }
        }


        /**
         * A worker for serving incoming requests.
         */
        public class Worker implements Runnable {

            private final HttpService httpservice;
            private final HttpServerConnection conn;
        
            public Worker(
                final HttpService httpservice, 
                final HttpServerConnection conn) {

                this.httpservice = httpservice;
                this.conn = conn;
            }


            public void run() {
                HttpContext context = new HttpExecutionContext(null);
                try {
                    while ((servicedSocket != null) &&
                           this.conn.isOpen() && !Thread.interrupted()) {
                        this.httpservice.handleRequest(this.conn, context);
                    }
                } catch (IOException ex) {
                    // ignore silently
                } catch (HttpException ex) {
                    // ignore silently
                } finally {
                    workerThreads.remove(Thread.currentThread());
                    try {
                        this.conn.shutdown();
                    } catch (IOException ignore) {}
                }
            }

        } // class Worker

    } // class RequestListener



/*
    
    static class HttpFileHandler implements HttpRequestHandler  {
        
        private final String docRoot;
        
        public HttpFileHandler(final String docRoot) {
            super();
            this.docRoot = docRoot;
        }
        
        public void handle(
                final HttpRequest request, 
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            String method = request.getRequestLine().getMethod().toUpperCase();
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                throw new MethodNotSupportedException(method + " method not supported"); 
            }
            String target = request.getRequestLine().getUri();

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                byte[] entityContent = EntityUtils.toByteArray(entity);
                System.out.println("Incoming entity content (bytes): " + entityContent.length);
            }
            
            final File file = new File(this.docRoot, URLDecoder.decode(target));
            if (!file.exists()) {

                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
                        writer.write("<html><body><h1>");
                        writer.write("File ");
                        writer.write(file.getPath());
                        writer.write(" not found");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                    
                });
                body.setContentType("text/html; charset=UTF-8");
                response.setEntity(body);
                System.out.println("File " + file.getPath() + " not found");
                
            } else if (!file.canRead() || file.isDirectory()) {
                
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
                        writer.write("<html><body><h1>");
                        writer.write("Access denied");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                    
                });
                body.setContentType("text/html; charset=UTF-8");
                response.setEntity(body);
                System.out.println("Cannot read file " + file.getPath());
                
            } else {
                
                response.setStatusCode(HttpStatus.SC_OK);
                FileEntity body = new FileEntity(file, "text/html");
                response.setEntity(body);
                System.out.println("Serving file " + file.getPath());
                
            }
        }
        
    }
*/

    
}
