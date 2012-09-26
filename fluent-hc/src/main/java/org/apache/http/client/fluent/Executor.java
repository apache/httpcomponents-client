/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.ChallengeState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.SSLInitializationException;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;

/**
 * An Executor for fluent requests
 * <p/>
 * A {@link PoolingClientConnectionManager} with maximum 100 connections per route and
 * a total maximum of 200 connections is used internally.
 */
public class Executor {

    final static PoolingClientConnectionManager CONNMGR;
    final static DefaultHttpClient CLIENT;

    static {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        SchemeSocketFactory plain = PlainSocketFactory.getSocketFactory();
        schemeRegistry.register(new Scheme("http", 80, plain));
        SchemeSocketFactory ssl = null;
        try {
            ssl = SSLSocketFactory.getSystemSocketFactory();
        } catch (SSLInitializationException ex) {
            SSLContext sslcontext;
            try {
                sslcontext = SSLContext.getInstance(SSLSocketFactory.TLS);
                sslcontext.init(null, null, null);
                ssl = new SSLSocketFactory(sslcontext);
            } catch (SecurityException ignore) {
            } catch (KeyManagementException ignore) {
            } catch (NoSuchAlgorithmException ignore) {
            }
        }
        if (ssl != null) {
            schemeRegistry.register(new Scheme("https", 443, ssl));
        }
        CONNMGR = new PoolingClientConnectionManager(schemeRegistry);
        CONNMGR.setDefaultMaxPerRoute(100);
        CONNMGR.setMaxTotal(200);
        CLIENT = new DefaultHttpClient(CONNMGR);
    }

    public static Executor newInstance() {
        return new Executor(CLIENT);
    }

    public static Executor newInstance(final HttpClient httpclient) {
        return new Executor(httpclient != null ? httpclient : CLIENT);
    }

    private final HttpClient httpclient;
    private final BasicHttpContext localContext;
    private final AuthCache authCache;

    private CredentialsProvider credentialsProvider;
    private CookieStore cookieStore;

    Executor(final HttpClient httpclient) {
        super();
        this.httpclient = httpclient;
        this.localContext = new BasicHttpContext();
        this.authCache = new BasicAuthCache();
    }

    public Executor auth(final AuthScope authScope, final Credentials creds) {
        if (this.credentialsProvider == null) {
            this.credentialsProvider = new BasicCredentialsProvider();
        }
        this.credentialsProvider.setCredentials(authScope, creds);
        return this;
    }

    public Executor auth(final HttpHost host, final Credentials creds) {
        AuthScope authScope = host != null ? new AuthScope(host) : AuthScope.ANY;
        return auth(authScope, creds);
    }

    public Executor authPreemptive(final HttpHost host) {
        this.authCache.put(host, new BasicScheme(ChallengeState.TARGET));
        return this;
    }

    public Executor authPreemptiveProxy(final HttpHost host) {
        this.authCache.put(host, new BasicScheme(ChallengeState.PROXY));
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
        if (this.credentialsProvider != null) {
            this.credentialsProvider.clear();
        }
        return this;
    }

    public Executor cookieStore(final CookieStore cookieStore) {
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
     * @see Response#handleResponse(org.apache.http.client.ResponseHandler)
     * @see Response#discardContent()
     */
    public Response execute(
            final Request request) throws ClientProtocolException, IOException {
        this.localContext.setAttribute(ClientContext.CREDS_PROVIDER, this.credentialsProvider);
        this.localContext.setAttribute(ClientContext.AUTH_CACHE, this.authCache);
        this.localContext.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);
        HttpRequestBase httprequest = request.getHttpRequest();
        httprequest.reset();
        return new Response(this.httpclient.execute(httprequest, this.localContext));
    }

    public static void registerScheme(final Scheme scheme) {
        CONNMGR.getSchemeRegistry().register(scheme);
    }

    public static void unregisterScheme(final String name) {
        CONNMGR.getSchemeRegistry().unregister(name);
    }

}
