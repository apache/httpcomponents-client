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
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
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

    private static final Logger LOG = LoggerFactory.getLogger(CachingExec.class);

    CachingExec(final HttpCache cache, final DefaultCacheRevalidator cacheRevalidator, final CacheConfig config) {
        super(config);
        this.responseCache = Args.notNull(cache, "Response cache");
        this.cacheRevalidator = cacheRevalidator;
        this.conditionalRequestBuilder = new ConditionalRequestBuilder<>(classicHttpRequest ->
                ClassicRequestBuilder.copy(classicHttpRequest).build());
    }

    CachingExec(
            final HttpCache responseCache,
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final DefaultCacheRevalidator cacheRevalidator,
            final ConditionalRequestBuilder<ClassicHttpRequest> conditionalRequestBuilder,
            final CacheConfig config) {
        super(validityPolicy, responseCachingPolicy, responseGenerator, cacheableRequestPolicy, suitabilityChecker, config);
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
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

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
        final CacheMatch result = responseCache.match(target, request);
        final CacheHit hit = result != null ? result.hit : null;
        final CacheHit root = result != null ? result.root : null;

        request.addHeader(HttpHeaders.VIA, via);

        final RequestCacheControl requestCacheControl = CacheControlHeaderParser.INSTANCE.parse(request);
        if (!cacheableRequestPolicy.isServableFromCache(requestCacheControl, request)) {
            LOG.debug("Request is not servable from cache");
            return callBackend(target, request, scope, chain);
        }


        if (hit == null) {
            LOG.debug("Cache miss");
            return handleCacheMiss(requestCacheControl, root, target, request, scope, chain);
        } else {
            final ResponseCacheControl responseCacheControl = CacheControlHeaderParser.INSTANCE.parse(hit.entry);
            return handleCacheHit(requestCacheControl, responseCacheControl, hit, target, request, scope, chain);
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
            final ContentType contentType = body.getContentType();
            final Header h = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
            final String contentEncoding = h != null ? h.getValue() : null;
            if (body.isText()) {
                response.setEntity(new StringEntity(body.getBodyText(), contentType, contentEncoding, false));
            } else {
                response.setEntity(new ByteArrayEntity(body.getBodyBytes(), contentType, contentEncoding, false));
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

        final Instant requestDate = getCurrentDate();

        LOG.debug("Calling the backend");
        final ClassicHttpResponse backendResponse = chain.proceed(request, scope);
        try {
            backendResponse.addHeader(HttpHeaders.VIA, generateViaHeader(backendResponse));
            return handleBackendResponse(target, request, scope, requestDate, getCurrentDate(), backendResponse);
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

    private ClassicHttpResponse handleCacheHit(
            final RequestCacheControl requestCacheControl,
            final ResponseCacheControl responseCacheControl,
            final CacheHit hit,
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        final HttpClientContext context  = scope.clientContext;
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        recordCacheHit(target, request);
        final Instant now = getCurrentDate();

        if (requestCacheControl.isNoCache()) {
            // Revalidate with the server
            return revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, scope, chain);
        }

        if (suitabilityChecker.canCachedResponseBeUsed(requestCacheControl, responseCacheControl, request, hit.entry, now)) {
            if (responseCachingPolicy.responseContainsNoCacheDirective(responseCacheControl, hit.entry)) {
                // Revalidate with the server due to no-cache directive in response
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Revalidating with server due to no-cache directive in response.");
                }
                return revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, scope, chain);
            }
            LOG.debug("Cache hit");
            try {
                return convert(generateCachedResponse(hit.entry, request, context), scope);
            } catch (final ResourceIOException ex) {
                recordCacheFailure(target, request);
                if (!mayCallBackend(requestCacheControl)) {
                    return convert(generateGatewayTimeout(context), scope);
                }
                setResponseStatus(scope.clientContext, CacheResponseStatus.FAILURE);
                return chain.proceed(request, scope);
            }
        } else if (!mayCallBackend(requestCacheControl)) {
            LOG.debug("Cache entry not suitable but only-if-cached requested");
            return convert(generateGatewayTimeout(context), scope);
        } else if (!(hit.entry.getStatus() == HttpStatus.SC_NOT_MODIFIED && !suitabilityChecker.isConditional(request))) {
            LOG.debug("Revalidating cache entry");
            final boolean staleIfErrorEnabled = responseCachingPolicy.isStaleIfErrorEnabled(responseCacheControl, hit.entry);
            try {
                if (cacheRevalidator != null
                        && !staleResponseNotAllowed(requestCacheControl, responseCacheControl, hit.entry, now)
                        && (validityPolicy.mayReturnStaleWhileRevalidating(responseCacheControl, hit.entry, now) || staleIfErrorEnabled)) {
                    LOG.debug("Serving stale with asynchronous revalidation");
                    final String exchangeId = ExecSupport.getNextExchangeId();
                    context.setExchangeId(exchangeId);
                    final ExecChain.Scope fork = new ExecChain.Scope(
                            exchangeId,
                            scope.route,
                            scope.originalRequest,
                            scope.execRuntime.fork(null),
                            HttpClientContext.create());
                    final SimpleHttpResponse response = generateCachedResponse(hit.entry, request, context);
                    cacheRevalidator.revalidateCacheEntry(
                            hit.getEntryKey(),
                            () -> revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, fork, chain));
                    return convert(response, scope);
                }
                return revalidateCacheEntry(requestCacheControl, responseCacheControl, hit, target, request, scope, chain);
            } catch (final IOException ioex) {
                if (staleIfErrorEnabled) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Serving stale response due to IOException and stale-if-error enabled");
                    }
                    return convert(generateCachedResponse(hit.entry, request, context), scope);
                }
                return convert(handleRevalidationFailure(requestCacheControl, responseCacheControl, hit.entry, request, context, now), scope);
            }
        } else {
            LOG.debug("Cache entry not usable; calling backend");
            return callBackend(target, request, scope, chain);
        }
    }

    ClassicHttpResponse revalidateCacheEntry(
            final RequestCacheControl requestCacheControl,
            final ResponseCacheControl responseCacheControl,
            final CacheHit hit,
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Instant requestDate = getCurrentDate();
        final ClassicHttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(
                responseCacheControl, scope.originalRequest, hit.entry);

        ClassicHttpResponse backendResponse = chain.proceed(conditionalRequest, scope);
        try {
            Instant responseDate = getCurrentDate();

            if (revalidationResponseIsTooOld(backendResponse, hit.entry)) {
                backendResponse.close();
                final ClassicHttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(
                        scope.originalRequest);
                requestDate = getCurrentDate();
                backendResponse = chain.proceed(unconditional, scope);
                responseDate = getCurrentDate();
            }

            backendResponse.addHeader(HttpHeaders.VIA, generateViaHeader(backendResponse));

            final int statusCode = backendResponse.getCode();
            if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
                recordCacheUpdate(scope.clientContext);
            }

            if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                final CacheHit updated = responseCache.update(hit, target, request, backendResponse, requestDate, responseDate);
                if (suitabilityChecker.isConditional(request)
                        && suitabilityChecker.allConditionalsMatch(request, updated.entry, Instant.now())) {
                    return convert(responseGenerator.generateNotModifiedResponse(updated.entry), scope);
                }
                return convert(responseGenerator.generateResponse(request, updated.entry), scope);
            }

            if (staleIfErrorAppliesTo(statusCode)
                    && !staleResponseNotAllowed(requestCacheControl, responseCacheControl, hit.entry, getCurrentDate())
                    && validityPolicy.mayReturnStaleIfError(requestCacheControl, responseCacheControl, hit.entry, responseDate)) {
                try {
                    final SimpleHttpResponse cachedResponse = responseGenerator.generateResponse(request, hit.entry);
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
            final Instant requestDate,
            final Instant responseDate,
            final ClassicHttpResponse backendResponse) throws IOException {

        responseCache.evictInvalidatedEntries(target, request, backendResponse);
        final ResponseCacheControl responseCacheControl = CacheControlHeaderParser.INSTANCE.parse(backendResponse);
        final boolean cacheable = responseCachingPolicy.isResponseCacheable(responseCacheControl, request, backendResponse);
        if (cacheable) {
            storeRequestIfModifiedSinceFor304Response(request, backendResponse);
            return cacheAndReturnResponse(target, request, backendResponse, scope, requestDate, responseDate);
        }
        LOG.debug("Backend response is not cacheable");
        return backendResponse;
    }

    ClassicHttpResponse cacheAndReturnResponse(
            final HttpHost target,
            final HttpRequest request,
            final ClassicHttpResponse backendResponse,
            final ExecChain.Scope scope,
            final Instant requestSent,
            final Instant responseReceived) throws IOException {
        LOG.debug("Caching backend response");

        // handle 304 Not Modified responses
        if (backendResponse.getCode() == HttpStatus.SC_NOT_MODIFIED) {
            final CacheMatch result = responseCache.match(target ,request);
            final CacheHit hit = result != null ? result.hit : null;
            if (hit != null) {
                final CacheHit updated = responseCache.update(
                        hit,
                        target,
                        request,
                        backendResponse,
                        requestSent,
                        responseReceived);
                return convert(responseGenerator.generateResponse(request, updated.entry), scope);
            }
        }

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
                    LOG.debug("Backend response content length exceeds maximum");
                    backendResponse.setEntity(new CombinedEntity(entity, buf));
                    return backendResponse;
                }
            }
        } else {
            buf = null;
        }
        backendResponse.close();

        CacheHit hit;
        if (cacheConfig.isFreshnessCheckEnabled()) {
            final CacheMatch result = responseCache.match(target ,request);
            hit = result != null ? result.hit : null;
            if (DateSupport.isAfter(hit != null ? hit.entry : null, backendResponse, HttpHeaders.DATE)) {
                LOG.debug("Backend already contains fresher cache entry");
            } else {
                hit = responseCache.store(target, request, backendResponse, buf, requestSent, responseReceived);
                LOG.debug("Backend response successfully cached");
            }
        } else {
            hit = responseCache.store(target, request, backendResponse, buf, requestSent, responseReceived);
            LOG.debug("Backend response successfully cached (freshness check skipped)");
        }
        return convert(responseGenerator.generateResponse(request, hit.entry), scope);
    }

    private ClassicHttpResponse handleCacheMiss(
            final RequestCacheControl requestCacheControl,
            final CacheHit partialMatch,
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        recordCacheMiss(target, request);

        if (!mayCallBackend(requestCacheControl)) {
            return new BasicClassicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
        }
        if (partialMatch != null && partialMatch.entry.hasVariants()) {
            final List<CacheHit> variants = responseCache.getVariants(partialMatch);
            if (variants != null && !variants.isEmpty()) {
                return negotiateResponseFromVariants(target, request, scope, chain, variants);
            }
        }

        return callBackend(target, request, scope, chain);
    }

    ClassicHttpResponse negotiateResponseFromVariants(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final List<CacheHit> variants) throws IOException, HttpException {
        final Map<String, CacheHit> variantMap = new HashMap<>();
        for (final CacheHit variant : variants) {
            final Header header = variant.entry.getFirstHeader(HttpHeaders.ETAG);
            if (header != null) {
                variantMap.put(header.getValue(), variant);
            }
        }

        final ClassicHttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequestFromVariants(request, variantMap.keySet());

        final Instant requestDate = getCurrentDate();
        final ClassicHttpResponse backendResponse = chain.proceed(conditionalRequest, scope);
        try {
            final Instant responseDate = getCurrentDate();

            backendResponse.addHeader(HttpHeaders.VIA, generateViaHeader(backendResponse));

            if (backendResponse.getCode() != HttpStatus.SC_NOT_MODIFIED) {
                return handleBackendResponse(target, request, scope, requestDate, responseDate, backendResponse);
            } else {
                // 304 response are not expected to have an enclosed content body, but still
                backendResponse.close();
            }

            final Header resultEtagHeader = backendResponse.getFirstHeader(HttpHeaders.ETAG);
            if (resultEtagHeader == null) {
                LOG.warn("304 response did not contain ETag");
                return callBackend(target, request, scope, chain);
            }

            final String resultEtag = resultEtagHeader.getValue();
            final CacheHit match = variantMap.get(resultEtag);
            if (match == null) {
                LOG.debug("304 response did not contain ETag matching one sent in If-None-Match");
                return callBackend(target, request, scope, chain);
            }

            if (revalidationResponseIsTooOld(backendResponse, match.entry)
                    && (request.getEntity() == null || request.getEntity().isRepeatable())) {
                final ClassicHttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(request);
                return callBackend(target, unconditional, scope, chain);
            }

            recordCacheUpdate(scope.clientContext);

            final CacheHit hit = responseCache.update(match, backendResponse, requestDate, responseDate);
            if (shouldSendNotModifiedResponse(request, hit.entry)) {
                return convert(responseGenerator.generateNotModifiedResponse(hit.entry), scope);
            }
            final SimpleHttpResponse response = responseGenerator.generateResponse(request, hit.entry);
            responseCache.storeReusing(hit, target, request, backendResponse, requestDate, responseDate);
            return convert(response, scope);
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

}
