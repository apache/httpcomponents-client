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

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
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
        final Date now = new Date();
        final SimpleHttpResponse response = new SimpleHttpResponse(entry.getStatus());
        response.setVersion(HttpVersion.DEFAULT);

        response.setHeaders(entry.getHeaders());

        if (responseShouldContainEntity(request, entry)) {
            final Resource resource = entry.getResource();
            final Header h = entry.getFirstHeader(HttpHeaders.CONTENT_TYPE);
            final ContentType contentType = h != null ? ContentType.parse(h.getValue()) : null;
            final byte[] content = resource.get();
            addMissingContentLengthHeader(response, content);
            response.setBody(content, contentType);
        }

        final TimeValue age = this.validityStrategy.getCurrentAge(entry, now);
        if (TimeValue.isPositive(age)) {
            if (age.compareTo(CacheValidityPolicy.MAX_AGE) >= 0) {
                response.setHeader(HeaderConstants.AGE, "" + CacheValidityPolicy.MAX_AGE.toSeconds());
            } else {
                response.setHeader(HeaderConstants.AGE, "" + age.toSeconds());
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

    private void addMissingContentLengthHeader(final HttpResponse response, final byte[] body) {
        if (transferEncodingIsPresent(response)) {
            return;
        }
        // Some well known proxies respond with Content-Length=0, when returning 304. For robustness, always
        // use the cached entity's content length, as modern browsers do.
        response.setHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length));
    }

    private boolean transferEncodingIsPresent(final HttpResponse response) {
        final Header hdr = response.getFirstHeader(HttpHeaders.TRANSFER_ENCODING);
        return hdr != null;
    }

    private boolean responseShouldContainEntity(final HttpRequest request, final HttpCacheEntry cacheEntry) {
        return request.getMethod().equals(HeaderConstants.GET_METHOD) && cacheEntry.getResource() != null;
    }

    /**
     * Extract error information about the {@link HttpRequest} telling the 'caller'
     * that a problem occured.
     *
     * @param errorCheck What type of error should I get
     * @return The {@link HttpResponse} that is the error generated
     */
    public SimpleHttpResponse getErrorForRequest(final RequestProtocolError errorCheck) {
        switch (errorCheck) {
            case BODY_BUT_NO_LENGTH_ERROR:
                return SimpleHttpResponse.create(HttpStatus.SC_LENGTH_REQUIRED);

            case WEAK_ETAG_AND_RANGE_ERROR:
                return SimpleHttpResponse.create(HttpStatus.SC_BAD_REQUEST,
                        "Weak eTag not compatible with byte range", ContentType.DEFAULT_TEXT);

            case WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR:
                return SimpleHttpResponse.create(HttpStatus.SC_BAD_REQUEST,
                        "Weak eTag not compatible with PUT or DELETE requests");

            case NO_CACHE_DIRECTIVE_WITH_FIELD_NAME:
                return SimpleHttpResponse.create(HttpStatus.SC_BAD_REQUEST,
                        "No-Cache directive MUST NOT include a field name");

            default:
                throw new IllegalStateException(
                        "The request was compliant, therefore no error can be generated for it.");

        }
    }

}
