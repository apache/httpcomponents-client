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

package org.apache.hc.client5.http.impl.classic;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal implementation of {@link CloseableHttpClient}.
 * <p>
 * Concurrent message exchanges executed by this client will get assigned to
 * separate connections leased from the connection pool.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Internal
class InternalHttpClient extends CloseableHttpClient implements Configurable {

    private static final Logger LOG = LoggerFactory.getLogger(InternalHttpClient.class);

    private final HttpClientConnectionManager connManager;
    private final HttpRequestExecutor requestExecutor;
    private final ExecChainElement execChain;
    private final HttpRoutePlanner routePlanner;
    private final Lookup<CookieSpecFactory> cookieSpecRegistry;
    private final Lookup<AuthSchemeFactory> authSchemeRegistry;
    private final CookieStore cookieStore;
    private final CredentialsProvider credentialsProvider;
    private final Function<HttpContext, HttpClientContext> contextAdaptor;
    private final RequestConfig defaultConfig;
    private final ConcurrentLinkedQueue<Closeable> closeables;

    public InternalHttpClient(
            final HttpClientConnectionManager connManager,
            final HttpRequestExecutor requestExecutor,
            final ExecChainElement execChain,
            final HttpRoutePlanner routePlanner,
            final Lookup<CookieSpecFactory> cookieSpecRegistry,
            final Lookup<AuthSchemeFactory> authSchemeRegistry,
            final CookieStore cookieStore,
            final CredentialsProvider credentialsProvider,
            final Function<HttpContext, HttpClientContext> contextAdaptor,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) {
        super();
        this.connManager = Args.notNull(connManager, "Connection manager");
        this.requestExecutor = Args.notNull(requestExecutor, "Request executor");
        this.execChain = Args.notNull(execChain, "Execution chain");
        this.routePlanner = Args.notNull(routePlanner, "Route planner");
        this.cookieSpecRegistry = cookieSpecRegistry;
        this.authSchemeRegistry = authSchemeRegistry;
        this.cookieStore = cookieStore;
        this.credentialsProvider = credentialsProvider;
        this.contextAdaptor = contextAdaptor;
        this.defaultConfig = defaultConfig;
        this.closeables = closeables != null ? new ConcurrentLinkedQueue<>(closeables) : null;
    }

    private HttpRoute determineRoute(final HttpHost target, final HttpRequest request, final HttpContext context) throws HttpException {
        return this.routePlanner.determineRoute(target, request, context);
    }

    private void setupContext(final HttpClientContext context) {
        if (context.getAuthSchemeRegistry() == null) {
            context.setAuthSchemeRegistry(this.authSchemeRegistry);
        }
        if (context.getCookieSpecRegistry() == null) {
            context.setCookieSpecRegistry(this.cookieSpecRegistry);
        }
        if (context.getCookieStore() == null) {
            context.setCookieStore(this.cookieStore);
        }
        if (context.getCredentialsProvider() == null) {
            context.setCredentialsProvider(this.credentialsProvider);
        }
        if (context.getRequestConfig() == null) {
            context.setRequestConfig(this.defaultConfig);
        }
    }

    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        Args.notNull(request, "HTTP request");
        try {
            final HttpClientContext localcontext = contextAdaptor.apply(context);
            RequestConfig config = null;
            if (request instanceof Configurable) {
                config = ((Configurable) request).getConfig();
            }
            if (config != null) {
                localcontext.setRequestConfig(config);
            }
            setupContext(localcontext);

            final HttpHost resolvedTarget = target != null ? target : RoutingSupport.determineHost(request);
            if (resolvedTarget != null) {
                if (request.getScheme() == null) {
                    request.setScheme(resolvedTarget.getSchemeName());
                }
                if (request.getAuthority() == null) {
                    request.setAuthority(new URIAuthority(resolvedTarget));
                }
            }
            final HttpRoute route = determineRoute(
                    resolvedTarget,
                    request,
                    localcontext);
            final String exchangeId = ExecSupport.getNextExchangeId();
            localcontext.setExchangeId(exchangeId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} preparing request execution", exchangeId);
            }

            final ExecRuntime execRuntime = new InternalExecRuntime(LOG, connManager, requestExecutor,
                    request instanceof CancellableDependency ? (CancellableDependency) request : null);
            final ExecChain.Scope scope = new ExecChain.Scope(exchangeId, route, request, execRuntime, localcontext);
            final ClassicHttpResponse response = this.execChain.execute(ClassicRequestBuilder.copy(request).build(), scope);
            return CloseableHttpResponse.adapt(response);
        } catch (final HttpException httpException) {
            throw new ClientProtocolException(httpException.getMessage(), httpException);
        }
    }

    @Override
    public RequestConfig getConfig() {
        return this.defaultConfig;
    }

    @Override
    public void close() {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (this.closeables != null) {
            Closeable closeable;
            while ((closeable = this.closeables.poll()) != null) {
                try {
                    if (closeable instanceof ModalCloseable) {
                        ((ModalCloseable) closeable).close(closeMode);
                    } else {
                        closeable.close();
                    }
                } catch (final IOException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

}
