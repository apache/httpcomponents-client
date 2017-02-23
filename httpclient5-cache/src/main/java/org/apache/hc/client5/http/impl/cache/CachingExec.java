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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.impl.sync.ClientExecChain;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.VersionInfo;

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
@Contract(threading = ThreadingBehavior.SAFE) // So long as the responseCache implementation is threadsafe
public class CachingExec implements ClientExecChain {

    private final static boolean SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS = false;

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheUpdates = new AtomicLong();

    private final Map<ProtocolVersion, String> viaHeaders = new HashMap<>(4);

    private final CacheConfig cacheConfig;
    private final ClientExecChain backend;
    private final HttpCache responseCache;
    private final CacheValidityPolicy validityPolicy;
    private final CachedHttpResponseGenerator responseGenerator;
    private final CacheableRequestPolicy cacheableRequestPolicy;
    private final CachedResponseSuitabilityChecker suitabilityChecker;
    private final ConditionalRequestBuilder conditionalRequestBuilder;
    private final ResponseProtocolCompliance responseCompliance;
    private final RequestProtocolCompliance requestCompliance;
    private final ResponseCachingPolicy responseCachingPolicy;

    private final AsynchronousValidator asynchRevalidator;

    private final Log log = LogFactory.getLog(getClass());

    public CachingExec(
            final ClientExecChain backend,
            final HttpCache cache,
            final CacheConfig config) {
        this(backend, cache, config, null);
    }

