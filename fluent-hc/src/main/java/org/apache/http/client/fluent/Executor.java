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
package org.apache.http.client.fluent;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLInitializationException;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;

/**
 * An Executor for fluent requests
 * <p/>
 * A {@link PoolingHttpClientConnectionManager} with maximum 100 connections per route and
 * a total maximum of 200 connections is used internally.
 */
public class Executor {

    final static PoolingHttpClientConnectionManager CONNMGR;
    final static HttpClient CLIENT;

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
            } catch (final SecurityException ignore) {
            } catch (final KeyManagementException ignore) {
            } catch (final NoSuchAlgorithmException ignore) {
            }
        }

        final Registry<ConnectionSocketFactory> sfr = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", ssl != null ? ssl : SSLConnectionSocketFactory.getSocketFactory())
            .build();

        CONNMGR = new PoolingHttpClientConnectionManager(sfr);
        CONNMGR.setDefaultMaxPerRoute(100);
        CONNMGR.setMaxTotal(200);
        CLIENT = HttpClientBuilder.create().setConnectionManager(CONNMGR).build();
    }

    public static Executor newInstance() {
        return new Executor(CLIENT);
    }

    public static Executor newInstance(final HttpClient httpclient) {
        return new Executor(httpclient != null ? httpclient : CLIENT);
    }

    private final HttpClient httpclient;
    private final AuthCache authCache;
    private final CredentialsProvider credentialsProvider;

    private volatile CookieStore cookieStore;

    Executor(final HttpClient httpclient) {
        super();
        this.httpclient = httpclient;
        this.credentialsProvider = new BasicCredentialsProvider();
        this.authCache = new BasicAuthCache();
    }

    public Executor auth(final AuthScope authScope, final Credentials creds) {
        this.credentialsProvider.setCredentials(authScope, creds);
        return this;
    }

    public Executor auth(final HttpHost host, final Credentials creds) {
        final AuthScope authScope = host != null ? new AuthScope(host) : AuthScope.ANY;
        return auth(authScope, creds);
    }

    public Executor authPreemptive(final HttpHost host) {
        final BasicScheme basicScheme = new BasicScheme();
        try {
            basicScheme.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "BASIC "));
        } catch (final MalformedChallengeException ignore) {
        }
        this.authCache.put(host, basicScheme);
        return this;
    }

    public Executor authPreemptiveProxy(final HttpHost host) {
        final BasicScheme basicScheme = new BasicScheme();
        try {
            basicScheme.processChallenge(new BasicHeader(AUTH.PROXY_AUTH, "BASIC "));
        } catch (final MalformedChallengeException ignore) {
        }
        this.authCache.put(host, basicScheme);
        return this;
    }

    public Executor auth(final Credentials cred) {
        return auth(AuthScope.ANY, cred);
    }

    public Executor auth(final String username, final String password) {
        return auth(new UsernamePasswordCredentials(username, password));
    }

    public Executor auth(final String username, final String password,
            final String workstation, final String domain) {
        return auth(new NTCredentials(username, password, workstation, domain));
    }

    public Executor auth(final HttpHost host,
            final String username, final String password) {
        return auth(host, new UsernamePasswordCredentials(username, password));
    }

    public Executor auth(final HttpHost host,
            final String username, final String password,
            final String workstation, final String domain) {
        return auth(host, new NTCredentials(username, password, workstation, domain));
    }

    public Executor clearAuth() {
        this.credentialsProvider.clear();
        return this;
    }

    public Executor cookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    public Executor clearCookies() {
        this.cookieStore.clear();
        return this;
    }

    /**
     * Executes the request. Please Note that response content must be processed
     * or discarded using {@link Response#discardContent()}, otherwise the
     * connection used for the request might not be released to the pool.
     *
     * @see Response#handleResponse(org.apache.http.client.ResponseHandler)
     * @see Response#discardContent()
     */
    public Response execute(
            final Request request) throws ClientProtocolException, IOException {
        final HttpClientContext localContext = HttpClientContext.create();
        localContext.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credentialsProvider);
        localContext.setAttribute(HttpClientContext.AUTH_CACHE, this.authCache);
        localContext.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        final HttpRequestBase httprequest = request.prepareRequest();
        httprequest.reset();
        return new Response(this.httpclient.execute(httprequest, localContext));
    }

    /**
     * @deprecated (4.3) do not use.
     */
    @Deprecated
    public static void registerScheme(final org.apache.http.conn.scheme.Scheme scheme) {
    }

    /**
     * @deprecated (4.3) do not use.
     */
    @Deprecated
    public static void unregisterScheme(final String name) {
    }

}
