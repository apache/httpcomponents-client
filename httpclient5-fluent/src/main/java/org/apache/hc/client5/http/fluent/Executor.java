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
package org.apache.hc.client5.http.fluent;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;

/**
 * Executor for {@link Request}s.
 * <p>
 * A connection pool with maximum 100 connections per route and
 * a total maximum of 200 connections is used internally.
 *
 * @since 4.2
 */
public class Executor {

    final static CloseableHttpClient CLIENT;

    static {
        CLIENT = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .useSystemProperties()
                        .setMaxConnPerRoute(100)
                        .setMaxConnTotal(200)
                        .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                        .build())
                .useSystemProperties()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1))
                .build();
    }

    public static Executor newInstance() {
        return new Executor(CLIENT);
    }

    public static Executor newInstance(final CloseableHttpClient httpclient) {
        return new Executor(httpclient != null ? httpclient : CLIENT);
    }

    private final CloseableHttpClient httpclient;
    private final AuthCache authCache;
    private volatile CredentialsStore credentialsStore;
    private volatile CookieStore cookieStore;

    Executor(final CloseableHttpClient httpclient) {
        super();
        this.httpclient = httpclient;
        this.authCache = new BasicAuthCache();
    }

    /**
     * @since 4.5
     */
    public Executor use(final CredentialsStore credentialsStore) {
        this.credentialsStore = credentialsStore;
        return this;
    }

    public Executor auth(final AuthScope authScope, final Credentials credentials) {
        if (this.credentialsStore == null) {
            this.credentialsStore = new BasicCredentialsProvider();
        }
        this.credentialsStore.setCredentials(authScope, credentials);
        return this;
    }

    public Executor auth(final HttpHost host, final Credentials credentials) {
        return auth(new AuthScope(host), credentials);
    }

    /**
     * @since 4.4
     */
    public Executor auth(final String host, final Credentials credentials) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(host);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        return auth(httpHost, credentials);
    }

    public Executor authPreemptive(final HttpHost host) {
        if (this.credentialsStore != null) {
            final Credentials credentials = this.credentialsStore.getCredentials(new AuthScope(host), null);
            if (credentials == null) {
                final BasicScheme basicScheme = new BasicScheme();
                basicScheme.initPreemptive(credentials);
                this.authCache.put(host, basicScheme);
            }
        }
        return this;
    }

    /**
     * @since 4.4
     */
    public Executor authPreemptive(final String host) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(host);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        return authPreemptive(httpHost);
    }

    public Executor authPreemptiveProxy(final HttpHost proxy) {
        if (this.credentialsStore != null) {
            final Credentials credentials = this.credentialsStore.getCredentials(new AuthScope(proxy), null);
            if (credentials == null) {
                final BasicScheme basicScheme = new BasicScheme();
                basicScheme.initPreemptive(credentials);
                this.authCache.put(proxy, basicScheme);
            }
        }
        return this;
    }

    /**
     * @since 4.4
     */
    public Executor authPreemptiveProxy(final String proxy) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(proxy);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + proxy);
        }
        return authPreemptiveProxy(httpHost);
    }

    public Executor auth(final HttpHost host,
            final String username, final char[] password) {
        return auth(host, new UsernamePasswordCredentials(username, password));
    }

    public Executor auth(final HttpHost host,
            final String username, final char[] password,
            final String workstation, final String domain) {
        return auth(host, new NTCredentials(username, password, workstation, domain));
    }

    public Executor clearAuth() {
        if (this.credentialsStore != null) {
            this.credentialsStore.clear();
        }
        return this;
    }

    /**
     * @since 4.5
     */
    public Executor use(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    public Executor clearCookies() {
        if (this.cookieStore != null) {
            this.cookieStore.clear();
        }
        return this;
    }

    /**
     * Executes the request. Please Note that response content must be processed
     * or discarded using {@link Response#discardContent()}, otherwise the
     * connection used for the request might not be released to the pool.
     *
     * @see Response#handleResponse(org.apache.hc.core5.http.io.HttpClientResponseHandler)
     * @see Response#discardContent()
     */
    public Response execute(
            final Request request) throws IOException {
        final HttpClientContext localContext = HttpClientContext.create();
        if (this.credentialsStore != null) {
            localContext.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credentialsStore);
        }
        if (this.authCache != null) {
            localContext.setAttribute(HttpClientContext.AUTH_CACHE, this.authCache);
        }
        if (this.cookieStore != null) {
            localContext.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        }
        return new Response(request.internalExecute(this.httpclient, localContext));
    }

}
