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

import static org.apache.hc.client5.http.utils.DateUtils.parseStandardDate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RequestProtocolCompliance {
    private final boolean weakETagOnPutDeleteAllowed;

    private static final Logger LOG = LoggerFactory.getLogger(RequestProtocolCompliance.class);

    public RequestProtocolCompliance() {
        super();
        this.weakETagOnPutDeleteAllowed = false;
    }

    public RequestProtocolCompliance(final boolean weakETagOnPutDeleteAllowed) {
        super();
        this.weakETagOnPutDeleteAllowed = weakETagOnPutDeleteAllowed;
    }

    /**
     * Test to see if the {@link HttpRequest} is HTTP1.1 compliant or not
     * and if not, we can not continue.
     *
     * @param request the HttpRequest Object
     * @return list of {@link RequestProtocolError}
     */
    public List<RequestProtocolError> requestIsFatallyNonCompliant(final HttpRequest request, final boolean resourceExists) {
        final List<RequestProtocolError> theErrors = new ArrayList<>();

        RequestProtocolError anError = requestHasWeakETagAndRange(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        if (!weakETagOnPutDeleteAllowed) {
            anError = requestHasWeekETagForPUTOrDELETEIfMatch(request, resourceExists);
            if (anError != null) {
                theErrors.add(anError);
            }
        }

        return theErrors;
    }

    /**
     * If the {@link HttpRequest} is non-compliant but 'fixable' we go ahead and
     * fix the request here.
     *
     * @param request the request to check for compliance
     */
    public void makeRequestCompliant(final HttpRequest request) {
        decrementOPTIONSMaxForwardsIfGreaterThen0(request);

        if (requestVersionIsTooLow(request) || requestMinorVersionIsTooHighMajorVersionsMatch(request)) {
            request.setVersion(HttpVersion.HTTP_1_1);
        }
    }

    private void decrementOPTIONSMaxForwardsIfGreaterThen0(final HttpRequest request) {
        if (!HeaderConstants.OPTIONS_METHOD.equals(request.getMethod())) {
            return;
        }

        final Header maxForwards = request.getFirstHeader(HttpHeaders.MAX_FORWARDS);
        if (maxForwards == null) {
            return;
        }

        request.removeHeaders(HttpHeaders.MAX_FORWARDS);
        final int currentMaxForwards = Integer.parseInt(maxForwards.getValue());

        request.setHeader(HttpHeaders.MAX_FORWARDS, Integer.toString(currentMaxForwards - 1));
    }

    protected boolean requestMinorVersionIsTooHighMajorVersionsMatch(final HttpRequest request) {
        final ProtocolVersion requestProtocol = request.getVersion();
        if (requestProtocol == null) {
            return false;
        }
        if (requestProtocol.getMajor() != HttpVersion.HTTP_1_1.getMajor()) {
            return false;
        }

        return requestProtocol.getMinor() > HttpVersion.HTTP_1_1.getMinor();
    }

    protected boolean requestVersionIsTooLow(final HttpRequest request) {
        final ProtocolVersion requestProtocol = request.getVersion();
        return requestProtocol != null && requestProtocol.compareToVersion(HttpVersion.HTTP_1_1) < 0;
    }

    private RequestProtocolError requestHasWeakETagAndRange(final HttpRequest request) {
        final String method = request.getMethod();
        if (!(HeaderConstants.GET_METHOD.equals(method) || HeaderConstants.HEAD_METHOD.equals(method))) {
            return null;
        }

        if (!request.containsHeader(HttpHeaders.RANGE)) {
            return null;
        }

        final Instant ifRangeInstant = parseStandardDate(request, HttpHeaders.IF_RANGE);
        final Instant lastModifiedInstant = parseStandardDate(request, HttpHeaders.LAST_MODIFIED);

        for (final Iterator<Header> it = request.headerIterator(HttpHeaders.IF_RANGE); it.hasNext(); ) {
            final String val = it.next().getValue();
            if (val.startsWith("W/")) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Weak ETag found in If-Range header");
                }
                return RequestProtocolError.WEAK_ETAG_AND_RANGE_ERROR;
            } else {
                // Not a strong validator or doesn't match Last-Modified
                if (ifRangeInstant != null && lastModifiedInstant != null
                        && !ifRangeInstant.equals(lastModifiedInstant)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("If-Range does not match Last-Modified");
                    }
                    return RequestProtocolError.WEAK_ETAG_AND_RANGE_ERROR;
                }
            }
        }
        return null;
    }

    private RequestProtocolError requestHasWeekETagForPUTOrDELETEIfMatch(final HttpRequest request, final boolean resourceExists) {
        final String method = request.getMethod();
        if (!(HeaderConstants.PUT_METHOD.equals(method) || HeaderConstants.DELETE_METHOD.equals(method)
                || HeaderConstants.POST_METHOD.equals(method))
        ) {
            return null;
        }

        for (final Iterator<Header> it = request.headerIterator(HttpHeaders.IF_MATCH); it.hasNext();) {
            final String val = it.next().getValue();
            if (val.equals("*") && !resourceExists) {
                return null;
            }
            if (val.startsWith("W/")) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Weak ETag found in If-Match header");
                }
                return RequestProtocolError.WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR;
            }
        }

        for (final Iterator<Header> it = request.headerIterator(HttpHeaders.IF_NONE_MATCH); it.hasNext();) {
            final String val = it.next().getValue();
            if (val.startsWith("W/") || (val.equals("*") && resourceExists)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Weak ETag found in If-None-Match header");
                }
                return RequestProtocolError.WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR;
            }
        }

        return null;
    }

}
