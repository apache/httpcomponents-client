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

import org.apache.hc.client5.http.AbstractClientContextBuilder;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;

public class CacheContextBuilder extends AbstractClientContextBuilder<HttpCacheContext> {

    public static CacheContextBuilder create(final SchemePortResolver schemePortResolver) {
        return new CacheContextBuilder(schemePortResolver);
    }

    public static CacheContextBuilder create() {
        return new CacheContextBuilder(DefaultSchemePortResolver.INSTANCE);
    }

    private RequestCacheControl cacheControl;

    protected CacheContextBuilder(final SchemePortResolver schemePortResolver) {
        super(schemePortResolver);
    }

    @Override
    public CacheContextBuilder useCookieSpecRegistry(final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        super.useCookieSpecRegistry(cookieSpecRegistry);
        return this;
    }

    @Override
    public CacheContextBuilder useAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        super.useAuthSchemeRegistry(authSchemeRegistry);
        return this;
    }

    @Override
    public CacheContextBuilder useCookieStore(final CookieStore cookieStore) {
        super.useCookieStore(cookieStore);
        return this;
    }

    @Override
    public CacheContextBuilder useCredentialsProvider(final CredentialsProvider credentialsProvider) {
        super.useCredentialsProvider(credentialsProvider);
        return this;
    }

    @Override
    public CacheContextBuilder useAuthCache(final AuthCache authCache) {
        super.useAuthCache(authCache);
        return this;
    }

    @Override
    public CacheContextBuilder preemptiveAuth(final HttpHost host, final AuthScheme authScheme) {
        super.preemptiveAuth(host, authScheme);
        return this;
    }

    @Override
    public CacheContextBuilder preemptiveBasicAuth(final HttpHost host, final UsernamePasswordCredentials credentials) {
        super.preemptiveBasicAuth(host, credentials);
        return this;
    }

    public CacheContextBuilder setCacheControl(final RequestCacheControl cacheControl) {
        this.cacheControl = cacheControl;
        return this;
    }

    @Override
    protected HttpCacheContext createContext() {
        return HttpCacheContext.create();
    }

    @Override
    public HttpCacheContext build() {
        final HttpCacheContext context =  super.build();
        context.setRequestCacheControl(cacheControl);
        return context;
    }

}
