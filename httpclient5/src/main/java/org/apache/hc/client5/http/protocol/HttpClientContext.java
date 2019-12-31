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
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Args;

/**
 * Adaptor class that provides convenience type safe setters and getters
 * for common {@link HttpContext} attributes used in the course
 * of HTTP request execution.
 *
 * @since 4.3
 */
public class HttpClientContext extends HttpCoreContext {

    /**
     * Attribute name of a {@link RouteInfo}
     * object that represents the actual connection route.
     */
    public static final String HTTP_ROUTE   = "http.route";

    /**
     * Attribute name of a {@link RedirectLocations} object that represents a collection of all
     * redirect locations received in the process of request execution.
     */
    public static final String REDIRECT_LOCATIONS = "http.protocol.redirect-locations";

    /**
     * Attribute name of a {@link org.apache.hc.core5.http.config.Lookup} object that represents
     * the actual {@link CookieSpecFactory} registry.
     */
    public static final String COOKIESPEC_REGISTRY   = "http.cookiespec-registry";

    /**
     * Attribute name of a {@link org.apache.hc.client5.http.cookie.CookieSpec}
     * object that represents the actual cookie specification.
     */
    public static final String COOKIE_SPEC           = "http.cookie-spec";

    /**
     * Attribute name of a {@link org.apache.hc.client5.http.cookie.CookieOrigin}
     * object that represents the actual details of the origin server.
     */
    public static final String COOKIE_ORIGIN         = "http.cookie-origin";

    /**
     * Attribute name of a {@link CookieStore}
     * object that represents the actual cookie store.
     */
    public static final String COOKIE_STORE          = "http.cookie-store";

    /**
     * Attribute name of a {@link CredentialsProvider}
     * object that represents the actual credentials provider.
     */
    public static final String CREDS_PROVIDER        = "http.auth.credentials-provider";

    /**
     * Attribute name of a {@link AuthCache} object
     * that represents the auth scheme cache.
     */
    public static final String AUTH_CACHE            = "http.auth.auth-cache";

    /**
     * Attribute name of a map containing actual {@link AuthExchange}s keyed by their respective
     * {@link org.apache.hc.core5.http.HttpHost}.
     */
    public static final String AUTH_EXCHANGE_MAP     = "http.auth.exchanges";

    /**
     * Attribute name of a {@link java.lang.Object} object that represents
     * the actual user identity such as user {@link java.security.Principal}.
     */
    public static final String USER_TOKEN            = "http.user-token";

    /**
     * Attribute name of a {@link org.apache.hc.core5.http.config.Lookup} object that represents
     * the actual {@link AuthSchemeFactory} registry.
     */
    public static final String AUTHSCHEME_REGISTRY   = "http.authscheme-registry";

    /**
     * Attribute name of a {@link org.apache.hc.client5.http.config.RequestConfig} object that
     * represents the actual request configuration.
     */
    public static final String REQUEST_CONFIG = "http.request-config";

    public static HttpClientContext adapt(final HttpContext context) {
        Args.notNull(context, "HTTP context");
        if (context instanceof HttpClientContext) {
            return (HttpClientContext) context;
        }
        return new HttpClientContext(context);
    }

    public static HttpClientContext create() {
        return new HttpClientContext(new BasicHttpContext());
    }

    public HttpClientContext(final HttpContext context) {
        super(context);
    }

    public HttpClientContext() {
        super();
    }

    public RouteInfo getHttpRoute() {
        return getAttribute(HTTP_ROUTE, HttpRoute.class);
    }

    public RedirectLocations getRedirectLocations() {
        return getAttribute(REDIRECT_LOCATIONS, RedirectLocations.class);
    }

    public CookieStore getCookieStore() {
        return getAttribute(COOKIE_STORE, CookieStore.class);
    }

    public void setCookieStore(final CookieStore cookieStore) {
        setAttribute(COOKIE_STORE, cookieStore);
    }

    public CookieSpec getCookieSpec() {
        return getAttribute(COOKIE_SPEC, CookieSpec.class);
    }

    public CookieOrigin getCookieOrigin() {
        return getAttribute(COOKIE_ORIGIN, CookieOrigin.class);
    }

    private <T> Lookup<T> getLookup(final String name, final Class<T> clazz) {
        return getAttribute(name, Lookup.class);
    }

    public Lookup<CookieSpecFactory> getCookieSpecRegistry() {
        return getLookup(COOKIESPEC_REGISTRY, CookieSpecFactory.class);
    }

    public void setCookieSpecRegistry(final Lookup<CookieSpecFactory> lookup) {
        setAttribute(COOKIESPEC_REGISTRY, lookup);
    }

    public Lookup<AuthSchemeFactory> getAuthSchemeRegistry() {
        return getLookup(AUTHSCHEME_REGISTRY, AuthSchemeFactory.class);
    }

    public void setAuthSchemeRegistry(final Lookup<AuthSchemeFactory> lookup) {
        setAttribute(AUTHSCHEME_REGISTRY, lookup);
    }

    public CredentialsProvider getCredentialsProvider() {
        return getAttribute(CREDS_PROVIDER, CredentialsProvider.class);
    }

    public void setCredentialsProvider(final CredentialsProvider credentialsProvider) {
        setAttribute(CREDS_PROVIDER, credentialsProvider);
    }

    public AuthCache getAuthCache() {
        return getAttribute(AUTH_CACHE, AuthCache.class);
    }

    public void setAuthCache(final AuthCache authCache) {
        setAttribute(AUTH_CACHE, authCache);
    }

    /**
     * @since 5.0
     */
    @SuppressWarnings("unchecked")
    public Map<HttpHost, AuthExchange> getAuthExchanges() {
        Map<HttpHost, AuthExchange> map = (Map<HttpHost, AuthExchange>) getAttribute(AUTH_EXCHANGE_MAP);
        if (map == null) {
            map = new HashMap<>();
            setAttribute(AUTH_EXCHANGE_MAP, map);
        }
        return map;
    }

    /**
     * @since 5.0
     */
    public AuthExchange getAuthExchange(final HttpHost host) {
        final Map<HttpHost, AuthExchange> authExchangeMap = getAuthExchanges();
        AuthExchange authExchange = authExchangeMap.get(host);
        if (authExchange == null) {
            authExchange = new AuthExchange();
            authExchangeMap.put(host, authExchange);
        }
        return authExchange;
    }

    /**
     * @since 5.0
     */
    public void setAuthExchange(final HttpHost host, final AuthExchange authExchange) {
        final Map<HttpHost, AuthExchange> authExchangeMap = getAuthExchanges();
        authExchangeMap.put(host, authExchange);
    }

    /**
     * @since 5.0
     */
    public void resetAuthExchange(final HttpHost host, final AuthScheme authScheme) {
        final AuthExchange authExchange = new AuthExchange();
        authExchange.select(authScheme);
        final Map<HttpHost, AuthExchange> authExchangeMap = getAuthExchanges();
        authExchangeMap.put(host, authExchange);
    }

    public <T> T getUserToken(final Class<T> clazz) {
        return getAttribute(USER_TOKEN, clazz);
    }

    public Object getUserToken() {
        return getAttribute(USER_TOKEN);
    }

    public void setUserToken(final Object obj) {
        setAttribute(USER_TOKEN, obj);
    }

    public RequestConfig getRequestConfig() {
        final RequestConfig config = getAttribute(REQUEST_CONFIG, RequestConfig.class);
        return config != null ? config : RequestConfig.DEFAULT;
    }

    public void setRequestConfig(final RequestConfig config) {
        setAttribute(REQUEST_CONFIG, config);
    }

}
