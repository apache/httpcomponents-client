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

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.client.RequestWrapper;

/**
 * @since 4.1
 */
@Immutable
class ConditionalRequestBuilder {

    /**
     * When a {@link HttpCacheEntry} is stale but 'might' be used as a response
     * to an {@link HttpRequest} we will attempt to revalidate the entry with
     * the origin.  Build the origin {@link HttpRequest} here and return it.
     *
     * @param request the original request from the caller
     * @param cacheEntry the entry that needs to be revalidated
     * @return the wrapped request
     * @throws ProtocolException when I am unable to build a new origin request.
     */
    public HttpRequest buildConditionalRequest(HttpRequest request, HttpCacheEntry cacheEntry)
            throws ProtocolException {
        RequestWrapper wrapperRequest = new RequestWrapper(request);
        wrapperRequest.resetHeaders();
        Header eTag = cacheEntry.getFirstHeader(HeaderConstants.ETAG);
        if (eTag != null) {
            wrapperRequest.setHeader(HeaderConstants.IF_NONE_MATCH, eTag.getValue());
        }
        Header lastModified = cacheEntry.getFirstHeader(HeaderConstants.LAST_MODIFIED);
        if (lastModified != null) {
            wrapperRequest.setHeader(HeaderConstants.IF_MODIFIED_SINCE, lastModified.getValue());
        }
        boolean mustRevalidate = false;
        for(Header h : cacheEntry.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for(HeaderElement elt : h.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE.equalsIgnoreCase(elt.getName())
                    || HeaderConstants.CACHE_CONTROL_PROXY_REVALIDATE.equalsIgnoreCase(elt.getName())) {
                    mustRevalidate = true;
                    break;
                }
            }
        }
        if (mustRevalidate) {
            wrapperRequest.addHeader("Cache-Control","max-age=0");
        }
        return wrapperRequest;

    }

    /**
     * Returns a request to unconditionally validate a cache entry with
     * the origin. In certain cases (due to multiple intervening caches)
     * our cache may actually receive a response to a normal conditional
     * validation where the Date header is actually older than that of
     * our current cache entry. In this case, the protocol recommendation
     * is to retry the validation and force syncup with the origin.
     * @param request client request we are trying to satisfy
     * @param entry existing cache entry we are trying to validate
     * @return an unconditional validation request
     * @throws ProtocolException
     */
    public HttpRequest buildUnconditionalRequest(HttpRequest request,
            HttpCacheEntry entry) throws ProtocolException {
        RequestWrapper wrapped = new RequestWrapper(request);
        wrapped.resetHeaders();
        wrapped.addHeader("Cache-Control","no-cache");
        wrapped.addHeader("Pragma","no-cache");
        wrapped.removeHeaders("If-Range");
        wrapped.removeHeaders("If-Match");
        wrapped.removeHeaders("If-None-Match");
        wrapped.removeHeaders("If-Unmodified-Since");
        wrapped.removeHeaders("If-Modified-Since");
        return wrapped;
    }

}
