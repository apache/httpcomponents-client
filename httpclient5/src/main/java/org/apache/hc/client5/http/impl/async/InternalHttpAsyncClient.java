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
package org.apache.hc.client5.http.impl.async;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthSchemeProvider;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieSpecProvider;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;

class InternalHttpAsyncClient extends InternalAbstractHttpAsyncClient {

    private final AsyncClientConnectionManager connmgr;
    private final HttpRoutePlanner routePlanner;
    private final HttpVersionPolicy versionPolicy;

    InternalHttpAsyncClient(
            final DefaultConnectingIOReactor ioReactor,
            final AsyncExecChainElement execChain,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final ThreadFactory threadFactory,
            final AsyncClientConnectionManager connmgr,
            final HttpRoutePlanner routePlanner,
            final HttpVersionPolicy versionPolicy,
            final Lookup<CookieSpecProvider> cookieSpecRegistry,
            final Lookup<AuthSchemeProvider> authSchemeRegistry,
            final CookieStore cookieStore,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) {
        super(ioReactor, pushConsumerRegistry, threadFactory, execChain,
                cookieSpecRegistry, authSchemeRegistry, cookieStore, credentialsProvider, defaultConfig, closeables);
        this.connmgr = connmgr;
        this.routePlanner = routePlanner;
        this.versionPolicy = versionPolicy;
    }

    @Override
    AsyncExecRuntime crerateAsyncExecRuntime() {
        return new InternalHttpAsyncExecRuntime(log, connmgr, getConnectionInitiator(), versionPolicy);
    }

    @Override
    HttpRoute determineRoute(final HttpRequest request, final HttpClientContext clientContext) throws HttpException {
        final HttpRoute route = routePlanner.determineRoute(RoutingSupport.determineHost(request), clientContext);
        final ProtocolVersion protocolVersion = clientContext.getProtocolVersion();
        if (route.isTunnelled() && protocolVersion.greaterEquals(HttpVersion.HTTP_2_0)) {
            throw new HttpException("HTTP/2 tunneling not supported");
        }
        return route;
    }

}
