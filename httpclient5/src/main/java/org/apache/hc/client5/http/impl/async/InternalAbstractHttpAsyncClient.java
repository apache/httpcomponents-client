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
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.RequestCopier;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;

abstract class InternalAbstractHttpAsyncClient extends AbstractHttpAsyncClientBase {

    private final AsyncExecChainElement execChain;
    private final Lookup<CookieSpecFactory> cookieSpecRegistry;
    private final Lookup<AuthSchemeFactory> authSchemeRegistry;
    private final CookieStore cookieStore;
    private final CredentialsProvider credentialsProvider;
    private final RequestConfig defaultConfig;
    private final ConcurrentLinkedQueue<Closeable> closeables;

    InternalAbstractHttpAsyncClient(
            final DefaultConnectingIOReactor ioReactor,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final ThreadFactory threadFactory,
            final AsyncExecChainElement execChain,
            final Lookup<CookieSpecFactory> cookieSpecRegistry,
            final Lookup<AuthSchemeFactory> authSchemeRegistry,
            final CookieStore cookieStore,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) {
        super(ioReactor, pushConsumerRegistry, threadFactory);
        this.execChain = execChain;
        this.cookieSpecRegistry = cookieSpecRegistry;
        this.authSchemeRegistry = authSchemeRegistry;
        this.cookieStore = cookieStore;
        this.credentialsProvider = credentialsProvider;
        this.defaultConfig = defaultConfig;
        this.closeables = closeables != null ? new ConcurrentLinkedQueue<>(closeables) : null;
    }

    @Override
    void internalClose(final CloseMode closeMode) {
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
                    this.log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private void setupContext(final HttpClientContext context) {
        if (context.getAttribute(HttpClientContext.AUTHSCHEME_REGISTRY) == null) {
            context.setAttribute(HttpClientContext.AUTHSCHEME_REGISTRY, authSchemeRegistry);
        }
        if (context.getAttribute(HttpClientContext.COOKIESPEC_REGISTRY) == null) {
            context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, cookieSpecRegistry);
        }
        if (context.getAttribute(HttpClientContext.COOKIE_STORE) == null) {
            context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        }
        if (context.getAttribute(HttpClientContext.CREDS_PROVIDER) == null) {
            context.setAttribute(HttpClientContext.CREDS_PROVIDER, credentialsProvider);
        }
        if (context.getAttribute(HttpClientContext.REQUEST_CONFIG) == null) {
            context.setAttribute(HttpClientContext.REQUEST_CONFIG, defaultConfig);
        }
    }

    abstract AsyncExecRuntime createAsyncExecRuntime(HandlerFactory<AsyncPushConsumer> pushHandlerFactory);

    abstract HttpRoute determineRoute(HttpHost httpHost, HttpClientContext clientContext) throws HttpException;

    @Override
    protected <T> Future<T> doExecute(
            final HttpHost httpHost,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpContext context,
            final FutureCallback<T> callback) {
        final ComplexFuture<T> future = new ComplexFuture<>(callback);
        try {
            if (!isRunning()) {
                throw new CancellationException("Request execution cancelled");
            }
            final HttpClientContext clientContext = context != null ? HttpClientContext.adapt(context) : HttpClientContext.create();
            requestProducer.sendRequest(new RequestChannel() {

                @Override
                public void sendRequest(
                        final HttpRequest request,
                        final EntityDetails entityDetails,
                        final HttpContext context) throws HttpException, IOException {

                    RequestConfig requestConfig = null;
                    if (request instanceof Configurable) {
                        requestConfig = ((Configurable) request).getConfig();
                    }
                    if (requestConfig != null) {
                        clientContext.setRequestConfig(requestConfig);
                    }
                    final HttpRoute route = determineRoute(
                            httpHost != null ? httpHost : RoutingSupport.determineHost(request),
                            clientContext);
                    final String exchangeId = ExecSupport.getNextExchangeId();
                    if (log.isDebugEnabled()) {
                        log.debug(exchangeId + ": preparing request execution");
                    }
                    final AsyncExecRuntime execRuntime = createAsyncExecRuntime(pushHandlerFactory);

                    setupContext(clientContext);

                    final AsyncExecChain.Scope scope = new AsyncExecChain.Scope(exchangeId, route, request, future,
                            clientContext, execRuntime);
                    final AtomicBoolean outputTerminated = new AtomicBoolean(false);
                    execChain.execute(
                            RequestCopier.INSTANCE.copy(request),
                            entityDetails != null ? new AsyncEntityProducer() {

                                @Override
                                public void releaseResources() {
                                    requestProducer.releaseResources();
                                }

                                @Override
                                public void failed(final Exception cause) {
                                    requestProducer.failed(cause);
                                }

                                @Override
                                public boolean isRepeatable() {
                                    return requestProducer.isRepeatable();
                                }

                                @Override
                                public long getContentLength() {
                                    return entityDetails.getContentLength();
                                }

                                @Override
                                public String getContentType() {
                                    return entityDetails.getContentType();
                                }

                                @Override
                                public String getContentEncoding() {
                                    return entityDetails.getContentEncoding();
                                }

                                @Override
                                public boolean isChunked() {
                                    return entityDetails.isChunked();
                                }

                                @Override
                                public Set<String> getTrailerNames() {
                                    return entityDetails.getTrailerNames();
                                }

                                @Override
                                public int available() {
                                    return requestProducer.available();
                                }

                                @Override
                                public void produce(final DataStreamChannel channel) throws IOException {
                                    if (outputTerminated.get()) {
                                        channel.endStream();
                                        return;
                                    }
                                    requestProducer.produce(channel);
                                }

                            } : null,
                            scope,
                            new AsyncExecCallback() {

                                @Override
                                public AsyncDataConsumer handleResponse(
                                        final HttpResponse response,
                                        final EntityDetails entityDetails) throws HttpException, IOException {
                                    if (response.getCode() >= HttpStatus.SC_CLIENT_ERROR) {
                                        outputTerminated.set(true);
                                        requestProducer.releaseResources();
                                    }
                                    responseConsumer.consumeResponse(response, entityDetails, context,
                                            new FutureCallback<T>() {

                                                @Override
                                                public void completed(final T result) {
                                                    future.completed(result);
                                                }

                                                @Override
                                                public void failed(final Exception ex) {
                                                    future.failed(ex);
                                                }

                                                @Override
                                                public void cancelled() {
                                                    future.cancel();
                                                }

                                            });
                                    return responseConsumer;
                                }

                                @Override
                                public void handleInformationResponse(
                                        final HttpResponse response) throws HttpException, IOException {
                                    responseConsumer.informationResponse(response, context);
                                }

                                @Override
                                public void completed() {
                                    if (log.isDebugEnabled()) {
                                        log.debug(exchangeId + ": message exchange successfully completed");
                                    }
                                    try {
                                        execRuntime.releaseEndpoint();
                                    } finally {
                                        responseConsumer.releaseResources();
                                        requestProducer.releaseResources();
                                    }
                                }

                                @Override
                                public void failed(final Exception cause) {
                                    if (log.isDebugEnabled()) {
                                        log.debug(exchangeId + ": request failed: " + cause.getMessage());
                                    }
                                    try {
                                        execRuntime.discardEndpoint();
                                        responseConsumer.failed(cause);
                                    } finally {
                                        try {
                                            future.failed(cause);
                                        } finally {
                                            responseConsumer.releaseResources();
                                            requestProducer.releaseResources();
                                        }
                                    }
                                }

                            });
                }

            }, context);
        } catch (final HttpException | IOException | IllegalStateException ex) {
            future.failed(ex);
        }
        return future;
    }

}
