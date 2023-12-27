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

package org.apache.hc.client5.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.util.Args;

/**
 * Abstract {@link HttpClientContext} builder.
 *
 * @since 5.4
 */
public abstract class AbstractClientContextBuilder<T extends HttpClientContext> {

    private final SchemePortResolver schemePortResolver;

    private Lookup<CookieSpecFactory> cookieSpecRegistry;
    private Lookup<AuthSchemeFactory> authSchemeRegistry;
    private CookieStore cookieStore;
    private CredentialsProvider credentialsProvider;
    private AuthCache authCache;
    private Map<HttpHost, AuthScheme> authSchemeMap;

    protected AbstractClientContextBuilder(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
    }

    public AbstractClientContextBuilder<T> useCookieSpecRegistry(final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        this.cookieSpecRegistry = cookieSpecRegistry;
        return this;
    }

    public AbstractClientContextBuilder<T> useAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.authSchemeRegistry = authSchemeRegistry;
        return this;
    }

    public AbstractClientContextBuilder<T> useCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    public AbstractClientContextBuilder<T> useCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    public AbstractClientContextBuilder<T> useAuthCache(final AuthCache authCache) {
        this.authCache = authCache;
        return this;
    }

    public AbstractClientContextBuilder<T> preemptiveAuth(final HttpHost host, final AuthScheme authScheme) {
        Args.notNull(host, "HTTP host");
        if (authSchemeMap == null) {
            authSchemeMap = new HashMap<>();
        }
        authSchemeMap.put(RoutingSupport.normalize(host, schemePortResolver), authScheme);
        return this;
    }

    public AbstractClientContextBuilder<T> preemptiveBasicAuth(final HttpHost host, final UsernamePasswordCredentials credentials) {
        Args.notNull(host, "HTTP host");
        final BasicScheme authScheme = new BasicScheme();
        authScheme.initPreemptive(credentials);
        preemptiveAuth(host, authScheme);
        return this;
    }

    protected abstract T createContext();

    public T build() {
        final T context = createContext();
        context.setCookieSpecRegistry(cookieSpecRegistry);
        context.setAuthSchemeRegistry(authSchemeRegistry);
        context.setCookieStore(cookieStore);
        context.setCredentialsProvider(credentialsProvider);
        context.setAuthCache(authCache);
        if (authSchemeMap != null) {
            for (final Map.Entry<HttpHost, AuthScheme> entry : authSchemeMap.entrySet()) {
                context.resetAuthExchange(entry.getKey(), entry.getValue());
            }
        }
        return context;
    }

}
