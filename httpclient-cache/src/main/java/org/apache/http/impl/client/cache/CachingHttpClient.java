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
package org.apache.http.impl.client.cache;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.VersionInfo;

/**
 * <p>The {@link CachingHttpClient} is meant to be a drop-in replacement for
 * a {@link DefaultHttpClient} that transparently adds client-side caching.
 * The current implementation is conditionally compliant with HTTP/1.1
 * (meaning all the MUST and MUST NOTs are obeyed), although quite a lot,
 * though not all, of the SHOULDs and SHOULD NOTs are obeyed too. Generally
 * speaking, you construct a {@code CachingHttpClient} by providing a
 * "backend" {@link HttpClient} used for making actual network requests and
 * provide an {@link HttpCacheStorage} instance to use for holding onto
 * cached responses. Additional configuration options can be provided by
 * passing in a {@link CacheConfig}. Note that all of the usual client
 * related configuration you want to do vis-a-vis timeouts and connection
 * pools should be done on this backend client before constructing a {@code
 * CachingHttpClient} from it.</p>
 * 
 * <p>Generally speaking, the {@code CachingHttpClient} is implemented as a
 * <a href="http://en.wikipedia.org/wiki/Decorator_pattern">Decorator</a>
 * of the backend client; for any incoming request it attempts to satisfy
 * it from the cache, but if it can't, or if it needs to revalidate a stale
 * cache entry, it will use the backend client to make an actual request.
 * However, a proper HTTP/1.1 cache won't change the semantics of a request
 * and response; in particular, if you issue an unconditional request you
 * will get a full response (although it may be served to you from the cache,
 * or the cache may make a conditional request on your behalf to the origin).
 * This notion of "semantic transparency" means you should be able to drop
 * a {@link CachingHttpClient} into an existing application without breaking
 * anything.</p>
 * 
 * <p>Folks that would like to experiment with alternative storage backends
 * should look at the {@link HttpCacheStorage} interface and the related
 * package documentation there. You may also be interested in the provided
 * {@link org.apache.http.impl.client.cache.ehcache.EhcacheHttpCacheStorage
 * EhCache} and {@link
 * org.apache.http.impl.client.cache.memcached.MemcachedHttpCacheStorage
 * memcached} storage backends.</p>
 * </p>
 * @since 4.1
 */
@ThreadSafe // So long as the responseCache implementation is threadsafe
public class CachingHttpClient implements HttpClient {

    /**
     * This is the name under which the {@link
     * org.apache.http.client.cache.CacheResponseStatus} of a request
     * (for example, whether it resulted in a cache hit) will be recorded if an
     * {@link HttpContext} is provided during execution.
     */
    public static final String CACHE_RESPONSE_STATUS = "http.cache.response.status";

    private final static boolean SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS = false;

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheUpdates = new AtomicLong();

    private final HttpClient backend;
    private final HttpCache responseCache;
    private final CacheValidityPolicy validityPolicy;
    private final ResponseCachingPolicy responseCachingPolicy;
    private final CachedHttpResponseGenerator responseGenerator;
    private final CacheableRequestPolicy cacheableRequestPolicy;
    private final CachedResponseSuitabilityChecker suitabilityChecker;

    private final ConditionalRequestBuilder conditionalRequestBuilder;

    private final int maxObjectSizeBytes;
    private final boolean sharedCache;

    private final ResponseProtocolCompliance responseCompliance;
    private final RequestProtocolCompliance requestCompliance;

    private final AsynchronousValidator asynchRevalidator;
    
    private final Log log = LogFactory.getLog(getClass());

