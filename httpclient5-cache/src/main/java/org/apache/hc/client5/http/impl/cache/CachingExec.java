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
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.impl.classic.ClassicRequestCopier;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Request executor in the request execution chain that is responsible for
 * transparent client-side caching.
 * </p>
 * <p>
 * The current implementation is conditionally
 * compliant with HTTP/1.1 (meaning all the MUST and MUST NOTs are obeyed),
 * although quite a lot, though not all, of the SHOULDs and SHOULD NOTs
 * are obeyed too.
 * </p>
 * <p>
 * Folks that would like to experiment with alternative storage backends
 * should look at the {@link HttpCacheStorage} interface and the related
 * package documentation there. You may also be interested in the provided
 * {@link org.apache.hc.client5.http.impl.cache.ehcache.EhcacheHttpCacheStorage
 * EhCache} and {@link
 * org.apache.hc.client5.http.impl.cache.memcached.MemcachedHttpCacheStorage
 * memcached} storage backends.
 * </p>
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
class CachingExec extends CachingExecBase implements ExecChainHandler {

    private final HttpCache responseCache;
    private final DefaultCacheRevalidator cacheRevalidator;
    private final ConditionalRequestBuilder<ClassicHttpRequest> conditionalRequestBuilder;

    private final Logger log = LoggerFactory.getLogger(getClass());

    CachingExec(final HttpCache cache, final DefaultCacheRevalidator cacheRevalidator, final CacheConfig config) {
        super(config);
        this.responseCache = Args.notNull(cache, "Response cache");
        this.cacheRevalidator = cacheRevalidator;
        this.conditionalRequestBuilder = new ConditionalRequestBuilder<>(ClassicRequestCopier.INSTANCE);
    }

    CachingExec(
            final HttpCache responseCache,
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final ResponseProtocolCompliance responseCompliance,
            final RequestProtocolCompliance requestCompliance,
            final DefaultCacheRevalidator cacheRevalidator,
            final ConditionalRequestBuilder<ClassicHttpRequest> conditionalRequestBuilder,
            final CacheConfig config) {
        super(validityPolicy, responseCachingPolicy, responseGenerator, cacheableRequestPolicy,
                suitabilityChecker, responseCompliance, requestCompliance, config);
        this.responseCache = responseCache;
        this.cacheRevalidator = cacheRevalidator;
        this.conditionalRequestBuilder = conditionalRequestBuilder;
    }

    CachingExec(
            final HttpCache cache,
            final ScheduledExecutorService executorService,
            final SchedulingStrategy schedulingStrategy,
            final CacheConfig config) {
        this(cache,
                executorService != null ? new DefaultCacheRevalidator(executorService, schedulingStrategy) : null,
                config);
    }

    CachingExec(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final ScheduledExecutorService executorService,
            final SchedulingStrategy schedulingStrategy,
            final CacheConfig config) {
        this(new BasicHttpCache(resourceFactory, storage), executorService, schedulingStrategy, config);
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final HttpRoute route = scope.route;
        final HttpClientContext context = scope.clientContext;
        context.setAttribute(HttpClientContext.HTTP_ROUTE, scope.route);
        context.setAttribute(HttpClientContext.HTTP_REQUEST, request);

        final URIAuthority authority = request.getAuthority();
        final String scheme = request.getScheme();
        final HttpHost target = authority != null ? new HttpHost(scheme, authority) : route.getTargetHost();
        final String via = generateViaHeader(request);

        // default response context
        setResponseStatus(context, CacheResponseStatus.CACHE_MISS);

        if (clientRequestsOurOptions(request)) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return new BasicClassicHttpResponse(HttpStatus.SC_NOT_IMPLEMENTED);
        }

        final SimpleHttpResponse fatalErrorResponse = getFatallyNoncompliantResponse(request, context);
        if (fatalErrorResponse != null) {
            return convert(fatalErrorResponse, scope);
        }

