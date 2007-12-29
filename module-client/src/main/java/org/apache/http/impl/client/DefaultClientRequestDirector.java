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
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import org.apache.http.ProtocolVersion;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.AuthState;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.ClientRequestDirector;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectException;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.BasicManagedEntity;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.HttpRoutePlanner;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.HttpRouteDirector;
import org.apache.http.conn.BasicRouteDirector;
import org.apache.http.conn.Scheme;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
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

    /** The route planner. */
    protected final HttpRoutePlanner routePlanner;

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
    
    /** The target authentication handler. */
    private final AuthenticationHandler targetAuthHandler;
    
    /** The proxy authentication handler. */
    private final AuthenticationHandler proxyAuthHandler;
    
    /** The HTTP parameters. */
    protected final HttpParams params;
    
    /** The currently allocated connection. */
    protected ManagedClientConnection managedConn;

    private int redirectCount;

    private int maxRedirects;
    
    private final AuthState targetAuthState;
    
    private final AuthState proxyAuthState;
    
    public DefaultClientRequestDirector(
            final ClientConnectionManager conman,
            final ConnectionReuseStrategy reustrat,
            final HttpRoutePlanner rouplan,
            final HttpProcessor httpProcessor,
            final HttpRequestRetryHandler retryHandler,
            final RedirectHandler redirectHandler,
            final AuthenticationHandler targetAuthHandler,
            final AuthenticationHandler proxyAuthHandler,
            final HttpParams params) {

        if (conman == null) {
            throw new IllegalArgumentException
                ("Client connection manager may not be null.");
        }
        if (reustrat == null) {
            throw new IllegalArgumentException
                ("Connection reuse strategy may not be null.");
        }
        if (rouplan == null) {
            throw new IllegalArgumentException
                ("Route planner may not be null.");
        }
        if (httpProcessor == null) {
            throw new IllegalArgumentException
                ("HTTP protocol processor may not be null.");
        }
        if (retryHandler == null) {
            throw new IllegalArgumentException
                ("HTTP request retry handler may not be null.");
        }
        if (redirectHandler == null) {
            throw new IllegalArgumentException
                ("Redirect handler may not be null.");
        }
        if (targetAuthHandler == null) {
            throw new IllegalArgumentException
                ("Target authentication handler may not be null.");
        }
        if (proxyAuthHandler == null) {
            throw new IllegalArgumentException
                ("Proxy authentication handler may not be null.");
        }
        if (params == null) {
            throw new IllegalArgumentException
                ("HTTP parameters may not be null");
        }
        this.connManager       = conman;
        this.reuseStrategy     = reustrat;
        this.routePlanner      = rouplan;
        this.httpProcessor     = httpProcessor;
        this.retryHandler      = retryHandler;
        this.redirectHandler   = redirectHandler;
        this.targetAuthHandler = targetAuthHandler;
        this.proxyAuthHandler  = proxyAuthHandler;
        this.params            = params;
        this.requestExec       = new HttpRequestExecutor();

        this.managedConn       = null;
        
        this.redirectCount = 0;
        this.maxRedirects = this.params.getIntParameter(ClientPNames.MAX_REDIRECTS, 100);
        this.targetAuthState = new AuthState();
        this.proxyAuthState = new AuthState();
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
    public HttpResponse execute(HttpHost target, HttpRequest request,
                                HttpContext context)
        throws HttpException, IOException, InterruptedException {

        RoutedRequest roureq = determineRoute(target, request, context);

        final HttpRequest orig = request;

        long timeout = HttpClientParams.getConnectionManagerTimeout(params);
        
        int execCount = 0;
        
        HttpResponse response = null;
        boolean done = false;
        try {
            while (!done) {
                // In this loop, the RoutedRequest may be replaced by a
                // followup request and route. The request and route passed
                // in the method arguments will be replaced. The original
                // request is still available in 'orig'.

                HttpRoute route = roureq.getRoute();

                // Allocate connection if needed
                if (managedConn == null) {
                    managedConn = allocateConnection(route, timeout);
                }
                // Reopen connection if needed
                if (!managedConn.isOpen()) {
                    managedConn.open(route, context, params);
                }
                
                try {
                    establishRoute(route, context);
                } catch (TunnelRefusedException ex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(ex.getMessage());
                    }
                    response = ex.getResponse();
                    break;
                }

                if (HttpConnectionParams.isStaleCheckingEnabled(params)) {
                    // validate connection
                    LOG.debug("Stale connection check");
                    if (managedConn.isStale()) {
                        LOG.debug("Stale connection detected");
                        managedConn.close();
                        continue;
                    }
                }

                // Wrap the request to be sent, original or followup.
                RequestWrapper reqwrap = wrapRequest(roureq.getRequest());
                reqwrap.setParams(this.params);
                
                // Re-write request URI if needed
                rewriteRequestURI(reqwrap, route);

                // Use virtual host if set
                target = (HttpHost) reqwrap.getParams().getParameter(
                        ClientPNames.VIRTUAL_HOST);

                if (target == null) {
                    target = route.getTargetHost();
                }

                HttpHost proxy = route.getProxyHost();

                // Populate the execution context
                context.setAttribute(ExecutionContext.HTTP_TARGET_HOST,
                        target);
                context.setAttribute(ExecutionContext.HTTP_PROXY_HOST,
                        proxy);
                context.setAttribute(ExecutionContext.HTTP_CONNECTION,
                        managedConn);
                context.setAttribute(ClientContext.TARGET_AUTH_STATE,
                        targetAuthState);
                context.setAttribute(ClientContext.PROXY_AUTH_STATE,
                        proxyAuthState);
                requestExec.preProcess(reqwrap, httpProcessor, context);
                
                if (orig instanceof AbortableHttpRequest) {
                    ((AbortableHttpRequest) orig).setReleaseTrigger(managedConn);
                }

                context.setAttribute(ExecutionContext.HTTP_REQUEST,
                        reqwrap);

                execCount++;
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Attempt " + execCount + " to execute request");
                    }
                    response = requestExec.execute(reqwrap, managedConn, context);
                    
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

                response.setParams(this.params);
                requestExec.postProcess(response, httpProcessor, context);
                
                RoutedRequest followup =
                    handleResponse(roureq, reqwrap, response, context);
                if (followup == null) {
                    done = true;
                } else {
                    boolean reuse = reuseStrategy.keepAlive(response, context);
                    if (reuse) {
                        LOG.debug("Connection kept alive");
                        // Make sure the response body is fully consumed, if present
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            entity.consumeContent();
                        }
                        // entity consumed above is not an auto-release entity,
                        // need to mark the connection re-usable explicitly
                        managedConn.markReusable();
                    } else {
                        managedConn.close();
                    }
                    // check if we can use the same connection for the followup
                    if ((managedConn != null) &&
                        !followup.getRoute().equals(roureq.getRoute())) {
                        // the followup has a different route, release conn
                        //@@@ need to consume response body first?
                        //@@@ or let that be done in handleResponse(...)?
                        connManager.releaseConnection(managedConn);
                        managedConn = null;
                    }
                    roureq = followup;
                }
            } // while not done

            // The connection is in or can be brought to a re-usable state.
            boolean reuse = reuseStrategy.keepAlive(response, context);

            // check for entity, release connection if possible
            if ((response == null) || (response.getEntity() == null) ||
                !response.getEntity().isStreaming()) {
                // connection not needed and (assumed to be) in re-usable state
                if (reuse)
                    managedConn.markReusable();
                connManager.releaseConnection(managedConn);
                managedConn = null;
            } else {
                // install an auto-release entity
                HttpEntity entity = response.getEntity();
                entity = new BasicManagedEntity(entity, managedConn, reuse);
                response.setEntity(entity);
            }

            return response;
            
        } catch (HttpException ex) {
            abortConnection();
            throw ex;
        } catch (IOException ex) {
            abortConnection();
            throw ex;
        } catch (InterruptedException ex) {
            abortConnection();
            throw ex;
        } catch (RuntimeException ex) {
            abortConnection();
            throw ex;
        }
    } // execute


    /**
     * Determines the route for a request.
     * Called by {@link #execute}
     * to determine the route for either the original or a followup request.
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
    protected RoutedRequest determineRoute(HttpHost    target,
                                           HttpRequest request,
                                           HttpContext context)
        throws HttpException {

        if (target == null) {
            target = (HttpHost) request.getParams().getParameter(
                ClientPNames.DEFAULT_HOST);
        }
        if (target == null) {
            throw new IllegalStateException
                ("Target host must not be null, or set in parameters.");
        }

        final HttpRoute route =
            this.routePlanner.determineRoute(target, request, context);

        return new RoutedRequest.Impl(request, route);
    }


    /**
     * Obtains a connection for the target route.
     *
     * @param route     the route for which to allocate a connection
     * @param timeout   the timeout in milliseconds,
     *                  0 or negative for no timeout
     *
     * @throws HttpException    in case of a (protocol) problem
     * @throws ConnectionPoolTimeoutException   in case of a timeout
     * @throws InterruptedException     in case of an interrupt
     */
    protected ManagedClientConnection allocateConnection(HttpRoute route,
                                                         long timeout)
        throws HttpException, ConnectionPoolTimeoutException,
               InterruptedException {

        return connManager.getConnection
            (route, timeout, TimeUnit.MILLISECONDS);

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

        HttpRouteDirector rowdy = new BasicRouteDirector();
        int step;
        do {
            HttpRoute fact = managedConn.getRoute();
            step = rowdy.nextStep(route, fact);

            switch (step) {

            case HttpRouteDirector.CONNECT_TARGET:
            case HttpRouteDirector.CONNECT_PROXY:
                managedConn.open(route, context, this.params);
                break;

            case HttpRouteDirector.TUNNEL_TARGET: {
                boolean secure = createTunnelToTarget(route, context);
                LOG.debug("Tunnel to target created.");
                managedConn.tunnelTarget(secure, this.params);
            }   break;

            case HttpRouteDirector.TUNNEL_PROXY: {
                // The most simple example for this case is a proxy chain
                // of two proxies, where P1 must be tunnelled to P2.
                // route: Source -> P1 -> P2 -> Target (3 hops)
                // fact:  Source -> P1 -> Target       (2 hops)
                final int hop = fact.getHopCount()-1; // the hop to establish
                boolean secure = createTunnelToProxy(route, hop, context);
                LOG.debug("Tunnel to proxy created.");
                managedConn.tunnelProxy(route.getHopTarget(hop),
                                        secure, this.params);
            }   break;


            case HttpRouteDirector.LAYER_PROTOCOL:
                managedConn.layerProtocol(context, this.params);
                break;

            case HttpRouteDirector.UNREACHABLE:
                throw new IllegalStateException
                    ("Unable to establish route." +
                     "\nplanned = " + route +
                     "\ncurrent = " + fact);

            case HttpRouteDirector.COMPLETE:
                // do nothing
                break;

            default:
                throw new IllegalStateException
                    ("Unknown step indicator "+step+" from RouteDirector.");
            } // switch

        } while (step > HttpRouteDirector.COMPLETE);

    } // establishConnection


    /**
     * Creates a tunnel to the target server.
     * The connection must be established to the (last) proxy.
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
    protected boolean createTunnelToTarget(HttpRoute route,
                                           HttpContext context)
        throws HttpException, IOException {

        HttpHost proxy = route.getProxyHost();
        HttpHost target = route.getTargetHost();
        HttpResponse response = null;
        
        boolean done = false;
        while (!done) {

            done = true;
            
            if (!this.managedConn.isOpen()) {
                this.managedConn.open(route, context, this.params);
            }
            
            HttpRequest connect = createConnectRequest(route, context);
            
            String agent = HttpProtocolParams.getUserAgent(params);
            if (agent != null) {
                connect.addHeader(HTTP.USER_AGENT, agent);
            }
            connect.addHeader(HTTP.TARGET_HOST, target.toHostString());
            
            AuthScheme authScheme = this.proxyAuthState.getAuthScheme();
            AuthScope authScope = this.proxyAuthState.getAuthScope();
            Credentials creds = this.proxyAuthState.getCredentials();
            if (creds != null) {
                if (authScope != null || !authScheme.isConnectionBased()) {
                    try {
                        connect.addHeader(authScheme.authenticate(creds, connect));
                    } catch (AuthenticationException ex) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Proxy authentication error: " + ex.getMessage());
                        }
                    }
                }
            }
            
            response = requestExec.execute(connect, this.managedConn, context);
            
            int status = response.getStatusLine().getStatusCode();
            if (status < 200) {
                throw new HttpException("Unexpected response to CONNECT request: " +
                        response.getStatusLine());
            }
            
            CredentialsProvider credsProvider = (CredentialsProvider)
                context.getAttribute(ClientContext.CREDS_PROVIDER);
            
            if (credsProvider != null && HttpClientParams.isAuthenticating(params)) {
                if (this.proxyAuthHandler.isAuthenticationRequested(response, context)) {

                    LOG.debug("Proxy requested authentication");
                    Map<String, Header> challenges = this.proxyAuthHandler.getChallenges(
                            response, context);
                    try {
                        processChallenges(
                                challenges, this.proxyAuthState, this.proxyAuthHandler, 
                                response, context);
                    } catch (AuthenticationException ex) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Authentication error: " +  ex.getMessage());
                            break;
                        }
                    }
                    updateAuthState(this.proxyAuthState, proxy, credsProvider);
                    
                    if (this.proxyAuthState.getCredentials() != null) {
                        done = false;

                        // Retry request
                        if (this.reuseStrategy.keepAlive(response, context)) {
                            LOG.debug("Connection kept alive");
                            // Consume response content
                            HttpEntity entity = response.getEntity();
                            if (entity != null) {
                                entity.consumeContent();
                            }                
                        } else {
                            this.managedConn.close();
                        }
                        
                    }
                    
                } else {
                    // Reset proxy auth scope
                    this.proxyAuthState.setAuthScope(null);
                }
            }
        }
        
        int status = response.getStatusLine().getStatusCode();

        if (status > 299) {

            // Buffer response content
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                response.setEntity(new BufferedHttpEntity(entity));
            }                
            
            this.managedConn.close();
            throw new TunnelRefusedException("CONNECT refused by proxy: " +
                    response.getStatusLine(), response);
        }

        this.managedConn.markReusable();
        
        // How to decide on security of the tunnelled connection?
        // The socket factory knows only about the segment to the proxy.
        // Even if that is secure, the hop to the target may be insecure.
        // Leave it to derived classes, consider insecure by default here.
        return false;

    } // createTunnelToTarget



    /**
     * Creates a tunnel to an intermediate proxy.
     * This method is <i>not</i> implemented in this class.
     * It just throws an exception here.
     *
     * @param route     the route to establish
     * @param hop       the hop in the route to establish now.
     *                  <code>route.getHopTarget(hop)</code>
     *                  will return the proxy to tunnel to.
     * @param context   the context for request execution
     *
     * @return  <code>true</code> if the partially tunnelled connection
     *          is secure, <code>false</code> otherwise.
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    protected boolean createTunnelToProxy(HttpRoute route, int hop,
                                          HttpContext context)
        throws HttpException, IOException {

        // Have a look at createTunnelToTarget and replicate the parts
        // you need in a custom derived class. If your proxies don't require
        // authentication, it is not too hard. But for the stock version of
        // HttpClient, we cannot make such simplifying assumptions and would
        // have to include proxy authentication code. The HttpComponents team
        // is currently not in a position to support rarely used code of this
        // complexity. Feel free to submit patches that refactor the code in
        // createTunnelToTarget to facilitate re-use for proxy tunnelling.

        throw new UnsupportedOperationException
            ("Proxy chains are not supported.");
    }



    /**
     * Creates the CONNECT request for tunnelling.
     * Called by {@link #createTunnelToTarget createTunnelToTarget}.
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
        ProtocolVersion ver = HttpProtocolParams.getVersion(params);
        HttpRequest req = new BasicHttpRequest
            ("CONNECT", authority, ver);

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

        HttpRoute route = roureq.getRoute();
        HttpHost target = route.getTargetHost();
        HttpHost proxy = route.getProxyHost();
        
        HttpParams params = request.getParams();
        if (HttpClientParams.isRedirecting(params) && 
                this.redirectHandler.isRedirectRequested(response, context)) {

            if (redirectCount >= maxRedirects) {
                throw new RedirectException("Maximum redirects ("
                        + maxRedirects + ") exceeded");
            }
            redirectCount++;
            
            URI uri = this.redirectHandler.getLocationURI(response, context);

            HttpHost newTarget = new HttpHost(
                    uri.getHost(), 
                    uri.getPort(),
                    uri.getScheme());
            
            HttpGet redirect = new HttpGet(uri);
            redirect.setParams(params);
            RoutedRequest newRequest = determineRoute(newTarget, redirect, context);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("Redirecting to '" + uri + "' via " + newRequest.getRoute());
            }
            
            return newRequest;
        }

        CredentialsProvider credsProvider = (CredentialsProvider)
            context.getAttribute(ClientContext.CREDS_PROVIDER);
    
        if (credsProvider != null && HttpClientParams.isAuthenticating(params)) {

            if (this.targetAuthHandler.isAuthenticationRequested(response, context)) {

                target = (HttpHost)
                    context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                if (target == null) {
                    target = route.getTargetHost();
                }
                
                LOG.debug("Target requested authentication");
                Map<String, Header> challenges = this.targetAuthHandler.getChallenges(
                        response, context); 
                try {
                    processChallenges(challenges, 
                            this.targetAuthState, this.targetAuthHandler,
                            response, context);
                } catch (AuthenticationException ex) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Authentication error: " +  ex.getMessage());
                        return null;
                    }
                }
                updateAuthState(this.targetAuthState, target, credsProvider);
                
                if (this.targetAuthState.getCredentials() != null) {
                    // Re-try the same request via the same route
                    return roureq;
                } else {
                    return null;
                }
            } else {
                // Reset target auth scope
                this.targetAuthState.setAuthScope(null);
            }
            
            if (this.proxyAuthHandler.isAuthenticationRequested(response, context)) {

                LOG.debug("Proxy requested authentication");
                Map<String, Header> challenges = this.proxyAuthHandler.getChallenges(
                        response, context);
                try {
                    processChallenges(challenges, 
                            this.proxyAuthState, this.proxyAuthHandler, 
                            response, context);
                } catch (AuthenticationException ex) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Authentication error: " +  ex.getMessage());
                        return null;
                    }
                }
                updateAuthState(this.proxyAuthState, proxy, credsProvider);
                
                if (this.proxyAuthState.getCredentials() != null) {
                    // Re-try the same request via the same route
                    return roureq;
                } else {
                    return null;
                }
            } else {
                // Reset proxy auth scope
                this.proxyAuthState.setAuthScope(null);
            }
        }
        return null;
    } // handleResponse


    /**
     * Shuts down the connection.
     * This method is called from a <code>catch</code> block in
     * {@link #execute execute} during exception handling.
     *
     * @throws IOException      in case of an IO problem
     */
    private void abortConnection() throws IOException {

        ManagedClientConnection mcc = managedConn;
        if (mcc != null) {
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
    } // abortConnection


    private void processChallenges(
            final Map<String, Header> challenges, 
            final AuthState authState,
            final AuthenticationHandler authHandler,
            final HttpResponse response, 
            final HttpContext context) 
                throws MalformedChallengeException, AuthenticationException {
        
        AuthScheme authScheme = authState.getAuthScheme();
        if (authScheme == null) {
            // Authentication not attempted before
            authScheme = authHandler.selectScheme(challenges, response, context);
            authState.setAuthScheme(authScheme);
        }
        String id = authScheme.getSchemeName();

        Header challenge = (Header) challenges.get(id.toLowerCase());
        if (challenge == null) {
            throw new AuthenticationException(id + 
                " authorization challenge expected, but not found");
        }
        authScheme.processChallenge(challenge);
        LOG.debug("Authorization challenge processed");
    }
    
    
    private void updateAuthState(
            final AuthState authState, 
            final HttpHost host,
            final CredentialsProvider credsProvider) {
        
        String hostname = host.getHostName();
        int port = host.getPort();
        if (port < 0) {
            Scheme scheme = connManager.getSchemeRegistry().getScheme(host);
            port = scheme.getDefaultPort();
        }
        
        AuthScheme authScheme = authState.getAuthScheme();
        AuthScope authScope = new AuthScope(
                hostname,
                port,
                authScheme.getRealm(), 
                authScheme.getSchemeName());  
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Authentication scope: " + authScope);
        }
        Credentials creds = authState.getCredentials();
        if (creds == null) {
            creds = credsProvider.getCredentials(authScope);
            if (LOG.isDebugEnabled()) {
                if (creds != null) {
                    LOG.debug("Found credentials");
                } else {
                    LOG.debug("Credentials not found");
                }
            }
        } else {
            if (authScheme.isComplete()) {
                LOG.debug("Authentication failed");
                creds = null;
            }
        }
        authState.setAuthScope(authScope);
        authState.setCredentials(creds);
    }
    
} // class DefaultClientRequestDirector
