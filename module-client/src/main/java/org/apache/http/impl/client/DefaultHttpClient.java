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
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.client.protocol.RequestDefaultHeaders;
import org.apache.http.client.protocol.RequestProxyAuthentication;
import org.apache.http.client.protocol.RequestTargetAuthentication;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.CookieSpecRegistry;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.conn.DefaultHttpRoutePlanner;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.cookie.BestMatchSpecFactory;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.impl.cookie.NetscapeDraftSpecFactory;
import org.apache.http.impl.cookie.RFC2109SpecFactory;
import org.apache.http.impl.cookie.RFC2965SpecFactory;
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
import org.apache.http.protocol.SyncBasicHttpContext;
import org.apache.http.util.VersionInfo;



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

    
    @Override
	protected HttpParams createHttpParams() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, 
                HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, 
                HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUseExpectContinue(params, 
                true);

        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo
            ("org.apache.http.client", getClass().getClassLoader());
        final String release = (vi != null) ?
            vi.getRelease() : VersionInfo.UNAVAILABLE;
        HttpProtocolParams.setUserAgent(params, 
                "Apache-HttpClient/" + release + " (java 1.4)");

        return params;
    }

    
    @Override
	protected ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(
                new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

        ClientConnectionManager connManager = null;        
        
        HttpParams params = getParams();
        String className = (String) params.getParameter(
                ClientPNames.CONNECTION_MANAGER_FACTORY);
        
        if (className != null) {
            try {
                Class<?> clazz = Class.forName(className);
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


    @Override
	protected HttpContext createHttpContext() {
        return new SyncBasicHttpContext(null);
    }

    
    @Override
	protected ConnectionReuseStrategy createConnectionReuseStrategy() {
        return new DefaultConnectionReuseStrategy();
    }
    

    @Override
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


    @Override
	protected CookieSpecRegistry createCookieSpecRegistry() {
        CookieSpecRegistry registry = new CookieSpecRegistry();
        registry.register(
                CookiePolicy.BEST_MATCH, 
                new BestMatchSpecFactory());
        registry.register(
                CookiePolicy.BROWSER_COMPATIBILITY, 
                new BrowserCompatSpecFactory());
        registry.register(
                CookiePolicy.NETSCAPE, 
                new NetscapeDraftSpecFactory());
        registry.register(
                CookiePolicy.RFC_2109, 
                new RFC2109SpecFactory());
        registry.register(
                CookiePolicy.RFC_2965, 
                new RFC2965SpecFactory());
        return registry;
    }


    @Override
	protected BasicHttpProcessor createHttpProcessor() {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestDefaultHeaders());
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


    @Override
	protected HttpRequestRetryHandler createHttpRequestRetryHandler() {
        return new DefaultHttpRequestRetryHandler();
    }


    @Override
	protected RedirectHandler createRedirectHandler() {
        return new DefaultRedirectHandler();
    }


    @Override
	protected AuthenticationHandler createTargetAuthenticationHandler() {
        return new DefaultTargetAuthenticationHandler();
    }


    @Override
	protected AuthenticationHandler createProxyAuthenticationHandler() {
        return new DefaultProxyAuthenticationHandler();
    }


    @Override
	protected CookieStore createCookieStore() {
        return new BasicCookieStore();
    }


    @Override
	protected CredentialsProvider createCredentialsProvider() {
        return new BasicCredentialsProvider();
    }


    @Override
	protected void populateContext(final HttpContext context) {
        context.setAttribute(
                ClientContext.AUTHSCHEME_REGISTRY, 
                getAuthSchemes());
        context.setAttribute(
                ClientContext.COOKIESPEC_REGISTRY, 
                getCookieSpecs());
        context.setAttribute(
                ClientContext.COOKIE_STORE, 
                getCookieStore());
        context.setAttribute(
                ClientContext.CREDS_PROVIDER, 
                getCredentialsProvider());
    }


    // non-javadoc, see base class AbstractHttpClient
    @Override
	protected HttpRoutePlanner createHttpRoutePlanner() {
        return new DefaultHttpRoutePlanner
            (getConnectionManager().getSchemeRegistry());
    }
    
} // class DefaultHttpClient
