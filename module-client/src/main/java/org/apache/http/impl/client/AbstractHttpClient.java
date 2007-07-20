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
import java.net.URI;
import java.util.List;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.ClientRequestDirector;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.RoutedRequest;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.cookie.CookieSpecRegistry;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpRequestInterceptorList;
import org.apache.http.protocol.HttpResponseInterceptorList;



/**
 * Convenience base class for HTTP client implementations.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public abstract class AbstractHttpClient
    implements HttpClient, HttpRequestInterceptorList, HttpResponseInterceptorList {


    /** The default context. */
    private HttpContext defaultContext;

    /** The parameters. */
    private HttpParams defaultParams;

    /** The connection manager. */
    private ClientConnectionManager connManager;

    /** The connection re-use strategy. */
    private ConnectionReuseStrategy reuseStrategy;

    /** The cookie spec registry. */
    private CookieSpecRegistry supportedCookieSpecs;

    /** The authentication scheme registry. */
    private AuthSchemeRegistry supportedAuthSchemes;
    
    /** The HTTP processor. */
    private BasicHttpProcessor httpProcessor;

    /** The request retry handler. */
    private HttpRequestRetryHandler retryHandler;

    /** The redirect handler. */
    private RedirectHandler redirectHandler;

    /** The authentication handler. */
    private AuthenticationHandler authHandler;

    /** The cookie store. */
    private CookieStore cookieStore;

    /** The credentials provider. */
    private CredentialsProvider credsProvider;

    /**
     * Creates a new HTTP client.
     *
     * @param conman    the connection manager
     * @param params    the parameters
     */
    protected AbstractHttpClient(
            final ClientConnectionManager conman,
            final HttpParams params) {
        defaultParams        = params;
        connManager          = conman;
    } // constructor


    protected abstract HttpParams createHttpParams();

    
    protected abstract HttpContext createHttpContext();

    
    protected abstract ClientConnectionManager createClientConnectionManager();


    protected abstract AuthSchemeRegistry createAuthSchemeRegistry();

    
    protected abstract CookieSpecRegistry createCookieSpecRegistry();

    
    protected abstract ConnectionReuseStrategy createConnectionReuseStrategy();
    
    
    protected abstract BasicHttpProcessor createHttpProcessor();

    
    protected abstract HttpRequestRetryHandler createHttpRequestRetryHandler();

    
    protected abstract RedirectHandler createRedirectHandler();

    
    protected abstract AuthenticationHandler createAuthenticationHandler();

    
    protected abstract CookieStore createCookieStore();
    
    
    protected abstract CredentialsProvider createCredentialsProvider();
    
    
    protected abstract void populateContext(HttpContext context);

    
    // non-javadoc, see interface HttpClient
    public synchronized final HttpParams getParams() {
        if (defaultParams == null) {
            defaultParams = createHttpParams();
        }
        return defaultParams;
    }


    /**
     * Replaces the parameters.
     * The implementation here does not update parameters of dependent objects.
     *
     * @param params    the new default parameters
     */
    public synchronized void setParams(HttpParams params) {
        defaultParams = params;
    }


    // non-javadoc, see interface HttpClient
    public synchronized final ClientConnectionManager getConnectionManager() {
        if (connManager == null) {
            connManager = createClientConnectionManager();
        }
        return connManager;
    }


    // no setConnectionManager(), too dangerous to replace while in use
    // derived classes may offer that method at their own risk


    public synchronized final AuthSchemeRegistry getAuthSchemes() {
        if (supportedAuthSchemes == null) {
            supportedAuthSchemes = createAuthSchemeRegistry();
        }
        return supportedAuthSchemes;
    }


    public synchronized void setAuthSchemes(final AuthSchemeRegistry authSchemeRegistry) {
        supportedAuthSchemes = authSchemeRegistry;
    }


    public synchronized final CookieSpecRegistry getCookieSpecs() {
        if (supportedCookieSpecs == null) {
            supportedCookieSpecs = createCookieSpecRegistry();
        }
        return supportedCookieSpecs;
    }


    public synchronized void setCookieSpecs(final CookieSpecRegistry cookieSpecRegistry) {
        supportedCookieSpecs = cookieSpecRegistry;
    }

    
    public synchronized final ConnectionReuseStrategy getConnectionReuseStrategy() {
        if (reuseStrategy == null) {
            reuseStrategy = createConnectionReuseStrategy();
        }
        return reuseStrategy;
    }


    public synchronized void setReuseStrategy(final ConnectionReuseStrategy reuseStrategy) {
        this.reuseStrategy = reuseStrategy;
    }


    public synchronized final HttpRequestRetryHandler getHttpRequestRetryHandler() {
        if (retryHandler == null) {
            retryHandler = createHttpRequestRetryHandler();
        }
        return retryHandler;
    }


    public synchronized void setHttpRequestRetryHandler(final HttpRequestRetryHandler retryHandler) {
        this.retryHandler = retryHandler;
    }


    public synchronized final RedirectHandler getRedirectHandler() {
        if (redirectHandler == null) {
            redirectHandler = createRedirectHandler();
        }
        return redirectHandler;
    }


    public synchronized void setRedirectHandler(final RedirectHandler redirectHandler) {
        this.redirectHandler = redirectHandler;
    }


    public synchronized final AuthenticationHandler getAuthenticationHandler() {
        if (authHandler == null) {
            authHandler = createAuthenticationHandler();
        }
        return authHandler;
    }


    public synchronized void setAuthenticationHandler(final AuthenticationHandler authHandler) {
        this.authHandler = authHandler;
    }


    public synchronized final CookieStore getCookieStore() {
        if (cookieStore == null) {
            cookieStore = createCookieStore();
        }
        return cookieStore;
    }


    public synchronized void setCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }


    public synchronized final CredentialsProvider getCredentialsProvider() {
        if (credsProvider == null) {
            credsProvider = createCredentialsProvider();
        }
        return credsProvider;
    }


    public synchronized void setState(final CredentialsProvider credsProvider) {
        this.credsProvider = credsProvider;
    }


    protected synchronized final BasicHttpProcessor getHttpProcessor() {
        if (httpProcessor == null) {
            httpProcessor = createHttpProcessor();
        }
        return httpProcessor;
    }


    public synchronized final HttpContext getDefaultContext() {
        if (defaultContext == null) {
            defaultContext = createHttpContext();
        }
        populateContext(defaultContext);
        return defaultContext;
    }
    
    
    public synchronized void addResponseInterceptor(final HttpResponseInterceptor itcp) {
        getHttpProcessor().addInterceptor(itcp);
    }


    public synchronized void addResponseInterceptor(final HttpResponseInterceptor itcp, int index) {
        getHttpProcessor().addInterceptor(itcp, index);
    }


    public synchronized HttpResponseInterceptor getResponseInterceptor(int index) {
        return getHttpProcessor().getResponseInterceptor(index);
    }


    public synchronized int getResponseInterceptorCount() {
        return getHttpProcessor().getResponseInterceptorCount();
    }


    public synchronized void clearResponseInterceptors() {
        getHttpProcessor().clearResponseInterceptors();
    }


    public void removeResponseInterceptorByClass(Class clazz) {
        getHttpProcessor().removeResponseInterceptorByClass(clazz);
    }

    
    public synchronized void addRequestInterceptor(final HttpRequestInterceptor itcp) {
        getHttpProcessor().addInterceptor(itcp);
    }


    public synchronized void addRequestInterceptor(final HttpRequestInterceptor itcp, int index) {
        getHttpProcessor().addInterceptor(itcp, index);
    }


    public synchronized HttpRequestInterceptor getRequestInterceptor(int index) {
        return getHttpProcessor().getRequestInterceptor(index);
    }


    public synchronized int getRequestInterceptorCount() {
        return getHttpProcessor().getRequestInterceptorCount();
    }


    public synchronized void clearRequestInterceptors() {
        getHttpProcessor().clearRequestInterceptors();
    }


    public void removeRequestInterceptorByClass(Class clazz) {
        getHttpProcessor().removeRequestInterceptorByClass(clazz);
    }


    public synchronized void setInterceptors(final List itcps) {
        getHttpProcessor().setInterceptors(itcps);
    }


    /**
     * Maps to {@link #execute(HttpUriRequest,HttpContext)
     *                 execute(request, context)}.
     * The route is computed by {@link #determineRoute determineRoute}.
     * This method uses {@link #getDefaultContext() default context}.
     *
     * @param request   the request to execute
     *
     * @return  the response to the request
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    public final HttpResponse execute(HttpUriRequest request)
        throws HttpException, IOException {

        return execute(request, null);
    }


    /**
     * Maps to {@link HttpClient#execute(RoutedRequest,HttpContext)
     *                           execute(roureq, context)}.
     * The route is computed by {@link #determineRoute determineRoute}.
     *
     * @param request   the request to execute
     * @param context   the request-specific execution context,
     *                  or <code>null</code> to use a default context
     */
    public final HttpResponse execute(HttpUriRequest request,
                                      HttpContext context)
        throws HttpException, IOException {

        if (request == null) {
            throw new IllegalArgumentException
                ("Request must not be null.");
        }

        // A null target may be acceptable if there is a default target.
        // Otherwise, the null target is detected in determineRoute().
        HttpHost target = null;
        
        URI requestURI = request.getURI();
        if (requestURI.isAbsolute()) {
            target = new HttpHost(
                    requestURI.getHost(), 
                    requestURI.getPort(), 
                    requestURI.getScheme());
        }
        
        synchronized (this) {
            if (context == null) {
                context = new BasicHttpContext(getDefaultContext());
            }
        }
        
        RoutedRequest roureq = determineRoute(target, request, context);
        return execute(roureq, context);
    }

    
    public HttpResponse execute(RoutedRequest roureq) 
        throws HttpException, IOException {
        return execute(roureq, null);
    }


    // non-javadoc, see interface HttpClient
    public final HttpResponse execute(RoutedRequest roureq, HttpContext context)
        throws HttpException, IOException {

        if (roureq == null) {
            throw new IllegalArgumentException
                ("Routed request must not be null.");
        }
        if (roureq.getRequest() == null) {
            throw new IllegalArgumentException
                ("Request must not be null.");
        }
        if (roureq.getRoute() == null) {
            throw new IllegalArgumentException
                ("Route must not be null.");
        }

        ClientRequestDirector director = null;
        
        // Initialize the request execution context making copies of 
        // all shared objects that are potentially threading unsafe.
        synchronized (this) {
            if (context == null) {
                context = new BasicHttpContext(getDefaultContext());
            }
            // Create a director for this request
            director = new DefaultClientRequestDirector(
                    getConnectionManager(),
                    getConnectionReuseStrategy(),
                    getHttpProcessor().copy(),
                    getHttpRequestRetryHandler(),
                    getRedirectHandler(),
                    getAuthenticationHandler(),
                    getParams());
        }

        HttpResponse  response = director.execute(roureq, context);
        // If the response depends on the connection, the director
        // will have set up an auto-release input stream.
        //@@@ or move that logic here into the client?

        //@@@ "finalize" response, to allow for buffering of entities?
        //@@@ here or in director?

        return response;

    } // execute


    /**
     * Determines the route for a request.
     * Called by {@link #execute(HttpUriRequest,HttpContext)
     *                   execute(urirequest, context)}
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
