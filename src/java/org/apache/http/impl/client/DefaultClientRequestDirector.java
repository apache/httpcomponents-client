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

import org.apache.http.HttpVersion;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpException;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.RouteDirector;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.client.RoutedRequest;
import org.apache.http.client.ClientRequestDirector;



/**
 * Default implementation of a client-side request director.
 * <br/>
 * This class replaces the <code>HttpMethodDirector</code> in HttpClient 3.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public class DefaultClientRequestDirector
    implements ClientRequestDirector {

    /** The connection manager. */
    protected final ClientConnectionManager connManager;

    /** The request executor. */
    protected final HttpRequestExecutor requestExec;

    /** The parameters. */
    protected final HttpParams defaultParams;

    /** The currently allocated connection. */
    protected ManagedClientConnection managedConn;


    public DefaultClientRequestDirector(ClientConnectionManager conman,
                                        HttpRequestExecutor reqexec,
                                        HttpParams params) {

        this.connManager   = conman;
        this.requestExec   = reqexec;
        this.defaultParams = params;

        this.managedConn   = null;

        //@@@ authentication?

    } // constructor


    // non-javadoc, see interface ClientRequestDirector
    public ManagedClientConnection getConnection() {
        return managedConn;
    }

    // non-javadoc, see interface ClientRequestDirector
    public HttpResponse execute(RoutedRequest roureq, HttpContext context)
        throws HttpException, IOException {

        //@@@ link parameters? Let's rely on the request executor for now.

        HttpResponse response = null;
        boolean done = false;

        //@@@ where to put the retry handling and counter?
        //@@@ here, or retry in a calling method, or retry in an inner loop?
        //@@@ Retry in a calling method would allow for selecting an alternate
        //@@@ proxy, but it would not be aware of redirects that have already
        //@@@ been followed. Would the retry counter apply for each request
        //@@@ if redirects are followed, or for the whole sequence?

        try {
            while (!done) {
                allocateConnection(roureq.getRoute());
                establishRoute(roureq.getRoute(), context);

                HttpRequest prepreq = prepareRequest(roureq, context);
                //@@@ handle authentication here or via interceptor?

                response = requestExec.execute(prepreq, managedConn, context);

                RoutedRequest followup =
                    handleResponse(roureq, prepreq, response, context);
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
            cleanupConnection(done, response);
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
    protected void allocateConnection(HttpRoute route)
        throws HttpException {

        // we assume that the connection would have been released
        // if it was not appropriate for the route of the followup
        if (managedConn != null)
            return;

        //@@@ use connection manager timeout
        managedConn = connManager.getConnection(route);

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

        //@@@ this check for secure connections is an ugly hack until the
        //@@@ director is changed to expect HttpRoute instead of HostConfig
/*
        //@@@ the actual check should be whether the socket factory
        //@@@ for the target host scheme is a SecureSocketFactory
        HttpRoute route = null;
        {
            final boolean secure =
                !"http".equals(hostconf.getHost().getSchemeName());
            if (hostconf.getProxyHost() == null)
                route = new HttpRoute(hostconf.getHost(),
                                      hostconf.getLocalAddress(),
                                      secure);
            else
                route = new HttpRoute(hostconf.getHost(),
                                      hostconf.getLocalAddress(),
                                      hostconf.getProxyHost(),
                                      secure);
        } //@@@ end of ugly HostConfiguration -> HttpRoute conversion
*/

        //@@@ should the request parameters already be used below?
        //@@@ probably yes, but they're not linked yet
        //@@@ will linking above cause problems with linking in reqExec?
        //@@@ probably not, because the parent is replaced
        //@@@ just make sure we don't link parameters to themselves

        System.out.println("@@@ planned: " + route);

        RouteDirector rowdy = new RouteDirector();
        int step;
        do {
            HttpRoute fact = managedConn.getRoute();
            System.out.println("@@@ current: " + fact);
            step = rowdy.nextStep(route, fact);
            System.out.println("@@@ action => " + step);

            switch (step) {

            case RouteDirector.CONNECT_TARGET:
            case RouteDirector.CONNECT_PROXY:
                managedConn.open(route.toHostConfig(), context, defaultParams);
                break;

            case RouteDirector.CREATE_TUNNEL:
                boolean secure = createTunnel(route, context);
                managedConn.tunnelCreated(secure, defaultParams);
                break;

            case RouteDirector.LAYER_PROTOCOL:
                managedConn.layerProtocol(context, defaultParams);
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
        // Leave it to derived classes, consider everything insecure here.
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
        // see RFC 2817, section 5.2
        final String authority =
            route.getTargetHost().getHostName() + ":" +
            route.getTargetHost().getPort();

        //@@@ do we need a more refined algorithm to choose the HTTP version?
        //@@@ use a request factory provided by the caller/creator?
        HttpRequest req = new BasicHttpRequest
            ("CONNECT", authority, HttpVersion.HTTP_1_1);

        req.addHeader("Host", authority);

        //@@@ authenticate here, in caller, or in request interceptor?

        return req;
    }


    /**
     * Prepares a request for execution.
     *
     * @param roureq    the request to be sent, along with the route
     * @param context   the context used for the current request execution
     *
     * @return  the prepared request to send. This can be a different
     *          object than the request stored in <code>roureq</code>.
     */
    protected HttpRequest prepareRequest(RoutedRequest roureq,
                                         HttpContext context)
        throws HttpException {

        HttpRequest prepared = roureq.getRequest();

        //@@@ Instantiate a wrapper to prevent modification of the original
        //@@@ request object? It might be needed for retries.
        //@@@ If proxied and non-tunnelled, make sure an absolute URL is
        //@@@ given in the request line. The target host is in the route.

        return prepared;

    } // prepareRequest


    /**
     * Analyzes a response to check need for a followup.
     *
     * @param roureq    the request and route. This is the same object as
     *                  was passed to {@link #prepareRequest prepareRequest}.
     * @param request   the request that was actually sent. This is the object
     *                  returned by {@link #prepareRequest prepareRequest}.
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

        //@@@ if there is a followup, check connection keep-alive and
        //@@@ consume response body if necessary or close otherwise

        //@@@ if the request needs to be re-sent with authentication,
        //@@@ how to revert the modifications applied by the interceptors?
        //@@@ use a wrapper when sending?

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
     *
     * @throws IOException      in case of an IO problem
     */
    protected void cleanupConnection(boolean success, HttpResponse response)
        throws IOException {

        ManagedClientConnection mcc = managedConn;
        if (mcc == null)
            return; // nothing to be cleaned up
        
        if (success) {
            // not in exception handling, there probably is a response
            // the connection (if any) is in a re-usable state
            // check for entity, release connection if possible
            //@@@ provide hook that allows for entity buffering?
            if ((response == null) || (response.getEntity() == null) ||
                !response.getEntity().isStreaming()) {
                // connection not needed and assumed to be in re-usable state
                //@@@ evaluate connection re-use strategy, close if necessary
                managedConn = null;
                connManager.releaseConnection(mcc);
            }
        } else {
            // we got here as the result of an exception
            // no response will be returned, release the connection
            managedConn = null;
            //@@@ is the connection in a re-usable state?
            //@@@ for now, just shut it down
            try {
                if (mcc.isOpen())
                    mcc.shutdown();
            } catch (IOException ignore) {
                // can't allow exception while handling exception
            }
            connManager.releaseConnection(mcc);
        }
    } // cleanupConnection


} // class DefaultClientRequestDirector
