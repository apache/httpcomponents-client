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

import java.util.Collection;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.util.CharArrayBuffer;

class ConditionalRequestBuilder<T extends HttpRequest> {

    private final Factory<T, T> messageCopier;

    ConditionalRequestBuilder(final Factory<T, T> messageCopier) {
        this.messageCopier = messageCopier;
    }

    /**
     * When a {@link HttpCacheEntry} is stale but 'might' be used as a response
     * to an {@link org.apache.hc.core5.http.HttpRequest} we will attempt to revalidate
     * the entry with the origin.  Build the origin {@link org.apache.hc.core5.http.HttpRequest}
     * here and return it.
     *
     * @param cacheControl the cache control directives.
     * @param request the original request from the caller
     * @param cacheEntry the entry that needs to be re-validated
     * @return the wrapped request
     */
    public T buildConditionalRequest(final ResponseCacheControl cacheControl, final T request, final HttpCacheEntry cacheEntry) {
        final T newRequest = messageCopier.create(request);

        final ETag eTag = cacheEntry.getETag();
        if (eTag != null) {
            newRequest.setHeader(HttpHeaders.IF_NONE_MATCH, eTag.toString());
        }
        final Header lastModified = cacheEntry.getFirstHeader(HttpHeaders.LAST_MODIFIED);
        if (lastModified != null) {
            newRequest.setHeader(HttpHeaders.IF_MODIFIED_SINCE, lastModified.getValue());
        }
        if (cacheControl.isMustRevalidate() || cacheControl.isProxyRevalidate()) {
            newRequest.addHeader(HttpHeaders.CACHE_CONTROL, HeaderConstants.CACHE_CONTROL_MAX_AGE + "=0");
        }
        return newRequest;

    }

    /**
     * When a {@link HttpCacheEntry} does not exist for a specific
     * {@link org.apache.hc.core5.http.HttpRequest} we attempt to see if an existing
     * {@link HttpCacheEntry} is appropriate by building a conditional
     * {@link org.apache.hc.core5.http.HttpRequest} using the variants' ETag values.
     * If no such values exist, the request is unmodified
     *
     * @param request the original request from the caller
     * @param variants
     * @return the wrapped request
     */
    public T buildConditionalRequestFromVariants(final T request, final Collection<ETag> variants) {
        final T newRequest = messageCopier.create(request);
        final CharArrayBuffer buffer = new CharArrayBuffer(256);
        buffer.append(HttpHeaders.IF_NONE_MATCH);
        buffer.append(": ");
        int i = 0;
        for (final ETag variant : variants) {
            if (i > 0) {
                buffer.append(", ");
            }
            variant.format(buffer);
            i++;
        }
        newRequest.setHeader(BufferedHeader.create(buffer));
        return newRequest;
    }

    /**
     * Returns a request to unconditionally validate a cache entry with
     * the origin. In certain cases (due to multiple intervening caches)
     * our cache may actually receive a response to a normal conditional
     * validation where the Date header is actually older than that of
     * our current cache entry. In this case, the protocol recommendation
     * is to retry the validation and force syncup with the origin.
     * @param request client request we are trying to satisfy
     * @return an unconditional validation request
     */
    public T buildUnconditionalRequest(final T request) {
        final T newRequest = messageCopier.create(request);
        newRequest.addHeader(HttpHeaders.CACHE_CONTROL,HeaderConstants.CACHE_CONTROL_NO_CACHE);
        newRequest.removeHeaders(HttpHeaders.IF_RANGE);
        newRequest.removeHeaders(HttpHeaders.IF_MATCH);
        newRequest.removeHeaders(HttpHeaders.IF_NONE_MATCH);
        newRequest.removeHeaders(HttpHeaders.IF_UNMODIFIED_SINCE);
        newRequest.removeHeaders(HttpHeaders.IF_MODIFIED_SINCE);
        return newRequest;
    }

}
