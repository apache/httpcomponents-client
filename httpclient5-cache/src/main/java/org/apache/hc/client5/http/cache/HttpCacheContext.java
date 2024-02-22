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
package org.apache.hc.client5.http.cache;

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
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Cache execution {@link HttpContext}. This class can be re-used for
 * multiple consecutive logically related request executions that represent
 * a single communication session. This context may not be used concurrently.
 * <p>
 * IMPORTANT: This class is NOT thread-safe and MUST NOT be used concurrently by
 * multiple message exchanges.
 *
 * @since 4.3
 */
public class HttpCacheContext extends HttpClientContext {

    /**
     * @deprecated Use getter methods
     */
    @Deprecated
    public static final String CACHE_RESPONSE_STATUS = "http.cache.response.status";
    static final String REQUEST_CACHE_CONTROL = "http.cache.request-control";
    static final String RESPONSE_CACHE_CONTROL = "http.cache.response-control";
    static final String CACHE_ENTRY = "http.cache.entry";

    /**
     * @deprecated Use {@link #castOrCreate(HttpContext)}.
     */
    @Deprecated
    public static HttpCacheContext adapt(final HttpContext context) {
        if (context instanceof HttpCacheContext) {
            return (HttpCacheContext) context;
        }
        return new Delegate(HttpClientContext.castOrCreate(context));
    }

    /**
     * Casts the given generic {@link HttpContext} as {@link HttpCacheContext} or
     * throws an {@link IllegalStateException} if the given context is not suitable.
     *
     * @since 5.4
     */
    public static HttpCacheContext cast(final HttpContext context) {
        if (context == null) {
            return null;
        }
        if (context instanceof HttpCacheContext) {
            return (HttpCacheContext) context;
        } else if (context instanceof HttpClientContext) {
            return new Delegate((HttpClientContext) context);
        } else {
            return new Delegate(HttpClientContext.cast(context));
        }
    }

    /**
     * Casts the given generic {@link HttpContext} as {@link HttpCacheContext} or
     * creates new {@link HttpCacheContext} if the given context is null..
     *
     * @since 5.4
     */
    public static HttpCacheContext castOrCreate(final HttpContext context) {
        return context != null ? cast(context) : create();
    }

    public static HttpCacheContext create() {
        return new HttpCacheContext();
    }

    private CacheResponseStatus responseStatus;
    private RequestCacheControl requestCacheControl;
    private ResponseCacheControl responseCacheControl;
    private HttpCacheEntry cacheEntry;

    public HttpCacheContext(final HttpContext context) {
        super(context);
    }

    public HttpCacheContext() {
        super();
    }

