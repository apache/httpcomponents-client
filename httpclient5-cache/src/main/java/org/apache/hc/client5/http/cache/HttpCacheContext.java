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

import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Adaptor class that provides convenience type safe setters and getters
 * for caching {@link HttpContext} attributes.
 *
 * @since 4.3
 */
public class HttpCacheContext extends HttpClientContext {

    /**
     * This is the name under which the {@link CacheResponseStatus} of a request
     * (for example, whether it resulted in a cache hit) will be recorded if an
     * {@link HttpContext} is provided during execution.
     */
    public static final String CACHE_RESPONSE_STATUS = "http.cache.response.status";

    /**
     * @since 5.4
     */
    public static final String CACHE_ENTRY = "http.cache.entry";

    /**
     * @since 5.4
     */
    public static final String CACHE_REQUEST_CONTROL = "http.cache.request.control";

    /**
     * @since 5.4
     */
    public static final String CACHE_RESPONSE_CONTROL = "http.cache.response.control";

    public static HttpCacheContext adapt(final HttpContext context) {
        if (context instanceof HttpCacheContext) {
            return (HttpCacheContext) context;
        } else {
            return new HttpCacheContext(context);
        }
    }

    public static HttpCacheContext create() {
        return new HttpCacheContext();
    }

    public HttpCacheContext(final HttpContext context) {
        super(context);
    }

    public HttpCacheContext() {
        super();
    }

    public CacheResponseStatus getCacheResponseStatus() {
        return getAttribute(CACHE_RESPONSE_STATUS, CacheResponseStatus.class);
    }

    /**
     * @since 5.4
     */
    public void setCacheResponseStatus(final CacheResponseStatus status) {
        setAttribute(CACHE_RESPONSE_STATUS, status);
    }

    /**
     * @since 5.4
     */
    public RequestCacheControl getRequestCacheControl() {
        final RequestCacheControl cacheControl = getAttribute(CACHE_REQUEST_CONTROL, RequestCacheControl.class);
        return cacheControl != null ? cacheControl : RequestCacheControl.DEFAULT;
    }

    /**
     * @since 5.4
     */
    public void setRequestCacheControl(final RequestCacheControl requestCacheControl) {
        setAttribute(CACHE_REQUEST_CONTROL, requestCacheControl);
    }

    /**
     * @since 5.4
     */
    public ResponseCacheControl getResponseCacheControl() {
        final ResponseCacheControl cacheControl = getAttribute(CACHE_RESPONSE_CONTROL, ResponseCacheControl.class);
        return cacheControl != null ? cacheControl : ResponseCacheControl.DEFAULT;
    }

    /**
     * @since 5.4
     */
    public void setResponseCacheControl(final ResponseCacheControl responseCacheControl) {
        setAttribute(CACHE_RESPONSE_CONTROL, responseCacheControl);
    }

    /**
     * @since 5.4
     */
    public HttpCacheEntry getCacheEntry() {
        return getAttribute(CACHE_ENTRY, HttpCacheEntry.class);
    }

    /**
     * @since 5.4
     */
    public void setCacheEntry(final HttpCacheEntry entry) {
        setAttribute(CACHE_ENTRY, entry);
    }

}