        requestCompliance.makeRequestCompliant(request);
        request.addHeader("Via",via);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            log.debug("Request is not servable from cache");
            responseCache.flushCacheEntriesInvalidatedByRequest(target, request);
            return callBackend(target, request, scope, chain);
        }

        final HttpCacheEntry entry = responseCache.getCacheEntry(target, request);
        if (entry == null) {
            log.debug("Cache miss");
            return handleCacheMiss(target, request, scope, chain);
        } else {
            return handleCacheHit(target, request, scope, chain, entry);
        }
    }

    private static ClassicHttpResponse convert(final SimpleHttpResponse cacheResponse, final ExecChain.Scope scope) {
        if (cacheResponse == null) {
            return null;
        }
        final ClassicHttpResponse response = new BasicClassicHttpResponse(cacheResponse.getCode(), cacheResponse.getReasonPhrase());
        for (final Iterator<Header> it = cacheResponse.headerIterator(); it.hasNext(); ) {
            response.addHeader(it.next());
        }
        response.setVersion(cacheResponse.getVersion() != null ? cacheResponse.getVersion() : HttpVersion.DEFAULT);
        final SimpleBody body = cacheResponse.getBody();
        if (body != null) {
            if (body.isText()) {
                response.setEntity(new StringEntity(body.getBodyText(), body.getContentType()));
            } else {
                response.setEntity(new ByteArrayEntity(body.getBodyBytes(), body.getContentType()));
            }
        }
        scope.clientContext.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        return response;
    }

    ClassicHttpResponse callBackend(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException  {

        final Date requestDate = getCurrentDate();

        log.debug("Calling the backend");
        final ClassicHttpResponse backendResponse = chain.proceed(request, scope);
        try {
            backendResponse.addHeader("Via", generateViaHeader(backendResponse));
            return handleBackendResponse(target, request, scope, requestDate, getCurrentDate(), backendResponse);
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

    private ClassicHttpResponse handleCacheHit(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final HttpCacheEntry entry) throws IOException, HttpException {
        final HttpClientContext context  = scope.clientContext;
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        recordCacheHit(target, request);
        final Date now = getCurrentDate();
        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry, now)) {
            log.debug("Cache hit");
            try {
                return convert(generateCachedResponse(request, context, entry, now), scope);
            } catch (final ResourceIOException ex) {
                recordCacheFailure(target, request);
                if (!mayCallBackend(request)) {
                    return convert(generateGatewayTimeout(context), scope);
                }
                setResponseStatus(scope.clientContext, CacheResponseStatus.FAILURE);
                return chain.proceed(request, scope);
            }
        } else if (!mayCallBackend(request)) {
            log.debug("Cache entry not suitable but only-if-cached requested");
            return convert(generateGatewayTimeout(context), scope);
        } else if (!(entry.getStatus() == HttpStatus.SC_NOT_MODIFIED && !suitabilityChecker.isConditional(request))) {
            log.debug("Revalidating cache entry");
            try {
                if (cacheRevalidator != null
                        && !staleResponseNotAllowed(request, entry, now)
                        && validityPolicy.mayReturnStaleWhileRevalidating(entry, now)) {
                    log.debug("Serving stale with asynchronous revalidation");
                    final String exchangeId = ExecSupport.getNextExchangeId();
                    final ExecChain.Scope fork = new ExecChain.Scope(
                            exchangeId,
                            scope.route,
                            scope.originalRequest,
                            scope.execRuntime.fork(null),
                            HttpClientContext.create());
                    final SimpleHttpResponse response = generateCachedResponse(request, context, entry, now);
                    cacheRevalidator.revalidateCacheEntry(
                            responseCache.generateKey(target, request, entry),
                            new DefaultCacheRevalidator.RevalidationCall() {

                        @Override
                        public ClassicHttpResponse execute() throws HttpException, IOException {
                            return revalidateCacheEntry(target, request, fork, chain, entry);
                        }

                    });
                    return convert(response, scope);
                }
                return revalidateCacheEntry(target, request, scope, chain, entry);
            } catch (final IOException ioex) {
                return convert(handleRevalidationFailure(request, context, entry, now), scope);
            }
        } else {
            log.debug("Cache entry not usable; calling backend");
            return callBackend(target, request, scope, chain);
        }
    }

    ClassicHttpResponse revalidateCacheEntry(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final HttpCacheEntry cacheEntry) throws IOException, HttpException {
        Date requestDate = getCurrentDate();
        final ClassicHttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(
                scope.originalRequest, cacheEntry);

        ClassicHttpResponse backendResponse = chain.proceed(conditionalRequest, scope);
        try {
            Date responseDate = getCurrentDate();

            if (revalidationResponseIsTooOld(backendResponse, cacheEntry)) {
                backendResponse.close();
                final ClassicHttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                        scope.originalRequest);
                requestDate = getCurrentDate();
                backendResponse = chain.proceed(unconditional, scope);
                responseDate = getCurrentDate();
            }

            backendResponse.addHeader(HeaderConstants.VIA, generateViaHeader(backendResponse));

            final int statusCode = backendResponse.getCode();
            if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
                recordCacheUpdate(scope.clientContext);
            }

            if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                final HttpCacheEntry updatedEntry = responseCache.updateCacheEntry(
                        target, request, cacheEntry, backendResponse, requestDate, responseDate);
                if (suitabilityChecker.isConditional(request)
                        && suitabilityChecker.allConditionalsMatch(request, updatedEntry, new Date())) {
                    return convert(responseGenerator.generateNotModifiedResponse(updatedEntry), scope);
                }
                return convert(responseGenerator.generateResponse(request, updatedEntry), scope);
            }

            if (staleIfErrorAppliesTo(statusCode)
                    && !staleResponseNotAllowed(request, cacheEntry, getCurrentDate())
                    && validityPolicy.mayReturnStaleIfError(request, cacheEntry, responseDate)) {
                try {
                    final SimpleHttpResponse cachedResponse = responseGenerator.generateResponse(request, cacheEntry);
                    cachedResponse.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
                    return convert(cachedResponse, scope);
                } finally {
                    backendResponse.close();
                }
            }
            return handleBackendResponse(target, conditionalRequest, scope, requestDate, responseDate, backendResponse);
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

    ClassicHttpResponse handleBackendResponse(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final Date requestDate,
            final Date responseDate,
            final ClassicHttpResponse backendResponse) throws IOException {

        responseCompliance.ensureProtocolCompliance(scope.originalRequest, request, backendResponse);

        responseCache.flushCacheEntriesInvalidatedByExchange(target, request, backendResponse);
        final boolean cacheable = responseCachingPolicy.isResponseCacheable(request, backendResponse);
        if (cacheable) {
            storeRequestIfModifiedSinceFor304Response(request, backendResponse);
            return cacheAndReturnResponse(target, request, backendResponse, scope, requestDate, responseDate);
        }
        log.debug("Backend response is not cacheable");
        responseCache.flushCacheEntriesFor(target, request);
        return backendResponse;
    }

    ClassicHttpResponse cacheAndReturnResponse(
            final HttpHost target,
            final HttpRequest request,
            final ClassicHttpResponse backendResponse,
            final ExecChain.Scope scope,
            final Date requestSent,
            final Date responseReceived) throws IOException {
        log.debug("Caching backend response");
        final ByteArrayBuffer buf;
        final HttpEntity entity = backendResponse.getEntity();
        if (entity != null) {
            buf = new ByteArrayBuffer(1024);
            final InputStream inStream = entity.getContent();
            final byte[] tmp = new byte[2048];
            long total = 0;
            int l;
            while ((l = inStream.read(tmp)) != -1) {
                buf.append(tmp, 0, l);
                total += l;
                if (total > cacheConfig.getMaxObjectSize()) {
                    log.debug("Backend response content length exceeds maximum");
                    backendResponse.setEntity(new CombinedEntity(entity, buf));
                    return backendResponse;
                }
            }
        } else {
            buf = null;
        }
        backendResponse.close();

        final HttpCacheEntry cacheEntry;
        if (cacheConfig.isFreshnessCheckEnabled()) {
            final HttpCacheEntry existingEntry = responseCache.getCacheEntry(target, request);
            if (DateUtils.isAfter(existingEntry, backendResponse, HttpHeaders.DATE)) {
                log.debug("Backend already contains fresher cache entry");
                cacheEntry = existingEntry;
            } else {
                cacheEntry = responseCache.createCacheEntry(target, request, backendResponse, buf, requestSent, responseReceived);
                log.debug("Backend response successfully cached");
            }
        } else {
            cacheEntry = responseCache.createCacheEntry(target, request, backendResponse, buf, requestSent, responseReceived);
            log.debug("Backend response successfully cached (freshness check skipped)");
        }
        return convert(responseGenerator.generateResponse(request, cacheEntry), scope);
    }

    private ClassicHttpResponse handleCacheMiss(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        recordCacheMiss(target, request);

        if (!mayCallBackend(request)) {
            return new BasicClassicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
        }

        final Map<String, Variant> variants = responseCache.getVariantCacheEntriesWithEtags(target, request);
        if (variants != null && !variants.isEmpty()) {
            return negotiateResponseFromVariants(target, request, scope, chain, variants);
        }

        return callBackend(target, request, scope, chain);
    }

    ClassicHttpResponse negotiateResponseFromVariants(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final Map<String, Variant> variants) throws IOException, HttpException {
        final ClassicHttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequestFromVariants(request, variants);

        final Date requestDate = getCurrentDate();
        final ClassicHttpResponse backendResponse = chain.proceed(conditionalRequest, scope);
        try {
            final Date responseDate = getCurrentDate();

            backendResponse.addHeader("Via", generateViaHeader(backendResponse));

            if (backendResponse.getCode() != HttpStatus.SC_NOT_MODIFIED) {
                return handleBackendResponse(target, request, scope, requestDate, responseDate, backendResponse);
            }

            final Header resultEtagHeader = backendResponse.getFirstHeader(HeaderConstants.ETAG);
            if (resultEtagHeader == null) {
                log.warn("304 response did not contain ETag");
                EntityUtils.consume(backendResponse.getEntity());
                backendResponse.close();
                return callBackend(target, request, scope, chain);
            }

            final String resultEtag = resultEtagHeader.getValue();
            final Variant matchingVariant = variants.get(resultEtag);
            if (matchingVariant == null) {
                log.debug("304 response did not contain ETag matching one sent in If-None-Match");
                EntityUtils.consume(backendResponse.getEntity());
                backendResponse.close();
                return callBackend(target, request, scope, chain);
            }

            if (revalidationResponseIsTooOld(backendResponse, matchingVariant.getEntry())
                    && (request.getEntity() == null || request.getEntity().isRepeatable())) {
                EntityUtils.consume(backendResponse.getEntity());
                backendResponse.close();
                final ClassicHttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(request);
                return callBackend(target, unconditional, scope, chain);
            }

            recordCacheUpdate(scope.clientContext);

            final HttpCacheEntry responseEntry = responseCache.updateVariantCacheEntry(
                    target, conditionalRequest, backendResponse, matchingVariant, requestDate, responseDate);
            backendResponse.close();
            if (shouldSendNotModifiedResponse(request, responseEntry)) {
                return convert(responseGenerator.generateNotModifiedResponse(responseEntry), scope);
            }
            final SimpleHttpResponse response = responseGenerator.generateResponse(request, responseEntry);
            responseCache.reuseVariantEntryFor(target, request, matchingVariant);
            return convert(response, scope);
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

}
