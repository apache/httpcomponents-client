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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
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
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.protocol.BasicHttpContext;

public class FluentExecutor {

    final static PoolingClientConnectionManager CONNMGR = new PoolingClientConnectionManager(
            SchemeRegistryFactory.createSystemDefault());
    final static DefaultHttpClient CLIENT = new DefaultHttpClient(CONNMGR);

    public static FluentExecutor newInstance() {
        return new FluentExecutor(CLIENT);
    }

    private final HttpClient httpclient;
    private final BasicHttpContext localContext;
    private final AuthCache authCache;

    private CredentialsProvider credentialsProvider;
    private CookieStore cookieStore;

    FluentExecutor(final HttpClient httpclient) {
        super();
        this.httpclient = httpclient;
        this.localContext = new BasicHttpContext();
        this.authCache = new BasicAuthCache();
    }

    public FluentExecutor auth(final AuthScope authScope, final Credentials creds) {
        if (this.credentialsProvider == null) {
            this.credentialsProvider = new BasicCredentialsProvider();
        }
        this.credentialsProvider.setCredentials(authScope, creds);
        return this;
    }

    public FluentExecutor auth(final HttpHost host, final Credentials creds) {
        AuthScope authScope = host != null ? new AuthScope(host) : AuthScope.ANY;
        return auth(authScope, creds);
    }

    public FluentExecutor authPreemptive(final HttpHost host, final Credentials creds) {
        auth(host, creds);
        this.authCache.put(host, new BasicScheme());
        return this;
    }

    public FluentExecutor auth(final Credentials cred) {
        return auth(AuthScope.ANY, cred);
    }

    public FluentExecutor auth(final String username, final String password) {
        return auth(new UsernamePasswordCredentials(username, password));
    }

    public FluentExecutor auth(final String username, final String password,
            final String workstation, final String domain) {
        return auth(new NTCredentials(username, password, workstation, domain));
    }

    public FluentExecutor auth(final HttpHost host,
            final String username, final String password) {
        return auth(host, new UsernamePasswordCredentials(username, password));
    }

    public FluentExecutor auth(final HttpHost host,
            final String username, final String password,
            final String workstation, final String domain) {
        return auth(host, new NTCredentials(username, password, workstation, domain));
    }

    public FluentExecutor authPreemptive(final HttpHost host,
            final String username, final String password) {
        auth(host, username, password);
        this.authCache.put(host, new BasicScheme());
        return this;
    }

    public FluentExecutor clearAuth() {
        if (this.credentialsProvider != null) {
            this.credentialsProvider.clear();
        }
        return this;
    }

    public FluentExecutor cookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    public FluentExecutor clearCookies() {
        if (this.cookieStore != null) {
            this.cookieStore.clear();
        }
        return this;
    }

    public FluentResponse exec(
            final FluentRequest req) throws ClientProtocolException, IOException {
        this.localContext.setAttribute(ClientContext.CREDS_PROVIDER, this.credentialsProvider);
        this.localContext.setAttribute(ClientContext.AUTH_CACHE, this.authCache);
        this.localContext.setAttribute(ClientContext.COOKIE_STORE, this.cookieStore);
        HttpRequestBase httprequest = req.getHttpRequest();
        httprequest.reset();
        return new FluentResponse(this.httpclient.execute(httprequest, this.localContext));
    }

    public static void setMaxTotal(int max) {
        CONNMGR.setMaxTotal(max);
    }

    public static void setDefaultMaxPerRoute(int max) {
        CONNMGR.setDefaultMaxPerRoute(max);
    }

    public static void setMaxPerRoute(final HttpRoute route, int max) {
        CONNMGR.setMaxPerRoute(route, max);
    }

    public static void registerScheme(final Scheme scheme) {
        CONNMGR.getSchemeRegistry().register(scheme);
    }

    public static void unregisterScheme(final String name) {
        CONNMGR.getSchemeRegistry().unregister(name);
    }

}
