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
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheEntry;
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
 * @since 4.1
 */
@ThreadSafe // So long as the responseCache implementation is threadsafe
public class CachingHttpClient implements HttpClient {

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

    private final Log log = LogFactory.getLog(getClass());

    public CachingHttpClient(
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
    }

    public CachingHttpClient() {
        this(new DefaultHttpClient(),
                new BasicHttpCache(),
                new CacheConfig());
    }

    public CachingHttpClient(CacheConfig config) {
        this(new DefaultHttpClient(),
                new BasicHttpCache(config),
                config);
    }

    public CachingHttpClient(HttpClient client) {
        this(client,
                new BasicHttpCache(),
                new CacheConfig());
    }

    public CachingHttpClient(HttpClient client, CacheConfig config) {
        this(client,
                new BasicHttpCache(config),
                config);
    }

    public CachingHttpClient(
            HttpCache cache) {
        this(new DefaultHttpClient(),
                cache,
                new CacheConfig());
    }

    public CachingHttpClient(
            HttpCache cache,
            CacheConfig config) {
        this(new DefaultHttpClient(),
                cache,
                config);
    }

    public CachingHttpClient(
            HttpClient client,
            HttpCache cache) {
        this(client,
                cache,
                new CacheConfig());
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
    }

    /**
     * Return the number of times that the cache successfully answered an HttpRequest
     * for a document of information from the server.
     *
     * @return long the number of cache successes
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Return the number of times that the cache was unable to answer an HttpRequest
     * for a document of information from the server.
     *
     * @return long the number of cache failures/misses
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Return the number of times that the cache was able to revalidate
     * an existing cache entry for a document of information from the server.
     *
     * @return long the number of cache revalidations
     */
    public long getCacheUpdates() {
        return cacheUpdates.get();
    }

    /**
     * Execute an {@link HttpRequest} @ a given {@link HttpHost}
     *
     * @param target  the target host for the request.
     *                Implementations may accept <code>null</code>
     *                if they can still determine a route, for example
     *                to a default target or by inspecting the request.
     * @param request the request to execute
     * @return HttpResponse The cached entry or the result of a backend call
     * @throws IOException
     */
    public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException {
        HttpContext defaultContext = null;
        return execute(target, request, defaultContext);
    }

