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
import java.util.Date;
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
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.RequestCopier;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.client5.http.utils.DateUtils;
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
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

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

    private final HttpAsyncCache responseCache;
    private final DefaultAsyncCacheRevalidator cacheRevalidator;
    private final ConditionalRequestBuilder<HttpRequest> conditionalRequestBuilder;

    AsyncCachingExec(final HttpAsyncCache cache, final DefaultAsyncCacheRevalidator cacheRevalidator, final CacheConfig config) {
        super(config);
        this.responseCache = Args.notNull(cache, "Response cache");
        this.cacheRevalidator = cacheRevalidator;
        this.conditionalRequestBuilder = new ConditionalRequestBuilder<>(RequestCopier.INSTANCE);
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
                dataConsumer.consume(ByteBuffer.wrap(content));
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
        context.setAttribute(HttpClientContext.HTTP_REQUEST, request);

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

        final SimpleHttpResponse fatalErrorResponse = getFatallyNoncompliantResponse(request, context);
        if (fatalErrorResponse != null) {
            triggerResponse(fatalErrorResponse, scope, asyncExecCallback);
            return;
        }

        requestCompliance.makeRequestCompliant(request);
        request.addHeader("Via",via);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            log.debug("Request is not servable from cache");
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
            operation.setDependency(responseCache.getCacheEntry(target, request, new FutureCallback<HttpCacheEntry>() {

                @Override
                public void completed(final HttpCacheEntry entry) {
                    if (entry == null) {
                        log.debug("Cache miss");
                        handleCacheMiss(target, request, entityProducer, scope, chain, asyncExecCallback);
                    } else {
                        handleCacheHit(target, request, entityProducer, scope, chain, asyncExecCallback, entry);
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
        log.debug("Calling the backend");
        final Date requestDate = getCurrentDate();
        final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();
        chainProceed(request, entityProducer, scope, chain, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                final Date responseDate = getCurrentDate();
                backendResponse.addHeader("Via", generateViaHeader(backendResponse));

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
                    log.debug("Backend response content length exceeds maximum");
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
        private final Date requestDate;
        private final Date responseDate;
        private final AsyncExecChain.Scope scope;
        private final AsyncExecCallback asyncExecCallback;
        private final AtomicReference<CachingAsyncDataConsumer> cachingConsumerRef;

        BackendResponseHandler(
                final HttpHost target,
                final HttpRequest request,
                final Date requestDate,
                final Date responseDate,
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
                    log.warn("Unable to flush invalidated entries from cache", ex);
                }

                @Override
                public void cancelled() {
                }

            });
            final boolean cacheable = responseCachingPolicy.isResponseCacheable(request, backendResponse);
            if (cacheable) {
                cachingConsumerRef.set(new CachingAsyncDataConsumer(asyncExecCallback, backendResponse, entityDetails));
                storeRequestIfModifiedSinceFor304Response(request, backendResponse);
            } else {
                log.debug("Backend response is not cacheable");
                responseCache.flushCacheEntriesFor(target, request, new FutureCallback<Boolean>() {

                    @Override
                    public void completed(final Boolean result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                        log.warn("Unable to flush invalidated entries from cache", ex);
                    }

                    @Override
                    public void cancelled() {
                    }

                });
            }
            final CachingAsyncDataConsumer cachingDataConsumer = cachingConsumerRef.get();
            if (cachingDataConsumer != null) {
                log.debug("Caching backend response");
                return cachingDataConsumer;
            }
            return asyncExecCallback.handleResponse(backendResponse, entityDetails);
        }

        @Override
        public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
            asyncExecCallback.handleInformationResponse(response);
        }

        void triggerNewCacheEntryResponse(final HttpResponse backendResponse, final Date responseDate, final ByteArrayBuffer buffer) {
            final CancellableDependency operation = scope.cancellableDependency;
            operation.setDependency(responseCache.createCacheEntry(
                    target,
                    request,
                    backendResponse,
                    buffer,
                    requestDate,
                    responseDate,
                    new FutureCallback<HttpCacheEntry>() {

                        @Override
                        public void completed(final HttpCacheEntry newEntry) {
                            log.debug("Backend response successfully cached");
                            try {
                                final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, newEntry);
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
                    operation.setDependency(responseCache.getCacheEntry(target, request, new FutureCallback<HttpCacheEntry>() {

                        @Override
                        public void completed(final HttpCacheEntry existingEntry) {
                            if (DateUtils.isAfter(existingEntry, backendResponse, HttpHeaders.DATE)) {
                                log.debug("Backend already contains fresher cache entry");
                                try {
                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, existingEntry);
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
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback,
            final HttpCacheEntry entry) {
        final HttpClientContext context  = scope.clientContext;
        recordCacheHit(target, request);
        final Date now = getCurrentDate();
        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry, now)) {
            log.debug("Cache hit");
            try {
                final SimpleHttpResponse cacheResponse = generateCachedResponse(request, context, entry, now);
                triggerResponse(cacheResponse, scope, asyncExecCallback);
            } catch (final ResourceIOException ex) {
                recordCacheFailure(target, request);
                if (!mayCallBackend(request)) {
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
        } else if (!mayCallBackend(request)) {
            log.debug("Cache entry not suitable but only-if-cached requested");
            final SimpleHttpResponse cacheResponse = generateGatewayTimeout(context);
            triggerResponse(cacheResponse, scope, asyncExecCallback);
        } else if (!(entry.getStatus() == HttpStatus.SC_NOT_MODIFIED && !suitabilityChecker.isConditional(request))) {
            log.debug("Revalidating cache entry");
            if (cacheRevalidator != null
                    && !staleResponseNotAllowed(request, entry, now)
                    && validityPolicy.mayReturnStaleWhileRevalidating(entry, now)) {
                log.debug("Serving stale with asynchronous revalidation");
                try {
                    final SimpleHttpResponse cacheResponse = generateCachedResponse(request, context, entry, now);
                    final String exchangeId = ExecSupport.getNextExchangeId();
                    final AsyncExecChain.Scope fork = new AsyncExecChain.Scope(
                            exchangeId,
                            scope.route,
                            scope.originalRequest,
                            new ComplexFuture<>(null),
                            HttpClientContext.create(),
                            scope.execRuntime.fork());
                    cacheRevalidator.revalidateCacheEntry(
                            responseCache.generateKey(target, request, entry),
                            asyncExecCallback,
                            new DefaultAsyncCacheRevalidator.RevalidationCall() {

                                @Override
                                public void execute(final AsyncExecCallback asyncExecCallback) {
                                    revalidateCacheEntry(target, request, entityProducer, fork, chain, asyncExecCallback, entry);
                                }

                            });
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } catch (final ResourceIOException ex) {
                    asyncExecCallback.failed(ex);
                }
            } else {
                revalidateCacheEntry(target, request, entityProducer, scope, chain, asyncExecCallback, entry);
            }
        } else {
            log.debug("Cache entry not usable; calling backend");
            callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
        }
    }

    void revalidateCacheEntry(
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback,
            final HttpCacheEntry cacheEntry) {
        final Date requestDate = getCurrentDate();
        final HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(scope.originalRequest, cacheEntry);
        chainProceed(conditionalRequest, entityProducer, scope, chain, new AsyncExecCallback() {

            final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();

            void triggerUpdatedCacheEntryResponse(final HttpResponse backendResponse, final Date responseDate) {
                final CancellableDependency operation = scope.cancellableDependency;
                recordCacheUpdate(scope.clientContext);
                operation.setDependency(responseCache.updateCacheEntry(
                        target,
                        request,
                        cacheEntry,
                        backendResponse,
                        requestDate,
                        responseDate,
                        new FutureCallback<HttpCacheEntry>() {

                            @Override
                            public void completed(final HttpCacheEntry updatedEntry) {
                                if (suitabilityChecker.isConditional(request)
                                        && suitabilityChecker.allConditionalsMatch(request, updatedEntry, new Date())) {
                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateNotModifiedResponse(updatedEntry);
                                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                                } else {
                                    try {
                                        final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, updatedEntry);
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
                    final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, cacheEntry);
                    cacheResponse.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } catch (final ResourceIOException ex) {
                    asyncExecCallback.failed(ex);
                }
            }

            AsyncExecCallback evaluateResponse(final HttpResponse backendResponse, final Date responseDate) {
                backendResponse.addHeader(HeaderConstants.VIA, generateViaHeader(backendResponse));

                final int statusCode = backendResponse.getCode();
                if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
                    recordCacheUpdate(scope.clientContext);
                }
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    return new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                        @Override
                        public void run() {
                            triggerUpdatedCacheEntryResponse(backendResponse, responseDate);
                        }

                    });
                }
                if (staleIfErrorAppliesTo(statusCode)
                        && !staleResponseNotAllowed(request, cacheEntry, getCurrentDate())
                        && validityPolicy.mayReturnStaleIfError(request, cacheEntry, responseDate)) {
                    return new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                        @Override
                        public void run() {
                            triggerResponseStaleCacheEntry();
                        }

                    });
                }
                return new BackendResponseHandler(target, conditionalRequest, requestDate, responseDate, scope, asyncExecCallback);
            }

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse1,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                final Date responseDate1 = getCurrentDate();

                final AsyncExecCallback callback1;
                if (revalidationResponseIsTooOld(backendResponse1, cacheEntry)
                        && (entityProducer == null || entityProducer.isRepeatable())) {

                    final HttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                            scope.originalRequest);

                    callback1 = new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                        @Override
                        public void run() {
                            chainProceed(unconditional, entityProducer, scope, chain, new AsyncExecCallback() {

                                @Override
                                public AsyncDataConsumer handleResponse(
                                        final HttpResponse backendResponse2,
                                        final EntityDetails entityDetails) throws HttpException, IOException {
                                    final Date responseDate2 = getCurrentDate();
                                    final AsyncExecCallback callback2 = evaluateResponse(backendResponse2, responseDate2);
                                    callbackRef.set(callback2);
                                    return callback2.handleResponse(backendResponse2, entityDetails);
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

                            });

                        }

                    });
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
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        recordCacheMiss(target, request);

        if (mayCallBackend(request)) {
            final CancellableDependency operation = scope.cancellableDependency;
            operation.setDependency(responseCache.getVariantCacheEntriesWithEtags(
                    target,
                    request,
                    new FutureCallback<Map<String, Variant>>() {

                        @Override
                        public void completed(final Map<String, Variant> variants) {
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
            final SimpleHttpResponse cacheResponse = SimpleHttpResponse.create(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
            triggerResponse(cacheResponse, scope, asyncExecCallback);
        }
    }

    void negotiateResponseFromVariants(
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback,
            final Map<String, Variant> variants) {
        final CancellableDependency operation = scope.cancellableDependency;
        final HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequestFromVariants(request, variants);

        final Date requestDate = getCurrentDate();
        chainProceed(conditionalRequest, entityProducer, scope, chain, new AsyncExecCallback() {

            final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();

            void updateVariantCacheEntry(final HttpResponse backendResponse, final Date responseDate, final Variant matchingVariant) {
                recordCacheUpdate(scope.clientContext);
                operation.setDependency(responseCache.updateVariantCacheEntry(
                        target,
                        conditionalRequest,
                        backendResponse,
                        matchingVariant,
                        requestDate,
                        responseDate,
                        new FutureCallback<HttpCacheEntry>() {

                            @Override
                            public void completed(final HttpCacheEntry responseEntry) {
                                if (shouldSendNotModifiedResponse(request, responseEntry)) {
                                    final SimpleHttpResponse cacheResponse = responseGenerator.generateNotModifiedResponse(responseEntry);
                                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                                } else {
                                    try {
                                        final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, responseEntry);
                                        operation.setDependency(responseCache.reuseVariantEntryFor(
                                                target,
                                                request,
                                                matchingVariant,
                                                new FutureCallback<Boolean>() {

                                                    @Override
                                                    public void completed(final Boolean result) {
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
                final Date responseDate = getCurrentDate();
                backendResponse.addHeader("Via", generateViaHeader(backendResponse));

                final AsyncExecCallback callback;

                if (backendResponse.getCode() != HttpStatus.SC_NOT_MODIFIED) {
                    callback = new BackendResponseHandler(target, request, requestDate, responseDate, scope, asyncExecCallback);
                } else {
                    final Header resultEtagHeader = backendResponse.getFirstHeader(HeaderConstants.ETAG);
                    if (resultEtagHeader == null) {
                        log.warn("304 response did not contain ETag");
                        callback = new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                            @Override
                            public void run() {
                                callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
                            }

                        });
                    } else {
                        final String resultEtag = resultEtagHeader.getValue();
                        final Variant matchingVariant = variants.get(resultEtag);
                        if (matchingVariant == null) {
                            log.debug("304 response did not contain ETag matching one sent in If-None-Match");
                            callback = new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                                @Override
                                public void run() {
                                    callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
                                }

                            });
                        } else {
                            if (revalidationResponseIsTooOld(backendResponse, matchingVariant.getEntry())) {
                                final HttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(request);
                                scope.clientContext.setAttribute(HttpCoreContext.HTTP_REQUEST, unconditional);
                                callback = new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                                    @Override
                                    public void run() {
                                        callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
                                    }

                                });
                            } else {
                                callback = new AsyncExecCallbackWrapper(asyncExecCallback, new Runnable() {

                                    @Override
                                    public void run() {
                                        updateVariantCacheEntry(backendResponse, responseDate, matchingVariant);
                                    }

                                });
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
