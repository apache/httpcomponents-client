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

package org.apache.http.impl.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthState;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParamConfig;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.execchain.ClientExecChain;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * @since 4.3
 */
@ThreadSafe
@SuppressWarnings("deprecation")
class InternalHttpClient extends CloseableHttpClient {

    private final Log log = LogFactory.getLog(getClass());

    private final ClientExecChain execChain;
    private final HttpClientConnectionManager connManager;
    private final HttpRoutePlanner routePlanner;
    private final Lookup<CookieSpecProvider> cookieSpecRegistry;
    private final Lookup<AuthSchemeProvider> authSchemeRegistry;
    private final CookieStore cookieStore;
    private final CredentialsProvider credentialsProvider;
    private final RequestConfig defaultConfig;
    private final List<Closeable> closeables;
    private final HttpParams params;

    public InternalHttpClient(
            final ClientExecChain execChain,
            final HttpClientConnectionManager connManager,
            final HttpRoutePlanner routePlanner,
            final Lookup<CookieSpecProvider> cookieSpecRegistry,
            final Lookup<AuthSchemeProvider> authSchemeRegistry,
            final CookieStore cookieStore,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) {
        super();
        Args.notNull(execChain, "HTTP client exec chain");
        Args.notNull(connManager, "HTTP connection manager");
        Args.notNull(routePlanner, "HTTP route planner");
        this.execChain = execChain;
        this.connManager = connManager;
        this.routePlanner = routePlanner;
        this.cookieSpecRegistry = cookieSpecRegistry;
        this.authSchemeRegistry = authSchemeRegistry;
        this.cookieStore = cookieStore;
        this.credentialsProvider = credentialsProvider;
        this.defaultConfig = defaultConfig;
        this.closeables = closeables;
        this.params = new BasicHttpParams();
    }

    private HttpRoute determineRoute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {
        HttpHost host = target;
        if (host == null) {
            host = (HttpHost) request.getParams().getParameter(ClientPNames.DEFAULT_HOST);
        }
        Asserts.notNull(host, "Target host");
        return this.routePlanner.determineRoute(host, request, context);
    }

    private HttpClientContext setupContext(final HttpContext localContext) {
        HttpClientContext context = HttpClientContext.adapt(
                localContext != null ? localContext : new BasicHttpContext());
        if (context.getAttribute(ClientContext.TARGET_AUTH_STATE) == null) {
            context.setAttribute(ClientContext.TARGET_AUTH_STATE, new AuthState());
        }
        if (context.getAttribute(ClientContext.PROXY_AUTH_STATE) == null) {
            context.setAttribute(ClientContext.PROXY_AUTH_STATE, new AuthState());
        }
        if (context.getAttribute(ClientContext.AUTHSCHEME_REGISTRY) == null) {
            context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, this.authSchemeRegistry);
        }
        if (context.getAttribute(ClientContext.COOKIESPEC_REGISTRY) == null) {
            context.setAttribute(ClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);
        }
        if (context.getAttribute(ClientContext.COOKIE_STORE) == null) {
            context.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);
        }
        if (context.getAttribute(ClientContext.CREDS_PROVIDER) == null) {
            context.setAttribute(ClientContext.CREDS_PROVIDER, this.credentialsProvider);
        }
        return context;
    }

    @Override
	protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        Args.notNull(request, "Request");
        HttpExecutionAware execListner = null;
        if (request instanceof HttpExecutionAware) {
            execListner = (HttpExecutionAware) request;
        }
        try {
            HttpRequestWrapper wrapper = HttpRequestWrapper.wrap(request);
            HttpClientContext localcontext = setupContext(context);
            HttpRoute route = determineRoute(target, wrapper, localcontext);
            RequestConfig config = null;
            if (request instanceof Configurable) {
                config = ((Configurable) request).getConfig();
            }
            if (config == null) {
                config = this.defaultConfig;
            }
            if (config == null) {
                config = HttpClientParamConfig.getRequestConfig(params);
            }
            localcontext.setRequestConfig(config);
            return this.execChain.execute(route, wrapper, localcontext, execListner);
        } catch (HttpException httpException) {
            throw new ClientProtocolException(httpException);
        }
    }

    public HttpParams getParams() {
        return this.params;
    }

    public void close() {
        this.connManager.shutdown();
        if (this.closeables != null) {
            for (Closeable closeable: this.closeables) {
                try {
                    closeable.close();
                } catch (IOException ex) {
                    this.log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    public ClientConnectionManager getConnectionManager() {

        return new ClientConnectionManager() {

            public void shutdown() {
                connManager.shutdown();
            }

            public ClientConnectionRequest requestConnection(
                    HttpRoute route, Object state) {
                throw new UnsupportedOperationException();
            }

            public void releaseConnection(
                    ManagedClientConnection conn,
                    long validDuration, TimeUnit timeUnit) {
                throw new UnsupportedOperationException();
            }

            public SchemeRegistry getSchemeRegistry() {
                throw new UnsupportedOperationException();
            }

            public void closeIdleConnections(long idletime, TimeUnit tunit) {
                connManager.closeIdleConnections(idletime, tunit);
            }

            public void closeExpiredConnections() {
                connManager.closeExpiredConnections();
            }

        };

    }

}
