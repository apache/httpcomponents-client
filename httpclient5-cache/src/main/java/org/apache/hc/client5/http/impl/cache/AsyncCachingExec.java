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
package org.apache.hc.client5.http.impl.cache;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request executor in the request execution chain that is responsible for
 * transparent client-side caching.
 * <p>
 * The current implementation is conditionally
 * compliant with HTTP/1.1 (meaning all the MUST and MUST NOTs are obeyed),
 * although quite a lot, though not all, of the SHOULDs and SHOULD NOTs
 * are obeyed too.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE) // So long as the responseCache implementation is threadsafe
class AsyncCachingExec extends CachingExecBase implements AsyncExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncCachingExec.class);
    private final HttpAsyncCache responseCache;
    private final DefaultAsyncCacheRevalidator cacheRevalidator;
    private final ConditionalRequestBuilder<HttpRequest> conditionalRequestBuilder;

    AsyncCachingExec(final HttpAsyncCache cache, final DefaultAsyncCacheRevalidator cacheRevalidator, final CacheConfig config) {
        super(config);
        this.responseCache = Args.notNull(cache, "Response cache");
        this.cacheRevalidator = cacheRevalidator;
        this.conditionalRequestBuilder = new ConditionalRequestBuilder<>(request ->
                BasicRequestBuilder.copy(request).build());
    }

    AsyncCachingExec(
            final HttpAsyncCache responseCache,
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final ResponseProtocolCompliance responseCompliance,
            final RequestProtocolCompliance requestCompliance,
            final DefaultAsyncCacheRevalidator cacheRevalidator,
            final ConditionalRequestBuilder<HttpRequest> conditionalRequestBuilder,
            final CacheConfig config) {
        super(validityPolicy, responseCachingPolicy, responseGenerator, cacheableRequestPolicy,
                suitabilityChecker, responseCompliance, requestCompliance, config);
        this.responseCache = responseCache;
        this.cacheRevalidator = cacheRevalidator;
        this.conditionalRequestBuilder = conditionalRequestBuilder;
    }

    AsyncCachingExec(
            final HttpAsyncCache cache,
            final ScheduledExecutorService executorService,
            final SchedulingStrategy schedulingStrategy,
            final CacheConfig config) {
        this(cache,
                executorService != null ? new DefaultAsyncCacheRevalidator(executorService, schedulingStrategy) : null,
                config);
    }

    AsyncCachingExec(
            final ResourceFactory resourceFactory,
            final HttpAsyncCacheStorage storage,
            final ScheduledExecutorService executorService,
            final SchedulingStrategy schedulingStrategy,
            final CacheConfig config) {
        this(new BasicHttpAsyncCache(resourceFactory, storage), executorService, schedulingStrategy, config);
    }

    private void triggerResponse(
            final SimpleHttpResponse cacheResponse,
            final AsyncExecChain.Scope scope,
            final AsyncExecCallback asyncExecCallback) {
        scope.clientContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, cacheResponse);
        scope.execRuntime.releaseEndpoint();

        final SimpleBody body = cacheResponse.getBody();
        final byte[] content = body != null ? body.getBodyBytes() : null;
        final ContentType contentType = body != null ? body.getContentType() : null;
        try {
            final AsyncDataConsumer dataConsumer = asyncExecCallback.handleResponse(
                    cacheResponse,
                    content != null ? new BasicEntityDetails(content.length, contentType) : null);
            if (dataConsumer != null) {
                if (content != null) {
                    dataConsumer.consume(ByteBuffer.wrap(content));
                }
                dataConsumer.streamEnd(null);
            }
            asyncExecCallback.completed();
        } catch (final HttpException | IOException ex) {
            asyncExecCallback.failed(ex);
        }
    }

    static class AsyncExecCallbackWrapper implements AsyncExecCallback {

        private final AsyncExecCallback asyncExecCallback;
        private final Runnable command;

        AsyncExecCallbackWrapper(final AsyncExecCallback asyncExecCallback, final Runnable command) {
            this.asyncExecCallback = asyncExecCallback;
            this.command = command;
        }

        @Override
        public AsyncDataConsumer handleResponse(
                final HttpResponse response,
                final EntityDetails entityDetails) throws HttpException, IOException {
            return null;
        }

        @Override
        public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
        }

        @Override
        public void completed() {
            command.run();
        }

        @Override
        public void failed(final Exception cause) {
            asyncExecCallback.failed(cause);
        }

    }

    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final HttpRoute route = scope.route;
        final CancellableDependency operation = scope.cancellableDependency;
        final HttpClientContext context = scope.clientContext;
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        final URIAuthority authority = request.getAuthority();
        final String scheme = request.getScheme();
        final HttpHost target = authority != null ? new HttpHost(scheme, authority) : route.getTargetHost();
        final String via = generateViaHeader(request);

        // default response context
        setResponseStatus(context, CacheResponseStatus.CACHE_MISS);

        if (clientRequestsOurOptions(request)) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            triggerResponse(SimpleHttpResponse.create(HttpStatus.SC_NOT_IMPLEMENTED), scope, asyncExecCallback);
            return;
        }

        requestCompliance.makeRequestCompliant(request);
        request.addHeader(HttpHeaders.VIA,via);

        final RequestCacheControl requestCacheControl = CacheControlHeaderParser.INSTANCE.parse(request);

        if (!cacheableRequestPolicy.isServableFromCache(requestCacheControl, request)) {
            LOG.debug("Request is not servable from cache");
            operation.setDependency(responseCache.flushCacheEntriesInvalidatedByRequest(target, request, new FutureCallback<Boolean>() {

                @Override
                public void completed(final Boolean result) {
                    callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
                }

                @Override
                public void failed(final Exception cause) {
                    asyncExecCallback.failed(cause);
                }

                @Override
                public void cancelled() {
                    asyncExecCallback.failed(new InterruptedIOException());
                }

            }));
        } else {
            operation.setDependency(responseCache.match(target, request, new FutureCallback<CacheMatch>() {

                @Override
                public void completed(final CacheMatch result) {
                    final CacheHit hit = result != null ? result.hit : null;
                    final CacheHit root = result != null ? result.root : null;
                    final SimpleHttpResponse fatalErrorResponse = getFatallyNonCompliantResponse(request, context, hit != null);
                    if (fatalErrorResponse != null) {
                        triggerResponse(fatalErrorResponse, scope, asyncExecCallback);
                        return;
                    }

                    if (hit == null) {
                        LOG.debug("Cache miss");
                        handleCacheMiss(requestCacheControl, root, target, request, entityProducer, scope, chain, asyncExecCallback);
                    } else {
                        final ResponseCacheControl responseCacheControl = CacheControlHeaderParser.INSTANCE.parse(hit.entry);
                        handleCacheHit(requestCacheControl, responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
                    }
                }

                @Override
                public void failed(final Exception cause) {
                    asyncExecCallback.failed(cause);
                }

                @Override
                public void cancelled() {
                    asyncExecCallback.failed(new InterruptedIOException());
                }

            }));

        }
    }

    void chainProceed(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        try {
            chain.proceed(request, entityProducer, scope, asyncExecCallback);
        } catch (final HttpException | IOException ex) {
            asyncExecCallback.failed(ex);
        }
    }

    void callBackend(
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        LOG.debug("Calling the backend");
        final Instant requestDate = getCurrentDate();
        final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();
        chainProceed(request, entityProducer, scope, chain, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                final Instant responseDate = getCurrentDate();
                backendResponse.addHeader(HttpHeaders.VIA, generateViaHeader(backendResponse));

                final AsyncExecCallback callback = new BackendResponseHandler(target, request, requestDate, responseDate, scope, asyncExecCallback);
                callbackRef.set(callback);
                return callback.handleResponse(backendResponse, entityDetails);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                final AsyncExecCallback callback = callbackRef.getAndSet(null);
                if (callback != null) {
                    callback.handleInformationResponse(response);
                } else {
                    asyncExecCallback.handleInformationResponse(response);
                }
            }

            @Override
            public void completed() {
                final AsyncExecCallback callback = callbackRef.getAndSet(null);
                if (callback != null) {
                    callback.completed();
                } else {
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                final AsyncExecCallback callback = callbackRef.getAndSet(null);
                if (callback != null) {
                    callback.failed(cause);
                } else {
                    asyncExecCallback.failed(cause);
                }
            }

        });
    }

    class CachingAsyncDataConsumer implements AsyncDataConsumer {

        private final AsyncExecCallback fallback;
        private final HttpResponse backendResponse;
        private final EntityDetails entityDetails;
        private final AtomicBoolean writtenThrough;
        private final AtomicReference<ByteArrayBuffer> bufferRef;
        private final AtomicReference<AsyncDataConsumer> dataConsumerRef;

        CachingAsyncDataConsumer(
                final AsyncExecCallback fallback,
                final HttpResponse backendResponse,
                final EntityDetails entityDetails) {
            this.fallback = fallback;
            this.backendResponse = backendResponse;
            this.entityDetails = entityDetails;
            this.writtenThrough = new AtomicBoolean(false);
            this.bufferRef = new AtomicReference<>(entityDetails != null ? new ByteArrayBuffer(1024) : null);
            this.dataConsumerRef = new AtomicReference<>();
        }

        @Override
        public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            final AsyncDataConsumer dataConsumer = dataConsumerRef.get();
            if (dataConsumer != null) {
                dataConsumer.updateCapacity(capacityChannel);
            } else {
                capacityChannel.update(Integer.MAX_VALUE);
            }
        }

        @Override
        public final void consume(final ByteBuffer src) throws IOException {
            final ByteArrayBuffer buffer = bufferRef.get();
            if (buffer != null) {
                if (src.hasArray()) {
                    buffer.append(src.array(), src.arrayOffset() + src.position(), src.remaining());
                } else {
                    while (src.hasRemaining()) {
                        buffer.append(src.get());
                    }
                }
                if (buffer.length() > cacheConfig.getMaxObjectSize()) {
                    LOG.debug("Backend response content length exceeds maximum");
                    // Over the max limit. Stop buffering and forward the response
                    // along with all the data buffered so far to the caller.
                    bufferRef.set(null);
                    try {
                        final AsyncDataConsumer dataConsumer = fallback.handleResponse(backendResponse, entityDetails);
                        if (dataConsumer != null) {
                            dataConsumerRef.set(dataConsumer);
                            writtenThrough.set(true);
                            dataConsumer.consume(ByteBuffer.wrap(buffer.array(), 0, buffer.length()));
                        }
                    } catch (final HttpException ex) {
                        fallback.failed(ex);
                    }
                }
            } else {
                final AsyncDataConsumer dataConsumer = dataConsumerRef.get();
                if (dataConsumer != null) {
                    dataConsumer.consume(src);
                }
            }
        }

        @Override
        public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            final AsyncDataConsumer dataConsumer = dataConsumerRef.getAndSet(null);
            if (dataConsumer != null) {
                dataConsumer.streamEnd(trailers);
            }
        }

        @Override
        public void releaseResources() {
            final AsyncDataConsumer dataConsumer = dataConsumerRef.getAndSet(null);
            if (dataConsumer != null) {
                dataConsumer.releaseResources();
            }
        }

    }

    class BackendResponseHandler implements AsyncExecCallback {

        private final HttpHost target;
        private final HttpRequest request;
        private final Instant requestDate;
        private final Instant responseDate;
        private final AsyncExecChain.Scope scope;
        private final AsyncExecCallback asyncExecCallback;
        private final AtomicReference<CachingAsyncDataConsumer> cachingConsumerRef;

        BackendResponseHandler(
                final HttpHost target,
                final HttpRequest request,
                final Instant requestDate,
                final Instant responseDate,
                final AsyncExecChain.Scope scope,
                final AsyncExecCallback asyncExecCallback) {
            this.target = target;
            this.request = request;
            this.requestDate = requestDate;
            this.responseDate = responseDate;
            this.scope = scope;
            this.asyncExecCallback = asyncExecCallback;
            this.cachingConsumerRef = new AtomicReference<>();
        }

        @Override
        public AsyncDataConsumer handleResponse(
                final HttpResponse backendResponse,
                final EntityDetails entityDetails) throws HttpException, IOException {
            responseCompliance.ensureProtocolCompliance(scope.originalRequest, request, backendResponse);
            responseCache.flushCacheEntriesInvalidatedByExchange(target, request, backendResponse, new FutureCallback<Boolean>() {

                @Override
                public void completed(final Boolean result) {
                }

                @Override
                public void failed(final Exception ex) {
                    LOG.warn("Unable to flush invalidated entries from cache", ex);
                }

                @Override
                public void cancelled() {
                }

            });
            final ResponseCacheControl responseCacheControl = CacheControlHeaderParser.INSTANCE.parse(backendResponse);
            final boolean cacheable = responseCachingPolicy.isResponseCacheable(responseCacheControl, request, backendResponse);
            if (cacheable) {
                cachingConsumerRef.set(new CachingAsyncDataConsumer(asyncExecCallback, backendResponse, entityDetails));
                storeRequestIfModifiedSinceFor304Response(request, backendResponse);
            } else {
                LOG.debug("Backend response is not cacheable");
                if (!Method.isSafe(request.getMethod())) {
                    responseCache.flushCacheEntriesFor(target, request, new FutureCallback<Boolean>() {

                        @Override
                        public void completed(final Boolean result) {
                        }

                        @Override
                        public void failed(final Exception ex) {
                            LOG.warn("Unable to flush invalidated entries from cache", ex);
                        }

                        @Override
                        public void cancelled() {
                        }

                    });
                }
            }
            final CachingAsyncDataConsumer cachingDataConsumer = cachingConsumerRef.get();
            if (cachingDataConsumer != null) {
                LOG.debug("Caching backend response");
                return cachingDataConsumer;
            }
            return asyncExecCallback.handleResponse(backendResponse, entityDetails);
        }

        @Override
        public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
            asyncExecCallback.handleInformationResponse(response);
        }

        void triggerNewCacheEntryResponse(final HttpResponse backendResponse, final Instant responseDate, final ByteArrayBuffer buffer) {
            final CancellableDependency operation = scope.cancellableDependency;
            operation.setDependency(responseCache.store(
                    target,
                    request,
                    backendResponse,
                    buffer,
                    requestDate,
                    responseDate,
                    new FutureCallback<CacheHit>() {

                        @Override
                        public void completed(final CacheHit hit) {
                            LOG.debug("Backend response successfully cached");
                            try {
                                final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                                triggerResponse(cacheResponse, scope, asyncExecCallback);
                            } catch (final ResourceIOException ex) {
                                asyncExecCallback.failed(ex);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            asyncExecCallback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }

                    }));

        }

        @Override
        public void completed() {
            final CachingAsyncDataConsumer cachingDataConsumer = cachingConsumerRef.getAndSet(null);
            if (cachingDataConsumer != null && !cachingDataConsumer.writtenThrough.get()) {
                final ByteArrayBuffer buffer = cachingDataConsumer.bufferRef.getAndSet(null);
                final HttpResponse backendResponse = cachingDataConsumer.backendResponse;
                if (cacheConfig.isFreshnessCheckEnabled()) {
                    final CancellableDependency operation = scope.cancellableDependency;
                    operation.setDependency(responseCache.match(target, request, new FutureCallback<CacheMatch>() {

                        @Override
                        public void completed(final CacheMatch result) {
                            final CacheHit hit = result != null ? result.hit : null;
                            if (DateSupport.isAfter(hit != null ? hit.entry : null, backendResponse, HttpHeaders.DATE)) {
                                LOG.debug("Backend already contains fresher cache entry");
                                try {
                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                                } catch (final ResourceIOException ex) {
                                    asyncExecCallback.failed(ex);
                                }
                            } else {
                                triggerNewCacheEntryResponse(backendResponse, responseDate, buffer);
                            }
                        }

                        @Override
                        public void failed(final Exception cause) {
                            asyncExecCallback.failed(cause);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }

                    }));
                } else {
                    triggerNewCacheEntryResponse(backendResponse, responseDate, buffer);
                }
            } else {
                asyncExecCallback.completed();
            }
        }

        @Override
        public void failed(final Exception cause) {
            asyncExecCallback.failed(cause);
        }

    }

    private void handleCacheHit(
            final RequestCacheControl requestCacheControl,
            final ResponseCacheControl responseCacheControl,
            final CacheHit hit,
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        final HttpClientContext context  = scope.clientContext;
        recordCacheHit(target, request);
        final Instant now = getCurrentDate();

        if (requestCacheControl.isNoCache()) {
            // Revalidate with the server due to no-cache directive in response
            if (LOG.isDebugEnabled()) {
                LOG.debug("Revalidating with server due to no-cache directive in response.");
            }
            revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
            return;
        }

        if (suitabilityChecker.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, hit.entry, now)) {
            if (responseCachingPolicy.responseContainsNoCacheDirective(responseCacheControl, hit.entry)) {
                // Revalidate with the server due to no-cache directive in response
                revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
                return;
            }
            LOG.debug("Cache hit");
            try {
                final SimpleHttpResponse cacheResponse = generateCachedResponse(responseCacheControl, hit.entry, request, context, now);
                triggerResponse(cacheResponse, scope, asyncExecCallback);
            } catch (final ResourceIOException ex) {
                recordCacheFailure(target, request);
                if (!mayCallBackend(requestCacheControl)) {
                    final SimpleHttpResponse cacheResponse = generateGatewayTimeout(context);
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } else {
                    setResponseStatus(scope.clientContext, CacheResponseStatus.FAILURE);
                    try {
                        chain.proceed(request, entityProducer, scope, asyncExecCallback);
                    } catch (final HttpException | IOException ex2) {
                        asyncExecCallback.failed(ex2);
                    }
                }
            }
        } else if (!mayCallBackend(requestCacheControl)) {
            LOG.debug("Cache entry not suitable but only-if-cached requested");
            final SimpleHttpResponse cacheResponse = generateGatewayTimeout(context);
            triggerResponse(cacheResponse, scope, asyncExecCallback);
        } else if (!(hit.entry.getStatus() == HttpStatus.SC_NOT_MODIFIED && !suitabilityChecker.isConditional(request))) {
            LOG.debug("Revalidating cache entry");
            final boolean staleIfErrorEnabled = responseCachingPolicy.isStaleIfErrorEnabled(responseCacheControl, hit.entry);
            if (cacheRevalidator != null
                    && !staleResponseNotAllowed(requestCacheControl, responseCacheControl, hit.entry, now)
                    && (validityPolicy.mayReturnStaleWhileRevalidating(responseCacheControl, hit.entry, now) || staleIfErrorEnabled)) {
                LOG.debug("Serving stale with asynchronous revalidation");
                try {
                    final SimpleHttpResponse cacheResponse = generateCachedResponse(responseCacheControl, hit.entry, request, context, now);
                    final String exchangeId = ExecSupport.getNextExchangeId();
                    context.setExchangeId(exchangeId);
                    final AsyncExecChain.Scope fork = new AsyncExecChain.Scope(
                            exchangeId,
                            scope.route,
                            scope.originalRequest,
                            new ComplexFuture<>(null),
                            HttpClientContext.create(),
                            scope.execRuntime.fork(),
                            scope.scheduler,
                            scope.execCount);
                    cacheRevalidator.revalidateCacheEntry(
                            hit.getEntryKey(),
                            asyncExecCallback,
                            asyncExecCallback1 -> revalidateCacheEntry(requestCacheControl, responseCacheControl,
                                    hit, target, request, entityProducer, fork, chain, asyncExecCallback1));
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } catch (final ResourceIOException ex) {
                    if (staleIfErrorEnabled) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Serving stale response due to IOException and stale-if-error enabled");
                        }
                        try {
                            final SimpleHttpResponse cacheResponse = generateCachedResponse(responseCacheControl, hit.entry, request, context, now);
                            triggerResponse(cacheResponse, scope, asyncExecCallback);
                        } catch (final ResourceIOException ex2) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Failed to generate cached response, falling back to backend", ex2);
                            }
                            callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
                        }
                    } else {
                        asyncExecCallback.failed(ex);
                    }
                }
            } else {
                revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
            }
        } else {
            LOG.debug("Cache entry not usable; calling backend");
            callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
        }
    }

    void revalidateCacheEntry(
            final RequestCacheControl requestCacheControl,
            final ResponseCacheControl responseCacheControl,
            final CacheHit hit,
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        final Instant requestDate = getCurrentDate();
        final HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(
                responseCacheControl,
                BasicRequestBuilder.copy(scope.originalRequest).build(),
                hit.entry);
        chainProceed(conditionalRequest, entityProducer, scope, chain, new AsyncExecCallback() {

            final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();

            void triggerUpdatedCacheEntryResponse(final HttpResponse backendResponse, final Instant responseDate) {
                final CancellableDependency operation = scope.cancellableDependency;
                recordCacheUpdate(scope.clientContext);
                operation.setDependency(responseCache.update(
                        hit,
                        request,
                        backendResponse,
                        requestDate,
                        responseDate,
                        new FutureCallback<CacheHit>() {

                            @Override
                            public void completed(final CacheHit updated) {
                                if (suitabilityChecker.isConditional(request)
                                        && suitabilityChecker.allConditionalsMatch(request, updated.entry, Instant.now())) {
                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateNotModifiedResponse(updated.entry);
                                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                                } else {
                                    try {
                                        final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, updated.entry);
                                        triggerResponse(cacheResponse, scope, asyncExecCallback);
                                    } catch (final ResourceIOException ex) {
                                        asyncExecCallback.failed(ex);
                                    }
                                }
                            }

                            @Override
                            public void failed(final Exception ex) {
                                asyncExecCallback.failed(ex);
                            }

                            @Override
                            public void cancelled() {
                                asyncExecCallback.failed(new InterruptedIOException());
                            }

                        }));
            }

            void triggerResponseStaleCacheEntry() {
                try {
                    final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                    cacheResponse.addHeader(HttpHeaders.WARNING, "110 localhost \"Response is stale\"");
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } catch (final ResourceIOException ex) {
                    asyncExecCallback.failed(ex);
                }
            }

            AsyncExecCallback evaluateResponse(final HttpResponse backendResponse, final Instant responseDate) {
                backendResponse.addHeader(HttpHeaders.VIA, generateViaHeader(backendResponse));

                final int statusCode = backendResponse.getCode();
                if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
                    recordCacheUpdate(scope.clientContext);
                }
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    return new AsyncExecCallbackWrapper(asyncExecCallback, () -> triggerUpdatedCacheEntryResponse(backendResponse, responseDate));
                }
                if (staleIfErrorAppliesTo(statusCode)
                        && !staleResponseNotAllowed(requestCacheControl, responseCacheControl, hit.entry, getCurrentDate())
                        && validityPolicy.mayReturnStaleIfError(requestCacheControl, responseCacheControl, hit.entry, responseDate)) {
                    return new AsyncExecCallbackWrapper(asyncExecCallback, this::triggerResponseStaleCacheEntry);
                }
                return new BackendResponseHandler(target, conditionalRequest, requestDate, responseDate, scope, asyncExecCallback);
            }

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse1,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                final Instant responseDate1 = getCurrentDate();

                final AsyncExecCallback callback1;
                if (revalidationResponseIsTooOld(backendResponse1, hit.entry)
                        && (entityProducer == null || entityProducer.isRepeatable())) {

                    final HttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                            BasicRequestBuilder.copy(scope.originalRequest).build());

                    callback1 = new AsyncExecCallbackWrapper(asyncExecCallback, () -> chainProceed(unconditional, entityProducer, scope, chain, new AsyncExecCallback() {

                        @Override
                        public AsyncDataConsumer handleResponse(
                                final HttpResponse backendResponse2,
                                final EntityDetails entityDetails1) throws HttpException, IOException {
                            final Instant responseDate2 = getCurrentDate();
                            final AsyncExecCallback callback2 = evaluateResponse(backendResponse2, responseDate2);
                            callbackRef.set(callback2);
                            return callback2.handleResponse(backendResponse2, entityDetails1);
                        }

                        @Override
                        public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                            final AsyncExecCallback callback2 = callbackRef.getAndSet(null);
                            if (callback2 != null) {
                                callback2.handleInformationResponse(response);
                            } else {
                                asyncExecCallback.handleInformationResponse(response);
                            }
                        }

                        @Override
                        public void completed() {
                            final AsyncExecCallback callback2 = callbackRef.getAndSet(null);
                            if (callback2 != null) {
                                callback2.completed();
                            } else {
                                asyncExecCallback.completed();
                            }
                        }

                        @Override
                        public void failed(final Exception cause) {
                            final AsyncExecCallback callback2 = callbackRef.getAndSet(null);
                            if (callback2 != null) {
                                callback2.failed(cause);
                            } else {
                                asyncExecCallback.failed(cause);
                            }
                        }

                    }));
                } else {
                    callback1 = evaluateResponse(backendResponse1, responseDate1);
                }
                callbackRef.set(callback1);
                return callback1.handleResponse(backendResponse1, entityDetails);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                final AsyncExecCallback callback1 = callbackRef.getAndSet(null);
                if (callback1 != null) {
                    callback1.handleInformationResponse(response);
                } else {
                    asyncExecCallback.handleInformationResponse(response);
                }
            }

            @Override
            public void completed() {
                final AsyncExecCallback callback1 = callbackRef.getAndSet(null);
                if (callback1 != null) {
                    callback1.completed();
                } else {
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                final AsyncExecCallback callback1 = callbackRef.getAndSet(null);
                if (callback1 != null) {
                    callback1.failed(cause);
                } else {
                    asyncExecCallback.failed(cause);
                }
            }

        });

    }

    private void handleCacheMiss(
            final RequestCacheControl requestCacheControl,
            final CacheHit partialMatch,
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        recordCacheMiss(target, request);

        final CancellableDependency operation = scope.cancellableDependency;
        if (!mayCallBackend(requestCacheControl)) {
            final SimpleHttpResponse cacheResponse = SimpleHttpResponse.create(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
            triggerResponse(cacheResponse, scope, asyncExecCallback);
        }

        if (partialMatch != null && partialMatch.entry.isVariantRoot()) {
            operation.setDependency(responseCache.getVariants(
                    partialMatch,
                    new FutureCallback<Collection<CacheHit>>() {

                        @Override
                        public void completed(final Collection<CacheHit> variants) {
                            if (variants != null && !variants.isEmpty() && (entityProducer == null || entityProducer.isRepeatable())) {
                                negotiateResponseFromVariants(target, request, entityProducer, scope, chain, asyncExecCallback, variants);
                            } else {
                                callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            asyncExecCallback.failed(ex);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }

                    }));
        } else {
            callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
        }
    }

    void negotiateResponseFromVariants(
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback,
            final Collection<CacheHit> variants) {
        final CancellableDependency operation = scope.cancellableDependency;
        final Map<String, CacheHit> variantMap = new HashMap<>();
        for (final CacheHit variant : variants) {
            final Header header = variant.entry.getFirstHeader(HttpHeaders.ETAG);
            if (header != null) {
                variantMap.put(header.getValue(), variant);
            }
        }
        final HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequestFromVariants(
                BasicRequestBuilder.copy(request).build(),
                variantMap.keySet());

        final Instant requestDate = getCurrentDate();
        chainProceed(conditionalRequest, entityProducer, scope, chain, new AsyncExecCallback() {

            final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();

            void updateVariantCacheEntry(final HttpResponse backendResponse, final Instant responseDate, final CacheHit match) {
                recordCacheUpdate(scope.clientContext);
                operation.setDependency(responseCache.update(
                        match,
                        backendResponse,
                        requestDate,
                        responseDate,
                        new FutureCallback<CacheHit>() {

                            @Override
                            public void completed(final CacheHit hit) {
                                if (shouldSendNotModifiedResponse(request, hit.entry)) {
                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateNotModifiedResponse(hit.entry);
                                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                                } else {
                                    try {
                                        final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                                        operation.setDependency(responseCache.storeReusing(
                                                hit,
                                                target,
                                                request,
                                                backendResponse,
                                                requestDate,
                                                responseDate,
                                                new FutureCallback<CacheHit>() {

                                                    @Override
                                                    public void completed(final CacheHit result) {
                                                        triggerResponse(cacheResponse, scope, asyncExecCallback);
                                                    }

                                                    @Override
                                                    public void failed(final Exception ex) {
                                                        asyncExecCallback.failed(ex);
                                                    }

                                                    @Override
                                                    public void cancelled() {
                                                        asyncExecCallback.failed(new InterruptedIOException());
                                                    }

                                                }));
                                    } catch (final ResourceIOException ex) {
                                        asyncExecCallback.failed(ex);
                                    }
                                }
                            }

                            @Override
                            public void failed(final Exception ex) {
                                asyncExecCallback.failed(ex);
                            }

                            @Override
                            public void cancelled() {
                                asyncExecCallback.failed(new InterruptedIOException());
                            }

                        }));
            }

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                final Instant responseDate = getCurrentDate();
                backendResponse.addHeader(HttpHeaders.VIA, generateViaHeader(backendResponse));

                final AsyncExecCallback callback;
                // Handle 304 Not Modified responses
                if (backendResponse.getCode() == HttpStatus.SC_NOT_MODIFIED) {
                    responseCache.match(target, request, new FutureCallback<CacheMatch>() {
                        @Override
                        public void completed(final CacheMatch result) {
                            final CacheHit hit = result != null ? result.hit : null;
                            if (hit != null) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Existing cache entry found, updating cache entry");
                                }
                                responseCache.update(
                                        hit,
                                        request,
                                        backendResponse,
                                        requestDate,
                                        responseDate,
                                        new FutureCallback<CacheHit>() {

                                            @Override
                                            public void completed(final CacheHit updated) {
                                                try {
                                                    if (LOG.isDebugEnabled()) {
                                                        LOG.debug("Cache entry updated, generating response from updated entry");
                                                    }
                                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, updated.entry);
                                                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                                                } catch (final ResourceIOException ex) {
                                                    asyncExecCallback.failed(ex);
                                                }
                                            }
                                            @Override
                                            public void failed(final Exception cause) {
                                                if (LOG.isDebugEnabled()) {
                                                    LOG.debug("Request failed: {}", cause.getMessage());
                                                }
                                                asyncExecCallback.failed(cause);
                                            }

                                            @Override
                                            public void cancelled() {
                                                if (LOG.isDebugEnabled()) {
                                                    LOG.debug("Cache entry updated aborted");
                                                }
                                                asyncExecCallback.failed(new InterruptedIOException());
                                            }

                                        });
                            }
                        }

                        @Override
                        public void failed(final Exception cause) {
                            asyncExecCallback.failed(cause);
                        }

                        @Override
                        public void cancelled() {
                            asyncExecCallback.failed(new InterruptedIOException());
                        }
                    });
                }

                if (backendResponse.getCode() != HttpStatus.SC_NOT_MODIFIED) {
                    callback = new BackendResponseHandler(target, request, requestDate, responseDate, scope, asyncExecCallback);
                } else {
                    final Header resultEtagHeader = backendResponse.getFirstHeader(HttpHeaders.ETAG);
                    if (resultEtagHeader == null) {
                        LOG.warn("304 response did not contain ETag");
                        callback = new AsyncExecCallbackWrapper(asyncExecCallback, () -> callBackend(target, request, entityProducer, scope, chain, asyncExecCallback));
                    } else {
                        final String resultEtag = resultEtagHeader.getValue();
                        final CacheHit match = variantMap.get(resultEtag);
                        if (match == null) {
                            LOG.debug("304 response did not contain ETag matching one sent in If-None-Match");
                            callback = new AsyncExecCallbackWrapper(asyncExecCallback, () -> callBackend(target, request, entityProducer, scope, chain, asyncExecCallback));
                        } else {
                            if (revalidationResponseIsTooOld(backendResponse, match.entry)) {
                                final HttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                                        BasicRequestBuilder.copy(request).build());
                                scope.clientContext.setAttribute(HttpCoreContext.HTTP_REQUEST, unconditional);
                                callback = new AsyncExecCallbackWrapper(asyncExecCallback, () -> callBackend(target, request, entityProducer, scope, chain, asyncExecCallback));
                            } else {
                                callback = new AsyncExecCallbackWrapper(asyncExecCallback, () -> updateVariantCacheEntry(backendResponse, responseDate, match));
                            }
                        }
                    }
                }
                callbackRef.set(callback);
                return callback.handleResponse(backendResponse, entityDetails);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                final AsyncExecCallback callback = callbackRef.getAndSet(null);
                if (callback != null) {
                    callback.handleInformationResponse(response);
                } else {
                    asyncExecCallback.handleInformationResponse(response);
                }
            }

            @Override
            public void completed() {
                final AsyncExecCallback callback = callbackRef.getAndSet(null);
                if (callback != null) {
                    callback.completed();
                } else {
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                final AsyncExecCallback callback = callbackRef.getAndSet(null);
                if (callback != null) {
                    callback.failed(cause);
                } else {
                    asyncExecCallback.failed(cause);
                }
            }

        });

    }

}
