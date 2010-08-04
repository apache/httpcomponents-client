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
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCache;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntryFactory;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.1
 */
@ThreadSafe // So long as the responseCache implementation is threadsafe
public class CachingHttpClient implements HttpClient {

    private final static int MAX_CACHE_ENTRIES = 1000;
    private final static boolean SUPPORTS_RANGE_AND_CONTENT_RANGE_HEADERS = false;

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheUpdates = new AtomicLong();

    private final HttpClient backend;
    private final HttpCache responseCache;
    private final HttpCacheEntryFactory cacheEntryFactory;
    private final CacheValidityPolicy validityPolicy;
    private final ResponseCachingPolicy responseCachingPolicy;
    private final URIExtractor uriExtractor;
    private final CachedHttpResponseGenerator responseGenerator;
    private final CacheInvalidator cacheInvalidator;
    private final CacheableRequestPolicy cacheableRequestPolicy;
    private final CachedResponseSuitabilityChecker suitabilityChecker;

    private final ConditionalRequestBuilder conditionalRequestBuilder;

    private final CacheEntryUpdater cacheEntryUpdater;

    private final int maxObjectSizeBytes;
    private final boolean sharedCache;

    private final ResponseProtocolCompliance responseCompliance;
    private final RequestProtocolCompliance requestCompliance;

    private final Log log = LogFactory.getLog(getClass());

    public CachingHttpClient(
            HttpClient client,
            HttpCache cache,
            HttpCacheEntryFactory cacheEntryFactory,
            CacheConfig config) {
        super();
        if (client == null) {
            throw new IllegalArgumentException("HttpClient may not be null");
        }
        if (cache == null) {
            throw new IllegalArgumentException("HttpCache may not be null");
        }
        if (cacheEntryFactory == null) {
            throw new IllegalArgumentException("HttpCacheEntryFactory may not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("CacheConfig may not be null");
        }
        this.maxObjectSizeBytes = config.getMaxObjectSizeBytes();
        this.sharedCache = config.isSharedCache();
        this.backend = client;
        this.responseCache = cache;
        this.cacheEntryFactory = cacheEntryFactory;

        this.validityPolicy = new CacheValidityPolicy();
        this.responseCachingPolicy = new ResponseCachingPolicy(maxObjectSizeBytes, sharedCache);
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.uriExtractor = new URIExtractor();
        this.cacheInvalidator = new CacheInvalidator(this.uriExtractor, this.responseCache);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy);
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.cacheEntryUpdater = new CacheEntryUpdater(this.cacheEntryFactory);

        this.responseCompliance = new ResponseProtocolCompliance();
        this.requestCompliance = new RequestProtocolCompliance();
    }

    public CachingHttpClient() {
        this(new DefaultHttpClient(),
                new BasicHttpCache(MAX_CACHE_ENTRIES),
                new MemCacheEntryFactory(),
                new CacheConfig());
    }

    public CachingHttpClient(CacheConfig config) {
        this(new DefaultHttpClient(),
                new BasicHttpCache(MAX_CACHE_ENTRIES),
                new MemCacheEntryFactory(),
                config);
    }

    public CachingHttpClient(HttpClient client) {
        this(client,
                new BasicHttpCache(MAX_CACHE_ENTRIES),
                new MemCacheEntryFactory(),
                new CacheConfig());
    }

    public CachingHttpClient(HttpClient client, CacheConfig config) {
        this(client,
                new BasicHttpCache(MAX_CACHE_ENTRIES),
                new MemCacheEntryFactory(),
                config);
    }

    public CachingHttpClient(
            HttpCache cache,
            HttpCacheEntryFactory cacheEntryFactory) {
        this(new DefaultHttpClient(),
                cache,
                cacheEntryFactory,
                new CacheConfig());
    }

    public CachingHttpClient(
            HttpCache cache,
            HttpCacheEntryFactory cacheEntryFactory,
            CacheConfig config) {
        this(new DefaultHttpClient(),
                cache,
                cacheEntryFactory,
                config);
    }

    public CachingHttpClient(
            HttpClient client,
            HttpCache cache,
            HttpCacheEntryFactory cacheEntryFactory) {
        this(client,
                cache,
                cacheEntryFactory,
                new CacheConfig());
    }

