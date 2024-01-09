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

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.TimeValue;

/**
 * Rebuilds an {@link HttpResponse} from a {@link HttpCacheEntry}
 */
class CachedHttpResponseGenerator {

    private final CacheValidityPolicy validityStrategy;

    CachedHttpResponseGenerator(final CacheValidityPolicy validityStrategy) {
        super();
        this.validityStrategy = validityStrategy;
    }

    /**
     * If it is legal to use cached content in response response to the {@link HttpRequest} then
     * generate an {@link HttpResponse} based on {@link HttpCacheEntry}.
     * @param request {@link HttpRequest} to generate the response for
     * @param entry {@link HttpCacheEntry} to transform into an {@link HttpResponse}
     * @return {@link SimpleHttpResponse} constructed response
     */
    SimpleHttpResponse generateResponse(final HttpRequest request, final HttpCacheEntry entry) throws ResourceIOException {
        final Instant now = Instant.now();
        final SimpleHttpResponse response = new SimpleHttpResponse(entry.getStatus());

        response.setHeaders(entry.getHeaders());

        if (responseShouldContainEntity(request, entry)) {
            final Resource resource = entry.getResource();
            final Header h = entry.getFirstHeader(HttpHeaders.CONTENT_TYPE);
            final ContentType contentType = h != null ? ContentType.parse(h.getValue()) : null;
            final byte[] content = resource.get();
            generateContentLength(response, content);
            response.setBody(content, contentType);
        }

        final TimeValue age = this.validityStrategy.getCurrentAge(entry, now);
        if (TimeValue.isPositive(age)) {
            if (age.compareTo(CacheSupport.MAX_AGE) >= 0) {
                response.setHeader(HttpHeaders.AGE, Long.toString(CacheSupport.MAX_AGE.toSeconds()));
            } else {
                response.setHeader(HttpHeaders.AGE, Long.toString(age.toSeconds()));
            }
        }

        return response;
    }

    /**
     * Generate a 304 - Not Modified response from the {@link HttpCacheEntry}. This should be
     * used to respond to conditional requests, when the entry exists or has been re-validated.
     */
    SimpleHttpResponse generateNotModifiedResponse(final HttpCacheEntry entry) {

        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not Modified");

        // The response MUST include the following headers

        // - Date
        Header dateHeader = entry.getFirstHeader(HttpHeaders.DATE);
        if (dateHeader == null) {
            dateHeader = new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(Instant.now()));
        }
        response.addHeader(dateHeader);

        // - ETag and/or Content-Location, if the header would have been sent
        //   in a 200 response to the same request
        final ETag eTag = entry.getETag();
        if (eTag != null) {
            response.addHeader(new BasicHeader(HttpHeaders.ETAG, eTag.toString()));
        }

        final Header contentLocationHeader = entry.getFirstHeader(HttpHeaders.CONTENT_LOCATION);
        if (contentLocationHeader != null) {
            response.addHeader(contentLocationHeader);
        }

        // - Expires, Cache-Control, and/or Vary, if the field-value might
        //   differ from that sent in any previous response for the same
        //   variant
        final Header expiresHeader = entry.getFirstHeader(HttpHeaders.EXPIRES);
        if (expiresHeader != null) {
            response.addHeader(expiresHeader);
        }

        final Header cacheControlHeader = entry.getFirstHeader(HttpHeaders.CACHE_CONTROL);
        if (cacheControlHeader != null) {
            response.addHeader(cacheControlHeader);
        }

        final Header varyHeader = entry.getFirstHeader(HttpHeaders.VARY);
        if (varyHeader != null) {
            response.addHeader(varyHeader);
        }

        //Since the goal of a 304 response is to minimize information transfer
        //when the recipient already has one or more cached representations, a
        //sender SHOULD NOT generate representation metadata other than the
        //above listed fields unless said metadata exists for the purpose of
        //guiding cache updates (e.g., Last-Modified might be useful if the
        //response does not have an ETag field).
        if (eTag == null) {
            final Header lastModifiedHeader = entry.getFirstHeader(HttpHeaders.LAST_MODIFIED);
            if (lastModifiedHeader != null) {
                response.addHeader(lastModifiedHeader);
            }
        }
        return response;
    }

    private void generateContentLength(final HttpResponse response, final byte[] body) {
        response.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length));
    }

    private boolean responseShouldContainEntity(final HttpRequest request, final HttpCacheEntry cacheEntry) {
        return Method.GET.isSame(request.getMethod()) && cacheEntry.getResource() != null;
    }

}
