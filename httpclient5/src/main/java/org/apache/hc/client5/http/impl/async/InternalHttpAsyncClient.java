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
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;

/**
 * Internal implementation of {@link CloseableHttpAsyncClient} that can negotiate
 * the most optimal HTTP protocol version during during the {@code TLS} handshake
 * with {@code ALPN} extension if supported by the Java runtime.
 * <p>
 * Concurrent message exchanges executed by this client will get assigned to
 * separate connections leased from the connection pool.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Internal
public final class InternalHttpAsyncClient extends InternalAbstractHttpAsyncClient {

    private final AsyncClientConnectionManager manager;
    private final HttpRoutePlanner routePlanner;
    private final HttpVersionPolicy versionPolicy;

    InternalHttpAsyncClient(
            final DefaultConnectingIOReactor ioReactor,
            final AsyncExecChainElement execChain,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final ThreadFactory threadFactory,
            final AsyncClientConnectionManager manager,
            final HttpRoutePlanner routePlanner,
            final HttpVersionPolicy versionPolicy,
            final Lookup<CookieSpecFactory> cookieSpecRegistry,
            final Lookup<AuthSchemeFactory> authSchemeRegistry,
            final CookieStore cookieStore,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) {
        super(ioReactor, pushConsumerRegistry, threadFactory, execChain,
                cookieSpecRegistry, authSchemeRegistry, cookieStore, credentialsProvider, defaultConfig, closeables);
        this.manager = manager;
        this.routePlanner = routePlanner;
        this.versionPolicy = versionPolicy;
    }

    @Override
    AsyncExecRuntime createAsyncExecRuntime(final HandlerFactory<AsyncPushConsumer> pushHandlerFactory) {
        return new InternalHttpAsyncExecRuntime(log, manager, getConnectionInitiator(), pushHandlerFactory, versionPolicy);
    }

    @Override
    HttpRoute determineRoute(final HttpHost httpHost, final HttpClientContext clientContext) throws HttpException {
        final HttpRoute route = routePlanner.determineRoute(httpHost, clientContext);
        final ProtocolVersion protocolVersion = clientContext.getProtocolVersion();
        if (route.isTunnelled() && protocolVersion.greaterEquals(HttpVersion.HTTP_2_0)) {
            throw new HttpException("HTTP/2 tunneling not supported");
        }
        return route;
    }

}
