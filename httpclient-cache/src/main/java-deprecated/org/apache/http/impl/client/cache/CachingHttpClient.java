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
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
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
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.VersionInfo;

/**
 * <p>
 * The {@link CachingHttpClient} is meant to be a drop-in replacement for
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
 * CachingHttpClient} from it.
 * </p>
 *
 * <p>
 * Generally speaking, the {@code CachingHttpClient} is implemented as a
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
 * anything.
 * </p>
 *
 * <p>
 * Folks that would like to experiment with alternative storage backends
 * should look at the {@link HttpCacheStorage} interface and the related
 * package documentation there. You may also be interested in the provided
 * {@link org.apache.http.impl.client.cache.ehcache.EhcacheHttpCacheStorage
 * EhCache} and {@link
 * org.apache.http.impl.client.cache.memcached.MemcachedHttpCacheStorage
 * memcached} storage backends.
 * </p>
 * @since 4.1
 *
 * @deprecated (4.3) use {@link CachingHttpClientBuilder} or {@link CachingHttpClients}.
 */
@Deprecated
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

    private final Map<ProtocolVersion, String> viaHeaders = new HashMap<ProtocolVersion, String>(4);

    private final HttpClient backend;
    private final HttpCache responseCache;
    private final CacheValidityPolicy validityPolicy;
    private final ResponseCachingPolicy responseCachingPolicy;
    private final CachedHttpResponseGenerator responseGenerator;
    private final CacheableRequestPolicy cacheableRequestPolicy;
    private final CachedResponseSuitabilityChecker suitabilityChecker;

    private final ConditionalRequestBuilder conditionalRequestBuilder;

    private final long maxObjectSizeBytes;
    private final boolean sharedCache;

    private final ResponseProtocolCompliance responseCompliance;
    private final RequestProtocolCompliance requestCompliance;

    private final AsynchronousValidator asynchRevalidator;

    private final Log log = LogFactory.getLog(getClass());

    CachingHttpClient(
            final HttpClient client,
            final HttpCache cache,
            final CacheConfig config) {
        super();
        Args.notNull(client, "HttpClient");
        Args.notNull(cache, "HttpCache");
        Args.notNull(config, "CacheConfig");
        this.maxObjectSizeBytes = config.getMaxObjectSize();
        this.sharedCache = config.isSharedCache();
        this.backend = client;
        this.responseCache = cache;
        this.validityPolicy = new CacheValidityPolicy();
        this.responseCachingPolicy = new ResponseCachingPolicy(maxObjectSizeBytes, sharedCache,
                config.isNeverCacheHTTP10ResponsesWithQuery(), config.is303CachingEnabled());
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy, config);
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();

        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance(config.isWeakETagOnPutDeleteAllowed());

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
    public CachingHttpClient(final CacheConfig config) {
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
    public CachingHttpClient(final HttpClient client) {
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
    public CachingHttpClient(final HttpClient client, final CacheConfig config) {
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
            final HttpClient client,
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheConfig config) {
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
            final HttpClient client,
            final HttpCacheStorage storage,
            final CacheConfig config) {
        this(client,
                new BasicHttpCache(new HeapResourceFactory(), storage, config),
                config);
    }

    CachingHttpClient(
            final HttpClient backend,
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final HttpCache responseCache,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final ConditionalRequestBuilder conditionalRequestBuilder,
            final ResponseProtocolCompliance responseCompliance,
            final RequestProtocolCompliance requestCompliance) {
        final CacheConfig config = new CacheConfig();
        this.maxObjectSizeBytes = config.getMaxObjectSize();
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
            final CacheConfig config) {
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

    public HttpResponse execute(final HttpHost target, final HttpRequest request) throws IOException {
        final HttpContext defaultContext = null;
        return execute(target, request, defaultContext);
    }

    public <T> T execute(final HttpHost target, final HttpRequest request,
                         final ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, responseHandler, null);
    }

    public <T> T execute(final HttpHost target, final HttpRequest request,
                         final ResponseHandler<? extends T> responseHandler, final HttpContext context) throws IOException {
        final HttpResponse resp = execute(target, request, context);
        return handleAndConsume(responseHandler,resp);
    }

    public HttpResponse execute(final HttpUriRequest request) throws IOException {
        final HttpContext context = null;
        return execute(request, context);
    }

    public HttpResponse execute(final HttpUriRequest request, final HttpContext context) throws IOException {
        final URI uri = request.getURI();
        final HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        return execute(httpHost, request, context);
    }

    public <T> T execute(final HttpUriRequest request, final ResponseHandler<? extends T> responseHandler)
            throws IOException {
        return execute(request, responseHandler, null);
    }

    public <T> T execute(final HttpUriRequest request, final ResponseHandler<? extends T> responseHandler,
                         final HttpContext context) throws IOException {
        final HttpResponse resp = execute(request, context);
        return handleAndConsume(responseHandler, resp);
    }

    private <T> T handleAndConsume(
            final ResponseHandler<? extends T> responseHandler,
            final HttpResponse response) throws Error, IOException {
        T result;
        try {
            result = responseHandler.handleResponse(response);
        } catch (final Exception t) {
            final HttpEntity entity = response.getEntity();
            try {
                IOUtils.consume(entity);
            } catch (final Exception t2) {
                // Log this exception. The original exception is more
                // important and will be thrown to the caller.
                this.log.warn("Error consuming content after an exception.", t2);
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new UndeclaredThrowableException(t);
        }

        // Handling the response was successful. Ensure that the content has
        // been fully consumed.
        final HttpEntity entity = response.getEntity();
        IOUtils.consume(entity);
        return result;
    }

    public ClientConnectionManager getConnectionManager() {
        return backend.getConnectionManager();
    }

    public HttpParams getParams() {
        return backend.getParams();
    }

    public HttpResponse execute(final HttpHost target, final HttpRequest originalRequest, final HttpContext context)
            throws IOException {

        HttpRequestWrapper request;
        if (originalRequest instanceof HttpRequestWrapper) {
            request = ((HttpRequestWrapper) originalRequest);
        } else {
            request = HttpRequestWrapper.wrap(originalRequest);
        }
        final String via = generateViaHeader(originalRequest);

        // default response context
        setResponseStatus(context, CacheResponseStatus.CACHE_MISS);

        if (clientRequestsOurOptions(request)) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return new OptionsHttp11Response();
        }

        final HttpResponse fatalErrorResponse = getFatallyNoncompliantResponse(
                request, context);
        if (fatalErrorResponse != null) {
            return fatalErrorResponse;
        }

        requestCompliance.makeRequestCompliant(request);
        request.addHeader("Via",via);

        flushEntriesInvalidatedByRequest(target, request);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            log.debug("Request is not servable from cache");
            return callBackend(target, request, context);
        }

        final HttpCacheEntry entry = satisfyFromCache(target, request);
        if (entry == null) {
            log.debug("Cache miss");
            return handleCacheMiss(target, request, context);
        }

        return handleCacheHit(target, request, context, entry);
    }

    private HttpResponse handleCacheHit(final HttpHost target, final HttpRequestWrapper request,
            final HttpContext context, final HttpCacheEntry entry)
            throws ClientProtocolException, IOException {
        recordCacheHit(target, request);
        HttpResponse out = null;
        final Date now = getCurrentDate();
        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry, now)) {
            log.debug("Cache hit");
            out = generateCachedResponse(request, context, entry, now);
        } else if (!mayCallBackend(request)) {
            log.debug("Cache entry not suitable but only-if-cached requested");
            out = generateGatewayTimeout(context);
        } else {
            log.debug("Revalidating cache entry");
            return revalidateCacheEntry(target, request, context, entry, now);
        }
        if (context != null) {
            context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, target);
            context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
            context.setAttribute(ExecutionContext.HTTP_RESPONSE, out);
            context.setAttribute(ExecutionContext.HTTP_REQ_SENT, Boolean.TRUE);
        }
        return out;
    }

    private HttpResponse revalidateCacheEntry(final HttpHost target,
            final HttpRequestWrapper request, final HttpContext context, final HttpCacheEntry entry,
            final Date now) throws ClientProtocolException {

        try {
            if (asynchRevalidator != null
                && !staleResponseNotAllowed(request, entry, now)
                && validityPolicy.mayReturnStaleWhileRevalidating(entry, now)) {
                log.trace("Serving stale with asynchronous revalidation");
                final HttpResponse resp = generateCachedResponse(request, context, entry, now);

                asynchRevalidator.revalidateCacheEntry(target, request, context, entry);

                return resp;
            }
            return revalidateCacheEntry(target, request, context, entry);
        } catch (final IOException ioex) {
            return handleRevalidationFailure(request, context, entry, now);
        } catch (final ProtocolException e) {
            throw new ClientProtocolException(e);
        }
    }

    private HttpResponse handleCacheMiss(final HttpHost target, final HttpRequestWrapper request,
            final HttpContext context) throws IOException {
        recordCacheMiss(target, request);

        if (!mayCallBackend(request)) {
            return new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_GATEWAY_TIMEOUT,
                    "Gateway Timeout");
        }

        final Map<String, Variant> variants =
            getExistingCacheVariants(target, request);
        if (variants != null && variants.size() > 0) {
            return negotiateResponseFromVariants(target, request, context, variants);
        }

        return callBackend(target, request, context);
    }

    private HttpCacheEntry satisfyFromCache(final HttpHost target, final HttpRequestWrapper request) {
        HttpCacheEntry entry = null;
        try {
            entry = responseCache.getCacheEntry(target, request);
        } catch (final IOException ioe) {
            log.warn("Unable to retrieve entries from cache", ioe);
        }
        return entry;
    }

    private HttpResponse getFatallyNoncompliantResponse(final HttpRequestWrapper request,
            final HttpContext context) {
        HttpResponse fatalErrorResponse = null;
        final List<RequestProtocolError> fatalError = requestCompliance.requestIsFatallyNonCompliant(request);

        for (final RequestProtocolError error : fatalError) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            fatalErrorResponse = requestCompliance.getErrorForRequest(error);
        }
        return fatalErrorResponse;
    }

    private Map<String, Variant> getExistingCacheVariants(final HttpHost target,
            final HttpRequestWrapper request) {
        Map<String,Variant> variants = null;
        try {
            variants = responseCache.getVariantCacheEntriesWithEtags(target, request);
        } catch (final IOException ioe) {
            log.warn("Unable to retrieve variant entries from cache", ioe);
        }
        return variants;
    }

    private void recordCacheMiss(final HttpHost target, final HttpRequestWrapper request) {
        cacheMisses.getAndIncrement();
        if (log.isTraceEnabled()) {
            final RequestLine rl = request.getRequestLine();
            log.trace("Cache miss [host: " + target + "; uri: " + rl.getUri() + "]");
        }
    }

    private void recordCacheHit(final HttpHost target, final HttpRequestWrapper request) {
        cacheHits.getAndIncrement();
        if (log.isTraceEnabled()) {
            final RequestLine rl = request.getRequestLine();
            log.trace("Cache hit [host: " + target + "; uri: " + rl.getUri() + "]");
        }
    }

    private void recordCacheUpdate(final HttpContext context) {
        cacheUpdates.getAndIncrement();
        setResponseStatus(context, CacheResponseStatus.VALIDATED);
    }

    private void flushEntriesInvalidatedByRequest(final HttpHost target,
            final HttpRequestWrapper request) {
        try {
            responseCache.flushInvalidatedCacheEntriesFor(target, request);
        } catch (final IOException ioe) {
            log.warn("Unable to flush invalidated entries from cache", ioe);
        }
    }

    private HttpResponse generateCachedResponse(final HttpRequestWrapper request,
            final HttpContext context, final HttpCacheEntry entry, final Date now) {
        final HttpResponse cachedResponse;
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

    private HttpResponse handleRevalidationFailure(final HttpRequestWrapper request,
            final HttpContext context, final HttpCacheEntry entry, final Date now) {
        if (staleResponseNotAllowed(request, entry, now)) {
            return generateGatewayTimeout(context);
        } else {
            return unvalidatedCacheHit(request, context, entry);
        }
    }

    private HttpResponse generateGatewayTimeout(final HttpContext context) {
        setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
        return new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
    }

    private HttpResponse unvalidatedCacheHit(
            final HttpRequestWrapper request,
            final HttpContext context,
            final HttpCacheEntry entry) {
        final HttpResponse cachedResponse = responseGenerator.generateResponse(request, entry);
        setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
        cachedResponse.addHeader(HeaderConstants.WARNING, "111 localhost \"Revalidation failed\"");
        return cachedResponse;
    }

    private boolean staleResponseNotAllowed(final HttpRequestWrapper request,
            final HttpCacheEntry entry, final Date now) {
        return validityPolicy.mustRevalidate(entry)
            || (isSharedCache() && validityPolicy.proxyRevalidate(entry))
            || explicitFreshnessRequest(request, entry, now);
    }

    private boolean mayCallBackend(final HttpRequestWrapper request) {
        for (final Header h: request.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for (final HeaderElement elt : h.getElements()) {
                if ("only-if-cached".equals(elt.getName())) {
                    log.trace("Request marked only-if-cached");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean explicitFreshnessRequest(final HttpRequestWrapper request, final HttpCacheEntry entry, final Date now) {
        for(final Header h : request.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for(final HeaderElement elt : h.getElements()) {
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
        }
        return false;
    }

    private String generateViaHeader(final HttpMessage msg) {

        final ProtocolVersion pv = msg.getProtocolVersion();
        final String existingEntry = viaHeaders.get(pv);
        if (existingEntry != null) {
            return existingEntry;
        }

        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.http.client", getClass().getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;

        String value;
        if ("http".equalsIgnoreCase(pv.getProtocol())) {
            value = String.format("%d.%d localhost (Apache-HttpClient/%s (cache))", pv.getMajor(), pv.getMinor(),
                    release);
        } else {
            value = String.format("%s/%d.%d localhost (Apache-HttpClient/%s (cache))", pv.getProtocol(), pv.getMajor(),
                    pv.getMinor(), release);
        }
        viaHeaders.put(pv, value);

        return value;
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

    boolean clientRequestsOurOptions(final HttpRequest request) {
        final RequestLine line = request.getRequestLine();

        if (!HeaderConstants.OPTIONS_METHOD.equals(line.getMethod())) {
            return false;
        }

        if (!"*".equals(line.getUri())) {
            return false;
        }

        if (!"0".equals(request.getFirstHeader(HeaderConstants.MAX_FORWARDS).getValue())) {
            return false;
        }

        return true;
    }

    HttpResponse callBackend(final HttpHost target, final HttpRequestWrapper request, final HttpContext context)
            throws IOException {

        final Date requestDate = getCurrentDate();

        log.trace("Calling the backend");
        final HttpResponse backendResponse = backend.execute(target, request, context);
        backendResponse.addHeader("Via", generateViaHeader(backendResponse));
        return handleBackendResponse(target, request, requestDate, getCurrentDate(),
                backendResponse);

    }

    private boolean revalidationResponseIsTooOld(final HttpResponse backendResponse,
            final HttpCacheEntry cacheEntry) {
        final Header entryDateHeader = cacheEntry.getFirstHeader(HTTP.DATE_HEADER);
        final Header responseDateHeader = backendResponse.getFirstHeader(HTTP.DATE_HEADER);
        if (entryDateHeader != null && responseDateHeader != null) {
            try {
                final Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
                final Date respDate = DateUtils.parseDate(responseDateHeader.getValue());
                if (respDate.before(entryDate)) {
                    return true;
                }
            } catch (final DateParseException e) {
                // either backend response or cached entry did not have a valid
                // Date header, so we can't tell if they are out of order
                // according to the origin clock; thus we can skip the
                // unconditional retry recommended in 13.2.6 of RFC 2616.
            }
        }
        return false;
    }

    HttpResponse negotiateResponseFromVariants(final HttpHost target,
            final HttpRequestWrapper request, final HttpContext context,
            final Map<String, Variant> variants) throws IOException {
        final HttpRequestWrapper conditionalRequest = conditionalRequestBuilder
            .buildConditionalRequestFromVariants(request, variants);

        final Date requestDate = getCurrentDate();
        final HttpResponse backendResponse = backend.execute(target, conditionalRequest, context);
        final Date responseDate = getCurrentDate();

        backendResponse.addHeader("Via", generateViaHeader(backendResponse));

        if (backendResponse.getStatusLine().getStatusCode() != HttpStatus.SC_NOT_MODIFIED) {
            return handleBackendResponse(target, request, requestDate, responseDate, backendResponse);
        }

        final Header resultEtagHeader = backendResponse.getFirstHeader(HeaderConstants.ETAG);
        if (resultEtagHeader == null) {
            log.warn("304 response did not contain ETag");
            return callBackend(target, request, context);
        }

        final String resultEtag = resultEtagHeader.getValue();
        final Variant matchingVariant = variants.get(resultEtag);
        if (matchingVariant == null) {
            log.debug("304 response did not contain ETag matching one sent in If-None-Match");
            return callBackend(target, request, context);
        }

        final HttpCacheEntry matchedEntry = matchingVariant.getEntry();

        if (revalidationResponseIsTooOld(backendResponse, matchedEntry)) {
            IOUtils.consume(backendResponse.getEntity());
            return retryRequestUnconditionally(target, request, context,
                    matchedEntry);
        }

        recordCacheUpdate(context);

        final HttpCacheEntry responseEntry = getUpdatedVariantEntry(target,
                conditionalRequest, requestDate, responseDate, backendResponse,
                matchingVariant, matchedEntry);

        final HttpResponse resp = responseGenerator.generateResponse(request, responseEntry);
        tryToUpdateVariantMap(target, request, matchingVariant);

        if (shouldSendNotModifiedResponse(request, responseEntry)) {
            return responseGenerator.generateNotModifiedResponse(responseEntry);
        }

        return resp;
    }

    private HttpResponse retryRequestUnconditionally(final HttpHost target,
            final HttpRequestWrapper request, final HttpContext context,
            final HttpCacheEntry matchedEntry) throws IOException {
        final HttpRequestWrapper unconditional = conditionalRequestBuilder
            .buildUnconditionalRequest(request, matchedEntry);
        return callBackend(target, unconditional, context);
    }

    private HttpCacheEntry getUpdatedVariantEntry(final HttpHost target,
            final HttpRequestWrapper conditionalRequest, final Date requestDate,
            final Date responseDate, final HttpResponse backendResponse,
            final Variant matchingVariant, final HttpCacheEntry matchedEntry) {
        HttpCacheEntry responseEntry = matchedEntry;
        try {
            responseEntry = responseCache.updateVariantCacheEntry(target, conditionalRequest,
                    matchedEntry, backendResponse, requestDate, responseDate, matchingVariant.getCacheKey());
        } catch (final IOException ioe) {
            log.warn("Could not update cache entry", ioe);
        }
        return responseEntry;
    }

    private void tryToUpdateVariantMap(final HttpHost target, final HttpRequestWrapper request,
            final Variant matchingVariant) {
        try {
            responseCache.reuseVariantEntryFor(target, request, matchingVariant);
        } catch (final IOException ioe) {
            log.warn("Could not update cache entry to reuse variant", ioe);
        }
    }

    private boolean shouldSendNotModifiedResponse(final HttpRequestWrapper request,
            final HttpCacheEntry responseEntry) {
        return (suitabilityChecker.isConditional(request)
                && suitabilityChecker.allConditionalsMatch(request, responseEntry, new Date()));
    }

    HttpResponse revalidateCacheEntry(
            final HttpHost target,
            final HttpRequestWrapper request,
            final HttpContext context,
            final HttpCacheEntry cacheEntry) throws IOException, ProtocolException {

        final HttpRequestWrapper conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(request, cacheEntry);

        Date requestDate = getCurrentDate();
        HttpResponse backendResponse = backend.execute(target, conditionalRequest, context);
        Date responseDate = getCurrentDate();

        if (revalidationResponseIsTooOld(backendResponse, cacheEntry)) {
            IOUtils.consume(backendResponse.getEntity());
            final HttpRequest unconditional = conditionalRequestBuilder
                .buildUnconditionalRequest(request, cacheEntry);
            requestDate = getCurrentDate();
            backendResponse = backend.execute(target, unconditional, context);
            responseDate = getCurrentDate();
        }

        backendResponse.addHeader(HeaderConstants.VIA, generateViaHeader(backendResponse));

        final int statusCode = backendResponse.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
            recordCacheUpdate(context);
        }

        if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
            final HttpCacheEntry updatedEntry = responseCache.updateCacheEntry(target, request, cacheEntry,
                    backendResponse, requestDate, responseDate);
            if (suitabilityChecker.isConditional(request)
                    && suitabilityChecker.allConditionalsMatch(request, updatedEntry, new Date())) {
                return responseGenerator.generateNotModifiedResponse(updatedEntry);
            }
            return responseGenerator.generateResponse(request, updatedEntry);
        }

        if (staleIfErrorAppliesTo(statusCode)
            && !staleResponseNotAllowed(request, cacheEntry, getCurrentDate())
            && validityPolicy.mayReturnStaleIfError(request, cacheEntry, responseDate)) {
            final HttpResponse cachedResponse = responseGenerator.generateResponse(request, cacheEntry);
            cachedResponse.addHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"");
            final HttpEntity errorBody = backendResponse.getEntity();
            if (errorBody != null) {
                IOUtils.consume(errorBody);
            }
            return cachedResponse;
        }

        return handleBackendResponse(target, conditionalRequest, requestDate, responseDate,
                                     backendResponse);
    }

    private boolean staleIfErrorAppliesTo(final int statusCode) {
        return statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR
                || statusCode == HttpStatus.SC_BAD_GATEWAY
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE
                || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT;
    }

    HttpResponse handleBackendResponse(
            final HttpHost target,
            final HttpRequestWrapper request,
            final Date requestDate,
            final Date responseDate,
            final HttpResponse backendResponse) throws IOException {

        log.trace("Handling Backend response");
        responseCompliance.ensureProtocolCompliance(request, backendResponse);

        final boolean cacheable = responseCachingPolicy.isResponseCacheable(request, backendResponse);
        responseCache.flushInvalidatedCacheEntriesFor(target, request, backendResponse);
        if (cacheable &&
            !alreadyHaveNewerCacheEntry(target, request, backendResponse)) {
            try {
                storeRequestIfModifiedSinceFor304Response(request, backendResponse);
                return responseCache.cacheAndReturnResponse(target, request, backendResponse, requestDate,
                        responseDate);
            } catch (final IOException ioe) {
                log.warn("Unable to store entries in cache", ioe);
            }
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
        if (backendResponse.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
            final Header h = request.getFirstHeader("If-Modified-Since");
            if (h != null) {
                backendResponse.addHeader("Last-Modified", h.getValue());
            }
        }
    }

    private boolean alreadyHaveNewerCacheEntry(final HttpHost target, final HttpRequest request,
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
        final Header entryDateHeader = existing.getFirstHeader(HTTP.DATE_HEADER);
        if (entryDateHeader == null) {
            return false;
        }
        final Header responseDateHeader = backendResponse.getFirstHeader(HTTP.DATE_HEADER);
        if (responseDateHeader == null) {
            return false;
        }
        try {
            final Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
            final Date responseDate = DateUtils.parseDate(responseDateHeader.getValue());
            return responseDate.before(entryDate);
        } catch (final DateParseException e) {
            // Empty on Purpose
        }
        return false;
    }

    static class AsynchronousValidator {
        private final CachingHttpClient cachingClient;
        private final ExecutorService executor;
        private final Set<String> queued;
        private final CacheKeyGenerator cacheKeyGenerator;

        private final Log log = LogFactory.getLog(getClass());

        /**
         * Create AsynchronousValidator which will make revalidation requests
         * using the supplied {@link CachingHttpClient}, and
         * a {@link ThreadPoolExecutor} generated according to the thread
         * pool settings provided in the given {@link CacheConfig}.
         * @param cachingClient used to execute asynchronous requests
         * @param config specifies thread pool settings. See
         * {@link CacheConfig#getAsynchronousWorkersMax()},
         * {@link CacheConfig#getAsynchronousWorkersCore()},
         * {@link CacheConfig#getAsynchronousWorkerIdleLifetimeSecs()},
         * and {@link CacheConfig#getRevalidationQueueSize()}.
         */
        public AsynchronousValidator(final CachingHttpClient cachingClient,
                final CacheConfig config) {
            this(cachingClient,
                    new ThreadPoolExecutor(config.getAsynchronousWorkersCore(),
                            config.getAsynchronousWorkersMax(),
                            config.getAsynchronousWorkerIdleLifetimeSecs(),
                            TimeUnit.SECONDS,
                            new ArrayBlockingQueue<Runnable>(config.getRevalidationQueueSize()))
                    );
        }

        /**
         * Create AsynchronousValidator which will make revalidation requests
         * using the supplied {@link CachingHttpClient} and
         * {@link ExecutorService}.
         * @param cachingClient used to execute asynchronous requests
         * @param executor used to manage a thread pool of revalidation workers
         */
        AsynchronousValidator(final CachingHttpClient cachingClient,
                final ExecutorService executor) {
            this.cachingClient = cachingClient;
            this.executor = executor;
            this.queued = new HashSet<String>();
            this.cacheKeyGenerator = new CacheKeyGenerator();
        }

        /**
         * Schedules an asynchronous revalidation
         *
         * @param target
         * @param request
         * @param context
         * @param entry
         */
        public synchronized void revalidateCacheEntry(final HttpHost target,
                final HttpRequestWrapper request, final HttpContext context, final HttpCacheEntry entry) {
            // getVariantURI will fall back on getURI if no variants exist
            final String uri = cacheKeyGenerator.getVariantURI(target, request, entry);

            if (!queued.contains(uri)) {
                final AsynchronousValidationRequest revalidationRequest =
                    new AsynchronousValidationRequest(this, cachingClient, target,
                            request, context, entry, uri);

                try {
                    executor.execute(revalidationRequest);
                    queued.add(uri);
                } catch (final RejectedExecutionException ree) {
                    log.debug("Revalidation for [" + uri + "] not scheduled: " + ree);
                }
            }
        }

        /**
         * Removes an identifier from the internal list of revalidation jobs in
         * progress.  This is meant to be called by
         * {@link AsynchronousValidationRequest#run()} once the revalidation is
         * complete, using the identifier passed in during constructions.
         * @param identifier
         */
        synchronized void markComplete(final String identifier) {
            queued.remove(identifier);
        }

        Set<String> getScheduledIdentifiers() {
            return Collections.unmodifiableSet(queued);
        }

        ExecutorService getExecutor() {
            return executor;
        }
    }

    static class AsynchronousValidationRequest implements Runnable {
        private final AsynchronousValidator parent;
        private final CachingHttpClient cachingClient;
        private final HttpHost target;
        private final HttpRequestWrapper request;
        private final HttpContext context;
        private final HttpCacheEntry cacheEntry;
        private final String identifier;

        private final Log log = LogFactory.getLog(getClass());

        /**
         * Used internally by {@link AsynchronousValidator} to schedule a
         * revalidation.
         * @param cachingClient
         * @param target
         * @param request
         * @param context
         * @param cacheEntry
         * @param bookKeeping
         * @param identifier
         */
        AsynchronousValidationRequest(final AsynchronousValidator parent,
                final CachingHttpClient cachingClient, final HttpHost target,
                final HttpRequestWrapper request, final HttpContext context,
                final HttpCacheEntry cacheEntry,
                final String identifier) {
            this.parent = parent;
            this.cachingClient = cachingClient;
            this.target = target;
            this.request = request;
            this.context = context;
            this.cacheEntry = cacheEntry;
            this.identifier = identifier;
        }

        public void run() {
            try {
                cachingClient.revalidateCacheEntry(target, request, context, cacheEntry);
            } catch (final IOException ioe) {
                log.debug("Asynchronous revalidation failed due to exception: " + ioe);
            } catch (final ProtocolException pe) {
                log.error("ProtocolException thrown during asynchronous revalidation: " + pe);
            } finally {
                parent.markComplete(identifier);
            }
        }

        String getIdentifier() {
            return identifier;
        }

    }

}
