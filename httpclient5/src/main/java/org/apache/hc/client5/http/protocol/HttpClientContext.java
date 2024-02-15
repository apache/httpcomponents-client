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

package org.apache.hc.client5.http.protocol;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

/**
 * Adaptor class that provides convenience type safe setters and getters
 * for common {@link HttpContext} attributes used in the course
 * of HTTP request execution.
 *
 * @since 4.3
 */
public class HttpClientContext extends HttpCoreContext {

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String HTTP_ROUTE   = "http.route";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String REDIRECT_LOCATIONS = "http.protocol.redirect-locations";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIESPEC_REGISTRY   = "http.cookiespec-registry";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIE_SPEC           = "http.cookie-spec";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIE_ORIGIN         = "http.cookie-origin";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIE_STORE          = "http.cookie-store";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String CREDS_PROVIDER        = "http.auth.credentials-provider";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String AUTH_CACHE            = "http.auth.auth-cache";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String AUTH_EXCHANGE_MAP     = "http.auth.exchanges";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String USER_TOKEN            = "http.user-token";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String AUTHSCHEME_REGISTRY   = "http.authscheme-registry";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String REQUEST_CONFIG = "http.request-config";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String EXCHANGE_ID = "http.exchange-id";

    @SuppressWarnings("deprecation")
    public static HttpClientContext adapt(final HttpContext context) {
        if (context instanceof HttpClientContext) {
            return (HttpClientContext) context;
        }
        return new HttpClientContext.Delegate(HttpCoreContext.adapt(context));
    }

    /**
     * Casts the given generic {@link HttpContext} as {@link HttpClientContext}
     * or creates a new {@link HttpClientContext} with the given {@link HttpContext}
     * as a parent.
     *
     * @since 5.4
     */
    public static HttpClientContext cast(final HttpContext context) {
        if (context == null) {
            return null;
        }
        if (context instanceof HttpClientContext) {
            return (HttpClientContext) context;
        } else {
            throw new IllegalStateException("Unexpected context type: " + context.getClass().getSimpleName() +
                    "; required context type: " + HttpClientContext.class.getSimpleName());
        }
    }

    public static HttpClientContext create() {
        return new HttpClientContext();
    }

    private HttpRoute route;
    private RedirectLocations redirectLocations;
    private CookieSpec cookieSpec;
    private CookieOrigin cookieOrigin;
    private Map<HttpHost, AuthExchange> authExchangeMap;
    private String exchangeId;

    private Lookup<CookieSpecFactory> cookieSpecFactoryLookup;
    private Lookup<AuthSchemeFactory> authSchemeFactoryLookup;
    private CookieStore cookieStore;
    private CredentialsProvider credentialsProvider;
    private AuthCache authCache;
    private Object userToken;
    private RequestConfig requestConfig;

    public HttpClientContext(final HttpContext context) {
        super(context);
    }

    public HttpClientContext() {
        super();
    }

