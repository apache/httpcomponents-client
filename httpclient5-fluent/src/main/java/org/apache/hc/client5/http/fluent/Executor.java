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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.sync.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLInitializationException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;

/**
 * An Executor for fluent requests.
 * <p>
 * A {@link PoolingHttpClientConnectionManager} with maximum 100 connections per route and
 * a total maximum of 200 connections is used internally.
 * </p>
 */
public class Executor {

    final static PoolingHttpClientConnectionManager CONNMGR;
    final static CloseableHttpClient CLIENT;

    static {
        LayeredConnectionSocketFactory ssl = null;
        try {
            ssl = SSLConnectionSocketFactory.getSystemSocketFactory();
        } catch (final SSLInitializationException ex) {
            final SSLContext sslcontext;
            try {
                sslcontext = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
                sslcontext.init(null, null, null);
                ssl = new SSLConnectionSocketFactory(sslcontext);
            } catch (final SecurityException | NoSuchAlgorithmException | KeyManagementException ignore) {
            }
        }

        final Registry<ConnectionSocketFactory> sfr = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", ssl != null ? ssl : SSLConnectionSocketFactory.getSocketFactory())
            .build();

        CONNMGR = new PoolingHttpClientConnectionManager(sfr);
        CONNMGR.setDefaultMaxPerRoute(100);
        CONNMGR.setMaxTotal(200);
        CONNMGR.setValidateAfterInactivity(1000);
        CLIENT = HttpClientBuilder.create()
                .setConnectionManager(CONNMGR)
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

    public Executor auth(final AuthScope authScope, final Credentials creds) {
        if (this.credentialsStore == null) {
            this.credentialsStore = new BasicCredentialsProvider();
        }
        this.credentialsStore.setCredentials(authScope, creds);
        return this;
    }

    public Executor auth(final HttpHost host, final Credentials creds) {
        final AuthScope authScope = host != null ?
                new AuthScope(host.getHostName(), host.getPort()) : AuthScope.ANY;
        return auth(authScope, creds);
    }

    /**
     * @since 4.4
     */
    public Executor auth(final String host, final Credentials creds) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(host);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        return auth(httpHost, creds);
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
        } catch (URISyntaxException ex) {
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
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + proxy);
        }
        return authPreemptiveProxy(httpHost);
    }

    public Executor auth(final Credentials cred) {
        return auth(AuthScope.ANY, cred);
    }

    public Executor auth(final String username, final char[] password) {
        return auth(new UsernamePasswordCredentials(username, password));
    }

    public Executor auth(final String username, final char[] password,
            final String workstation, final String domain) {
        return auth(new NTCredentials(username, password, workstation, domain));
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
     * @see Response#handleResponse(org.apache.hc.client5.http.sync.ResponseHandler)
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

    /**
     * Closes all idle persistent connections used by the internal pool.
     * @since 4.4
     */
    public static void closeIdleConnections() {
        CONNMGR.closeIdle(0, TimeUnit.MICROSECONDS);
    }

}
