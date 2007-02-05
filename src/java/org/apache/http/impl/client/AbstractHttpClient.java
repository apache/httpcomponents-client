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

package org.apache.http.impl.client;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpException;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncHttpExecutionContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.conn.ClientConnectionManager;

import org.apache.http.client.HttpClient;
import org.apache.http.client.RoutedRequest;



/**
 * Convenience base class for HTTP client implementations.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public abstract class AbstractHttpClient
    implements HttpClient {


    /** The default context. */
    protected HttpContext defaultContext;

    /** The parameters. */
    protected HttpParams defaultParams;

    /** The connection manager. */
    protected ClientConnectionManager connManager;

    /** The HTTP processor, if defined. */
    protected BasicHttpProcessor httpProcessor;


    /**
     * Creates a new HTTP client.
     *
     * @param context   the context, or <code>null</code> to use an instance of
     *        {@link SyncHttpExecutionContext SyncHttpExecutionContext}
     * @param params    the parameters
     * @param conman    the connection manager
     * @param hproc     the HTTP processor, or <code>null</code>
     */
    protected AbstractHttpClient(HttpContext context, HttpParams params,
                                 ClientConnectionManager conman,
                                 BasicHttpProcessor hproc) {
        if (params == null)
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        if (conman == null)
            throw new IllegalArgumentException
                ("Connection manager must not be null.");

        defaultContext = (context != null) ?
            context : new SyncHttpExecutionContext(null);
        defaultParams  = params;
        connManager    = conman;
        httpProcessor  = hproc;

    } // constructor


    // non-javadoc, see interface HttpClient
    public final HttpContext getContext() {
        return defaultContext;
    }


    /**
     * Replaces the default context.
     *
     * @param context   the new default context
     */
    public void setContext(HttpContext context) {
        if (context == null)
            throw new IllegalArgumentException
                ("Context must not be null.");
        defaultContext = context;
    }


    // non-javadoc, see interface HttpClient
    public final HttpParams getParams() {
        return defaultParams;
    }


    /**
     * Replaces the parameters.
     * The implementation here does not update parameters of dependent objects.
     *
     * @param params    the new default parameters
     */
    public void setParams(HttpParams params) {
        if (params == null)
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        defaultParams = params;
    }


    // non-javadoc, see interface HttpClient
    public final ClientConnectionManager getConnectionManager() {
        return connManager;
    }


    // no setConnectionManager(), too dangerous to replace while in use
    // derived classes may offer that method at their own risk


    /**
     * Obtains the HTTP processor.
     *
     * @return  the HTTP processor, or <code>null</code> if not set
     */
    public BasicHttpProcessor getProcessor() {
        return httpProcessor;
    }


    /**
     * Specifies the HTTP processor.
     *
     * @param hproc     the HTTP processor, or <code>null</code> to unset
     */
    public void setProcessor(BasicHttpProcessor hproc) {
        httpProcessor = hproc;
    }


    /**
     * Maps to {@link #execute(HttpHost,HttpRequest,HttpContext)
     *                 execute(target, request, context)}.
     *
     * @param target    the target host for the request.
     *                  Some implementations may accept <code>null</code>.
     * @param request   the request to execute
     *
     * @return  the response to the request
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    public final HttpResponse execute(HttpHost target, HttpRequest request)
        throws HttpException, IOException {

        return execute(target, request, defaultContext);
    }


    /**
     * Maps to {@link HttpClient#execute(RoutedRequest,HttpContext)
     *                           execute(roureq, context)}.
     * The route is computed by {@link #determineRoute determineRoute}.
     *
     * @param target    the target host for the request.
     *                  Some implementations may accept <code>null</code>.
     * @param request   the request to execute
     */
    public final HttpResponse execute(HttpHost target, HttpRequest request,
                                      HttpContext context)
        throws HttpException, IOException {

        if (request == null) {
            throw new IllegalArgumentException
                ("Request must not be null.");
        }
        // A null target may be acceptable if there is a default target.
        // Otherwise, the null target is detected in determineRoute().

        if (context == null)
            context = defaultContext;

        RoutedRequest roureq = determineRoute(target, request, context);
        return execute(roureq, context);
    }


    /**
     * Determines the route for a request.
     * Called by {@link #execute(HttpHost,HttpRequest,HttpContext)
     *                   execute(target, request, context)}
     * to map to {@link HttpClient#execute(RoutedRequest,HttpContext)
     *                             execute(roureq, context)}.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept <code>null</code>
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param context   the context to use for the execution,
     *                  never <code>null</code>
     *
     * @return  the request along with the route it should take
     *
     * @throws HttpException    in case of a problem
     */
    protected abstract RoutedRequest determineRoute(HttpHost target,
                                                    HttpRequest request,
                                                    HttpContext context)
        throws HttpException
        ;


} // class AbstractHttpClient
