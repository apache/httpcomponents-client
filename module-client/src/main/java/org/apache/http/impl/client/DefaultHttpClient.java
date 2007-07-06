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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpState;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.RoutedRequest;
import org.apache.http.client.VersionInfo;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.client.protocol.RequestProxyAuthentication;
import org.apache.http.client.protocol.RequestTargetAuthentication;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.CookieSpecRegistry;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.impl.cookie.NetscapeDraftSpecFactory;
import org.apache.http.impl.cookie.RFC2109SpecFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.SyncHttpExecutionContext;



/**
 * Default implementation of an HTTP client.
 * <br/>
 * This class replaces <code>HttpClient</code> in HttpClient 3.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public class DefaultHttpClient extends AbstractHttpClient {


    /**
     * Creates a new HTTP client from parameters and a connection manager.
     *
     * @param params    the parameters
     * @param conman    the connection manager
     */
    public DefaultHttpClient(
            final ClientConnectionManager conman,
            final HttpParams params) {
        super(conman, params);
    }

    
    public DefaultHttpClient(final HttpParams params) {
        super(null, params);
    }

    
    public DefaultHttpClient() {
        super(null, null);
    }

    
    protected HttpParams createHttpParams() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, 
                HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, 
                HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUserAgent(params, 
                "Apache-HttpClient/" + VersionInfo.getReleaseVersion() + " (java 1.4)");
        HttpProtocolParams.setUseExpectContinue(params, 
                true);
        return params;
    }

    
    protected ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(
                new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

        ClientConnectionManager connManager = null;        
        
        HttpParams params = getParams();
        String className = (String) params.getParameter(
                HttpClientParams.CONNECTION_MANAGER_FACTORY);
        
        if (className != null) {
            try {
                Class clazz = Class.forName(className);
                ClientConnectionManagerFactory factory = 
                    (ClientConnectionManagerFactory) clazz.newInstance();
                connManager = factory.newInstance(params, registry);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Invalid class name: " + className);
            } catch (IllegalAccessException ex) {
                throw new IllegalAccessError(ex.getMessage());
            } catch (InstantiationException ex) {
                throw new InstantiationError(ex.getMessage());
            }
        } else {
            connManager = new SingleClientConnManager(getParams(), registry); 
        }
        return connManager;
    }


    protected HttpContext createHttpContext() {
        return new SyncHttpExecutionContext(null);
    }

    
    protected ConnectionReuseStrategy createConnectionReuseStrategy() {
        return new DefaultConnectionReuseStrategy();
    }
    

    protected AuthSchemeRegistry createAuthSchemeRegistry() {
        AuthSchemeRegistry registry = new AuthSchemeRegistry(); 
        registry.register(
                AuthPolicy.BASIC, 
                new BasicSchemeFactory());
        registry.register(
                AuthPolicy.DIGEST, 
                new DigestSchemeFactory());
        return registry;
    }


    protected CookieSpecRegistry createCookieSpecRegistry() {
        CookieSpecRegistry registry = new CookieSpecRegistry();
        registry.register(
                CookiePolicy.BROWSER_COMPATIBILITY, 
                new BrowserCompatSpecFactory());
        registry.register(
                CookiePolicy.NETSCAPE, 
                new NetscapeDraftSpecFactory());
        registry.register(
                CookiePolicy.RFC_2109, 
                new RFC2109SpecFactory());
        return registry;
    }


    protected BasicHttpProcessor createHttpProcessor() {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        // Required protocol interceptors
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        // Recommended protocol interceptors
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        // HTTP state management interceptors
        httpproc.addInterceptor(new RequestAddCookies());
        httpproc.addInterceptor(new ResponseProcessCookies());
        // HTTP authentication interceptors
        httpproc.addInterceptor(new RequestTargetAuthentication());
        httpproc.addInterceptor(new RequestProxyAuthentication());
        return httpproc;
    }


    protected HttpRequestRetryHandler createHttpRequestRetryHandler() {
        return new DefaultHttpRequestRetryHandler();
    }


    protected RedirectHandler createRedirectHandler() {
        return new DefaultRedirectHandler();
    }


    protected AuthenticationHandler createAuthenticationHandler() {
        return new DefaultAuthenticationHandler();
    }


    protected HttpState createHttpState() {
        return new HttpState();
    }


    protected void populateContext(final HttpContext context) {
        context.setAttribute(
                HttpClientContext.AUTHSCHEME_REGISTRY, 
                getAuthSchemes());
        context.setAttribute(
                HttpClientContext.COOKIESPEC_REGISTRY, 
                getCookieSpecs());
        context.setAttribute(
                HttpClientContext.HTTP_STATE, 
                getState());
    }


    // non-javadoc, see base class AbstractHttpClient
    protected RoutedRequest determineRoute(HttpHost target,
                                           HttpRequest request,
                                           HttpContext context)
        throws HttpException {

        if (target == null) {
            target = (HttpHost) request.getParams().getParameter(
                    HttpClientParams.DEFAULT_HOST);
        }
        if (target == null) {
            throw new IllegalStateException
                ("Target host must not be null.");
        }

        HttpHost proxy = (HttpHost) request.getParams().getParameter(
                HttpClientParams.DEFAULT_PROXY);

        Scheme schm = getConnectionManager().getSchemeRegistry().
            getScheme(target.getSchemeName());
        // as it is typically used for TLS/SSL, we assume that
        // a layered scheme implies a secure connection
        boolean secure = schm.isLayered();
        
        HttpRoute route;
        if (proxy == null) {
            route = new HttpRoute(target, null, secure);
        } else {
            route = new HttpRoute(target, null, proxy, secure);
        }
        return new RoutedRequest.Impl(request, route);
    }


} // class DefaultHttpClient
