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

package org.apache.http.client.protocol;

/**
 * {@link org.apache.http.protocol.HttpContext} attribute names for
 * client side HTTP protocol processing.
 *
 * @since 4.0
 *
 * @deprecated (4.3) use {@link HttpClientContext}.
 */
@Deprecated
public interface ClientContext {

    /**
     * Attribute name of a {@link org.apache.http.conn.routing.RouteInfo}
     * object that represents the actual connection route.
     *
     * @since 4.3
     */
    String ROUTE   = "http.route";

    /**
     * Attribute name of a {@link org.apache.http.conn.scheme.Scheme}
     * object that represents the actual protocol scheme registry.
     */
    String SCHEME_REGISTRY   = "http.scheme-registry";

    /**
     * Attribute name of a {@link org.apache.http.config.Lookup} object that represents
     * the actual {@link org.apache.http.cookie.CookieSpecRegistry} registry.
     */
    String COOKIESPEC_REGISTRY   = "http.cookiespec-registry";

    /**
     * Attribute name of a {@link org.apache.http.cookie.CookieSpec}
     * object that represents the actual cookie specification.
     */
    String COOKIE_SPEC           = "http.cookie-spec";

    /**
     * Attribute name of a {@link org.apache.http.cookie.CookieOrigin}
     * object that represents the actual details of the origin server.
     */
    String COOKIE_ORIGIN         = "http.cookie-origin";

    /**
     * Attribute name of a {@link org.apache.http.client.CookieStore}
     * object that represents the actual cookie store.
     */
    String COOKIE_STORE          = "http.cookie-store";

    /**
     * Attribute name of a {@link org.apache.http.client.CredentialsProvider}
     * object that represents the actual credentials provider.
     */
    String CREDS_PROVIDER        = "http.auth.credentials-provider";

    /**
     * Attribute name of a {@link org.apache.http.client.AuthCache} object
     * that represents the auth scheme cache.
     */
    String AUTH_CACHE            = "http.auth.auth-cache";

    /**
     * Attribute name of a {@link org.apache.http.auth.AuthState}
     * object that represents the actual target authentication state.
     */
    String TARGET_AUTH_STATE     = "http.auth.target-scope";

    /**
     * Attribute name of a {@link org.apache.http.auth.AuthState}
     * object that represents the actual proxy authentication state.
     */
    String PROXY_AUTH_STATE      = "http.auth.proxy-scope";

    String AUTH_SCHEME_PREF      = "http.auth.scheme-pref";

    /**
     * Attribute name of a {@link java.lang.Object} object that represents
     * the actual user identity such as user {@link java.security.Principal}.
     */
    String USER_TOKEN            = "http.user-token";

    /**
     * Attribute name of a {@link org.apache.http.config.Lookup} object that represents
     * the actual {@link org.apache.http.auth.AuthSchemeRegistry} registry.
     */
    String AUTHSCHEME_REGISTRY   = "http.authscheme-registry";

    String SOCKET_FACTORY_REGISTRY = "http.socket-factory-registry";

    /**
     * Attribute name of a {@link org.apache.http.client.config.RequestConfig} object that
     * represents the actual request configuration.
     *
     * @since 4.3
     */
    String REQUEST_CONFIG = "http.request-config";

}
