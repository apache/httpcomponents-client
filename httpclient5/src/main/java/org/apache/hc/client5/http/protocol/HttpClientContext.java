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
 * Client execution {@link HttpContext}. This class can be re-used for
 * multiple consecutive logically related request executions that represent
 * a single communication session. This context may not be used concurrently.
 * <p>
 * IMPORTANT: This class is NOT thread-safe and MUST NOT be used concurrently by
 * multiple message exchanges.
 *
 * @since 4.3
 */
public class HttpClientContext extends HttpCoreContext {

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String HTTP_ROUTE = "http.route";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String REDIRECT_LOCATIONS = "http.protocol.redirect-locations";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIESPEC_REGISTRY = "http.cookiespec-registry";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIE_SPEC = "http.cookie-spec";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIE_ORIGIN = "http.cookie-origin";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String COOKIE_STORE = "http.cookie-store";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String CREDS_PROVIDER = "http.auth.credentials-provider";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String AUTH_CACHE = "http.auth.auth-cache";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String AUTH_EXCHANGE_MAP = "http.auth.exchanges";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String USER_TOKEN = "http.user-token";

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String AUTHSCHEME_REGISTRY = "http.authscheme-registry";

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

    /**
     * @deprecated Use {@link #castOrCreate(HttpContext)}.
     */
    @Deprecated
    public static HttpClientContext adapt(final HttpContext context) {
        if (context == null) {
            return new HttpClientContext();
        }
        if (context instanceof HttpClientContext) {
            return (HttpClientContext) context;
        }
        return new HttpClientContext(context);
    }

    /**
     * Casts the given generic {@link HttpContext} as {@link HttpClientContext}.
     *
     * @since 5.4
     */
    public static HttpClientContext cast(final HttpContext context) {
        if (context == null) {
            return null;
        }
        if (context instanceof HttpClientContext) {
            return (HttpClientContext) context;
        }
        return new Delegate(context);
    }

