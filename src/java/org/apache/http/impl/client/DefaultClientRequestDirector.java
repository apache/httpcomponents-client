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
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.client.ClientRequestDirector;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectException;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.RoutedRequest;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.BasicManagedEntity;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.RouteDirector;
import org.apache.http.conn.Scheme;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.CharArrayBuffer;

/**
 * Default implementation of a client-side request director.
 * <br/>
 * This class replaces the <code>HttpMethodDirector</code> in HttpClient 3.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public class DefaultClientRequestDirector
    implements ClientRequestDirector {

    private static final Log LOG = LogFactory.getLog(DefaultClientRequestDirector.class);
    
    /** The connection manager. */
    protected final ClientConnectionManager connManager;

    /** The connection re-use strategy. */
    protected final ConnectionReuseStrategy reuseStrategy;

    /** The request executor. */
    protected final HttpRequestExecutor requestExec;

    /** The HTTP protocol processor. */
    protected final HttpProcessor httpProcessor;
    
    /** The request retry handler. */
    protected final HttpRequestRetryHandler retryHandler;
    
    /** The redirect handler. */
    protected final RedirectHandler redirectHandler;
    
    /** The HTTP parameters. */
    protected final HttpParams params;
    
    /** The currently allocated connection. */
    protected ManagedClientConnection managedConn;

    private int redirectCount;

    private int maxRedirects;
    
    public DefaultClientRequestDirector(
            final ClientConnectionManager conman,
            final ConnectionReuseStrategy reustrat,
            final HttpProcessor httpProcessor,
            final HttpRequestRetryHandler retryHandler,
            final RedirectHandler redirectHandler,
            final HttpParams params) {

        if (conman == null) {
            throw new IllegalArgumentException("Client connection manager may not be null");
        }
        if (reustrat == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP protocol processor may not be null");
        }
        if (retryHandler == null) {
            throw new IllegalArgumentException("HTTP request retry handler may not be null");
        }
        if (redirectHandler == null) {
            throw new IllegalArgumentException("Redirect handler may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.connManager   = conman;
        this.reuseStrategy = reustrat;
        this.httpProcessor = httpProcessor;
        this.retryHandler  = retryHandler;
        this.redirectHandler = redirectHandler;
        this.params        = params;
        this.requestExec   = new HttpRequestExecutor(params);

        this.managedConn   = null;
        
        this.redirectCount = 0;
        this.maxRedirects = this.params.getIntParameter(HttpClientParams.MAX_REDIRECTS, 100);

        //@@@ authentication?

    } // constructor


    // non-javadoc, see interface ClientRequestDirector
    public ManagedClientConnection getConnection() {
        return managedConn;
    }

    
    private RequestWrapper wrapRequest(
            final HttpRequest request) throws ProtocolException {
        try {
            if (request instanceof HttpEntityEnclosingRequest) {
                return new EntityEnclosingRequestWrapper(
                        (HttpEntityEnclosingRequest) request);
            } else {
                return new RequestWrapper(
                        request);
            }
        } catch (URISyntaxException ex) {
            throw new ProtocolException("Invalid URI: " + 
                    request.getRequestLine().getUri(), ex);
        }
    }
    
    
    private void rewriteRequestURI(
            final RequestWrapper request,
            final HttpRoute route) throws ProtocolException {
        try {
            
            URI uri = request.getURI();
            if (route.getProxyHost() != null && !route.isTunnelled()) {
                // Make sure the request URI is absolute
                if (!uri.isAbsolute()) {
                    HttpHost target = route.getTargetHost();
                    uri = new URI(
                            target.getSchemeName(), 
                            null, 
                            target.getHostName(), 
                            target.getPort(), 
                            uri.getPath(), 
                            uri.getQuery(), 
                            uri.getFragment());
                    request.setURI(uri);
                }
            } else {
                // Make sure the request URI is relative
                if (uri.isAbsolute()) {
                    uri = new URI(null, null, null, -1, 
                            uri.getPath(), 
                            uri.getQuery(), 
                            uri.getFragment());
                    request.setURI(uri);
                }
            }
            
        } catch (URISyntaxException ex) {
            throw new ProtocolException("Invalid URI: " + 
                    request.getRequestLine().getUri(), ex);
        }
    }
    
    
    // non-javadoc, see interface ClientRequestDirector
    public HttpResponse execute(RoutedRequest roureq, HttpContext context)
        throws HttpException, IOException {

        HttpRequest orig = roureq.getRequest();
        
        // Link parameter collections to form a hierarchy:
        // request -> client
        orig.getParams().setDefaults(this.params);

        // Add default headers
        Collection defHeaders = (Collection) orig.getParams().getParameter(
                HttpClientParams.DEFAULT_HEADERS);
        if (defHeaders != null) {
            for (Iterator it = defHeaders.iterator(); it.hasNext(); ) {
                orig.addHeader((Header) it.next());
            }
        }
        
        int execCount = 0;
        
        HttpResponse response = null;
        boolean done = false;
        try {
            while (!done) {

                RequestWrapper request = wrapRequest(roureq.getRequest());
                HttpRoute route = roureq.getRoute();

                // Re-write request URI if needed
                rewriteRequestURI(request, route);
                
                if (managedConn == null || !managedConn.isOpen()) {
                    managedConn = allocateConnection(route);
                }
                establishRoute(route, context);

                context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST,
                        roureq.getRoute().getTargetHost());
                context.setAttribute(HttpExecutionContext.HTTP_CONNECTION,
                        managedConn);
                
                requestExec.preProcess(request, httpProcessor, context);
                //@@@ handle authentication here or via interceptor?
                
                if (request instanceof AbortableHttpRequest) {
                    ((AbortableHttpRequest) request).setReleaseTrigger(managedConn);
                }

                context.setAttribute(HttpExecutionContext.HTTP_REQUEST,
                        request);

                if (HttpConnectionParams.isStaleCheckingEnabled(params)) {
                    // validate connection
                    LOG.debug("Stale connection check");
                    if (managedConn.isStale()) {
                        LOG.debug("Stale connection detected");
                        managedConn.close();
                        continue;
                    }
                }
                
                execCount++;
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Attempt " + execCount + " to execute request");
                    }
                    response = requestExec.execute(request, managedConn, context);
                    
                } catch (IOException ex) {
                    LOG.debug("Closing the connection.");
                    managedConn.close();
                    if (retryHandler.retryRequest(ex, execCount, context)) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("I/O exception ("+ ex.getClass().getName() + 
                                    ") caught when processing request: "
                                    + ex.getMessage());
                        }
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(ex.getMessage(), ex);
                        }
                        LOG.info("Retrying request");
                        continue;
                    }
                    throw ex;
                }

                requestExec.postProcess(response, httpProcessor, context);
                
                RoutedRequest followup =
                    handleResponse(roureq, request, response, context);
                if (followup == null) {
                    done = true;
                } else {
                    // check if we can use the same connection for the followup
                    if ((managedConn != null) &&
                        !followup.getRoute().equals(roureq.getRoute())) {
                        // the followup has a different route, release conn
                        //@@@ need to consume response body first?
                        //@@@ or let that be done in handleResponse(...)?
                        connManager.releaseConnection(managedConn);
                    }
                    roureq = followup;
                }
            } // while not done

        } finally {
            // if 'done' is false, we're handling an exception
            cleanupConnection(done, response, context);
        }

        return response;

    } // execute


    /**
     * Obtains a connection for the target route.
     *
     * @param route     the route for which to allocate a connection
     *
     * @throws HttpException    in case of a problem
     */
    protected ManagedClientConnection allocateConnection(HttpRoute route)
        throws HttpException, ConnectionPoolTimeoutException {

        long timeout = HttpClientParams.getConnectionManagerTimeout(params);
        return connManager.getConnection(route, timeout);

    } // allocateConnection


    /**
     * Establishes the target route.
     *
     * @param route     the route to establish
     * @param context   the context for the request execution
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    protected void establishRoute(HttpRoute route, HttpContext context)
        throws HttpException, IOException {

        //@@@ how to handle CONNECT requests for tunnelling?
        //@@@ refuse to send external CONNECT via director? special handling?

        //@@@ should the request parameters already be used below?
        //@@@ probably yes, but they're not linked yet
        //@@@ will linking above cause problems with linking in reqExec?
        //@@@ probably not, because the parent is replaced
        //@@@ just make sure we don't link parameters to themselves

        //System.out.println("@@@ planned: " + route);

        RouteDirector rowdy = new RouteDirector();
        int step;
        do {
            HttpRoute fact = managedConn.getRoute();
            //System.out.println("@@@ current: " + fact);
            step = rowdy.nextStep(route, fact);
            //System.out.println("@@@ action => " + step);

            switch (step) {

            case RouteDirector.CONNECT_TARGET:
            case RouteDirector.CONNECT_PROXY:
                managedConn.open(route, context, requestExec.getParams());
                break;

            case RouteDirector.CREATE_TUNNEL:
                boolean secure = createTunnel(route, context);
                managedConn.tunnelCreated(secure, requestExec.getParams());
                break;

            case RouteDirector.LAYER_PROTOCOL:
                managedConn.layerProtocol(context, requestExec.getParams());
                break;

            case RouteDirector.UNREACHABLE:
                throw new IllegalStateException
                    ("Unable to establish route." +
                     "\nplanned = " + route +
                     "\ncurrent = " + fact);

            case RouteDirector.COMPLETE:
                // do nothing
                break;

            default:
                throw new IllegalStateException
                    ("Unknown step indicator "+step+" from RouteDirector.");
            } // switch

        } while (step > RouteDirector.COMPLETE);

    } // establishConnection


    /**
     * Creates a tunnel.
     * The connection must be established to the proxy.
     * A CONNECT request for tunnelling through the proxy will
     * be created and sent, the response received and checked.
     * This method does <i>not</i> update the connection with
     * information about the tunnel, that is left to the caller.
     *
     * @param route     the route to establish
     * @param context   the context for request execution
     *
     * @return  <code>true</code> if the tunnelled route is secure,
     *          <code>false</code> otherwise.
     *          The implementation here always returns <code>false</code>,
     *          but derived classes may override.
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    protected boolean createTunnel(HttpRoute route, HttpContext context)
        throws HttpException, IOException {

        HttpRequest connect = createConnectRequest(route, context);
        //@@@ authenticate here, in method above, or in request interceptor?

        HttpResponse connected =
            requestExec.execute(connect, managedConn, context);
        managedConn.markReusable();
        int status = connected.getStatusLine().getStatusCode();
        //@@@ log something about the response?

        //@@@ check for proxy authentication challenge, repeat with auth

        if ((status < 200) || (status > 299)) {
            throw new HttpException("CONNECT refused by proxy: " +
                                    connected.getStatusLine());
        }

        // How to decide on security of the tunnelled connection?
        // The socket factory knows only about the segment to the proxy.
        // Even if that is secure, the hop to the target may be insecure.
        // Leave it to derived classes, consider insecure by default here.
        return false;
    }


    /**
     * Creates the CONNECT request for tunnelling.
     * Called by {@link #createTunnel createTunnel}.
     *
     * @param route     the route to establish
     * @param context   the context for request execution
     *
     * @return  the CONNECT request for tunnelling
     */
    protected HttpRequest createConnectRequest(HttpRoute route,
                                               HttpContext context) {
        // see RFC 2817, section 5.2 and 
        // INTERNET-DRAFT: Tunneling TCP based protocols through 
        // Web proxy servers
            
        HttpHost target = route.getTargetHost();
        
        String host = target.getHostName();
        int port = target.getPort();
        if (port < 0) {
            Scheme scheme = connManager.getSchemeRegistry().
                getScheme(target.getSchemeName());
            port = scheme.getDefaultPort();
        }
        
        CharArrayBuffer buffer = new CharArrayBuffer(host.length() + 6);
        buffer.append(host);
        buffer.append(":");
        buffer.append(Integer.toString(port));
        
        String authority = buffer.toString();
        HttpVersion ver = HttpProtocolParams.getVersion(params);
        HttpRequest req = new BasicHttpRequest
            ("CONNECT", authority, ver);

        String agent = HttpProtocolParams.getUserAgent(params);
        if (agent != null) {
            req.addHeader(HTTP.USER_AGENT, agent);
        }

        //@@@ authenticate here, in caller, or in request interceptor?

        return req;
    }


    /**
     * Analyzes a response to check need for a followup.
     *
     * @param roureq    the request and route. This is the same object as
     *                  was passed to {@link #wrapRequest(HttpRequest)}.
     * @param request   the request that was actually sent. This is the object
     *                  returned by {@link #wrapRequest(HttpRequest)}.
     * @param response  the response to analayze
     * @param context   the context used for the current request execution
     *
     * @return  the followup request and route if there is a followup, or
     *          <code>null</code> if the response should be returned as is
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    protected RoutedRequest handleResponse(RoutedRequest roureq,
                                           HttpRequest request,
                                           HttpResponse response,
                                           HttpContext context)
        throws HttpException, IOException {

        HttpParams params = request.getParams();
        if (params.getBooleanParameter(HttpClientParams.HANDLE_REDIRECTS, true) && 
                this.redirectHandler.isRedirectNeeded(response, context)) {

            if (redirectCount >= maxRedirects) {
                throw new RedirectException("Maximum redirects ("
                        + maxRedirects + ") exceeded");
            }
            redirectCount++;
            
            URI uri;
            try {
                uri = this.redirectHandler.getLocationURI(response, context);
            } catch (ProtocolException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn(ex.getMessage());
                }
                return null;
            }

            HttpRoute oldRoute = roureq.getRoute();

            HttpHost newTarget = new HttpHost(
                    uri.getHost(), 
                    uri.getPort(),
                    uri.getScheme());
            
            Scheme schm = connManager.getSchemeRegistry().
                getScheme(newTarget.getSchemeName());
            
            InetAddress localAddress = oldRoute.getLocalAddress();
            HttpHost proxy = oldRoute.getProxyHost();
            
            HttpRoute newRoute = new HttpRoute(
                    newTarget,
                    localAddress,
                    proxy,
                    schm.isLayered(),
                    (proxy != null),
                    (proxy != null));

            // Make sure redirect response body is fully consumed, if present
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                entity.consumeContent();
            }
            
            HttpGet redirect = new HttpGet(uri);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("Redirecting to '" + uri + "' via " + newRoute);
            }
            
            return new RoutedRequest.Impl(redirect, newRoute);
        }
        return null;
    } // handleResponse


    /**
     * Releases the connection if possible.
     * This method is called from a <code>finally</code> block in
     * {@link #execute execute}, possibly during exception handling.
     *
     * @param success   <code>true</code> if a response is to be returned
     *                  from {@link #execute execute}, or
     *                  <code>false</code> if exception handling is in progress
     * @param response  the response available for return by
     *                  {@link #execute execute}, or <code>null</code>
     * @param context   the context used for the last request execution
     *
     * @throws IOException      in case of an IO problem
     */
    protected void cleanupConnection(boolean success,
                                     HttpResponse response,
                                     HttpContext context)
        throws IOException {

        ManagedClientConnection mcc = managedConn;
        if (mcc == null)
            return; // nothing to be cleaned up
        
        if (success) {
            // Not in exception handling, there probably is a response.
            // The connection is in or can be brought to a re-usable state.
            boolean reuse = reuseStrategy.keepAlive(response, context);

            // check for entity, release connection if possible
            if ((response == null) || (response.getEntity() == null) ||
                !response.getEntity().isStreaming()) {
                // connection not needed and (assumed to be) in re-usable state
                managedConn = null;
                if (reuse)
                    mcc.markReusable();
                connManager.releaseConnection(mcc);
            } else {
                setupResponseEntity(response, context, reuse);
            }
        } else {
            // we got here as the result of an exception
            // no response will be returned, release the connection
            managedConn = null;
            //@@@ is the connection in a re-usable state? consume response?
            //@@@ for now, just shut it down
            try {
                mcc.abortConnection();
            } catch (IOException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(ex.getMessage(), ex);
                }
            }
        }
    } // cleanupConnection


    /**
     * Prepares the entity in the ultimate response being returned.
     * The default implementation here installs an entity with auto-release
     * capability for the connection.
     * <br/>
     * This method might be overridden to buffer the response entity
     * and release the connection immediately.
     * Derived implementations MUST release the connection if an exception
     * is thrown here!
     *
     * @param response  the response holding the entity to prepare
     * @param context   the context used for the last request execution
     * @param reuse     <code>true</code> if the connection should be
     *                  kept alive and re-used for another request,
     *                  <code>false</code> if the connection should be
     *                  closed and not re-used
     *
     * @throws IOException      in case of an IO problem.
     *         The connection MUST be released in this method if
     *         an exception is thrown!
     */
    protected void setupResponseEntity(HttpResponse response,
                                       HttpContext context,
                                       boolean reuse)
        throws IOException {

        // install an auto-release entity
        HttpEntity entity = response.getEntity();
        response.setEntity(new BasicManagedEntity(entity, managedConn, reuse));
    }


} // class DefaultClientRequestDirector
