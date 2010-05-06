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
package org.apache.http.client.cache.impl;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheOperationException;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.1
 */
public class CachingHttpClient implements HttpClient {

    private final static int MAX_CACHE_ENTRIES = 1000;
    private final static int DEFAULT_MAX_OBJECT_SIZE_BYTES = 8192;

    public static final ProtocolVersion HTTP_1_1 = new ProtocolVersion("HTTP", 1, 1);

    private final static boolean SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS = false;

    private final HttpClient backend;
    private final ResponseCachingPolicy responseCachingPolicy;
    private final CacheEntryGenerator cacheEntryGenerator;
    private final URIExtractor uriExtractor;
    private final HttpCache<CacheEntry> responseCache;
    private final CachedHttpResponseGenerator responseGenerator;
    private final CacheInvalidator cacheInvalidator;
    private final CacheableRequestPolicy cacheableRequestPolicy;
    private final CachedResponseSuitabilityChecker suitabilityChecker;

    private final ConditionalRequestBuilder conditionalRequestBuilder;
    private final int maxObjectSizeBytes;
    private final CacheEntryUpdater cacheEntryUpdater;

    private volatile AtomicLong cacheHits = new AtomicLong();
    private volatile AtomicLong cacheMisses = new AtomicLong();
    private volatile AtomicLong cacheUpdates = new AtomicLong();

    private final ResponseProtocolCompliance responseCompliance;
    private final RequestProtocolCompliance requestCompliance;

    private final Log LOG = LogFactory.getLog(CachingHttpClient.class);

    public CachingHttpClient() {
        this.backend = new DefaultHttpClient();
        this.maxObjectSizeBytes = DEFAULT_MAX_OBJECT_SIZE_BYTES;
        this.responseCachingPolicy = new ResponseCachingPolicy(maxObjectSizeBytes);
        this.cacheEntryGenerator = new CacheEntryGenerator();
        this.uriExtractor = new URIExtractor();
        this.responseCache = new BasicHttpCache(MAX_CACHE_ENTRIES);
        this.responseGenerator = new CachedHttpResponseGenerator();
        this.cacheInvalidator = new CacheInvalidator(this.uriExtractor, this.responseCache);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker();
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.cacheEntryUpdater = new CacheEntryUpdater();
        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance();
    }

    public CachingHttpClient(HttpCache<CacheEntry> cache, int maxObjectSizeBytes) {
        this.responseCache = cache;

        this.backend = new DefaultHttpClient();
        this.maxObjectSizeBytes = maxObjectSizeBytes;
        this.responseCachingPolicy = new ResponseCachingPolicy(maxObjectSizeBytes);
        this.cacheEntryGenerator = new CacheEntryGenerator();
        this.uriExtractor = new URIExtractor();
        this.responseGenerator = new CachedHttpResponseGenerator();
        this.cacheInvalidator = new CacheInvalidator(this.uriExtractor, this.responseCache);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker();
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.cacheEntryUpdater = new CacheEntryUpdater();
        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance();
    }

    public CachingHttpClient(HttpClient client, HttpCache<CacheEntry> cache, int maxObjectSizeBytes) {
        this.responseCache = cache;

        this.backend = client;
        this.responseCachingPolicy = new ResponseCachingPolicy(maxObjectSizeBytes);
        this.cacheEntryGenerator = new CacheEntryGenerator();
        this.uriExtractor = new URIExtractor();
        this.responseGenerator = new CachedHttpResponseGenerator();
        this.cacheInvalidator = new CacheInvalidator(this.uriExtractor, this.responseCache);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker();
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.cacheEntryUpdater = new CacheEntryUpdater();
        this.maxObjectSizeBytes = maxObjectSizeBytes;
        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance();
    }

