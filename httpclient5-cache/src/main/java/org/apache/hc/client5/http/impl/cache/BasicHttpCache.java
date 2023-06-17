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

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BasicHttpCache implements HttpCache {

    private static final Logger LOG = LoggerFactory.getLogger(BasicHttpCache.class);

    private final CacheUpdateHandler cacheUpdateHandler;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final HttpCacheInvalidator cacheInvalidator;
    private final HttpCacheStorage storage;

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator,
            final HttpCacheInvalidator cacheInvalidator) {
        this.cacheUpdateHandler = new CacheUpdateHandler(resourceFactory);
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.storage = storage;
        this.cacheInvalidator = cacheInvalidator;
    }

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator cacheKeyGenerator) {
        this(resourceFactory, storage, cacheKeyGenerator, new DefaultCacheInvalidator());
    }

    public BasicHttpCache(final ResourceFactory resourceFactory, final HttpCacheStorage storage) {
        this( resourceFactory, storage, new CacheKeyGenerator());
    }

    public BasicHttpCache(final CacheConfig config) {
        this(new HeapResourceFactory(), new BasicHttpCacheStorage(config));
    }

    public BasicHttpCache() {
        this(CacheConfig.DEFAULT);
    }

    @Override
    public String generateKey(final HttpHost host, final HttpRequest request, final HttpCacheEntry cacheEntry) {
        if (cacheEntry == null) {
            return cacheKeyGenerator.generateKey(host, request);
        } else {
            return cacheKeyGenerator.generateKey(host, request, cacheEntry);
        }
    }

    @Override
    public void flushCacheEntriesFor(final HttpHost host, final HttpRequest request) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries: {}; {}", host, new RequestLine(request));
        }
        if (!Method.isSafe(request.getMethod())) {
            final String cacheKey = cacheKeyGenerator.generateKey(host, request);
            try {
                storage.removeEntry(cacheKey);
            } catch (final ResourceIOException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("I/O error removing cache entry with key {}", cacheKey);
                }
            }
        }
    }

    @Override
    public void flushCacheEntriesInvalidatedByRequest(final HttpHost host, final HttpRequest request) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by request: {}; {}", host, new RequestLine(request));
        }
        cacheInvalidator.flushCacheEntriesInvalidatedByRequest(host, request, cacheKeyGenerator, storage);
    }

    @Override
    public void flushCacheEntriesInvalidatedByExchange(final HttpHost host, final HttpRequest request, final HttpResponse response) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Flush cache entries invalidated by exchange: {}; {} -> {}", host, new RequestLine(request), new StatusLine(response));
        }
        if (!Method.isSafe(request.getMethod())) {
            cacheInvalidator.flushCacheEntriesInvalidatedByExchange(host, request, response, cacheKeyGenerator, storage);
        }
    }

    void storeInCache(
            final String cacheKey,
            final HttpHost host,
            final HttpRequest request,
            final HttpCacheEntry entry) {
        if (entry.hasVariants()) {
            storeVariantEntry(cacheKey, host, request, entry);
        } else {
            storeEntry(cacheKey, entry);
        }
    }

    void storeEntry(final String cacheKey, final HttpCacheEntry entry) {
        try {
            storage.putEntry(cacheKey, entry);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error storing cache entry with key {}", cacheKey);
            }
        }
    }

    void storeVariantEntry(
            final String cacheKey,
            final HttpHost host,
            final HttpRequest req,
            final HttpCacheEntry entry) {
        final String variantKey = cacheKeyGenerator.generateVariantKey(req, entry);
        final String variantCacheKey = cacheKeyGenerator.generateKey(host, req, entry);
        storeEntry(variantCacheKey, entry);
        try {
            storage.updateEntry(cacheKey, existing -> cacheUpdateHandler.updateParentCacheEntry(req.getRequestUri(), existing, entry, variantKey, variantCacheKey));
        } catch (final HttpCacheUpdateException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Cannot update cache entry with key {}", cacheKey);
            }
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error updating cache entry with key {}", cacheKey);
            }
        }
    }

    @Override
    public void reuseVariantEntryFor(
            final HttpHost host, final HttpRequest request, final Variant variant) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Re-use variant entry: {}; {} / {}", host, new RequestLine(request), variant);
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry entry = variant.getEntry();
        final String variantKey = cacheKeyGenerator.generateVariantKey(request, entry);
        final String variantCacheKey = variant.getCacheKey();

        try {
            storage.updateEntry(cacheKey, existing -> cacheUpdateHandler.updateParentCacheEntry(request.getRequestUri(), existing, entry, variantKey, variantCacheKey));
        } catch (final HttpCacheUpdateException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Cannot update cache entry with key {}", cacheKey);
            }
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error updating cache entry with key {}", cacheKey);
            }
        }
    }

    @Override
    public HttpCacheEntry updateCacheEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpCacheEntry stale,
            final HttpResponse originResponse,
            final Instant requestSent,
            final Instant responseReceived) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update cache entry: {}; {}", host, new RequestLine(request));
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        try {
            final HttpCacheEntry updatedEntry = cacheUpdateHandler.updateCacheEntry(
                    request.getRequestUri(),
                    stale,
                    requestSent,
                    responseReceived,
                    originResponse);
            storeInCache(cacheKey, host, request, updatedEntry);
            return updatedEntry;
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error updating cache entry with key {}", cacheKey);
            }
            return stale;
        }
    }

    @Override
    public HttpCacheEntry updateVariantCacheEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final Variant variant,
            final Instant requestSent,
            final Instant responseReceived) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update variant cache entry: {}; {} / {}", host, new RequestLine(request), variant);
        }
        final HttpCacheEntry entry = variant.getEntry();
        final String cacheKey = variant.getCacheKey();
        try {
            final HttpCacheEntry updatedEntry = cacheUpdateHandler.updateCacheEntry(
                    request.getRequestUri(),
                    entry,
                    requestSent,
                    responseReceived,
                    originResponse);
            storeEntry(cacheKey, updatedEntry);
            return updatedEntry;
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error updating cache entry with key {}", cacheKey);
            }
            return entry;
        }
    }

    @Override
    public HttpCacheEntry createCacheEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Instant requestSent,
            final Instant responseReceived) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Create cache entry: {}; {}", host, new RequestLine(request));
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        try {
            final HttpCacheEntry entry = cacheUpdateHandler.createCacheEntry(request, originResponse, content, requestSent, responseReceived);
            storeInCache(cacheKey, host, request, entry);
            return entry;
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error creating cache entry with key {}", cacheKey);
            }
            return HttpCacheEntry.create(
                    requestSent,
                    responseReceived,
                    request,
                    originResponse,
                    content != null ? HeapResourceFactory.INSTANCE.generate(null, content.array(), 0, content.length()) : null,
                    null);
        }
    }

    @Override
    public HttpCacheEntry getCacheEntry(final HttpHost host, final HttpRequest request) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get cache entry: {}; {}", host, new RequestLine(request));
        }
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry root;
        try {
            root = storage.getEntry(cacheKey);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error retrieving cache entry with key {}", cacheKey);
            }
            return null;
        }
        if (root == null) {
            return null;
        }
        if (!root.hasVariants()) {
            return root;
        }

        HttpCacheEntry mostRecentVariant = null;
        for (final String variantCacheKey : root.getVariantMap().values()) {
            final HttpCacheEntry variant;
            try {
                variant = storage.getEntry(variantCacheKey);
            } catch (final ResourceIOException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("I/O error retrieving cache entry with key {}", variantCacheKey);
                }
                continue;
            }
            if (variant == null) {
                continue;
            }

            // Skip the variant if it is not suitable
            if (!isSuitableVariant(variant, request)) {
                continue;
            }

            if (!variant.containsHeader(HttpHeaders.DATE)) {
                continue;
            }

            if (mostRecentVariant == null || mostRecentVariant.getDate().before(variant.getDate())) {
                mostRecentVariant = variant;
            }
        }
        return mostRecentVariant != null ? mostRecentVariant : root;
    }

    /**
     * Checks if a cached variant is suitable for a given request.
     * <p>
     * It considers Vary, Accept, Accept-Language, and
     * Accept-Encoding headers of the request and the cached variant.
     * <p>
     * If the Vary header is present in the variant, the method ensures
     * that the value of each field specified in the Vary header matches
     * between the request and the variant.
     * <p>
     * The method also ensures that if the request has Accept,
     * Accept-Language, or Accept-Encoding headers, the corresponding
     * headers in the variant match the requested values.
     * <p>
     * Note: The current implementation only checks for exact matches of
     * single header values. If the headers have multiple values, or
     * q-values and wildcards are used, the method may not work correctly.
     *
     * @param variant The cached variant.
     * @param request The request.
     * @return true if the variant is suitable for the request; false otherwise.
     */
    private boolean isSuitableVariant(final HttpCacheEntry variant, final HttpRequest request) {
        // Check Vary header
        final Header varyHeader = variant.getFirstHeader(HttpHeaders.VARY);
        if (varyHeader != null) {
            final Iterator<HeaderElement> it = MessageSupport.iterate(variant, HttpHeaders.VARY);
            while (it.hasNext()) {
                final HeaderElement varyField = it.next();
                final Header requestHeader = request.getFirstHeader(varyField.getName());
                final Header variantHeader = variant.getFirstHeader(varyField.getName());
                if (requestHeader == null && variantHeader != null ||
                        requestHeader != null && variantHeader == null ||
                        requestHeader != null && !requestHeader.getValue().equals(variantHeader.getValue())) {
                    return false;
                }
            }
        }

        // Check content type
        final Header acceptHeader = request.getFirstHeader(HttpHeaders.ACCEPT);
        if (acceptHeader != null && !variant.containsHeader(HttpHeaders.CONTENT_TYPE)) {
            // This only checks for the presence of Content-Type header in variant.
            // Ideally, this should parse the Accept and Content-Type headers and check
            // if any acceptable media type matches the variant's media type.
            return false;
        }

        // Check language
        final Header acceptLanguageHeader = request.getFirstHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (acceptLanguageHeader != null) {
            // Again, this should parse the Accept-Language and Content-Language headers
            // and check if any acceptable language matches the variant's language.
            final Header contentLanguageHeader = variant.getFirstHeader(HttpHeaders.CONTENT_LANGUAGE);
            if (contentLanguageHeader == null || !contentLanguageHeader.getValue().equals(acceptLanguageHeader.getValue())) {
                return false;
            }
        }

        // Check encoding
        final Header acceptEncodingHeader = request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING);
        if (acceptEncodingHeader != null) {
            // Similar to the Accept and Accept-Language headers, the Accept-Encoding
            // header can have multiple values and should be parsed accordingly.
            final Header contentEncodingHeader = variant.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
            if (contentEncodingHeader == null || !contentEncodingHeader.getValue().equals(acceptEncodingHeader.getValue())) {
                return false;
            }
        }

        return true;
    }


    @Override
    public Map<String, Variant> getVariantCacheEntriesWithEtags(final HttpHost host, final HttpRequest request) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get variant cache entries: {}; {}", host, new RequestLine(request));
        }
        final Map<String,Variant> variants = new HashMap<>();
        final String cacheKey = cacheKeyGenerator.generateKey(host, request);
        final HttpCacheEntry root;
        try {
            root = storage.getEntry(cacheKey);
        } catch (final ResourceIOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("I/O error retrieving cache entry with key {}", cacheKey);
            }
            return variants;
        }
        if (root != null && root.hasVariants()) {
            for(final Map.Entry<String, String> variant : root.getVariantMap().entrySet()) {
                final String variantCacheKey = variant.getValue();
                try {
                    final HttpCacheEntry entry = storage.getEntry(variantCacheKey);
                    if (entry != null) {
                        final Header etagHeader = entry.getFirstHeader(HttpHeaders.ETAG);
                        if (etagHeader != null) {
                            variants.put(etagHeader.getValue(), new Variant(variantCacheKey, entry));
                        }
                    }
                } catch (final ResourceIOException ex) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("I/O error retrieving cache entry with key {}", variantCacheKey);
                    }
                    return variants;
                }
            }
        }
        return variants;
    }

}
