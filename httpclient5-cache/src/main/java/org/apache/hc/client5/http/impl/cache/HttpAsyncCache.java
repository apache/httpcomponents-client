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
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.ByteArrayBuffer;

interface HttpAsyncCache {

    String generateKey (HttpHost host, HttpRequest request, HttpCacheEntry cacheEntry);

    /**
     * Clear all matching {@link HttpCacheEntry}s.
     */
    Cancellable flushCacheEntriesFor(
            HttpHost host, HttpRequest request, FutureCallback<Boolean> callback);

    /**
     * Flush {@link HttpCacheEntry}s invalidated by the given request
     */
    Cancellable flushCacheEntriesInvalidatedByRequest(
            HttpHost host, HttpRequest request, FutureCallback<Boolean> callback);

    /**
     * Flush {@link HttpCacheEntry}s invalidated by the given message exchange.
     */
    Cancellable flushCacheEntriesInvalidatedByExchange(
            HttpHost host, HttpRequest request, HttpResponse response, FutureCallback<Boolean> callback);

    /**
     * Retrieve matching {@link HttpCacheEntry} from the cache if it exists
     */
    Cancellable getCacheEntry(
            HttpHost host, HttpRequest request, FutureCallback<HttpCacheEntry> callback);

    /**
     * Retrieve all variants from the cache, if there are no variants then an empty
     */
    Cancellable getVariantCacheEntriesWithEtags(
            HttpHost host, HttpRequest request, FutureCallback<Map<String,Variant>> callback);

    /**
     * Store a {@link HttpResponse} in the cache if possible, and return
     */
    Cancellable createCacheEntry(
            HttpHost host,
            HttpRequest request,
            HttpResponse originResponse,
            ByteArrayBuffer content,
            Date requestSent,
            Date responseReceived,
            FutureCallback<HttpCacheEntry> callback);

    /**
     * Update a {@link HttpCacheEntry} using a 304 {@link HttpResponse}.
     */
    Cancellable updateCacheEntry(
            HttpHost host,
            HttpRequest request,
            HttpCacheEntry stale,
            HttpResponse originResponse,
            Date requestSent,
            Date responseReceived,
            FutureCallback<HttpCacheEntry> callback);

    /**
     * Update a specific {@link HttpCacheEntry} representing a cached variant
     * using a 304 {@link HttpResponse}.
     */
    Cancellable updateVariantCacheEntry(
            HttpHost host,
            HttpRequest request,
            HttpResponse originResponse,
            Variant variant,
            Date requestSent,
            Date responseReceived,
            FutureCallback<HttpCacheEntry> callback);

    /**
     * Specifies cache should reuse the given cached variant to satisfy
     * requests whose varying headers match those of the given client request.
     */
    Cancellable reuseVariantEntryFor(
            HttpHost host,
            HttpRequest req,
            Variant variant,
            FutureCallback<Boolean> callback);
}
