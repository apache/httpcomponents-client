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
import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * Creates new {@link HttpCacheEntry}s and updates existing ones with new or updated information
 * based on the response from the origin server.
 */
class CacheUpdateHandler {

    private final ResourceFactory resourceFactory;

    CacheUpdateHandler() {
        this(new HeapResourceFactory());
    }

    CacheUpdateHandler(final ResourceFactory resourceFactory) {
        super();
        this.resourceFactory = resourceFactory;
    }

    /**
     * Creates a cache entry for the given request, origin response message and response content.
     */
    public HttpCacheEntry createtCacheEntry(
            final HttpRequest request,
            final HttpResponse originResponse,
            final ByteArrayBuffer content,
            final Date requestSent,
            final Date responseReceived) throws ResourceIOException {
        return new HttpCacheEntry(
                requestSent,
                responseReceived,
                originResponse.getCode(),
                originResponse.getHeaders(),
                content != null ? resourceFactory.generate(request.getRequestUri(), content.array(), 0, content.length()) : null);
    }

    /**
     * Update the entry with the new information from the response.  Should only be used for
     * 304 responses.
     */
    public HttpCacheEntry updateCacheEntry(
            final String requestId,
            final HttpCacheEntry entry,
            final Date requestDate,
            final Date responseDate,
            final HttpResponse response) throws ResourceIOException {
        Args.check(response.getCode() == HttpStatus.SC_NOT_MODIFIED,
                "Response must have 304 status code");
        final Header[] mergedHeaders = mergeHeaders(entry, response);
        Resource resource = null;
        if (entry.getResource() != null) {
            resource = resourceFactory.copy(requestId, entry.getResource());
        }
        return new HttpCacheEntry(
                requestDate,
                responseDate,
                entry.getStatus(),
                mergedHeaders,
                resource);
    }

    public HttpCacheEntry updateParentCacheEntry(
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
                src.getHeaders(),
                resource,
                variantMap);
    }

    private Header[] mergeHeaders(final HttpCacheEntry entry, final HttpResponse response) {
        if (DateUtils.isAfter(entry, response, HttpHeaders.DATE)) {
            return entry.getHeaders();
        }
        final HeaderGroup headerGroup = new HeaderGroup();
        headerGroup.setHeaders(entry.getHeaders());
        // Remove cache headers that match response
        for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
            final Header responseHeader = it.next();
            // Since we do not expect a content in a 304 response, should retain the original Content-Encoding header
            if (HttpHeaders.CONTENT_ENCODING.equals(responseHeader.getName())) {
                continue;
            }
            headerGroup.removeHeaders(responseHeader.getName());
        }
        // remove cache entry 1xx warnings
        for (final Iterator<Header> it = headerGroup.headerIterator(); it.hasNext(); ) {
            final Header cacheHeader = it.next();
            if (HeaderConstants.WARNING.equalsIgnoreCase(cacheHeader.getName())) {
                final String warningValue = cacheHeader.getValue();
                if (warningValue != null && warningValue.startsWith("1")) {
                    it.remove();
                }
            }
        }
        for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
            final Header responseHeader = it.next();
            // Since we do not expect a content in a 304 response, should update the cache entry with Content-Encoding
            if (HttpHeaders.CONTENT_ENCODING.equals(responseHeader.getName())) {
                continue;
            }
            headerGroup.addHeader(responseHeader);
        }
        return headerGroup.getHeaders();
    }

}
