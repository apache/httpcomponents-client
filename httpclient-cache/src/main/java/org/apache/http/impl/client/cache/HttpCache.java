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
import java.util.Date;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.cache.HttpCacheEntry;

/**
 * @since 4.1
 */
interface HttpCache {

    /**
     * Clear all matching {@link HttpCacheEntry}s.
     * @param host
     * @param request
     * @throws IOException
     */
    void flushCacheEntriesFor(HttpHost host, HttpRequest request)
        throws IOException;

    /**
     * Clear invalidated matching {@link HttpCacheEntry}s
     * @param host
     * @param request
     * @throws IOException
     */
    void flushInvalidatedCacheEntriesFor(HttpHost host, HttpRequest request)
        throws IOException;

    /**
     * Retrieve matching {@link HttpCacheEntry} from the cache if it exists
     * @param host
     * @param request
     * @return
     * @throws IOException
     */
    HttpCacheEntry getCacheEntry(HttpHost host, HttpRequest request)
        throws IOException;

    /**
     * Retrieve all variants from the cache, if there are no variants then an empty
     * {@link Map} is returned
     * @param host
     * @param request
     * @return a <code>Map</code> mapping Etags to variant cache entries
     * @throws IOException
     */
    Map<String,Variant> getVariantCacheEntriesWithEtags(HttpHost host, HttpRequest request)
        throws IOException;

    /**
     * Store a {@link HttpResponse} in the cache if possible, and return
     * @param host
     * @param request
     * @param originResponse
     * @param requestSent
     * @param responseReceived
     * @return
     * @throws IOException
     */
    HttpResponse cacheAndReturnResponse(
            HttpHost host, HttpRequest request, HttpResponse originResponse,
            Date requestSent, Date responseReceived)
        throws IOException;

    /**
     * Update a {@link HttpCacheEntry} using a 304 {@link HttpResponse}.
     * @param target
     * @param request
     * @param stale
     * @param originResponse
     * @param requestSent
     * @param responseReceived
     * @return
     * @throws IOException
     */
    HttpCacheEntry updateCacheEntry(
            HttpHost target, HttpRequest request, HttpCacheEntry stale, HttpResponse originResponse,
            Date requestSent, Date responseReceived)
        throws IOException;

}
