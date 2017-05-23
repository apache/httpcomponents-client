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

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;

/**
 * Rebuilds an {@link HttpResponse} from a {@link net.sf.ehcache.CacheEntry}
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class CachedHttpResponseGenerator {

    private final CacheValidityPolicy validityStrategy;

    CachedHttpResponseGenerator(final CacheValidityPolicy validityStrategy) {
        super();
        this.validityStrategy = validityStrategy;
    }

    CachedHttpResponseGenerator() {
        this(new CacheValidityPolicy());
    }

    /**
     * If I was able to use a {@link CacheEntity} to response to the {@link HttpRequest} then
     * generate an {@link HttpResponse} based on the cache entry.
     * @param request {@link HttpRequest} to generate the response for
     * @param entry {@link CacheEntity} to transform into an {@link HttpResponse}
     * @return {@link HttpResponse} that was constructed
     */
    ClassicHttpResponse generateResponse(final HttpRequest request, final HttpCacheEntry entry) {
        final Date now = new Date();
        final ClassicHttpResponse response = new BasicClassicHttpResponse(entry.getStatus());
        response.setVersion(HttpVersion.DEFAULT);

        response.setHeaders(entry.getAllHeaders());

        if (responseShouldContainEntity(request, entry)) {
            final HttpEntity entity = new CacheEntity(entry);
            addMissingContentLengthHeader(response, entity);
            response.setEntity(entity);
        }

        final long age = this.validityStrategy.getCurrentAgeSecs(entry, now);
        if (age > 0) {
            if (age >= Integer.MAX_VALUE) {
                response.setHeader(HeaderConstants.AGE, "2147483648");
            } else {
                response.setHeader(HeaderConstants.AGE, "" + ((int) age));
            }
        }

        return response;
    }

    /**
     * Generate a 304 - Not Modified response from a {@link CacheEntity}.  This should be
     * used to respond to conditional requests, when the entry exists or has been re-validated.
     */
    ClassicHttpResponse generateNotModifiedResponse(final HttpCacheEntry entry) {

        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");

        // The response MUST include the following headers
        //  (http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html)

        // - Date, unless its omission is required by section 14.8.1
        Header dateHeader = entry.getFirstHeader(HttpHeaders.DATE);
        if (dateHeader == null) {
             dateHeader = new BasicHeader(HttpHeaders.DATE, DateUtils.formatDate(new Date()));
        }
        response.addHeader(dateHeader);

        // - ETag and/or Content-Location, if the header would have been sent
        //   in a 200 response to the same request
        final Header etagHeader = entry.getFirstHeader(HeaderConstants.ETAG);
        if (etagHeader != null) {
            response.addHeader(etagHeader);
        }

        final Header contentLocationHeader = entry.getFirstHeader("Content-Location");
        if (contentLocationHeader != null) {
            response.addHeader(contentLocationHeader);
        }

        // - Expires, Cache-Control, and/or Vary, if the field-value might
        //   differ from that sent in any previous response for the same
        //   variant
        final Header expiresHeader = entry.getFirstHeader(HeaderConstants.EXPIRES);
        if (expiresHeader != null) {
            response.addHeader(expiresHeader);
        }

        final Header cacheControlHeader = entry.getFirstHeader(HeaderConstants.CACHE_CONTROL);
        if (cacheControlHeader != null) {
            response.addHeader(cacheControlHeader);
        }

        final Header varyHeader = entry.getFirstHeader(HeaderConstants.VARY);
        if (varyHeader != null) {
            response.addHeader(varyHeader);
        }

        return response;
    }

    private void addMissingContentLengthHeader(final HttpResponse response, final HttpEntity entity) {
        if (transferEncodingIsPresent(response)) {
            return;
        }

        Header contentLength = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null) {
            contentLength = new BasicHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(entity
                    .getContentLength()));
            response.setHeader(contentLength);
        }
    }

    private boolean transferEncodingIsPresent(final HttpResponse response) {
        final Header hdr = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        return hdr != null;
    }

    private boolean responseShouldContainEntity(final HttpRequest request, final HttpCacheEntry cacheEntry) {
        return request.getMethod().equals(HeaderConstants.GET_METHOD) && cacheEntry.getResource() != null;
    }

}