    /**
     * Execute an {@link HttpRequest} @ a given {@link HttpHost} with a specified
     * {@link ResponseHandler} that will deal with the result of the call.
     *
     * @param target          the target host for the request.
     *                        Implementations may accept <code>null</code>
     *                        if they can still determine a route, for example
     *                        to a default target or by inspecting the request.
     * @param request         the request to execute
     * @param responseHandler the response handler
     * @param <T>             The Return Type Identified by the generic type of the {@link ResponseHandler}
     * @return T The response type as handled by ResponseHandler
     * @throws IOException
     */
    public <T> T execute(HttpHost target, HttpRequest request,
                         ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, responseHandler, null);
    }

    /**
     * Execute an {@link HttpRequest} @ a given {@link HttpHost} with a specified
     * {@link ResponseHandler} that will deal with the result of the call using
     * a specific {@link HttpContext}
     *
     * @param target          the target host for the request.
     *                        Implementations may accept <code>null</code>
     *                        if they can still determine a route, for example
     *                        to a default target or by inspecting the request.
     * @param request         the request to execute
     * @param responseHandler the response handler
     * @param context         the context to use for the execution, or
     *                        <code>null</code> to use the default context
     * @param <T>             The Return Type Identified by the generic type of the {@link ResponseHandler}
     * @return T The response type as handled by ResponseHandler
     * @throws IOException
     */
    public <T> T execute(HttpHost target, HttpRequest request,
                         ResponseHandler<? extends T> responseHandler, HttpContext context) throws IOException {
        HttpResponse resp = execute(target, request, context);
        return responseHandler.handleResponse(resp);
    }

    /**
     * @param request the request to execute
     * @return HttpResponse The cached entry or the result of a backend call
     * @throws IOException
     */
    public HttpResponse execute(HttpUriRequest request) throws IOException {
        HttpContext context = null;
        return execute(request, context);
    }

    /**
     * @param request the request to execute
     * @param context the context to use for the execution, or
     *                <code>null</code> to use the default context
     * @return HttpResponse The cached entry or the result of a backend call
     * @throws IOException
     */
    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException {
        URI uri = request.getURI();
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        return execute(httpHost, request, context);
    }

    /**
     * @param request         the request to execute
     * @param responseHandler the response handler
     * @param <T>             The Return Type Identified by the generic type of the {@link ResponseHandler}
     * @return T The response type as handled by ResponseHandler
     * @throws IOException
     */
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler)
            throws IOException {
        return execute(request, responseHandler, null);
    }

    /**
     * @param request         the request to execute
     * @param responseHandler the response handler
     * @param context         the http context
     * @param <T>             The Return Type Identified by the generic type of the {@link ResponseHandler}
     * @return T The response type as handled by ResponseHandler
     * @throws IOException
     */
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler,
                         HttpContext context) throws IOException {
        HttpResponse resp = execute(request, context);
        return responseHandler.handleResponse(resp);
    }

    /**
     * @return the connection manager
     */
    public ClientConnectionManager getConnectionManager() {
        return backend.getConnectionManager();
    }

    /**
     * @return the parameters
     */
    public HttpParams getParams() {
        return backend.getParams();
    }

    /**
     * @param target  the target host for the request.
     *                Implementations may accept <code>null</code>
     *                if they can still determine a route, for example
     *                to a default target or by inspecting the request.
     * @param request the request to execute
     * @param context the context to use for the execution, or
     *                <code>null</code> to use the default context
     * @return the response
     * @throws IOException
     */
    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {

        // default response context
        setResponseStatus(context, CacheResponseStatus.CACHE_MISS);

        String via = generateViaHeader(request);

        if (clientRequestsOurOptions(request)) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return new OptionsHttp11Response();
        }

        List<RequestProtocolError> fatalError = requestCompliance.requestIsFatallyNonCompliant(request);

        for (RequestProtocolError error : fatalError) {
            setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
            return requestCompliance.getErrorForRequest(error);
        }

        try {
            request = requestCompliance.makeRequestCompliant(request);
        } catch (ProtocolException e) {
            throw new ClientProtocolException(e);
        }
        request.addHeader("Via",via);

        responseCache.flushInvalidatedCacheEntriesFor(target, request);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            return callBackend(target, request, context);
        }

        HttpCacheEntry entry = responseCache.getCacheEntry(target, request);
        if (entry == null) {
            cacheMisses.getAndIncrement();
            if (log.isDebugEnabled()) {
                RequestLine rl = request.getRequestLine();
                log.debug("Cache miss [host: " + target + "; uri: " + rl.getUri() + "]");

            }
            return callBackend(target, request, context);
        }

        if (log.isDebugEnabled()) {
            RequestLine rl = request.getRequestLine();
            log.debug("Cache hit [host: " + target + "; uri: " + rl.getUri() + "]");

        }
        cacheHits.getAndIncrement();

        Date now = getCurrentDate();
        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry, now)) {
            final HttpResponse cachedResponse = responseGenerator.generateResponse(entry);
            setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
            if (validityPolicy.getStalenessSecs(entry, now) > 0L) {
                cachedResponse.addHeader("Warning","110 localhost \"Response is stale\"");
            }
            return cachedResponse;
        }

        if (validityPolicy.isRevalidatable(entry)) {
            log.debug("Revalidating the cache entry");

            try {
                return revalidateCacheEntry(target, request, context, entry);
            } catch (IOException ioex) {
                if (validityPolicy.mustRevalidate(entry)
                    || (isSharedCache() && validityPolicy.proxyRevalidate(entry))
                    || explicitFreshnessRequest(request, entry, now)) {
                    setResponseStatus(context, CacheResponseStatus.CACHE_MODULE_RESPONSE);
                    return new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
                } else {
                    final HttpResponse cachedResponse = responseGenerator.generateResponse(entry);
                    setResponseStatus(context, CacheResponseStatus.CACHE_HIT);
                    cachedResponse.addHeader(HeaderConstants.WARNING, "111 localhost \"Revalidation failed\"");
                    log.debug("111 revalidation failed due to exception: " + ioex);
                    return cachedResponse;
                }
            } catch (ProtocolException e) {
                throw new ClientProtocolException(e);
            }
        }
        return callBackend(target, request, context);
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

    public boolean supportsRangeAndContentRangeHeaders() {
        return SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS;
    }

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

    HttpResponse revalidateCacheEntry(
            HttpHost target,
            HttpRequest request,
            HttpContext context,
            HttpCacheEntry cacheEntry) throws IOException, ProtocolException {
        HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(request, cacheEntry);

        Date requestDate = getCurrentDate();
        HttpResponse backendResponse = backend.execute(target, conditionalRequest, context);
        Date responseDate = getCurrentDate();


        final Header entryDateHeader = cacheEntry.getFirstHeader("Date");
        final Header responseDateHeader = backendResponse.getFirstHeader("Date");
        if (entryDateHeader != null && responseDateHeader != null) {
            try {
                Date entryDate = DateUtils.parseDate(entryDateHeader.getValue());
                Date respDate = DateUtils.parseDate(responseDateHeader.getValue());
                if (respDate.before(entryDate)) {
                    HttpRequest unconditional = conditionalRequestBuilder
                    .buildUnconditionalRequest(request, cacheEntry);
                    requestDate = getCurrentDate();
                    backendResponse = backend.execute(target, unconditional, context);
                    responseDate = getCurrentDate();
                }
            } catch (DateParseException e) {
                // either backend response or cached entry did not have a valid
                // Date header, so we can't tell if they are out of order
                // according to the origin clock; thus we can skip the
                // unconditional retry recommended in 13.2.6 of RFC 2616.
            }
        }

        backendResponse.addHeader("Via", generateViaHeader(backendResponse));

        int statusCode = backendResponse.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
            cacheUpdates.getAndIncrement();
            setResponseStatus(context, CacheResponseStatus.VALIDATED);
            if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                return responseCache.updateCacheEntry(target, request, cacheEntry,
                        backendResponse, requestDate, responseDate);
            }
        }

        return handleBackendResponse(target, conditionalRequest, requestDate, responseDate,
                                     backendResponse);
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
        if (cacheable &&
            !alreadyHaveNewerCacheEntry(target, request, backendResponse)) {
            return responseCache.cacheAndReturnResponse(target, request, backendResponse, requestDate,
                    responseDate);
        }
        if (!cacheable) {
            responseCache.flushCacheEntriesFor(target, request);
        }
        return backendResponse;
    }

    private boolean alreadyHaveNewerCacheEntry(HttpHost target, HttpRequest request,
            HttpResponse backendResponse) throws IOException {
        HttpCacheEntry existing = responseCache.getCacheEntry(target, request);
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