    public CachingHttpClient(HttpClient backend, ResponseCachingPolicy responseCachingPolicy,
                             CacheEntryGenerator cacheEntryGenerator, URIExtractor uriExtractor,
                             HttpCache<CacheEntry> responseCache, CachedHttpResponseGenerator responseGenerator,
                             CacheInvalidator cacheInvalidator, CacheableRequestPolicy cacheableRequestPolicy,
                             CachedResponseSuitabilityChecker suitabilityChecker,
                             ConditionalRequestBuilder conditionalRequestBuilder, CacheEntryUpdater entryUpdater,
                             ResponseProtocolCompliance responseCompliance,
                             RequestProtocolCompliance requestCompliance) {
        this.maxObjectSizeBytes = DEFAULT_MAX_OBJECT_SIZE_BYTES;
        this.backend = backend;
        this.responseCachingPolicy = responseCachingPolicy;
        this.cacheEntryGenerator = cacheEntryGenerator;
        this.uriExtractor = uriExtractor;
        this.responseCache = responseCache;
        this.responseGenerator = responseGenerator;
        this.cacheInvalidator = cacheInvalidator;
        this.cacheableRequestPolicy = cacheableRequestPolicy;
        this.suitabilityChecker = suitabilityChecker;
        this.conditionalRequestBuilder = conditionalRequestBuilder;
        this.cacheEntryUpdater = entryUpdater;
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
     * @param context
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
     * @return
     */
    public ClientConnectionManager getConnectionManager() {
        return backend.getConnectionManager();
    }

    /**
     * @return
     */
    public HttpParams getParams() {
        return backend.getParams();
    }

    protected Date getCurrentDate() {
        return new Date();
    }

    protected CacheEntry getCacheEntry(HttpHost target, HttpRequest request) {
        String uri = uriExtractor.getURI(target, request);
        CacheEntry entry = null;
        try {
            entry = responseCache.getEntry(uri);
        } catch (HttpCacheOperationException probablyIgnore) {
            LOG.warn("Cache: Was unable to get an entry from the cache based on the uri provided.", probablyIgnore);
        }

        if (entry == null || !entry.hasVariants())
            return entry;

        String variantUri = uriExtractor.getVariantURI(target, request, entry);
        try {
            return responseCache.getEntry(variantUri);
        } catch (HttpCacheOperationException probablyIgnore) {
            return null;
        }
    }

    /**
     * @param target  the target host for the request.
     *                Implementations may accept <code>null</code>
     *                if they can still determine a route, for example
     *                to a default target or by inspecting the request.
     * @param request the request to execute
     * @param context the context to use for the execution, or
     *                <code>null</code> to use the default context
     * @return
     * @throws IOException
     */
    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {

        if (clientRequestsOurOptions(request)) {
            return new OptionsHttp11Response();
        }

        List<RequestProtocolError> fatalError = requestCompliance.requestIsFatallyNonCompliant(request);

        for (RequestProtocolError error : fatalError) {
            return requestCompliance.getErrorForRequest(error);
        }

        try {
            request = requestCompliance.makeRequestCompliant(request);
        } catch (ProtocolException e) {
            throw new ClientProtocolException(e);
        }

        cacheInvalidator.flushInvalidatedCacheEntries(target, request);

        if (!cacheableRequestPolicy.isServableFromCache(request)) {
            return callBackend(target, request, context);
        }

        CacheEntry entry = getCacheEntry(target, request);
        if (entry == null) {
            cacheMisses.getAndIncrement();
            LOG.debug("CLIENT: Cache Miss.");
            return callBackend(target, request, context);
        }

        LOG.debug("CLIENT: Cache HIT.");
        cacheHits.getAndIncrement();

        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry)) {
            return responseGenerator.generateResponse(entry);
        }

