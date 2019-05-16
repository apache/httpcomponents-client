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

import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.impl.MessageCopier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.MessageSupport;

class ConditionalRequestBuilder<T extends HttpRequest> {

    private final MessageCopier<T> messageCopier;

    ConditionalRequestBuilder(final MessageCopier<T> messageCopier) {
        this.messageCopier = messageCopier;
    }

    /**
     * When a {@link HttpCacheEntry} is stale but 'might' be used as a response
     * to an {@link org.apache.hc.core5.http.HttpRequest} we will attempt to revalidate
     * the entry with the origin.  Build the origin {@link org.apache.hc.core5.http.HttpRequest}
     * here and return it.
     *
     * @param request the original request from the caller
     * @param cacheEntry the entry that needs to be re-validated
     * @return the wrapped request
     */
    public T buildConditionalRequest(final T request, final HttpCacheEntry cacheEntry) {
        final T newRequest = messageCopier.copy(request);
        newRequest.setHeaders(request.getHeaders());
        final Header eTag = cacheEntry.getFirstHeader(HeaderConstants.ETAG);
        if (eTag != null) {
            newRequest.setHeader(HeaderConstants.IF_NONE_MATCH, eTag.getValue());
        }
        final Header lastModified = cacheEntry.getFirstHeader(HeaderConstants.LAST_MODIFIED);
        if (lastModified != null) {
            newRequest.setHeader(HeaderConstants.IF_MODIFIED_SINCE, lastModified.getValue());
        }
        boolean mustRevalidate = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(cacheEntry, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE.equalsIgnoreCase(elt.getName())
                    || HeaderConstants.CACHE_CONTROL_PROXY_REVALIDATE.equalsIgnoreCase(elt.getName())) {
                mustRevalidate = true;
                break;
            }
        }
        if (mustRevalidate) {
            newRequest.addHeader(HeaderConstants.CACHE_CONTROL, HeaderConstants.CACHE_CONTROL_MAX_AGE + "=0");
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
    public T buildConditionalRequestFromVariants(final T request, final Map<String, Variant> variants) {
        final T newRequest = messageCopier.copy(request);
        newRequest.setHeaders(request.getHeaders());

        // we do not support partial content so all etags are used
        final StringBuilder etags = new StringBuilder();
        boolean first = true;
        for(final String etag : variants.keySet()) {
            if (!first) {
                etags.append(",");
            }
            first = false;
            etags.append(etag);
        }

        newRequest.setHeader(HeaderConstants.IF_NONE_MATCH, etags.toString());
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
        final T newRequest = messageCopier.copy(request);
        newRequest.addHeader(HeaderConstants.CACHE_CONTROL,HeaderConstants.CACHE_CONTROL_NO_CACHE);
        newRequest.addHeader(HeaderConstants.PRAGMA,HeaderConstants.CACHE_CONTROL_NO_CACHE);
        newRequest.removeHeaders(HeaderConstants.IF_RANGE);
        newRequest.removeHeaders(HeaderConstants.IF_MATCH);
        newRequest.removeHeaders(HeaderConstants.IF_NONE_MATCH);
        newRequest.removeHeaders(HeaderConstants.IF_UNMODIFIED_SINCE);
        newRequest.removeHeaders(HeaderConstants.IF_MODIFIED_SINCE);
        return newRequest;
    }

}