    CachingHttpClient(HttpClient backend, CacheValidityPolicy validityPolicy, ResponseCachingPolicy responseCachingPolicy,
                             HttpCacheEntryFactory cacheEntryFactory, URIExtractor uriExtractor,
                             HttpCache responseCache, CachedHttpResponseGenerator responseGenerator,
                             CacheInvalidator cacheInvalidator, CacheableRequestPolicy cacheableRequestPolicy,
                             CachedResponseSuitabilityChecker suitabilityChecker,
                             ConditionalRequestBuilder conditionalRequestBuilder, CacheEntryUpdater entryUpdater,
                             ResponseProtocolCompliance responseCompliance,
                             RequestProtocolCompliance requestCompliance) {
        CacheConfig config = new CacheConfig();
        this.maxObjectSizeBytes = config.getMaxObjectSizeBytes();
        this.sharedCache = config.isSharedCache();
        this.backend = backend;
        this.cacheEntryFactory = cacheEntryFactory;
        this.validityPolicy = validityPolicy;
        this.responseCachingPolicy = responseCachingPolicy;
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

        HttpCacheEntry entry = getCacheEntry(target, request);
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

        if (suitabilityChecker.canCachedResponseBeUsed(target, request, entry)) {
            return responseGenerator.generateResponse(entry);
        }

        if (validityPolicy.isRevalidatable(entry)) {
            log.debug("Revalidating the cache entry");

            try {
                return revalidateCacheEntry(target, request, context, entry);
            } catch (IOException ioex) {
                if (validityPolicy.mustRevalidate(entry)
                    || (isSharedCache() && validityPolicy.proxyRevalidate(entry))) {
                    return new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
                } else {
                    HttpResponse response = responseGenerator.generateResponse(entry);
                    response.addHeader(HeaderConstants.WARNING, "111 Revalidation Failed - " + ioex.getMessage());
                    log.debug("111 revalidation failed due to exception: " + ioex);
                    return response;
                }
            } catch (ProtocolException e) {
                throw new ClientProtocolException(e);
            }
        }
        return callBackend(target, request, context);
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

    HttpCacheEntry getCacheEntry(HttpHost target, HttpRequest request) throws IOException {
        String uri = uriExtractor.getURI(target, request);
        HttpCacheEntry entry = responseCache.getEntry(uri);

        if (entry == null || !entry.hasVariants()) {
            return entry;
        }

        String variantUri = uriExtractor.getVariantURI(target, request, entry);
        return responseCache.getEntry(variantUri);
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

        try {
            log.debug("Calling the backend");
            HttpResponse backendResponse = backend.execute(target, request, context);
            return handleBackendResponse(target, request, requestDate, getCurrentDate(),
                                         backendResponse);
        } catch (ClientProtocolException cpex) {
            throw cpex;
        } catch (IOException ex) {
            StatusLine status = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_SERVICE_UNAVAILABLE, ex
                    .getMessage());
            return new BasicHttpResponse(status);
        }

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

        int statusCode = backendResponse.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_OK) {
            cacheUpdates.getAndIncrement();
            HttpCacheEntry updatedEntry = cacheEntryUpdater.updateCacheEntry(
                    request.getRequestLine().getUri(),
                    cacheEntry,
                    requestDate,
                    responseDate,
                    backendResponse);
            storeInCache(target, request, updatedEntry);
            return responseGenerator.generateResponse(updatedEntry);
        }

        return handleBackendResponse(target, conditionalRequest, requestDate, responseDate,
                                     backendResponse);
    }

    void storeInCache(
            HttpHost target, HttpRequest request, HttpCacheEntry entry) throws IOException {
        if (entry.hasVariants()) {
            storeVariantEntry(target, request, entry);
        } else {
            storeNonVariantEntry(target, request, entry);
        }
    }

    void storeNonVariantEntry(
            HttpHost target, HttpRequest req, HttpCacheEntry entry) throws IOException {
        String uri = uriExtractor.getURI(target, req);
        responseCache.putEntry(uri, entry);
    }

    void storeVariantEntry(
            final HttpHost target,
            final HttpRequest req,
            final HttpCacheEntry entry) throws IOException {
        final String parentURI = uriExtractor.getURI(target, req);
        final String variantURI = uriExtractor.getVariantURI(target, req, entry);
        responseCache.putEntry(variantURI, entry);

        HttpCacheUpdateCallback callback = new HttpCacheUpdateCallback() {

            public HttpCacheEntry update(HttpCacheEntry existing) throws IOException {
                return doGetUpdatedParentEntry(
                        req.getRequestLine().getUri(), existing, entry, variantURI);
            }

        };
        responseCache.updateEntry(parentURI, callback);
    }

    HttpCacheEntry doGetUpdatedParentEntry(
            final String requestId,
            final HttpCacheEntry existing,
            final HttpCacheEntry entry,
            final String variantURI) throws IOException {
        if (existing != null) {
            return cacheEntryFactory.copyVariant(requestId, existing, variantURI);
        } else {
            return cacheEntryFactory.copyVariant(requestId, entry, variantURI);
        }
    }

    HttpResponse correctIncompleteResponse(HttpResponse resp,
                                                     byte[] bodyBytes) {
        int status = resp.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_OK
            && status != HttpStatus.SC_PARTIAL_CONTENT) {
            return resp;
        }
        Header hdr = resp.getFirstHeader("Content-Length");
        if (hdr == null) return resp;
        int contentLength;
        try {
            contentLength = Integer.parseInt(hdr.getValue());
        } catch (NumberFormatException nfe) {
            return resp;
        }
        if (bodyBytes.length >= contentLength) return resp;
        HttpResponse error =
            new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_GATEWAY, "Bad Gateway");
        error.setHeader("Content-Type","text/plain;charset=UTF-8");
        String msg = String.format("Received incomplete response with Content-Length %d but actual body length %d", contentLength, bodyBytes.length);
        byte[] msgBytes = msg.getBytes();
        error.setHeader("Content-Length", String.format("%d",msgBytes.length));
        error.setEntity(new ByteArrayEntity(msgBytes));
        return error;
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

        HttpResponse corrected = backendResponse;
        if (cacheable) {

            SizeLimitedResponseReader responseReader = getResponseReader(backendResponse);

            if (responseReader.isResponseTooLarge()) {
                return responseReader.getReconstructedResponse();
            }

            byte[] responseBytes = responseReader.getResponseBytes();
            corrected = correctIncompleteResponse(backendResponse,
                                                               responseBytes);
            int correctedStatus = corrected.getStatusLine().getStatusCode();
            if (HttpStatus.SC_BAD_GATEWAY != correctedStatus) {
                HttpCacheEntry entry = cacheEntryFactory.generate(
                            request.getRequestLine().getUri(),
                            requestDate,
                            responseDate,
                            corrected.getStatusLine(),
                            corrected.getAllHeaders(),
                            responseBytes);
                storeInCache(target, request, entry);
                return responseGenerator.generateResponse(entry);
            }
        }

        String uri = uriExtractor.getURI(target, request);
        responseCache.removeEntry(uri);
        return corrected;
    }

    SizeLimitedResponseReader getResponseReader(HttpResponse backEndResponse) {
        return new SizeLimitedResponseReader(maxObjectSizeBytes, backEndResponse);
    }

}
