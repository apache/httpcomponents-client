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
import java.util.function.Consumer;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.client5.http.validator.ETag;
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
import org.apache.hc.core5.http.message.RequestLine;
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
            final HttpAsyncCache cache,
            final ScheduledExecutorService executorService,
            final SchedulingStrategy schedulingStrategy,
            final CacheConfig config) {
        this(cache,
                executorService != null ? new DefaultAsyncCacheRevalidator(executorService, schedulingStrategy) : null,
                config);
    }

    private void triggerResponse(
            final SimpleHttpResponse cacheResponse,
            final AsyncExecChain.Scope scope,
            final AsyncExecCallback asyncExecCallback) {
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

        private final Runnable command;
        private final Consumer<Exception> exceptionConsumer;

        AsyncExecCallbackWrapper(final Runnable command, final Consumer<Exception> exceptionConsumer) {
            this.command = command;
            this.exceptionConsumer = exceptionConsumer;
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
            if (exceptionConsumer != null) {
                exceptionConsumer.accept(cause);
            }
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
        final HttpClientContext context = scope.clientContext;

        final URIAuthority authority = request.getAuthority();
        final String scheme = request.getScheme();
        final HttpHost target = authority != null ? new HttpHost(scheme, authority) : route.getTargetHost();
        doExecute(target,
                request,
                entityProducer,
                scope,
                chain,
                new AsyncExecCallback() {

                    @Override
                    public AsyncDataConsumer handleResponse(
                            final HttpResponse response,
                            final EntityDetails entityDetails) throws HttpException, IOException {
                        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
                        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                        return asyncExecCallback.handleResponse(response, entityDetails);
                    }

                    @Override
                    public void handleInformationResponse(
                            final HttpResponse response) throws HttpException, IOException {
                        asyncExecCallback.handleInformationResponse(response);
                    }

                    @Override
                    public void completed() {
                        asyncExecCallback.completed();
                    }

                    @Override
                    public void failed(final Exception cause) {
                        asyncExecCallback.failed(cause);
                    }

                });
    }

    public void doExecute(
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

        final String exchangeId = scope.exchangeId;
        final HttpCacheContext context = HttpCacheContext.adapt(scope.clientContext);
        final CancellableDependency operation = scope.cancellableDependency;

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} request via cache: {}", exchangeId, new RequestLine(request));
        }

        context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MISS);
        context.setAttribute(HttpCacheContext.CACHE_ENTRY, null);

        if (clientRequestsOurOptions(request)) {
            context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            triggerResponse(SimpleHttpResponse.create(HttpStatus.SC_NOT_IMPLEMENTED), scope, asyncExecCallback);
            return;
        }

        final RequestCacheControl requestCacheControl;
        if (request.containsHeader(HttpHeaders.CACHE_CONTROL)) {
            requestCacheControl = CacheControlHeaderParser.INSTANCE.parse(request);
            context.setRequestCacheControl(requestCacheControl);
        } else {
            requestCacheControl = context.getRequestCacheControl();
            CacheControlHeaderGenerator.INSTANCE.generate(requestCacheControl, request);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} request cache control: {}", exchangeId, requestCacheControl);
        }

        if (cacheableRequestPolicy.canBeServedFromCache(requestCacheControl, request)) {
            operation.setDependency(responseCache.match(target, request, new FutureCallback<CacheMatch>() {

                @Override
                public void completed(final CacheMatch result) {
                    final CacheHit hit = result != null ? result.hit : null;
                    final CacheHit root = result != null ? result.root : null;
                    if (hit == null) {
                        handleCacheMiss(requestCacheControl, root, target, request, entityProducer, scope, chain, asyncExecCallback);
                    } else {
                        final ResponseCacheControl responseCacheControl = CacheControlHeaderParser.INSTANCE.parse(hit.entry);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} response cache control: {}", exchangeId, responseCacheControl);
                        }
                        context.setResponseCacheControl(responseCacheControl);
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

        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} request cannot be served from cache", exchangeId);
            }
            callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
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
        final String exchangeId = scope.exchangeId;

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} calling the backend", exchangeId);
        }
        final Instant requestDate = getCurrentDate();
        final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();
        chainProceed(request, entityProducer, scope, chain, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                final Instant responseDate = getCurrentDate();
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

        private final String exchangeId;
        private final AsyncExecCallback fallback;
        private final HttpResponse backendResponse;
        private final EntityDetails entityDetails;
        private final AtomicBoolean writtenThrough;
        private final AtomicReference<ByteArrayBuffer> bufferRef;
        private final AtomicReference<AsyncDataConsumer> dataConsumerRef;

        CachingAsyncDataConsumer(
                final String exchangeId,
                final AsyncExecCallback fallback,
                final HttpResponse backendResponse,
                final EntityDetails entityDetails) {
            this.exchangeId = exchangeId;
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
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} backend response content length exceeds maximum", exchangeId);
                    }
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
            final String exchangeId = scope.exchangeId;
            responseCache.evictInvalidatedEntries(target, request, backendResponse, new FutureCallback<Boolean>() {

                @Override
                public void completed(final Boolean result) {
                }

                @Override
                public void failed(final Exception ex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} unable to flush invalidated entries from cache", exchangeId, ex);
                    }
                }

                @Override
                public void cancelled() {
                }

            });
            if (isResponseTooBig(entityDetails)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} backend response is known to be too big", exchangeId);
                }
                return asyncExecCallback.handleResponse(backendResponse, entityDetails);
            }

            final ResponseCacheControl responseCacheControl = CacheControlHeaderParser.INSTANCE.parse(backendResponse);
            final boolean cacheable = responseCachingPolicy.isResponseCacheable(responseCacheControl, request, backendResponse);
            if (cacheable) {
                storeRequestIfModifiedSinceFor304Response(request, backendResponse);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} caching backend response", exchangeId);
                }
                final CachingAsyncDataConsumer cachingDataConsumer = new CachingAsyncDataConsumer(
                        exchangeId, asyncExecCallback, backendResponse, entityDetails);
                cachingConsumerRef.set(cachingDataConsumer);
                return cachingDataConsumer;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} backend response is not cacheable", exchangeId);
                }
                return asyncExecCallback.handleResponse(backendResponse, entityDetails);
            }
        }

        @Override
        public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
            asyncExecCallback.handleInformationResponse(response);
        }

        void triggerNewCacheEntryResponse(final HttpResponse backendResponse, final Instant responseDate, final ByteArrayBuffer buffer) {
            final String exchangeId = scope.exchangeId;
            final HttpClientContext context = scope.clientContext;
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
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} backend response successfully cached", exchangeId);
                            }
                            try {
                                final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                                context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
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

        void triggerCachedResponse(final HttpCacheEntry entry) {
            final HttpClientContext context = scope.clientContext;
            try {
                final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, entry);
                context.setAttribute(HttpCacheContext.CACHE_ENTRY, entry);
                triggerResponse(cacheResponse, scope, asyncExecCallback);
            } catch (final ResourceIOException ex) {
                asyncExecCallback.failed(ex);
            }
        }

        @Override
        public void completed() {
            final String exchangeId = scope.exchangeId;
            final CachingAsyncDataConsumer cachingDataConsumer = cachingConsumerRef.getAndSet(null);
            if (cachingDataConsumer == null || cachingDataConsumer.writtenThrough.get()) {
                asyncExecCallback.completed();
                return;
            }
            final HttpResponse backendResponse = cachingDataConsumer.backendResponse;
            final ByteArrayBuffer buffer = cachingDataConsumer.bufferRef.getAndSet(null);

            // Handle 304 Not Modified responses
            if (backendResponse.getCode() == HttpStatus.SC_NOT_MODIFIED) {
                responseCache.match(target, request, new FutureCallback<CacheMatch>() {

                    @Override
                    public void completed(final CacheMatch result) {
                        final CacheHit hit = result != null ? result.hit : null;
                        if (hit != null) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} existing cache entry found, updating cache entry", exchangeId);
                            }
                            responseCache.update(
                                    hit,
                                    target,
                                    request,
                                    backendResponse,
                                    requestDate,
                                    responseDate,
                                    new FutureCallback<CacheHit>() {

                                        @Override
                                        public void completed(final CacheHit updated) {
                                            if (LOG.isDebugEnabled()) {
                                                LOG.debug("{} cache entry updated, generating response from updated entry", exchangeId);
                                            }
                                            triggerCachedResponse(updated.entry);
                                        }
                                        @Override
                                        public void failed(final Exception cause) {
                                            if (LOG.isDebugEnabled()) {
                                                LOG.debug("{} request failed: {}", exchangeId, cause.getMessage());
                                            }
                                            asyncExecCallback.failed(cause);
                                        }

                                        @Override
                                        public void cancelled() {
                                            if (LOG.isDebugEnabled()) {
                                                LOG.debug("{} cache entry updated aborted", exchangeId);
                                            }
                                            asyncExecCallback.failed(new InterruptedIOException());
                                        }

                                    });
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

                });
            } else {
                if (cacheConfig.isFreshnessCheckEnabled()) {
                    final CancellableDependency operation = scope.cancellableDependency;
                    operation.setDependency(responseCache.match(target, request, new FutureCallback<CacheMatch>() {

                        @Override
                        public void completed(final CacheMatch result) {
                            final CacheHit hit = result != null ? result.hit : null;
                            if (HttpCacheEntry.isNewer(hit != null ? hit.entry : null, backendResponse)) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("{} backend already contains fresher cache entry", exchangeId);
                                }
                                triggerCachedResponse(hit.entry);
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
        final String exchangeId = scope.exchangeId;

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} cache hit: {}", exchangeId, new RequestLine(request));
        }

        context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_HIT);
        cacheHits.getAndIncrement();

        final Instant now = getCurrentDate();

        final CacheSuitability cacheSuitability = suitabilityChecker.assessSuitability(requestCacheControl, responseCacheControl, request, hit.entry, now);
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} cache suitability: {}", exchangeId, cacheSuitability);
        }
        if (cacheSuitability == CacheSuitability.FRESH || cacheSuitability == CacheSuitability.FRESH_ENOUGH) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} cache hit is fresh enough", exchangeId);
            }
            try {
                final SimpleHttpResponse cacheResponse = generateCachedResponse(request, hit.entry, now);
                context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
                triggerResponse(cacheResponse, scope, asyncExecCallback);
            } catch (final ResourceIOException ex) {
                if (requestCacheControl.isOnlyIfCached()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} request marked only-if-cached", exchangeId);
                    }
                    context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                    final SimpleHttpResponse cacheResponse = generateGatewayTimeout();
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } else {
                    context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.FAILURE);
                    try {
                        chain.proceed(request, entityProducer, scope, asyncExecCallback);
                    } catch (final HttpException | IOException ex2) {
                        asyncExecCallback.failed(ex2);
                    }
                }
            }
        } else {
            if (requestCacheControl.isOnlyIfCached()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} cache entry not is not fresh and only-if-cached requested", exchangeId);
                }
                context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                final SimpleHttpResponse cacheResponse = generateGatewayTimeout();
                triggerResponse(cacheResponse, scope, asyncExecCallback);
            } else if (cacheSuitability == CacheSuitability.MISMATCH) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} cache entry does not match the request; calling backend", exchangeId);
                }
                callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
            } else if (entityProducer != null && !entityProducer.isRepeatable()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} request is not repeatable; calling backend", exchangeId);
                }
                callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
            } else if (hit.entry.getStatus() == HttpStatus.SC_NOT_MODIFIED && !suitabilityChecker.isConditional(request)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} non-modified cache entry does not match the non-conditional request; calling backend", exchangeId);
                }
                callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
            } else if (cacheSuitability == CacheSuitability.REVALIDATION_REQUIRED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} revalidation required; revalidating cache entry", exchangeId);
                }
                revalidateCacheEntryWithoutFallback(responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
            } else if (cacheSuitability == CacheSuitability.STALE_WHILE_REVALIDATED) {
                if (cacheRevalidator != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} serving stale with asynchronous revalidation", exchangeId);
                    }
                    try {
                        final String revalidationExchangeId = ExecSupport.getNextExchangeId();
                        context.setExchangeId(revalidationExchangeId);
                        final AsyncExecChain.Scope fork = new AsyncExecChain.Scope(
                                revalidationExchangeId,
                                scope.route,
                                scope.originalRequest,
                                new ComplexFuture<>(null),
                                HttpClientContext.create(),
                                scope.execRuntime.fork(),
                                scope.scheduler,
                                scope.execCount);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} starting asynchronous revalidation exchange {}", exchangeId, revalidationExchangeId);
                        }
                        cacheRevalidator.revalidateCacheEntry(
                                hit.getEntryKey(),
                                asyncExecCallback,
                                c -> revalidateCacheEntry(responseCacheControl, hit, target, request, entityProducer, fork, chain, c));
                        context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                        final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                        context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
                        triggerResponse(cacheResponse, scope, asyncExecCallback);
                    } catch (final IOException ex) {
                        asyncExecCallback.failed(ex);
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} revalidating stale cache entry (asynchronous revalidation disabled)", exchangeId);
                    }
                    revalidateCacheEntryWithFallback(requestCacheControl, responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
                }
            } else if (cacheSuitability == CacheSuitability.STALE) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} revalidating stale cache entry", exchangeId);
                }
                revalidateCacheEntryWithFallback(requestCacheControl, responseCacheControl, hit, target, request, entityProducer, scope, chain, asyncExecCallback);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} cache entry not usable; calling backend", exchangeId);
                }
                callBackend(target, request, entityProducer, scope, chain, asyncExecCallback);
            }
        }
    }

    void revalidateCacheEntry(
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
                BasicRequestBuilder.copy(request).build(),
                hit.entry);
        final HttpClientContext context = scope.clientContext;
        chainProceed(conditionalRequest, entityProducer, scope, chain, new AsyncExecCallback() {

            final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();

            void triggerUpdatedCacheEntryResponse(final HttpResponse backendResponse, final Instant responseDate) {
                final CancellableDependency operation = scope.cancellableDependency;
                operation.setDependency(responseCache.update(
                        hit,
                        target,
                        request,
                        backendResponse,
                        requestDate,
                        responseDate,
                        new FutureCallback<CacheHit>() {

                            @Override
                            public void completed(final CacheHit updated) {
                                try {
                                    final SimpleHttpResponse cacheResponse = generateCachedResponse(request, updated.entry, responseDate);
                                    context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
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

            AsyncExecCallback evaluateResponse(final HttpResponse backendResponse, final Instant responseDate) {
                final int statusCode = backendResponse.getCode();
                if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
                    context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.VALIDATED);
                    cacheUpdates.getAndIncrement();
                }
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    return new AsyncExecCallbackWrapper(() -> triggerUpdatedCacheEntryResponse(backendResponse, responseDate), asyncExecCallback::failed);
                }
                return new BackendResponseHandler(target, conditionalRequest, requestDate, responseDate, scope, asyncExecCallback);
            }

            @Override
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse1,
                    final EntityDetails entityDetails) throws HttpException, IOException {

                final Instant responseDate = getCurrentDate();

                final AsyncExecCallback callback1;
                if (HttpCacheEntry.isNewer(hit.entry, backendResponse1)) {

                    final HttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                            BasicRequestBuilder.copy(scope.originalRequest).build());

                    callback1 = new AsyncExecCallbackWrapper(() -> chainProceed(unconditional, entityProducer, scope, chain, new AsyncExecCallback() {

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

                    }), asyncExecCallback::failed);
                } else {
                    callback1 = evaluateResponse(backendResponse1, responseDate);
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

    void revalidateCacheEntryWithoutFallback(
            final ResponseCacheControl responseCacheControl,
            final CacheHit hit,
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        final String exchangeId = scope.exchangeId;
        final HttpClientContext context = scope.clientContext;
        revalidateCacheEntry(responseCacheControl, hit, target, request, entityProducer, scope, chain, new AsyncExecCallback() {

            private final AtomicBoolean committed = new AtomicBoolean();

            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse response,
                                                    final EntityDetails entityDetails) throws HttpException, IOException {
                committed.set(true);
                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                asyncExecCallback.completed();
            }

            @Override
            public void failed(final Exception cause) {
                if (!committed.get() && cause instanceof IOException) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} I/O error while revalidating cache entry", exchangeId, cause);
                    }
                    final SimpleHttpResponse cacheResponse = generateGatewayTimeout();
                    context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                    triggerResponse(cacheResponse, scope, asyncExecCallback);
                } else {
                    asyncExecCallback.failed(cause);
                }
            }

        });
    }

    void revalidateCacheEntryWithFallback(
            final RequestCacheControl requestCacheControl,
            final ResponseCacheControl responseCacheControl,
            final CacheHit hit,
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback) {
        final String exchangeId = scope.exchangeId;
        final HttpClientContext context = scope.clientContext;
        revalidateCacheEntry(responseCacheControl, hit, target, request, entityProducer, scope, chain, new AsyncExecCallback() {

            private final AtomicReference<HttpResponse> committed = new AtomicReference<>();

            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
                final int status = response.getCode();
                if (staleIfErrorAppliesTo(status) &&
                        suitabilityChecker.isSuitableIfError(requestCacheControl, responseCacheControl, hit.entry, getCurrentDate())) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} serving stale response due to {} status and stale-if-error enabled", exchangeId, status);
                    }
                    return null;
                } else {
                    committed.set(response);
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                final HttpResponse response = committed.get();
                if (response == null) {
                    try {
                        context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                        final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                        context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
                        triggerResponse(cacheResponse, scope, asyncExecCallback);
                    } catch (final IOException ex) {
                        asyncExecCallback.failed(ex);
                    }
                } else {
                    asyncExecCallback.completed();
                }
            }

            @Override
            public void failed(final Exception cause) {
                final HttpResponse response = committed.get();
                if (response == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} I/O error while revalidating cache entry", exchangeId, cause);
                    }
                    context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                    if (cause instanceof IOException &&
                            suitabilityChecker.isSuitableIfError(requestCacheControl, responseCacheControl, hit.entry, getCurrentDate())) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} serving stale response due to IOException and stale-if-error enabled", exchangeId);
                        }
                        try {
                            final SimpleHttpResponse cacheResponse = responseGenerator.generateResponse(request, hit.entry);
                            context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
                            triggerResponse(cacheResponse, scope, asyncExecCallback);
                        } catch (final IOException ex) {
                            asyncExecCallback.failed(cause);
                        }
                    } else {
                        final SimpleHttpResponse cacheResponse = generateGatewayTimeout();
                        triggerResponse(cacheResponse, scope, asyncExecCallback);
                    }
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
        final String exchangeId = scope.exchangeId;

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} cache miss: {}", exchangeId, new RequestLine(request));
        }
        cacheMisses.getAndIncrement();

        final CancellableDependency operation = scope.cancellableDependency;
        if (requestCacheControl.isOnlyIfCached()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} request marked only-if-cached", exchangeId);
            }
            final HttpClientContext context = scope.clientContext;
            context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            final SimpleHttpResponse cacheResponse = generateGatewayTimeout();
            triggerResponse(cacheResponse, scope, asyncExecCallback);
        }

        if (partialMatch != null && partialMatch.entry.hasVariants() && entityProducer == null) {
            operation.setDependency(responseCache.getVariants(
                    partialMatch,
                    new FutureCallback<Collection<CacheHit>>() {

                        @Override
                        public void completed(final Collection<CacheHit> variants) {
                            if (variants != null && !variants.isEmpty()) {
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
        final String exchangeId = scope.exchangeId;
        final CancellableDependency operation = scope.cancellableDependency;
        final Map<ETag, CacheHit> variantMap = new HashMap<>();
        for (final CacheHit variant : variants) {
            final ETag eTag = variant.entry.getETag();
            if (eTag != null) {
                variantMap.put(eTag, variant);
            }
        }

        final HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequestFromVariants(
                request,
                variantMap.keySet());

        final Instant requestDate = getCurrentDate();
        chainProceed(conditionalRequest, entityProducer, scope, chain, new AsyncExecCallback() {

            final AtomicReference<AsyncExecCallback> callbackRef = new AtomicReference<>();

            void updateVariantCacheEntry(final HttpResponse backendResponse, final Instant responseDate, final CacheHit match) {
                final HttpClientContext context = scope.clientContext;
                context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, CacheResponseStatus.VALIDATED);
                cacheUpdates.getAndIncrement();

                operation.setDependency(responseCache.storeFromNegotiated(
                        match,
                        target,
                        request,
                        backendResponse,
                        requestDate,
                        responseDate,
                        new FutureCallback<CacheHit>() {

                            @Override
                            public void completed(final CacheHit hit) {
                                try {
                                    final SimpleHttpResponse cacheResponse = generateCachedResponse(request, hit.entry, responseDate);
                                    context.setAttribute(HttpCacheContext.CACHE_ENTRY, hit.entry);
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
            public AsyncDataConsumer handleResponse(
                    final HttpResponse backendResponse,
                    final EntityDetails entityDetails) throws HttpException, IOException {
                final HttpClientContext context = scope.clientContext;
                final Instant responseDate = getCurrentDate();
                final AsyncExecCallback callback;
                if (backendResponse.getCode() != HttpStatus.SC_NOT_MODIFIED) {
                    callback = new BackendResponseHandler(target, request, requestDate, responseDate, scope, asyncExecCallback);
                } else {
                    final ETag resultEtag = ETag.get(backendResponse);
                    if (resultEtag == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} 304 response did not contain ETag", exchangeId);
                        }
                        callback = new AsyncExecCallbackWrapper(() -> callBackend(target, request, entityProducer, scope, chain, asyncExecCallback), asyncExecCallback::failed);
                    } else {
                        final CacheHit match = variantMap.get(resultEtag);
                        if (match == null) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} 304 response did not contain ETag matching one sent in If-None-Match", exchangeId);
                            }
                            callback = new AsyncExecCallbackWrapper(() -> callBackend(target, request, entityProducer, scope, chain, asyncExecCallback), asyncExecCallback::failed);
                        } else {
                            if (HttpCacheEntry.isNewer(match.entry, backendResponse)) {
                                final HttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                                        BasicRequestBuilder.copy(request).build());
                                callback = new AsyncExecCallbackWrapper(() -> callBackend(target, unconditional, entityProducer, scope, chain, asyncExecCallback), asyncExecCallback::failed);
                            } else {
                                callback = new AsyncExecCallbackWrapper(() -> updateVariantCacheEntry(backendResponse, responseDate, match), asyncExecCallback::failed);
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