    /**
     * Casts the given generic {@link HttpContext} as {@link HttpClientContext} or
     * creates new {@link HttpClientContext} if the given context is null.
     *
     * @since 5.4
     */
    public static HttpClientContext castOrCreate(final HttpContext context) {
        return context != null ? cast(context) : create();
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

    /**
     * Stores the {@code nextnonce} value provided by the server in an HTTP response.
     * <p>
     * In the context of HTTP Digest Access Authentication, the {@code nextnonce} parameter
     * is used by the client in subsequent requests to ensure one-time or session-bound usage
     * of nonce values, enhancing security by preventing replay attacks.
     * </p>
     * <p>
     * This field is set by an interceptor or other component that processes the server's
     * response containing the {@code Authentication-Info} header. Once used, this value
     * may be cleared from the context to avoid reuse.
     * </p>
     *
     * @since 5.5
     */
    private String nextNonce;

    public HttpClientContext(final HttpContext context) {
        super(context);
    }

    public HttpClientContext() {
        super();
    }

    /**
     * Represents current route used to execute message exchanges.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
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
     * Represents a collection of all redirects executed in the context of request execution.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
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

    /**
     * Represents a {@link CookieStore} used in the context of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    /**
     * Represents a {@link CookieSpec} chosen in the context of request execution.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
     */
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

    /**
     * Represents a {@link CookieOrigin} produced in the context of request execution.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
     */
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

    /**
     * Represents a {@link CookieSpecFactory} registry used in the context of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public Lookup<CookieSpecFactory> getCookieSpecRegistry() {
        return cookieSpecFactoryLookup;
    }

    public void setCookieSpecRegistry(final Lookup<CookieSpecFactory> lookup) {
        this.cookieSpecFactoryLookup = lookup;
    }

    /**
     * Represents a {@link AuthSchemeFactory} registry used in the context of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public Lookup<AuthSchemeFactory> getAuthSchemeRegistry() {
        return authSchemeFactoryLookup;
    }

    public void setAuthSchemeRegistry(final Lookup<AuthSchemeFactory> lookup) {
        this.authSchemeFactoryLookup = lookup;
    }

    /**
     * Represents a {@link CredentialsProvider} registry used in the context of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public CredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public void setCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Represents a {@link AuthCache} used in the context of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public AuthCache getAuthCache() {
        return authCache;
    }

    public void setAuthCache(final AuthCache authCache) {
        this.authCache = authCache;
    }

    /**
     * Represents a map of {@link AuthExchange}s performed in the context of the request
     * execution.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
     *
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

    /**
     * Represents an arbitrary user token that identifies the user in the context
     * of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public Object getUserToken() {
        return userToken;
    }

    public void setUserToken(final Object userToken) {
        this.userToken = userToken;
    }

    /**
     * Represents an {@link RequestConfig used} in the context of the request execution.
     * <p>
     * This context attribute can be set by the caller.
     */
    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    /**
     * Returns {@link RequestConfig} set in the context or {@link RequestConfig#DEFAULT}
     * if not explicitly set in the context.
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
     * Represents an identifier generated for the current message exchange executed
     * in the given context.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
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

    /**
     * Retrieves the stored {@code nextnonce} value.
     *
     * @return the {@code nextnonce} parameter value, or {@code null} if not set
     * @since 5.5
     */
    @Internal
    public String getNextNonce() {
        return nextNonce;
    }

    /**
     * Sets the {@code nextnonce} value directly as an instance attribute.
     *
     * @param nextNonce the nonce value to set
     * @since 5.5
     */
    @Internal
    public void setNextNonce(final String nextNonce) {
        this.nextNonce = nextNonce;
    }

    /**
     * Internal adaptor class that delegates all its method calls to a plain {@link HttpContext}.
     * To be removed in the future.
     */
    @SuppressWarnings("deprecation")
    @Internal
    static class Delegate extends HttpClientContext {

        private final HttpContext httpContext;

        Delegate(final HttpContext httpContext) {
            super(null);
            this.httpContext = httpContext;
        }

        <T> T getAttr(final String id, final Class<T> clazz) {
            final Object obj = httpContext.getAttribute(id);
            if (obj == null) {
                return null;
            }
            return clazz.cast(obj);
        }

        @Override
        public RouteInfo getHttpRoute() {
            return getAttr(HTTP_ROUTE, RouteInfo.class);
        }

        @Override
        public void setRoute(final HttpRoute route) {
            httpContext.setAttribute(HTTP_ROUTE, route);
        }

        @Override
        public RedirectLocations getRedirectLocations() {
            RedirectLocations redirectLocations = getAttr(REDIRECT_LOCATIONS, RedirectLocations.class);
            if (redirectLocations == null) {
                redirectLocations = new RedirectLocations();
                httpContext.setAttribute(REDIRECT_LOCATIONS, redirectLocations);
            }
            return redirectLocations;
        }

        @Override
        public void setRedirectLocations(final RedirectLocations redirectLocations) {
            httpContext.setAttribute(REDIRECT_LOCATIONS, redirectLocations);
        }

        @Override
        public CookieStore getCookieStore() {
            return getAttr(COOKIE_STORE, CookieStore.class);
        }

        @Override
        public void setCookieStore(final CookieStore cookieStore) {
            httpContext.setAttribute(COOKIE_STORE, cookieStore);
        }

        @Override
        public CookieSpec getCookieSpec() {
            return getAttr(COOKIE_SPEC, CookieSpec.class);
        }

        @Override
        public void setCookieSpec(final CookieSpec cookieSpec) {
            httpContext.setAttribute(COOKIE_SPEC, cookieSpec);
        }

        @Override
        public CookieOrigin getCookieOrigin() {
            return getAttr(COOKIE_ORIGIN, CookieOrigin.class);
        }

        @Override
        public void setCookieOrigin(final CookieOrigin cookieOrigin) {
            httpContext.setAttribute(COOKIE_ORIGIN, cookieOrigin);
        }

        @Override
        public Lookup<CookieSpecFactory> getCookieSpecRegistry() {
            return getAttr(COOKIESPEC_REGISTRY, Lookup.class);
        }

        @Override
        public void setCookieSpecRegistry(final Lookup<CookieSpecFactory> lookup) {
            httpContext.setAttribute(COOKIESPEC_REGISTRY, lookup);
        }

        @Override
        public Lookup<AuthSchemeFactory> getAuthSchemeRegistry() {
            return getAttr(AUTHSCHEME_REGISTRY, Lookup.class);
        }

        @Override
        public void setAuthSchemeRegistry(final Lookup<AuthSchemeFactory> lookup) {
            httpContext.setAttribute(AUTHSCHEME_REGISTRY, lookup);
        }

        @Override
        public CredentialsProvider getCredentialsProvider() {
            return getAttr(CREDS_PROVIDER, CredentialsProvider.class);
        }

        @Override
        public void setCredentialsProvider(final CredentialsProvider credentialsProvider) {
            httpContext.setAttribute(CREDS_PROVIDER, credentialsProvider);
        }

        @Override
        public AuthCache getAuthCache() {
            return getAttr(AUTH_CACHE, AuthCache.class);
        }

        @Override
        public void setAuthCache(final AuthCache authCache) {
            httpContext.setAttribute(AUTH_CACHE, authCache);
        }

        @Override
        public Map<HttpHost, AuthExchange> getAuthExchanges() {
            Map<HttpHost, AuthExchange> map = getAttr(AUTH_EXCHANGE_MAP, Map.class);
            if (map == null) {
                map = new HashMap<>();
                httpContext.setAttribute(AUTH_EXCHANGE_MAP, map);
            }
            return map;
        }

        @Override
        public Object getUserToken() {
            return httpContext.getAttribute(USER_TOKEN);
        }

        @Override
        public void setUserToken(final Object userToken) {
            httpContext.setAttribute(USER_TOKEN, userToken);
        }

        @Override
        public RequestConfig getRequestConfig() {
            return getAttr(REQUEST_CONFIG, RequestConfig.class);
        }

        @Override
        public void setRequestConfig(final RequestConfig requestConfig) {
            httpContext.setAttribute(REQUEST_CONFIG, requestConfig);
        }

        @Override
        public String getExchangeId() {
            return getAttr(EXCHANGE_ID, String.class);
        }

        @Override
        public void setExchangeId(final String exchangeId) {
            httpContext.setAttribute(EXCHANGE_ID, exchangeId);
        }

        @Override
        public HttpRequest getRequest() {
            return getAttr(HttpCoreContext.HTTP_REQUEST, HttpRequest.class);
        }

        @Override
        public void setRequest(final HttpRequest request) {
            httpContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        }

        @Override
        public HttpResponse getResponse() {
            return getAttr(HttpCoreContext.HTTP_RESPONSE, HttpResponse.class);
        }

        @Override
        public void setResponse(final HttpResponse response) {
            httpContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        }

        @Override
        public EndpointDetails getEndpointDetails() {
            return getAttr(HttpCoreContext.CONNECTION_ENDPOINT, EndpointDetails.class);
        }

        @Override
        public void setEndpointDetails(final EndpointDetails endpointDetails) {
            httpContext.setAttribute(CONNECTION_ENDPOINT, endpointDetails);
        }

        @Override
        public SSLSession getSSLSession() {
            return getAttr(HttpCoreContext.SSL_SESSION, SSLSession.class);
        }

        @Override
        public void setSSLSession(final SSLSession sslSession) {
            httpContext.setAttribute(HttpCoreContext.SSL_SESSION, sslSession);
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return httpContext.getProtocolVersion();
        }

        @Override
        public void setProtocolVersion(final ProtocolVersion version) {
            httpContext.setProtocolVersion(version);
        }

        @Override
        public Object getAttribute(final String id) {
            return httpContext.getAttribute(id);
        }

        @Override
        public Object setAttribute(final String id, final Object obj) {
            return httpContext.setAttribute(id, obj);
        }

        @Override
        public Object removeAttribute(final String id) {
            return httpContext.removeAttribute(id);
        }

        @Override
        public <T> T getAttribute(final String id, final Class<T> clazz) {
            return getAttr(id, clazz);
        }

        @Override
        public String toString() {
            return httpContext.toString();
        }

    }

}
