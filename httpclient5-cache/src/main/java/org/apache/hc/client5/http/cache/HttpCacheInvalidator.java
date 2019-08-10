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
package org.apache.hc.client5.http.cache;

import java.net.URI;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

/**
 * Given a particular HTTP request / response pair, flush any cache entries
 * that this exchange would invalidate.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public interface HttpCacheInvalidator {

    /**
     * Flush {@link HttpCacheEntry}s invalidated by the given request.
     *
     * @param host backend host
     * @param request request message
     * @param cacheKeyResolver cache key resolver used by cache storage
     * @param cacheStorage internal cache storage
     *
     * @since 5.0
     */
    void flushCacheEntriesInvalidatedByRequest(
            HttpHost host,
            HttpRequest request,
            Resolver<URI, String> cacheKeyResolver,
            HttpCacheStorage cacheStorage);

    /**
     * Flush {@link HttpCacheEntry}s invalidated by the given message exchange.
     *
     * @param host backend host
     * @param request request message
     * @param response response message
     * @param cacheKeyResolver cache key resolver used by cache storage
     * @param cacheStorage internal cache storage
     *
     * @since 5.0
     */
    void flushCacheEntriesInvalidatedByExchange(
            HttpHost host,
            HttpRequest request,
            HttpResponse response,
            Resolver<URI, String> cacheKeyResolver,
            HttpCacheStorage cacheStorage);

}
