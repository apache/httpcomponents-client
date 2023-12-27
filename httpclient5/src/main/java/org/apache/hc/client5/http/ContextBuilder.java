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

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.protocol.BasicHttpContext;

/**
 * {@link HttpClientContext} builder.
 *
 * @since 5.2
 */
public class ContextBuilder extends AbstractClientContextBuilder<HttpClientContext> {

    protected ContextBuilder(final SchemePortResolver schemePortResolver) {
        super(schemePortResolver);
    }

    public static ContextBuilder create(final SchemePortResolver schemePortResolver) {
        return new ContextBuilder(schemePortResolver);
    }

    public static ContextBuilder create() {
        return new ContextBuilder(DefaultSchemePortResolver.INSTANCE);
    }

    @Override
    public ContextBuilder useCookieSpecRegistry(final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        super.useCookieSpecRegistry(cookieSpecRegistry);
        return this;
    }

    @Override
    public ContextBuilder useAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        super.useAuthSchemeRegistry(authSchemeRegistry);
        return this;
    }

    @Override
    public ContextBuilder useCookieStore(final CookieStore cookieStore) {
        super.useCookieStore(cookieStore);
        return this;
    }

    @Override
    public ContextBuilder useCredentialsProvider(final CredentialsProvider credentialsProvider) {
        super.useCredentialsProvider(credentialsProvider);
        return this;
    }

    @Override
    public ContextBuilder useAuthCache(final AuthCache authCache) {
        super.useAuthCache(authCache);
        return this;
    }

    @Override
    public ContextBuilder preemptiveAuth(final HttpHost host, final AuthScheme authScheme) {
        super.preemptiveAuth(host, authScheme);
        return this;
    }

    @Override
    public ContextBuilder preemptiveBasicAuth(final HttpHost host, final UsernamePasswordCredentials credentials) {
        super.preemptiveBasicAuth(host, credentials);
        return this;
    }

    @Override
    protected HttpClientContext createContext() {
        return new HttpClientContext(new BasicHttpContext());
    }

}