    CachingHttpClient(
            HttpClient client,
            HttpCache cache,
            CacheConfig config) {
        super();
        if (client == null) {
            throw new IllegalArgumentException("HttpClient may not be null");
        }
        if (cache == null) {
            throw new IllegalArgumentException("HttpCache may not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("CacheConfig may not be null");
        }
        this.maxObjectSizeBytes = config.getMaxObjectSizeBytes();
        this.sharedCache = config.isSharedCache();
        this.backend = client;
        this.responseCache = cache;
        this.validityPolicy = new CacheValidityPolicy();
        this.responseCachingPolicy = new ResponseCachingPolicy(maxObjectSizeBytes, sharedCache);
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy, config);
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();

        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance();

        this.asynchRevalidator = makeAsynchronousValidator(config);
    }

    /**
     * Constructs a {@code CachingHttpClient} with default caching settings that
     * stores cache entries in memory and uses a vanilla {@link DefaultHttpClient}
     * for backend requests.
     */
    public CachingHttpClient() {
        this(new DefaultHttpClient(),
                new BasicHttpCache(),
                new CacheConfig());
    }

    /**
     * Constructs a {@code CachingHttpClient} with the given caching options that
     * stores cache entries in memory and uses a vanilla {@link DefaultHttpClient}
     * for backend requests.
     * @param config cache module options
     */
    public CachingHttpClient(CacheConfig config) {
        this(new DefaultHttpClient(),
                new BasicHttpCache(config),
                config);
    }

    /**
     * Constructs a {@code CachingHttpClient} with default caching settings that
     * stores cache entries in memory and uses the given {@link HttpClient}
     * for backend requests.
     * @param client used to make origin requests
     */
    public CachingHttpClient(HttpClient client) {
        this(client,
                new BasicHttpCache(),
                new CacheConfig());
    }

    /**
     * Constructs a {@code CachingHttpClient} with the given caching options that
     * stores cache entries in memory and uses the given {@link HttpClient}
     * for backend requests.
     * @param config cache module options
     * @param client used to make origin requests
     */
    public CachingHttpClient(HttpClient client, CacheConfig config) {
        this(client,
                new BasicHttpCache(config),
                config);
    }

    /**
     * Constructs a {@code CachingHttpClient} with the given caching options
     * that stores cache entries in the provided storage backend and uses
     * the given {@link HttpClient} for backend requests. However, cached
     * response bodies are managed using the given {@link ResourceFactory}.
     * @param client used to make origin requests
     * @param resourceFactory how to manage cached response bodies
     * @param storage where to store cache entries 
     * @param config cache module options
     */
    public CachingHttpClient(
            HttpClient client,
            ResourceFactory resourceFactory,
            HttpCacheStorage storage,
            CacheConfig config) {
        this(client,
                new BasicHttpCache(resourceFactory, storage, config),
                config);
    }

    /**
     * Constructs a {@code CachingHttpClient} with the given caching options
     * that stores cache entries in the provided storage backend and uses
     * the given {@link HttpClient} for backend requests.
     * @param client used to make origin requests
     * @param storage where to store cache entries 
     * @param config cache module options
     */
    public CachingHttpClient(
            HttpClient client,
            HttpCacheStorage storage,
            CacheConfig config) {
        this(client,
                new BasicHttpCache(new HeapResourceFactory(), storage, config),
                config);
    }

    CachingHttpClient(
            HttpClient backend,
            CacheValidityPolicy validityPolicy,
            ResponseCachingPolicy responseCachingPolicy,
            HttpCache responseCache,
            CachedHttpResponseGenerator responseGenerator,
            CacheableRequestPolicy cacheableRequestPolicy,
            CachedResponseSuitabilityChecker suitabilityChecker,
            ConditionalRequestBuilder conditionalRequestBuilder,
            ResponseProtocolCompliance responseCompliance,
            RequestProtocolCompliance requestCompliance) {
        CacheConfig config = new CacheConfig();
        this.maxObjectSizeBytes = config.getMaxObjectSizeBytes();
        this.sharedCache = config.isSharedCache();
        this.backend = backend;
        this.validityPolicy = validityPolicy;
        this.responseCachingPolicy = responseCachingPolicy;
        this.responseCache = responseCache;
        this.responseGenerator = responseGenerator;
        this.cacheableRequestPolicy = cacheableRequestPolicy;
        this.suitabilityChecker = suitabilityChecker;
        this.conditionalRequestBuilder = conditionalRequestBuilder;
        this.responseCompliance = responseCompliance;
        this.requestCompliance = requestCompliance;
        this.asynchRevalidator = makeAsynchronousValidator(config);
    }
    