        if (entry.isRevalidatable()) {
            LOG.debug("CLIENT: Revalidate the entry.");

            try {
                return revalidateCacheEntry(target, request, context, entry);
            } catch (IOException ioex) {
                HttpResponse response = responseGenerator.generateResponse(entry);
                response.addHeader(HeaderConstants.WARNING, "111 Revalidation Failed - " + ioex.getMessage());
                LOG.debug("111 revalidation failed due to exception: " + ioex);
                return response;
            } catch (ProtocolException e) {
                throw new ClientProtocolException(e);
            }
        }
        return callBackend(target, request, context);
    }

    private boolean clientRequestsOurOptions(HttpRequest request) {
        RequestLine line = request.getRequestLine();

        if (!HeaderConstants.OPTIONS_METHOD.equals(line.getMethod()))
            return false;

        if (!"*".equals(line.getUri()))
            return false;

        if (!"0".equals(request.getFirstHeader(HeaderConstants.MAX_FORWARDS).getValue()))
            return false;

        return true;
    }

    protected HttpResponse callBackend(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {

        Date requestDate = getCurrentDate();

        try {
            LOG.debug("CLIENT: Calling the backend.");
            HttpResponse backendResponse = backend.execute(target, request, context);
            return handleBackendResponse(target, request, requestDate, getCurrentDate(),
                                         backendResponse);
        } catch (ClientProtocolException cpex) {
            throw cpex;
        } catch (IOException ex) {
            StatusLine status = new BasicStatusLine(HTTP_1_1, HttpStatus.SC_SERVICE_UNAVAILABLE, ex
                    .getMessage());
            return new BasicHttpResponse(status);
        }

    }

    protected HttpResponse revalidateCacheEntry(HttpHost target, HttpRequest request,
                                                HttpContext context, CacheEntry cacheEntry) throws IOException, ProtocolException {
        HttpRequest conditionalRequest = conditionalRequestBuilder.buildConditionalRequest(request, cacheEntry);
        Date requestDate = getCurrentDate();

        HttpResponse backendResponse = backend.execute(target, conditionalRequest, context);

        Date responseDate = getCurrentDate();

        int statusCode = backendResponse.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
            cacheUpdates.getAndIncrement();
            CacheEntry updatedEntry = cacheEntryUpdater.updateCacheEntry(cacheEntry, requestDate, responseDate, backendResponse);
            storeInCache(target, request, updatedEntry);
            return responseGenerator.generateResponse(updatedEntry);
        }

        return handleBackendResponse(target, conditionalRequest, requestDate, responseDate,
                                     backendResponse);
    }

    protected void storeInCache(HttpHost target, HttpRequest request, CacheEntry entry) {
        if (entry.hasVariants()) {
            try {
                String uri = uriExtractor.getURI(target, request);
                HttpCacheUpdateCallback<CacheEntry> callback = storeVariantEntry(target, request, entry);
                responseCache.updateCacheEntry(uri, callback);
            } catch (HttpCacheOperationException probablyIgnore) {
                LOG.warn("Cache: Was unable to PUT/UPDATE an entry into the cache based on the uri provided.", probablyIgnore);
            }
        } else {
            storeNonVariantEntry(target, request, entry);
        }
    }

    private void storeNonVariantEntry(HttpHost target, HttpRequest req, CacheEntry entry) {
        String uri = uriExtractor.getURI(target, req);
        try {
            responseCache.putEntry(uri, entry);
        } catch (HttpCacheOperationException probablyIgnore) {
            LOG.warn("Cache: Was unable to PUT an entry into the cache based on the uri provided.", probablyIgnore);
        }
    }

    protected HttpCacheUpdateCallback<CacheEntry> storeVariantEntry(final HttpHost target, final HttpRequest req,
                                                                    final CacheEntry entry) {

        return new HttpCacheUpdateCallback<CacheEntry>() {
            public CacheEntry getUpdatedEntry(CacheEntry existing) throws HttpCacheOperationException {

                return doGetUpdatedParentEntry(existing, target, req, entry);
            }
        };
    }

    protected CacheEntry doGetUpdatedParentEntry(CacheEntry existing, HttpHost target, HttpRequest req, CacheEntry entry) throws HttpCacheOperationException {

        String variantURI = uriExtractor.getVariantURI(target, req, entry);
        responseCache.putEntry(variantURI, entry);

        if (existing != null) {
            return existing.addVariantURI(variantURI);
        } else {
            return entry.addVariantURI(variantURI);
        }
    }

    protected HttpResponse handleBackendResponse(HttpHost target, HttpRequest request,
                                                 Date requestDate, Date responseDate, HttpResponse backendResponse) throws IOException {

        LOG.debug("CLIENT: Handling Backend response.");
        responseCompliance.ensureProtocolCompliance(request, backendResponse);

        boolean cacheable = responseCachingPolicy.isResponseCacheable(request, backendResponse);

        if (cacheable) {

            SizeLimitedResponseReader responseReader = getResponseReader(backendResponse);

            if (responseReader.isResponseTooLarge()) {
                return responseReader.getReconstructedResponse();
            }

            CacheEntry entry = cacheEntryGenerator.generateEntry(requestDate, responseDate,
                                                                 backendResponse, responseReader.getResponseBytes());
            storeInCache(target, request, entry);
            return responseGenerator.generateResponse(entry);
        }

        String uri = uriExtractor.getURI(target, request);
        try {
            responseCache.removeEntry(uri);
        } catch (HttpCacheOperationException coe) {
            LOG.warn("Cache: Was unable to remove an entry from the cache based on the uri provided.", coe);
            // TODO: track failed state
        }
        return backendResponse;
    }

    protected SizeLimitedResponseReader getResponseReader(HttpResponse backEndResponse) {
        return new SizeLimitedResponseReader(maxObjectSizeBytes, backEndResponse);
    }

    public boolean supportsRangeAndContentRangeHeaders() {
        return SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS;
    }

}
