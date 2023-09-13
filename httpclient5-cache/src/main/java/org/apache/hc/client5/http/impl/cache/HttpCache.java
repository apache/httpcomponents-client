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
import java.util.List;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.ByteArrayBuffer;

interface HttpCache {

    /**
     * Returns a result with either a fully matching {@link HttpCacheEntry}
     * a partial match with a list of known variants or null if no match could be found.
     */
    CacheMatch match(HttpHost host, HttpRequest request);

    /**
     * Retrieves variant {@link HttpCacheEntry}s for the given hit.
     */
    List<CacheHit> getVariants(CacheHit hit);

    /**
     * Stores {@link HttpRequest} / {@link HttpResponse} exchange details in the cache.
     */
    CacheHit store(
            HttpHost host,
            HttpRequest request,
            HttpResponse originResponse,
            ByteArrayBuffer content,
            Instant requestSent,
            Instant responseReceived);

    /**
     * Updates {@link HttpCacheEntry} using details from a 304 {@link HttpResponse} and
     * updates the root entry if the given cache entry represents a variant.
     */
    CacheHit update(
            CacheHit stale,
            HttpHost host,
            HttpRequest request,
            HttpResponse originResponse,
            Instant requestSent,
            Instant responseReceived);

    /**
     * Stores {@link HttpRequest} / {@link HttpResponse} exchange details in the cache
     * from the negotiated {@link HttpCacheEntry}.
     */
    CacheHit storeFromNegotiated(
            CacheHit negotiated,
            HttpHost host,
            HttpRequest request,
            HttpResponse originResponse,
            Instant requestSent,
            Instant responseReceived);

    /**
     * Evicts {@link HttpCacheEntry}s invalidated by the given message exchange.
     */
    void evictInvalidatedEntries(HttpHost host, HttpRequest request, HttpResponse response);

}