    public CachingExec(
            final ClientExecChain backend,
            final HttpCache cache,
            final CacheConfig config,
            final AsynchronousValidator asynchRevalidator) {
        super();
        Args.notNull(backend, "HTTP backend");
        Args.notNull(cache, "HttpCache");
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
        this.backend = backend;
        this.responseCache = cache;
        this.validityPolicy = new CacheValidityPolicy();
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy, this.cacheConfig);
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance(this.cacheConfig.isWeakETagOnPutDeleteAllowed());
        this.responseCachingPolicy = new ResponseCachingPolicy(
                this.cacheConfig.getMaxObjectSize(), this.cacheConfig.isSharedCache(),
                this.cacheConfig.isNeverCacheHTTP10ResponsesWithQuery(), this.cacheConfig.is303CachingEnabled());
        this.asynchRevalidator = asynchRevalidator;
    }

    public CachingExec(
            final ClientExecChain backend,
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheConfig config) {
        this(backend, new BasicHttpCache(resourceFactory, storage, config), config);
    }

    public CachingExec(final ClientExecChain backend) {
        this(backend, new BasicHttpCache(), CacheConfig.DEFAULT);
    }

    CachingExec(
            final ClientExecChain backend,
            final HttpCache responseCache,
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final ConditionalRequestBuilder conditionalRequestBuilder,
            final ResponseProtocolCompliance responseCompliance,
            final RequestProtocolCompliance requestCompliance,
            final CacheConfig config,
            final AsynchronousValidator asynchRevalidator) {
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
        this.backend = backend;
        this.responseCache = responseCache;
        this.validityPolicy = validityPolicy;
        this.responseCachingPolicy = responseCachingPolicy;
        this.responseGenerator = responseGenerator;
        this.cacheableRequestPolicy = cacheableRequestPolicy;
        this.suitabilityChecker = suitabilityChecker;
        this.conditionalRequestBuilder = conditionalRequestBuilder;
        this.responseCompliance = responseCompliance;
        this.requestCompliance = requestCompliance;
        this.asynchRevalidator = asynchRevalidator;
    }

    /**
     * Reports the number of times that the cache successfully responded
     * to an {@link HttpRequest} without contacting the origin server.
     * @return the number of cache hits
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Reports the number of times that the cache contacted the origin
     * server because it had no appropriate response cached.
     * @return the number of cache misses
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Reports the number of times that the cache was able to satisfy
     * a response by revalidating an existing but stale cache entry.
     * @return the number of cache revalidations
     */
    public long getCacheUpdates() {
        return cacheUpdates.get();
    }

    public ClassicHttpResponse execute(final RoutedHttpRequest request) throws IOException, HttpException {
        return execute(request, HttpClientContext.create(), null);
    }

    public ClassicHttpResponse execute(
            final RoutedHttpRequest request,
            final HttpClientContext context) throws IOException, HttpException {
        return execute(request, context, null);
    }

    @Override
    public ClassicHttpResponse execute(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {

        final HttpHost target = request.getTargetHost();
        final String via = generateViaHeader(request.getOriginal());

        // default response context
        setResponseStatus(context, CacheResponseStatus.CACHE_MISS);

        if (clientRequestsOurOptions(request)) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return new OptionsHttp11Response();
        }

        final ClassicHttpResponse fatalErrorResponse = getFatallyNoncompliantResponse(request, context);
        if (fatalErrorResponse != null) {
            return fatalErrorResponse;
        }

        requestCompliance.makeRequestCompliant(request);
        request.addHeader("Via",via);

        flushEntriesInvalidatedByRequest(target, request);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            log.debug("Request is not servable from cache");
            return callBackend(request, context, execAware);
        }

        final HttpCacheEntry entry = satisfyFromCache(target, request);
        if (entry == null) {
            log.debug("Cache miss");
            return handleCacheMiss(request, context, execAware);
        } else {
            return handleCacheHit(request, context, execAware, entry);
        }
    }

    private ClassicHttpResponse handleCacheHit(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware,
            final HttpCacheEntry entry) throws IOException, HttpException {
        final HttpHost target = request.getTargetHost();
        recordCacheHit(target, request);
        ClassicHttpResponse out;
        final Date now = getCurrentDate();
        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry, now)) {
            log.debug("Cache hit");
            out = generateCachedResponse(request, context, entry, now);
        } else if (!mayCallBackend(request)) {
            log.debug("Cache entry not suitable but only-if-cached requested");
            out = generateGatewayTimeout(context);
        } else if (!(entry.getStatus() == HttpStatus.SC_NOT_MODIFIED
                && !suitabilityChecker.isConditional(request))) {
            log.debug("Revalidating cache entry");
            return revalidateCacheEntry(request, context, execAware, entry, now);
        } else {
            log.debug("Cache entry not usable; calling backend");
            return callBackend(request, context, execAware);
        }
        final HttpRoute route = request.getRoute();
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, out);
        return out;
    }

    private ClassicHttpResponse revalidateCacheEntry(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware,
            final HttpCacheEntry entry,
            final Date now) throws HttpException {

        try {
            if (asynchRevalidator != null
                && !staleResponseNotAllowed(request, entry, now)
                && validityPolicy.mayReturnStaleWhileRevalidating(entry, now)) {
                log.trace("Serving stale with asynchronous revalidation");
                final ClassicHttpResponse resp = generateCachedResponse(request, context, entry, now);
                asynchRevalidator.revalidateCacheEntry(this, request, context, execAware, entry);
                return resp;
            }
            return revalidateCacheEntry(request, context, execAware, entry);
        } catch (final IOException ioex) {
            return handleRevalidationFailure(request, context, entry, now);
        }
    }

    private ClassicHttpResponse handleCacheMiss(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        final HttpHost target = request.getTargetHost();
        recordCacheMiss(target, request);

        if (!mayCallBackend(request)) {
            return new BasicClassicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
        }

        final Map<String, Variant> variants = getExistingCacheVariants(target, request);
        if (variants != null && !variants.isEmpty()) {
            return negotiateResponseFromVariants(request, context, execAware, variants);
        }

        return callBackend(request, context, execAware);
    }

    private HttpCacheEntry satisfyFromCache(
            final HttpHost target, final RoutedHttpRequest request) {
        HttpCacheEntry entry = null;
        try {
            entry = responseCache.getCacheEntry(target, request);
        } catch (final IOException ioe) {
            log.warn("Unable to retrieve entries from cache", ioe);
        }
        return entry;
    }

    private ClassicHttpResponse getFatallyNoncompliantResponse(
            final RoutedHttpRequest request,
            final HttpContext context) {
        ClassicHttpResponse fatalErrorResponse = null;
        final List<RequestProtocolError> fatalError = requestCompliance.requestIsFatallyNonCompliant(request);

        for (final RequestProtocolError error : fatalError) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            fatalErrorResponse = requestCompliance.getErrorForRequest(error);
        }
        return fatalErrorResponse;
    }

    private Map<String, Variant> getExistingCacheVariants(
            final HttpHost target,
            final RoutedHttpRequest request) {
        Map<String,Variant> variants = null;
        try {
            variants = responseCache.getVariantCacheEntriesWithEtags(target, request);
        } catch (final IOException ioe) {
            log.warn("Unable to retrieve variant entries from cache", ioe);
        }
        return variants;
    }

    private void recordCacheMiss(final HttpHost target, final RoutedHttpRequest request) {
        cacheMisses.getAndIncrement();
        if (log.isTraceEnabled()) {
            log.trace("Cache miss [host: " + target + "; uri: " + request.getRequestUri() + "]");
        }
    }

    private void recordCacheHit(final HttpHost target, final RoutedHttpRequest request) {
        cacheHits.getAndIncrement();
        if (log.isTraceEnabled()) {
            log.trace("Cache hit [host: " + target + "; uri: " + request.getRequestUri() + "]");
        }
    }

    private void recordCacheUpdate(final HttpContext context) {
        cacheUpdates.getAndIncrement();
        setResponseStatus(context, CacheResponseStatus.VALIDATED);
    }

    private void flushEntriesInvalidatedByRequest(
            final HttpHost target,
            final RoutedHttpRequest request) {
        try {
            responseCache.flushInvalidatedCacheEntriesFor(target, request);
        } catch (final IOException ioe) {
            log.warn("Unable to flush invalidated entries from cache", ioe);
        }
    }

    private ClassicHttpResponse generateCachedResponse(final RoutedHttpRequest request,
            final HttpContext context, final HttpCacheEntry entry, final Date now) {
        final ClassicHttpResponse cachedResponse;
        if (request.containsHeader(HeaderConstants.IF_NONE_MATCH)
                || request.containsHeader(HeaderConstants.IF_MODIFIED_SINCE)) {
            cachedResponse = responseGenerator.generateNotModifiedResponse(entry);
        } else {
            cachedResponse = responseGenerator.generateResponse(request, entry);
        }
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        if (validityPolicy.getStalenessSecs(entry, now) > 0L) {
            cachedResponse.addHeader(HeaderConstants.WARNING,"110 localhost \"Response is stale\"");
        }
        return cachedResponse;
    }

    private ClassicHttpResponse handleRevalidationFailure(
            final RoutedHttpRequest request,
            final HttpContext context,
            final HttpCacheEntry entry,
            final Date now) {
        if (staleResponseNotAllowed(request, entry, now)) {
            return generateGatewayTimeout(context);
        } else {
            return unvalidatedCacheHit(request, context, entry);
        }
    }

    private ClassicHttpResponse generateGatewayTimeout(
            final HttpContext context) {
        setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
        return new BasicClassicHttpResponse(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
    }

    private ClassicHttpResponse unvalidatedCacheHit(
            final RoutedHttpRequest request,
            final HttpContext context,
            final HttpCacheEntry entry) {
        final ClassicHttpResponse cachedResponse = responseGenerator.generateResponse(request, entry);
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        cachedResponse.addHeader(HeaderConstants.WARNING, "111 localhost \"Revalidation failed\"");
        return cachedResponse;
    }

    private boolean staleResponseNotAllowed(
            final RoutedHttpRequest request,
            final HttpCacheEntry entry,
            final Date now) {
        return validityPolicy.mustRevalidate(entry)
            || (cacheConfig.isSharedCache() && validityPolicy.proxyRevalidate(entry))
            || explicitFreshnessRequest(request, entry, now);
    }

    private boolean mayCallBackend(final RoutedHttpRequest request) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if ("only-if-cached".equals(elt.getName())) {
                log.trace("Request marked only-if-cached");
                return false;
            }
        }
        return true;
    }

    private boolean explicitFreshnessRequest(
            final RoutedHttpRequest request,
            final HttpCacheEntry entry,
            final Date now) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.CACHE_CONTROL_MAX_STALE.equals(elt.getName())) {
                try {
                    final int maxstale = Integer.parseInt(elt.getValue());
                    final long age = validityPolicy.getCurrentAgeSecs(entry, now);
                    final long lifetime = validityPolicy.getFreshnessLifetimeSecs(entry);
                    if (age - lifetime > maxstale) {
                        return true;
                    }
                } catch (final NumberFormatException nfe) {
                    return true;
                }
            } else if (HeaderConstants.CACHE_CONTROL_MIN_FRESH.equals(elt.getName())
                    || HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName())) {
                return true;
            }
        }
        return false;
    }

    private String generateViaHeader(final HttpMessage msg) {

        final ProtocolVersion pv = msg.getVersion() != null ? msg.getVersion() : HttpVersion.DEFAULT;
        final String existingEntry = viaHeaders.get(pv);
        if (existingEntry != null) {
            return existingEntry;
        }

        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.hc.client5", getClass().getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;

        final String value;
        final int major = pv.getMajor();
        final int minor = pv.getMinor();
        if ("http".equalsIgnoreCase(pv.getProtocol())) {
            value = String.format("%d.%d localhost (Apache-HttpClient/%s (cache))", major, minor,
                    release);
        } else {
            value = String.format("%s/%d.%d localhost (Apache-HttpClient/%s (cache))", pv.getProtocol(), major,
                    minor, release);
        }
        viaHeaders.put(pv, value);

        return value;
    }

    private void setResponseStatus(final HttpContext context, final CacheResponseStatus value) {
        if (context != null) {
            context.setAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS, value);
        }
    }

    /**
     * Reports whether this {@code CachingHttpClient} implementation
     * supports byte-range requests as specified by the {@code Range}
     * and {@code Content-Range} headers.
     * @return {@code true} if byte-range requests are supported
     */
    public boolean supportsRangeAndContentRangeHeaders() {
        return SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS;
    }

    Date getCurrentDate() {
        return new Date();
    }

    boolean clientRequestsOurOptions(final HttpRequest request) {
        if (!HeaderConstants.OPTIONS_METHOD.equals(request.getMethod())) {
            return false;
        }

        if (!"*".equals(request.getRequestUri())) {
            return false;
        }

        if (!"0".equals(request.getFirstHeader(HeaderConstants.MAX_FORWARDS).getValue())) {
            return false;
        }

        return true;
    }

    ClassicHttpResponse callBackend(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException  {

        final Date requestDate = getCurrentDate();

        log.trace("Calling the backend");
        final ClassicHttpResponse backendResponse = backend.execute(request, context, execAware);
        try {
            backendResponse.addHeader("Via", generateViaHeader(backendResponse));
            return handleBackendResponse(request, context, requestDate, getCurrentDate(),
                    backendResponse);
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

    private boolean revalidationResponseIsTooOld(final HttpResponse backendResponse,
            final HttpCacheEntry cacheEntry) {
        final Header entryDateHeader = cacheEntry.getFirstHeader(HttpHeaders.DATE);
        final Header responseDateHeader = backendResponse.getFirstHeader(HttpHeaders.DATE);
        if (entryDateHeader != null && responseDateHeader != null) {
            final Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
            final Date respDate = DateUtils.parseDate(responseDateHeader.getValue());
            if (entryDate == null || respDate == null) {
                // either backend response or cached entry did not have a valid
                // Date header, so we can't tell if they are out of order
                // according to the origin clock; thus we can skip the
                // unconditional retry recommended in 13.2.6 of RFC 2616.
                return false;
            }
            if (respDate.before(entryDate)) {
                return true;
            }
        }
        return false;
    }

    ClassicHttpResponse negotiateResponseFromVariants(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware,
            final Map<String, Variant> variants) throws IOException, HttpException {
        final RoutedHttpRequest conditionalRequest = conditionalRequestBuilder
            .buildConditionalRequestFromVariants(request, variants);

        final Date requestDate = getCurrentDate();
        final ClassicHttpResponse backendResponse = backend.execute(conditionalRequest, context, execAware);
        try {
            final Date responseDate = getCurrentDate();

            backendResponse.addHeader("Via", generateViaHeader(backendResponse));

            if (backendResponse.getCode() != HttpStatus.SC_NOT_MODIFIED) {
                return handleBackendResponse(request, context, requestDate, responseDate,
                        backendResponse);
            }

            final Header resultEtagHeader = backendResponse.getFirstHeader(HeaderConstants.ETAG);
            if (resultEtagHeader == null) {
                log.warn("304 response did not contain ETag");
                IOUtils.consume(backendResponse.getEntity());
                backendResponse.close();
                return callBackend(request, context, execAware);
            }

            final String resultEtag = resultEtagHeader.getValue();
            final Variant matchingVariant = variants.get(resultEtag);
            if (matchingVariant == null) {
                log.debug("304 response did not contain ETag matching one sent in If-None-Match");
                IOUtils.consume(backendResponse.getEntity());
                backendResponse.close();
                return callBackend(request, context, execAware);
            }

            final HttpCacheEntry matchedEntry = matchingVariant.getEntry();

            if (revalidationResponseIsTooOld(backendResponse, matchedEntry)) {
                IOUtils.consume(backendResponse.getEntity());
                backendResponse.close();
                return retryRequestUnconditionally(request, context, execAware);
            }

            recordCacheUpdate(context);

            final HttpCacheEntry responseEntry = getUpdatedVariantEntry(
                request.getTargetHost(), conditionalRequest, requestDate, responseDate,
                    backendResponse, matchingVariant, matchedEntry);
            backendResponse.close();

            final ClassicHttpResponse resp = responseGenerator.generateResponse(request, responseEntry);
            tryToUpdateVariantMap(request.getTargetHost(), request, matchingVariant);

            if (shouldSendNotModifiedResponse(request, responseEntry)) {
                return responseGenerator.generateNotModifiedResponse(responseEntry);
            }
            return resp;
        } catch (final IOException | RuntimeException ex) {
            backendResponse.close();
            throw ex;
        }
    }

    private ClassicHttpResponse retryRequestUnconditionally(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        final RoutedHttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(request);
        return callBackend(unconditional, context, execAware);
    }

    private HttpCacheEntry getUpdatedVariantEntry(
            final HttpHost target,
            final RoutedHttpRequest conditionalRequest,
            final Date requestDate,
            final Date responseDate,
            final ClassicHttpResponse backendResponse,
            final Variant matchingVariant,
            final HttpCacheEntry matchedEntry) throws IOException {
        HttpCacheEntry responseEntry = matchedEntry;
        try {
            responseEntry = responseCache.updateVariantCacheEntry(target, conditionalRequest,
                    matchedEntry, backendResponse, requestDate, responseDate, matchingVariant.getCacheKey());
        } catch (final IOException ioe) {
            log.warn("Could not processChallenge cache entry", ioe);
        } finally {
            backendResponse.close();
        }
        return responseEntry;
    }

    private void tryToUpdateVariantMap(
            final HttpHost target,
            final RoutedHttpRequest request,
            final Variant matchingVariant) {
        try {
            responseCache.reuseVariantEntryFor(target, request, matchingVariant);
        } catch (final IOException ioe) {
            log.warn("Could not processChallenge cache entry to reuse variant", ioe);
        }
    }

    private boolean shouldSendNotModifiedResponse(
            final RoutedHttpRequest request,
            final HttpCacheEntry responseEntry) {
        return (suitabilityChecker.isConditional(request)
                && suitabilityChecker.allConditionalsMatch(request, responseEntry, new Date()));
    }

    ClassicHttpResponse revalidateCacheEntry(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final HttpExecutionAware execAware,
            final HttpCacheEntry cacheEntry) throws IOException, HttpException {

        final RoutedHttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(request, cacheEntry);

        Date requestDate = getCurrentDate();
        ClassicHttpResponse backendResponse = backend.execute(conditionalRequest, context, execAware);
        Date responseDate = getCurrentDate();

        if (revalidationResponseIsTooOld(backendResponse, cacheEntry)) {
            backendResponse.close();
            final RoutedHttpRequest unconditional = conditionalRequestBuilder.buildUnconditionalRequest(request);
            requestDate = getCurrentDate();
            backendResponse = backend.execute(unconditional, context, execAware);
            responseDate = getCurrentDate();
        }

        backendResponse.addHeader(HeaderConstants.VIA, generateViaHeader(backendResponse));

        final int statusCode = backendResponse.getCode();
        if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
            recordCacheUpdate(context);
        }

        if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
            final HttpCacheEntry updatedEntry = responseCache.updateCacheEntry(
                    request.getTargetHost(), request, cacheEntry,
                    backendResponse, requestDate, responseDate);
            if (suitabilityChecker.isConditional(request)
                    && suitabilityChecker.allConditionalsMatch(request, updatedEntry, new Date())) {
                return responseGenerator
                        .generateNotModifiedResponse(updatedEntry);
            }
            return responseGenerator.generateResponse(request, updatedEntry);
        }

        if (staleIfErrorAppliesTo(statusCode)
            && !staleResponseNotAllowed(request, cacheEntry, getCurrentDate())
            && validityPolicy.mayReturnStaleIfError(request, cacheEntry, responseDate)) {
            try {
                final ClassicHttpResponse cachedResponse = responseGenerator.generateResponse(request, cacheEntry);
                cachedResponse.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
                return cachedResponse;
            } finally {
                backendResponse.close();
            }
        }
        return handleBackendResponse(conditionalRequest, context, requestDate, responseDate,
                backendResponse);
    }

    private boolean staleIfErrorAppliesTo(final int statusCode) {
        return statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR
                || statusCode == HttpStatus.SC_BAD_GATEWAY
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE
                || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT;
    }

    ClassicHttpResponse handleBackendResponse(
            final RoutedHttpRequest request,
            final HttpClientContext context,
            final Date requestDate,
            final Date responseDate,
            final ClassicHttpResponse backendResponse) throws IOException {

        log.trace("Handling Backend response");
        responseCompliance.ensureProtocolCompliance(request, backendResponse);

        final HttpHost target = request.getTargetHost();
        final boolean cacheable = responseCachingPolicy.isResponseCacheable(request, backendResponse);
        responseCache.flushInvalidatedCacheEntriesFor(target, request, backendResponse);
        if (cacheable && !alreadyHaveNewerCacheEntry(target, request, backendResponse)) {
            storeRequestIfModifiedSinceFor304Response(request, backendResponse);
            return responseCache.cacheAndReturnResponse(target, request, backendResponse, requestDate, responseDate);
        }
        if (!cacheable) {
            try {
                responseCache.flushCacheEntriesFor(target, request);
            } catch (final IOException ioe) {
                log.warn("Unable to flush invalid cache entries", ioe);
            }
        }
        return backendResponse;
    }

    /**
     * For 304 Not modified responses, adds a "Last-Modified" header with the
     * value of the "If-Modified-Since" header passed in the request. This
     * header is required to be able to reuse match the cache entry for
     * subsequent requests but as defined in http specifications it is not
     * included in 304 responses by backend servers. This header will not be
     * included in the resulting response.
     */
    private void storeRequestIfModifiedSinceFor304Response(
            final HttpRequest request, final HttpResponse backendResponse) {
        if (backendResponse.getCode() == HttpStatus.SC_NOT_MODIFIED) {
            final Header h = request.getFirstHeader("If-Modified-Since");
            if (h != null) {
                backendResponse.addHeader("Last-Modified", h.getValue());
            }
        }
    }

    private boolean alreadyHaveNewerCacheEntry(final HttpHost target, final RoutedHttpRequest request,
            final HttpResponse backendResponse) {
        HttpCacheEntry existing = null;
        try {
            existing = responseCache.getCacheEntry(target, request);
        } catch (final IOException ioe) {
            // nop
        }
        if (existing == null) {
            return false;
        }
        final Header entryDateHeader = existing.getFirstHeader(HttpHeaders.DATE);
        if (entryDateHeader == null) {
            return false;
        }
        final Header responseDateHeader = backendResponse.getFirstHeader(HttpHeaders.DATE);
        if (responseDateHeader == null) {
            return false;
        }
        final Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
        final Date responseDate = DateUtils.parseDate(responseDateHeader.getValue());
        if (entryDate == null || responseDate == null) {
            return false;
        }
        return responseDate.before(entryDate);
    }

}
