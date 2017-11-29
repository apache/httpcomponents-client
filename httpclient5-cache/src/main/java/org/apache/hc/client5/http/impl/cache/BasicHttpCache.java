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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheUpdateException;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class BasicHttpCache implements HttpCache {

    private final CacheKeyGenerator uriExtractor;
    private final ResourceFactory resourceFactory;
    private final CacheEntryUpdater cacheEntryUpdater;
    private final HttpCacheInvalidator cacheInvalidator;
    private final HttpCacheStorage storage;

    private final Logger log = LogManager.getLogger(getClass());

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator uriExtractor,
            final HttpCacheInvalidator cacheInvalidator) {
        this.resourceFactory = resourceFactory;
        this.uriExtractor = uriExtractor;
        this.cacheEntryUpdater = new CacheEntryUpdater(resourceFactory);
        this.storage = storage;
        this.cacheInvalidator = cacheInvalidator;
    }

    public BasicHttpCache(
            final ResourceFactory resourceFactory,
            final HttpCacheStorage storage,
            final CacheKeyGenerator uriExtractor) {
        this(resourceFactory, storage, uriExtractor, new DefaultCacheInvalidator(uriExtractor, storage));
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
    public void flushCacheEntriesFor(final HttpHost host, final HttpRequest request) throws ResourceIOException {
        if (!StandardMethods.isSafe(request.getMethod())) {
            final String uri = uriExtractor.generateKey(host, request);
            storage.removeEntry(uri);
        }
    }

    @Override
    public void flushInvalidatedCacheEntriesFor(final HttpHost host, final HttpRequest request, final HttpResponse response) {
        if (!StandardMethods.isSafe(request.getMethod())) {
            cacheInvalidator.flushInvalidatedCacheEntries(host, request, response);
        }
    }

    void storeInCache(
            final HttpHost target, final HttpRequest request, final HttpCacheEntry entry) throws ResourceIOException {
        if (entry.hasVariants()) {
            storeVariantEntry(target, request, entry);
        } else {
            storeNonVariantEntry(target, request, entry);
        }
    }

    void storeNonVariantEntry(
            final HttpHost target, final HttpRequest req, final HttpCacheEntry entry) throws ResourceIOException {
        final String uri = uriExtractor.generateKey(target, req);
        storage.putEntry(uri, entry);
    }

    void storeVariantEntry(
            final HttpHost target,
            final HttpRequest req,
            final HttpCacheEntry entry) throws ResourceIOException {
        final String parentCacheKey = uriExtractor.generateKey(target, req);
        final String variantKey = uriExtractor.generateVariantKey(req, entry);
        final String variantURI = uriExtractor.generateVariantURI(target, req, entry);
        storage.putEntry(variantURI, entry);

        try {
            storage.updateEntry(parentCacheKey, new HttpCacheCASOperation() {

                @Override
                public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                    return doGetUpdatedParentEntry(req.getRequestUri(), existing, entry, variantKey, variantURI);
                }

            });
        } catch (final HttpCacheUpdateException e) {
            log.warn("Could not processChallenge key [" + parentCacheKey + "]", e);
        }
    }

    @Override
    public void reuseVariantEntryFor(
            final HttpHost target, final HttpRequest req, final Variant variant) throws ResourceIOException {
        final String parentCacheKey = uriExtractor.generateKey(target, req);
        final HttpCacheEntry entry = variant.getEntry();
        final String variantKey = uriExtractor.generateVariantKey(req, entry);
        final String variantCacheKey = variant.getCacheKey();

        try {
            storage.updateEntry(parentCacheKey, new HttpCacheCASOperation() {

                @Override
                public HttpCacheEntry execute(final HttpCacheEntry existing) throws ResourceIOException {
                    return doGetUpdatedParentEntry(req.getRequestUri(), existing, entry, variantKey, variantCacheKey);
                }

            });
        } catch (final HttpCacheUpdateException e) {
            log.warn("Could not processChallenge key [" + parentCacheKey + "]", e);
        }
    }

    HttpCacheEntry doGetUpdatedParentEntry(
            final String requestId,
            final HttpCacheEntry existing,
            final HttpCacheEntry entry,
            final String variantKey,
            final String variantCacheKey) throws ResourceIOException {
        HttpCacheEntry src = existing;
        if (src == null) {
            src = entry;
        }

        Resource resource = null;
        if (src.getResource() != null) {
            resource = resourceFactory.copy(requestId, src.getResource());
        }
        final Map<String,String> variantMap = new HashMap<>(src.getVariantMap());
        variantMap.put(variantKey, variantCacheKey);
        return new HttpCacheEntry(
                src.getRequestDate(),
                src.getResponseDate(),
                src.getStatus(),
                src.getAllHeaders(),
                resource,
                variantMap);
    }

    @Override
    public HttpCacheEntry updateCacheEntry(
            final HttpHost target,
            final HttpRequest request,
            final HttpCacheEntry stale,
            final HttpResponse originResponse,
            final Date requestSent,
            final Date responseReceived) throws ResourceIOException {
        final HttpCacheEntry updatedEntry = cacheEntryUpdater.updateCacheEntry(
                request.getRequestUri(),
                stale,
                requestSent,
                responseReceived,
                originResponse);
        storeInCache(target, request, updatedEntry);
        return updatedEntry;
    }

    @Override
    public HttpCacheEntry updateVariantCacheEntry(final HttpHost target, final HttpRequest request,
            final HttpCacheEntry stale, final HttpResponse originResponse,
            final Date requestSent, final Date responseReceived, final String cacheKey) throws ResourceIOException {
        final HttpCacheEntry updatedEntry = cacheEntryUpdater.updateCacheEntry(
                request.getRequestUri(),
                stale,
                requestSent,
                responseReceived,
                originResponse);
        storage.putEntry(cacheKey, updatedEntry);
        return updatedEntry;
    }

    public HttpCacheEntry createCacheEntry(
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Date requestSent,
            final Date responseReceived) throws ResourceIOException {
        final Resource resource;
        if (content != null) {
            resource = resourceFactory.generate(request.getRequestUri(), content.array(), 0, content.length());
        } else {
            resource = null;
        }
        final HttpCacheEntry entry = new HttpCacheEntry(
                requestSent,
                responseReceived,
                originResponse.getCode(),
                originResponse.getAllHeaders(),
                resource);
        storeInCache(host, request, entry);
        return entry;
    }

    @Override
    public HttpCacheEntry getCacheEntry(final HttpHost host, final HttpRequest request) throws ResourceIOException {
        final HttpCacheEntry root = storage.getEntry(uriExtractor.generateKey(host, request));
        if (root == null) {
            return null;
        }
        if (!root.hasVariants()) {
            return root;
        }
        final String variantCacheKey = root.getVariantMap().get(uriExtractor.generateVariantKey(request, root));
        if (variantCacheKey == null) {
            return null;
        }
        return storage.getEntry(variantCacheKey);
    }

    @Override
    public void flushInvalidatedCacheEntriesFor(final HttpHost host,
            final HttpRequest request) throws ResourceIOException {
        cacheInvalidator.flushInvalidatedCacheEntries(host, request);
    }

    @Override
    public Map<String, Variant> getVariantCacheEntriesWithEtags(final HttpHost host, final HttpRequest request)
            throws ResourceIOException {
        final Map<String,Variant> variants = new HashMap<>();
        final HttpCacheEntry root = storage.getEntry(uriExtractor.generateKey(host, request));
        if (root == null || !root.hasVariants()) {
            return variants;
        }
        for(final Map.Entry<String, String> variant : root.getVariantMap().entrySet()) {
            final String variantKey = variant.getKey();
            final String variantCacheKey = variant.getValue();
            addVariantWithEtag(variantKey, variantCacheKey, variants);
        }
        return variants;
    }

    private void addVariantWithEtag(final String variantKey,
            final String variantCacheKey, final Map<String, Variant> variants)
            throws ResourceIOException {
        final HttpCacheEntry entry = storage.getEntry(variantCacheKey);
        if (entry == null) {
            return;
        }
        final Header etagHeader = entry.getFirstHeader(HeaderConstants.ETAG);
        if (etagHeader == null) {
            return;
        }
        variants.put(etagHeader.getValue(), new Variant(variantKey, variantCacheKey, entry));
    }

}