    /**
     * Represents an outcome of the cache operation and the way the response has been
     * generated.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
     */
    public CacheResponseStatus getCacheResponseStatus() {
        return responseStatus;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setCacheResponseStatus(final CacheResponseStatus responseStatus) {
        this.responseStatus = responseStatus;
    }

    /**
     * Represents cache control requested by the client.
     * <p>
     * This context attribute is expected to be set by the caller.
     *
     * @since 5.4
     */
    public RequestCacheControl getRequestCacheControl() {
        return requestCacheControl;
    }

    /**
     * Returns cache control requested by the client or {@link RequestCacheControl#DEFAULT}
     * if not explicitly set in the context.
     *
     * @since 5.4
     */
    public final RequestCacheControl getRequestCacheControlOrDefault() {
        final RequestCacheControl cacheControl = getRequestCacheControl();
        return cacheControl != null ? cacheControl : RequestCacheControl.DEFAULT;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setRequestCacheControl(final RequestCacheControl requestCacheControl) {
        this.requestCacheControl = requestCacheControl;
    }

    /**
     * Represents cache control enforced by the server.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
     *
     * @since 5.4
     */
    public ResponseCacheControl getResponseCacheControl() {
        return responseCacheControl;
    }

    /**
     * Represents cache control enforced by the server or {@link ResponseCacheControl#DEFAULT}
     * if not explicitly set in the context.
     *
     * @since 5.4
     */
    public final ResponseCacheControl getResponseCacheControlOrDefault() {
        final ResponseCacheControl cacheControl = getResponseCacheControl();
        return cacheControl != null ? cacheControl : ResponseCacheControl.DEFAULT;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setResponseCacheControl(final ResponseCacheControl responseCacheControl) {
        this.responseCacheControl = responseCacheControl;
    }

    /**
     * Represents the cache entry the resource of which has been used to generate the response.
     * <p>
     * This context attribute is expected to be populated by the protocol handler.
     *
     * @since 5.4
     */
    public HttpCacheEntry getCacheEntry() {
        return cacheEntry;
    }

    /**
     * @since 5.4
     */
    @Internal
    public void setCacheEntry(final HttpCacheEntry cacheEntry) {
        this.cacheEntry = cacheEntry;
    }

    /**
     * Internal adaptor class that delegates all its method calls to {@link HttpClientContext}.
     * To be removed in the future.
     */
    @SuppressWarnings("deprecation")
    @Internal
    static class Delegate extends HttpCacheContext {

        private final HttpClientContext clientContext;

        Delegate(final HttpClientContext clientContext) {
            super(null);
            this.clientContext = clientContext;
        }

        @Override
        public CacheResponseStatus getCacheResponseStatus() {
            return clientContext.getAttribute(CACHE_RESPONSE_STATUS, CacheResponseStatus.class);
        }

        @Override
        public void setCacheResponseStatus(final CacheResponseStatus responseStatus) {
            clientContext.setAttribute(CACHE_RESPONSE_STATUS, responseStatus);
        }

        @Override
        public RequestCacheControl getRequestCacheControl() {
            return clientContext.getAttribute(REQUEST_CACHE_CONTROL, RequestCacheControl.class);
        }

        @Override
        public void setRequestCacheControl(final RequestCacheControl requestCacheControl) {
            clientContext.setAttribute(REQUEST_CACHE_CONTROL, requestCacheControl);
        }

        @Override
        public ResponseCacheControl getResponseCacheControl() {
            return clientContext.getAttribute(RESPONSE_CACHE_CONTROL, ResponseCacheControl.class);
        }

        @Override
        public void setResponseCacheControl(final ResponseCacheControl responseCacheControl) {
            clientContext.setAttribute(RESPONSE_CACHE_CONTROL, responseCacheControl);
        }

        @Override
        public HttpCacheEntry getCacheEntry() {
            return clientContext.getAttribute(CACHE_ENTRY, HttpCacheEntry.class);
        }

        @Override
        public void setCacheEntry(final HttpCacheEntry cacheEntry) {
            clientContext.setAttribute(CACHE_ENTRY, cacheEntry);
        }

        @Override
        public RouteInfo getHttpRoute() {
            return clientContext.getHttpRoute();
        }

        @Override
        @Internal
        public void setRoute(final HttpRoute route) {
            clientContext.setRoute(route);
        }

        @Override
        public RedirectLocations getRedirectLocations() {
            return clientContext.getRedirectLocations();
        }

        @Override
        @Internal
        public void setRedirectLocations(final RedirectLocations redirectLocations) {
            clientContext.setRedirectLocations(redirectLocations);
        }

        @Override
        public CookieStore getCookieStore() {
            return clientContext.getCookieStore();
        }

        @Override
        public void setCookieStore(final CookieStore cookieStore) {
            clientContext.setCookieStore(cookieStore);
        }

        @Override
        public CookieSpec getCookieSpec() {
            return clientContext.getCookieSpec();
        }

        @Override
        @Internal
        public void setCookieSpec(final CookieSpec cookieSpec) {
            clientContext.setCookieSpec(cookieSpec);
        }

        @Override
        public CookieOrigin getCookieOrigin() {
            return clientContext.getCookieOrigin();
        }

        @Override
        @Internal
        public void setCookieOrigin(final CookieOrigin cookieOrigin) {
            clientContext.setCookieOrigin(cookieOrigin);
        }

        @Override
        public Lookup<CookieSpecFactory> getCookieSpecRegistry() {
            return clientContext.getCookieSpecRegistry();
        }

        @Override
        public void setCookieSpecRegistry(final Lookup<CookieSpecFactory> lookup) {
            clientContext.setCookieSpecRegistry(lookup);
        }

        @Override
        public Lookup<AuthSchemeFactory> getAuthSchemeRegistry() {
            return clientContext.getAuthSchemeRegistry();
        }

        @Override
        public void setAuthSchemeRegistry(final Lookup<AuthSchemeFactory> lookup) {
            clientContext.setAuthSchemeRegistry(lookup);
        }

        @Override
        public CredentialsProvider getCredentialsProvider() {
            return clientContext.getCredentialsProvider();
        }

        @Override
        public void setCredentialsProvider(final CredentialsProvider credentialsProvider) {
            clientContext.setCredentialsProvider(credentialsProvider);
        }

        @Override
        public AuthCache getAuthCache() {
            return clientContext.getAuthCache();
        }

        @Override
        public void setAuthCache(final AuthCache authCache) {
            clientContext.setAuthCache(authCache);
        }

        @Override
        public Map<HttpHost, AuthExchange> getAuthExchanges() {
            return clientContext.getAuthExchanges();
        }

        @Override
        public AuthExchange getAuthExchange(final HttpHost host) {
            return clientContext.getAuthExchange(host);
        }

        @Override
        public void setAuthExchange(final HttpHost host, final AuthExchange authExchange) {
            clientContext.setAuthExchange(host, authExchange);
        }

        @Override
        public void resetAuthExchange(final HttpHost host, final AuthScheme authScheme) {
            clientContext.resetAuthExchange(host, authScheme);
        }

        @Override
        public Object getUserToken() {
            return clientContext.getUserToken();
        }

        @Override
        public void setUserToken(final Object userToken) {
            clientContext.setUserToken(userToken);
        }

        @Override
        public RequestConfig getRequestConfig() {
            return clientContext.getRequestConfig();
        }

        @Override
        public void setRequestConfig(final RequestConfig requestConfig) {
            clientContext.setRequestConfig(requestConfig);
        }

        @Override
        public String getExchangeId() {
            return clientContext.getExchangeId();
        }

        @Override
        public void setExchangeId(final String exchangeId) {
            clientContext.setExchangeId(exchangeId);
        }

        @Override
        public HttpRequest getRequest() {
            return clientContext.getRequest();
        }

        @Override
        public void setRequest(final HttpRequest request) {
            clientContext.setRequest(request);
        }

        @Override
        public HttpResponse getResponse() {
            return clientContext.getResponse();
        }

        @Override
        public void setResponse(final HttpResponse response) {
            clientContext.setResponse(response);
        }

        @Override
        public EndpointDetails getEndpointDetails() {
            return clientContext.getEndpointDetails();
        }

        @Override
        public void setEndpointDetails(final EndpointDetails endpointDetails) {
            clientContext.setEndpointDetails(endpointDetails);
        }

        @Override
        public SSLSession getSSLSession() {
            return clientContext.getSSLSession();
        }

        @Override
        public void setSSLSession(final SSLSession sslSession) {
            clientContext.setSSLSession(sslSession);
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return clientContext.getProtocolVersion();
        }

        @Override
        public void setProtocolVersion(final ProtocolVersion version) {
            clientContext.setProtocolVersion(version);
        }

        @Override
        public Object getAttribute(final String id) {
            return clientContext.getAttribute(id);
        }

        @Override
        public Object setAttribute(final String id, final Object obj) {
            return clientContext.setAttribute(id, obj);
        }

        @Override
        public Object removeAttribute(final String id) {
            return clientContext.removeAttribute(id);
        }

        @Override
        public <T> T getAttribute(final String id, final Class<T> clazz) {
            return clientContext.getAttribute(id, clazz);
        }

        @Override
        public String toString() {
            return clientContext.toString();
        }

    }

}