    /**
     * Represents current route used to execute message exchanges.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     */
    public RouteInfo getHttpRoute() {
        return route;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setRoute(final HttpRoute route) {
        this.route = route;
    }

    /**
     * Represents a collection of all redirects executed in the course of request execution.
     * <p>
     * This context attribute is expected to be populated by the protocol handler
     * in the course of request execution.
     */
    public RedirectLocations getRedirectLocations() {
        if (this.redirectLocations == null) {
            this.redirectLocations = new RedirectLocations();
        }
        return this.redirectLocations;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setRedirectLocations(final RedirectLocations redirectLocations) {
        this.redirectLocations = redirectLocations;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public CookieSpec getCookieSpec() {
        return cookieSpec;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setCookieSpec(final CookieSpec cookieSpec) {
        this.cookieSpec = cookieSpec;
    }

    public CookieOrigin getCookieOrigin() {
        return cookieOrigin;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setCookieOrigin(final CookieOrigin cookieOrigin) {
        this.cookieOrigin = cookieOrigin;
    }

    public Lookup<CookieSpecFactory> getCookieSpecRegistry() {
        return cookieSpecFactoryLookup;
    }

    public void setCookieSpecRegistry(final Lookup<CookieSpecFactory> lookup) {
        this.cookieSpecFactoryLookup = lookup;
    }

    public Lookup<AuthSchemeFactory> getAuthSchemeRegistry() {
        return authSchemeFactoryLookup;
    }

    public void setAuthSchemeRegistry(final Lookup<AuthSchemeFactory> lookup) {
        this.authSchemeFactoryLookup = lookup;
    }

    public CredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public void setCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public AuthCache getAuthCache() {
        return authCache;
    }

    public void setAuthCache(final AuthCache authCache) {
        this.authCache = authCache;
    }

    /**
     * @since 5.0
     */
    public Map<HttpHost, AuthExchange> getAuthExchanges() {
        if (authExchangeMap == null) {
            authExchangeMap = new HashMap<>();
        }
        return authExchangeMap;
    }

    /**
     * @since 5.0
     */
    public AuthExchange getAuthExchange(final HttpHost host) {
        return getAuthExchanges().computeIfAbsent(host, k -> new AuthExchange());
    }

    /**
     * @since 5.0
     */
    public void setAuthExchange(final HttpHost host, final AuthExchange authExchange) {
        getAuthExchanges().put(host, authExchange);
    }

    /**
     * @since 5.0
     */
    public void resetAuthExchange(final HttpHost host, final AuthScheme authScheme) {
        final AuthExchange authExchange = new AuthExchange();
        authExchange.select(authScheme);
        getAuthExchanges().put(host, authExchange);
    }

    /**
     * @deprecated Use {@link #getUserToken()}
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T getUserToken(final Class<T> clazz) {
        return (T) getUserToken();
    }

    public Object getUserToken() {
        return userToken;
    }

    public void setUserToken(final Object userToken) {
        this.userToken = userToken;
    }

    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    /**
     * Returns {@link RequestConfig} set in the context or {@link RequestConfig#DEFAULT}
     * if not present in the context.
     *
     * @since 5.4
     */
    public final RequestConfig getRequestConfigOrDefault() {
        final RequestConfig requestConfig = getRequestConfig();
        return requestConfig != null ? requestConfig : RequestConfig.DEFAULT;
    }

    public void setRequestConfig(final RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
    }

    /**
     * @since 5.1
     */
    public String getExchangeId() {
        return exchangeId;
    }

    /**
     * @since 5.1
     */
    public void setExchangeId(final String exchangeId) {
        this.exchangeId = exchangeId;
    }

    static class Delegate extends HttpClientContext {

        final private HttpCoreContext parent;

        Delegate(final HttpCoreContext parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return parent.getProtocolVersion();
        }

        @Override
        public void setProtocolVersion(final ProtocolVersion version) {
            parent.setProtocolVersion(version);
        }

        @Override
        public Object getAttribute(final String id) {
            return parent.getAttribute(id);
        }

        @Override
        public Object setAttribute(final String id, final Object obj) {
            return parent.setAttribute(id, obj);
        }

        @Override
        public Object removeAttribute(final String id) {
            return parent.removeAttribute(id);
        }

        @Override
        public <T> T getAttribute(final String id, final Class<T> clazz) {
            return parent.getAttribute(id, clazz);
        }

        @Override
        public HttpRequest getRequest() {
            return parent.getRequest();
        }

        @Override
        public void setRequest(final HttpRequest request) {
            parent.setRequest(request);
        }

        @Override
        public HttpResponse getResponse() {
            return parent.getResponse();
        }

        @Override
        public void setResponse(final HttpResponse response) {
            parent.setResponse(response);
        }

        @Override
        public EndpointDetails getEndpointDetails() {
            return parent.getEndpointDetails();
        }

        @Override
        public void setEndpointDetails(final EndpointDetails endpointDetails) {
            parent.setEndpointDetails(endpointDetails);
        }

        @Override
        public SSLSession getSSLSession() {
            return parent.getSSLSession();
        }

        @Override
        public void setSSLSession(final SSLSession sslSession) {
            parent.setSSLSession(sslSession);
        }

        @Override
        public String toString() {
            return parent.toString();
        }

    }

}