    private AsynchronousValidator makeAsynchronousValidator(
            CacheConfig config) {
        if (config.getAsynchronousWorkersMax() > 0) {
            return new AsynchronousValidator(this, config);
        }
        return null;
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

    public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException {
        HttpContext defaultContext = null;
        return execute(target, request, defaultContext);
    }

    public <T> T execute(HttpHost target, HttpRequest request,
                         ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, responseHandler, null);
    }

    public <T> T execute(HttpHost target, HttpRequest request,
                         ResponseHandler<? extends T> responseHandler, HttpContext context) throws IOException {
        HttpResponse resp = execute(target, request, context);
        return responseHandler.handleResponse(resp);
    }

    public HttpResponse execute(HttpUriRequest request) throws IOException {
        HttpContext context = null;
        return execute(request, context);
    }

    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException {
        URI uri = request.getURI();
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        return execute(httpHost, request, context);
    }

    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler)
            throws IOException {
        return execute(request, responseHandler, null);
    }

    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler,
                         HttpContext context) throws IOException {
        HttpResponse resp = execute(request, context);
        return responseHandler.handleResponse(resp);
    }

    public ClientConnectionManager getConnectionManager() {
        return backend.getConnectionManager();
    }

    public HttpParams getParams() {
        return backend.getParams();
    }

    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {

        // default response context
        setResponseStatus(context, CacheResponseStatus.CACHE_MISS);

        String via = generateViaHeader(request);

        if (clientRequestsOurOptions(request)) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return new OptionsHttp11Response();
        }

        HttpResponse fatalErrorResponse = getFatallyNoncompliantResponse(
                request, context);
        if (fatalErrorResponse != null) return fatalErrorResponse;

        request = requestCompliance.makeRequestCompliant(request);
        request.addHeader("Via",via);

        flushEntriesInvalidatedByRequest(target, request);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            return callBackend(target, request, context);
        }

        HttpCacheEntry entry = satisfyFromCache(target, request);
        if (entry == null) {
            return handleCacheMiss(target, request, context);
        }

        return handleCacheHit(target, request, context, entry); 
    }

    private HttpResponse handleCacheHit(HttpHost target, HttpRequest request,
            HttpContext context, HttpCacheEntry entry)
            throws ClientProtocolException, IOException {
        recordCacheHit(target, request);

        Date now = getCurrentDate();
        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry, now)) {
            return generateCachedResponse(request, context, entry, now);
        }

        if (!mayCallBackend(request)) {
            return generateGatewayTimeout(context);
        }

        if (validityPolicy.isRevalidatable(entry)) {
            return revalidateCacheEntry(target, request, context, entry, now);
        }
        return callBackend(target, request, context);
    }

    private HttpResponse revalidateCacheEntry(HttpHost target,
            HttpRequest request, HttpContext context, HttpCacheEntry entry,
            Date now) throws ClientProtocolException {
        log.debug("Revalidating the cache entry");

        try {
            if (asynchRevalidator != null
                && !staleResponseNotAllowed(request, entry, now)
                && validityPolicy.mayReturnStaleWhileRevalidating(entry, now)) {
                final HttpResponse resp = responseGenerator.generateResponse(entry);
                resp.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
                
                asynchRevalidator.revalidateCacheEntry(target, request, context, entry);
                
                return resp;
            }
            return revalidateCacheEntry(target, request, context, entry);
        } catch (IOException ioex) {
            return handleRevalidationFailure(request, context, entry, now);
        } catch (ProtocolException e) {
            throw new ClientProtocolException(e);
        }
    }

    private HttpResponse handleCacheMiss(HttpHost target, HttpRequest request,
            HttpContext context) throws IOException {
        recordCacheMiss(target, request);

        if (!mayCallBackend(request)) {
            return new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_GATEWAY_TIMEOUT,
                    "Gateway Timeout");
        }

        Map<String, Variant> variants =
            getExistingCacheVariants(target, request);
        if (variants != null && variants.size() > 0) {
            return negotiateResponseFromVariants(target, request, context, variants);
        }

        return callBackend(target, request, context);
    }

    private HttpCacheEntry satisfyFromCache(HttpHost target, HttpRequest request) {
        HttpCacheEntry entry = null;
        try {
            entry = responseCache.getCacheEntry(target, request);
        } catch (IOException ioe) {
            log.warn("Unable to retrieve entries from cache", ioe);
        }
        return entry;
    }
    
    private HttpResponse getFatallyNoncompliantResponse(HttpRequest request,
            HttpContext context) {
        HttpResponse fatalErrorResponse = null;
        List<RequestProtocolError> fatalError = requestCompliance.requestIsFatallyNonCompliant(request);
        
        for (RequestProtocolError error : fatalError) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            fatalErrorResponse = requestCompliance.getErrorForRequest(error);
        }
        return fatalErrorResponse;
    }

    private Map<String, Variant> getExistingCacheVariants(HttpHost target,
            HttpRequest request) {
        Map<String,Variant> variants = null;
        try {
            variants = responseCache.getVariantCacheEntriesWithEtags(target, request);
        } catch (IOException ioe) {
            log.warn("Unable to retrieve variant entries from cache", ioe);
        }
        return variants;
    }

    private void recordCacheMiss(HttpHost target, HttpRequest request) {
        cacheMisses.getAndIncrement();
        if (log.isDebugEnabled()) {
            RequestLine rl = request.getRequestLine();
            log.debug("Cache miss [host: " + target + "; uri: " + rl.getUri() + "]");
        }
    }

    private void recordCacheHit(HttpHost target, HttpRequest request) {
        cacheHits.getAndIncrement();
        if (log.isDebugEnabled()) {
            RequestLine rl = request.getRequestLine();
            log.debug("Cache hit [host: " + target + "; uri: " + rl.getUri() + "]");
        }
    }
    
    private void recordCacheUpdate(HttpContext context) {
        cacheUpdates.getAndIncrement();
        setResponseStatus(context, CacheResponseStatus.VALIDATED);
    }

    private void flushEntriesInvalidatedByRequest(HttpHost target,
            HttpRequest request) {
        try {
            responseCache.flushInvalidatedCacheEntriesFor(target, request);
        } catch (IOException ioe) {
            log.warn("Unable to flush invalidated entries from cache", ioe);
        }
    }

    private HttpResponse generateCachedResponse(HttpRequest request,
            HttpContext context, HttpCacheEntry entry, Date now) {
        final HttpResponse cachedResponse;
        if (request.containsHeader(HeaderConstants.IF_NONE_MATCH)
                || request.containsHeader(HeaderConstants.IF_MODIFIED_SINCE)) {
            cachedResponse = responseGenerator.generateNotModifiedResponse(entry);
        } else {
            cachedResponse = responseGenerator.generateResponse(entry);
        }
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        if (validityPolicy.getStalenessSecs(entry, now) > 0L) {
            cachedResponse.addHeader("Warning","110 localhost \"Response is stale\"");
        }
        return cachedResponse;
    }

    private HttpResponse handleRevalidationFailure(HttpRequest request,
            HttpContext context, HttpCacheEntry entry, Date now) {
        if (staleResponseNotAllowed(request, entry, now)) {
            return generateGatewayTimeout(context);
        } else {
            return unvalidatedCacheHit(context, entry);
        }
    }

    private HttpResponse generateGatewayTimeout(HttpContext context) {
        setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
        return new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
    }

    private HttpResponse unvalidatedCacheHit(HttpContext context,
            HttpCacheEntry entry) {
        final HttpResponse cachedResponse = responseGenerator.generateResponse(entry);
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        cachedResponse.addHeader(HeaderConstants.WARNING, "111 localhost \"Revalidation failed\"");
        return cachedResponse;
    }

    private boolean staleResponseNotAllowed(HttpRequest request,
            HttpCacheEntry entry, Date now) {
        return validityPolicy.mustRevalidate(entry)
            || (isSharedCache() && validityPolicy.proxyRevalidate(entry))
            || explicitFreshnessRequest(request, entry, now);
    }

    private boolean mayCallBackend(HttpRequest request) {
        for (Header h: request.getHeaders("Cache-Control")) {
            for (HeaderElement elt : h.getElements()) {
                if ("only-if-cached".equals(elt.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean explicitFreshnessRequest(HttpRequest request, HttpCacheEntry entry, Date now) {
        for(Header h : request.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("max-stale".equals(elt.getName())) {
                    try {
                        int maxstale = Integer.parseInt(elt.getValue());
                        long age = validityPolicy.getCurrentAgeSecs(entry, now);
                        long lifetime = validityPolicy.getFreshnessLifetimeSecs(entry);
                        if (age - lifetime > maxstale) return true;
                    } catch (NumberFormatException nfe) {
                        return true;
                    }
                } else if ("min-fresh".equals(elt.getName())
                            || "max-age".equals(elt.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private String generateViaHeader(HttpMessage msg) {
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.http.client", getClass().getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
        final ProtocolVersion pv = msg.getProtocolVersion();
        if ("http".equalsIgnoreCase(pv.getProtocol())) {
            return String.format("%d.%d localhost (Apache-HttpClient/%s (cache))",
                pv.getMajor(), pv.getMinor(), release);
        } else {
            return String.format("%s/%d.%d localhost (Apache-HttpClient/%s (cache))",
                    pv.getProtocol(), pv.getMajor(), pv.getMinor(), release);
        }
    }

    private void setResponseStatus(final HttpContext context, final CacheResponseStatus value) {
        if (context != null) {
            context.setAttribute(CACHE_RESPONSE_STATUS, value);
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

    /**
     * Reports whether this {@code CachingHttpClient} is configured as
     * a shared (public) or non-shared (private) cache. See {@link
     * CacheConfig#setSharedCache(boolean)}.
     * @return {@code true} if we are behaving as a shared (public)
     *   cache
     */
    public boolean isSharedCache() {
        return sharedCache;
    }

    Date getCurrentDate() {
        return new Date();
    }

    boolean clientRequestsOurOptions(HttpRequest request) {
        RequestLine line = request.getRequestLine();

        if (!HeaderConstants.OPTIONS_METHOD.equals(line.getMethod()))
            return false;

        if (!"*".equals(line.getUri()))
            return false;

        if (!"0".equals(request.getFirstHeader(HeaderConstants.MAX_FORWARDS).getValue()))
            return false;

        return true;
    }

    HttpResponse callBackend(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {

        Date requestDate = getCurrentDate();

        log.debug("Calling the backend");
        HttpResponse backendResponse = backend.execute(target, request, context);
        backendResponse.addHeader("Via", generateViaHeader(backendResponse));
        return handleBackendResponse(target, request, requestDate, getCurrentDate(),
                backendResponse);

    }

    private boolean revalidationResponseIsTooOld(HttpResponse backendResponse,
            HttpCacheEntry cacheEntry) {
        final Header entryDateHeader = cacheEntry.getFirstHeader("Date");
        final Header responseDateHeader = backendResponse.getFirstHeader("Date");
        if (entryDateHeader != null && responseDateHeader != null) {
            try {
                Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
                Date respDate = DateUtils.parseDate(responseDateHeader.getValue());
                if (respDate.before(entryDate)) return true;
            } catch (DateParseException e) {
                // either backend response or cached entry did not have a valid
                // Date header, so we can't tell if they are out of order
                // according to the origin clock; thus we can skip the
                // unconditional retry recommended in 13.2.6 of RFC 2616.
            }
        }
        return false;
    }
    
    HttpResponse negotiateResponseFromVariants(HttpHost target,
            HttpRequest request, HttpContext context,
            Map<String, Variant> variants) throws IOException {
        HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequestFromVariants(request, variants);

        Date requestDate = getCurrentDate();
        HttpResponse backendResponse = backend.execute(target, conditionalRequest, context);
        Date responseDate = getCurrentDate();

        backendResponse.addHeader("Via", generateViaHeader(backendResponse));

        if (backendResponse.getStatusLine().getStatusCode() != HttpStatus.SC_NOT_MODIFIED) {
            return handleBackendResponse(target, request, requestDate, responseDate, backendResponse);
        }

        Header resultEtagHeader = backendResponse.getFirstHeader(HeaderConstants.ETAG);
        if (resultEtagHeader == null) {
            log.warn("304 response did not contain ETag");
            return callBackend(target, request, context);
        }

        String resultEtag = resultEtagHeader.getValue();
        Variant matchingVariant = variants.get(resultEtag);
        if (matchingVariant == null) {
            log.debug("304 response did not contain ETag matching one sent in If-None-Match");
            return callBackend(target, request, context);
        }

        HttpCacheEntry matchedEntry = matchingVariant.getEntry();
        
        if (revalidationResponseIsTooOld(backendResponse, matchedEntry)) {
            return retryRequestUnconditionally(target, request, context,
                    matchedEntry);
        }
        
        recordCacheUpdate(context);

        HttpCacheEntry responseEntry = getUpdatedVariantEntry(target,
                conditionalRequest, requestDate, responseDate, backendResponse,
                matchingVariant, matchedEntry);

        HttpResponse resp = responseGenerator.generateResponse(responseEntry);
        tryToUpdateVariantMap(target, request, matchingVariant);

        if (shouldSendNotModifiedResponse(request, responseEntry)) {
            return responseGenerator.generateNotModifiedResponse(responseEntry);
        }

        return resp;
    }

    private HttpResponse retryRequestUnconditionally(HttpHost target,
            HttpRequest request, HttpContext context,
            HttpCacheEntry matchedEntry) throws IOException {
        HttpRequest unconditional = conditionalRequestBuilder
            .buildUnconditionalRequest(request, matchedEntry);
        return callBackend(target, unconditional, context);
    }

    private HttpCacheEntry getUpdatedVariantEntry(HttpHost target,
            HttpRequest conditionalRequest, Date requestDate,
            Date responseDate, HttpResponse backendResponse,
            Variant matchingVariant, HttpCacheEntry matchedEntry) {
        HttpCacheEntry responseEntry = matchedEntry;
        try {
            responseEntry = responseCache.updateVariantCacheEntry(target, conditionalRequest,
                    matchedEntry, backendResponse, requestDate, responseDate, matchingVariant.getCacheKey());
        } catch (IOException ioe) {
            log.warn("Could not update cache entry", ioe);
        }
        return responseEntry;
    }

    private void tryToUpdateVariantMap(HttpHost target, HttpRequest request,
            Variant matchingVariant) {
        try {
            responseCache.reuseVariantEntryFor(target, request, matchingVariant);
        } catch (IOException ioe) {
            log.warn("Could not update cache entry to reuse variant", ioe);
        }
    }

    private boolean shouldSendNotModifiedResponse(HttpRequest request,
            HttpCacheEntry responseEntry) {
        return (suitabilityChecker.isConditional(request)
                && suitabilityChecker.allConditionalsMatch(request, responseEntry, new Date()));
    }

    HttpResponse revalidateCacheEntry(
            HttpHost target,
            HttpRequest request,
            HttpContext context,
            HttpCacheEntry cacheEntry) throws IOException, ProtocolException {

        HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(request, cacheEntry);

        Date requestDate = getCurrentDate();
        HttpResponse backendResponse = backend.execute(target, conditionalRequest, context);
        Date responseDate = getCurrentDate();

        if (revalidationResponseIsTooOld(backendResponse, cacheEntry)) {
            HttpRequest unconditional = conditionalRequestBuilder
                .buildUnconditionalRequest(request, cacheEntry);
            requestDate = getCurrentDate();
            backendResponse = backend.execute(target, unconditional, context);
            responseDate = getCurrentDate();
        }

        backendResponse.addHeader("Via", generateViaHeader(backendResponse));

        int statusCode = backendResponse.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
            recordCacheUpdate(context);
        }

        if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
            HttpCacheEntry updatedEntry = responseCache.updateCacheEntry(target, request, cacheEntry,
                    backendResponse, requestDate, responseDate);
            if (suitabilityChecker.isConditional(request)
                    && suitabilityChecker.allConditionalsMatch(request, updatedEntry, new Date())) {
                return responseGenerator.generateNotModifiedResponse(updatedEntry);
            }
            return responseGenerator.generateResponse(updatedEntry);
        }
        
        if (staleIfErrorAppliesTo(statusCode)
            && !staleResponseNotAllowed(request, cacheEntry, getCurrentDate())
            && validityPolicy.mayReturnStaleIfError(request, cacheEntry, responseDate)) {
            final HttpResponse cachedResponse = responseGenerator.generateResponse(cacheEntry);
            cachedResponse.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
            return cachedResponse; 
        }

        return handleBackendResponse(target, conditionalRequest, requestDate, responseDate,
                                     backendResponse);
    }

    private boolean staleIfErrorAppliesTo(int statusCode) {
        return statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR  
                || statusCode == HttpStatus.SC_BAD_GATEWAY  
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE  
                || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT;
    }

    HttpResponse handleBackendResponse(
            HttpHost target,
            HttpRequest request,
            Date requestDate,
            Date responseDate,
            HttpResponse backendResponse) throws IOException {

        log.debug("Handling Backend response");
        responseCompliance.ensureProtocolCompliance(request, backendResponse);

        boolean cacheable = responseCachingPolicy.isResponseCacheable(request, backendResponse);
        responseCache.flushInvalidatedCacheEntriesFor(target, request, backendResponse);
        if (cacheable &&
            !alreadyHaveNewerCacheEntry(target, request, backendResponse)) {
            try {
                return responseCache.cacheAndReturnResponse(target, request, backendResponse, requestDate,
                        responseDate);
            } catch (IOException ioe) {
                log.warn("Unable to store entries in cache", ioe);
            }
        }
        if (!cacheable) {
            try {
                responseCache.flushCacheEntriesFor(target, request);
            } catch (IOException ioe) {
                log.warn("Unable to flush invalid cache entries", ioe);
            }
        }
        return backendResponse;
    }

    private boolean alreadyHaveNewerCacheEntry(HttpHost target, HttpRequest request,
            HttpResponse backendResponse) {
        HttpCacheEntry existing = null;
        try {
            existing = responseCache.getCacheEntry(target, request);
        } catch (IOException ioe) {
            // nop
        }
        if (existing == null) return false;
        Header entryDateHeader = existing.getFirstHeader("Date");
        if (entryDateHeader == null) return false;
        Header responseDateHeader = backendResponse.getFirstHeader("Date");
        if (responseDateHeader == null) return false;
        try {
            Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
            Date responseDate = DateUtils.parseDate(responseDateHeader.getValue());
            return responseDate.before(entryDate);
        } catch (DateParseException e) {
        }
        return false;
    }

}
