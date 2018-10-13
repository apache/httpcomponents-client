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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;

class RequestProtocolCompliance {
    private final boolean weakETagOnPutDeleteAllowed;

    public RequestProtocolCompliance() {
        super();
        this.weakETagOnPutDeleteAllowed = false;
    }

    public RequestProtocolCompliance(final boolean weakETagOnPutDeleteAllowed) {
        super();
        this.weakETagOnPutDeleteAllowed = weakETagOnPutDeleteAllowed;
    }

    private static final List<String> disallowedWithNoCache =
        Arrays.asList(HeaderConstants.CACHE_CONTROL_MIN_FRESH, HeaderConstants.CACHE_CONTROL_MAX_STALE, HeaderConstants.CACHE_CONTROL_MAX_AGE);

    /**
     * Test to see if the {@link HttpRequest} is HTTP1.1 compliant or not
     * and if not, we can not continue.
     *
     * @param request the HttpRequest Object
     * @return list of {@link RequestProtocolError}
     */
    public List<RequestProtocolError> requestIsFatallyNonCompliant(final HttpRequest request) {
        final List<RequestProtocolError> theErrors = new ArrayList<>();

        RequestProtocolError anError = requestHasWeakETagAndRange(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        if (!weakETagOnPutDeleteAllowed) {
            anError = requestHasWeekETagForPUTOrDELETEIfMatch(request);
            if (anError != null) {
                theErrors.add(anError);
            }
        }

        anError = requestContainsNoCacheDirectiveWithFieldName(request);
        if (anError != null) {
            theErrors.add(anError);
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
        stripOtherFreshnessDirectivesWithNoCache(request);

        if (requestVersionIsTooLow(request) || requestMinorVersionIsTooHighMajorVersionsMatch(request)) {
            request.setVersion(HttpVersion.HTTP_1_1);
        }
    }

    private void stripOtherFreshnessDirectivesWithNoCache(final HttpRequest request) {
        final List<HeaderElement> outElts = new ArrayList<>();
        boolean shouldStrip = false;
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (!disallowedWithNoCache.contains(elt.getName())) {
                outElts.add(elt);
            }
            if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elt.getName())) {
                shouldStrip = true;
            }
        }
        if (!shouldStrip) {
            return;
        }
        request.removeHeaders(HeaderConstants.CACHE_CONTROL);
        request.setHeader(HeaderConstants.CACHE_CONTROL, buildHeaderFromElements(outElts));
    }

    private String buildHeaderFromElements(final List<HeaderElement> outElts) {
        final StringBuilder newHdr = new StringBuilder("");
        boolean first = true;
        for(final HeaderElement elt : outElts) {
            if (!first) {
                newHdr.append(",");
            } else {
                first = false;
            }
            newHdr.append(elt.toString());
        }
        return newHdr.toString();
    }

    private void decrementOPTIONSMaxForwardsIfGreaterThen0(final HttpRequest request) {
        if (!HeaderConstants.OPTIONS_METHOD.equals(request.getMethod())) {
            return;
        }

        final Header maxForwards = request.getFirstHeader(HeaderConstants.MAX_FORWARDS);
        if (maxForwards == null) {
            return;
        }

        request.removeHeaders(HeaderConstants.MAX_FORWARDS);
        final int currentMaxForwards = Integer.parseInt(maxForwards.getValue());

        request.setHeader(HeaderConstants.MAX_FORWARDS, Integer.toString(currentMaxForwards - 1));
    }

    protected boolean requestMinorVersionIsTooHighMajorVersionsMatch(final HttpRequest request) {
        final ProtocolVersion requestProtocol = request.getVersion();
        if (requestProtocol == null) {
            return false;
        }
        if (requestProtocol.getMajor() != HttpVersion.HTTP_1_1.getMajor()) {
            return false;
        }

        if (requestProtocol.getMinor() > HttpVersion.HTTP_1_1.getMinor()) {
            return true;
        }

        return false;
    }

    protected boolean requestVersionIsTooLow(final HttpRequest request) {
        final ProtocolVersion requestProtocol = request.getVersion();
        return requestProtocol != null && requestProtocol.compareToVersion(HttpVersion.HTTP_1_1) < 0;
    }

    private RequestProtocolError requestHasWeakETagAndRange(final HttpRequest request) {
        // TODO: Should these be looking at all the headers marked as Range?
        final String method = request.getMethod();
        if (!(HeaderConstants.GET_METHOD.equals(method))) {
            return null;
        }

        final Header range = request.getFirstHeader(HeaderConstants.RANGE);
        if (range == null) {
            return null;
        }

        final Header ifRange = request.getFirstHeader(HeaderConstants.IF_RANGE);
        if (ifRange == null) {
            return null;
        }

        final String val = ifRange.getValue();
        if (val.startsWith("W/")) {
            return RequestProtocolError.WEAK_ETAG_AND_RANGE_ERROR;
        }

        return null;
    }

    private RequestProtocolError requestHasWeekETagForPUTOrDELETEIfMatch(final HttpRequest request) {
        // TODO: Should these be looking at all the headers marked as If-Match/If-None-Match?

        final String method = request.getMethod();
        if (!(HeaderConstants.PUT_METHOD.equals(method) || HeaderConstants.DELETE_METHOD.equals(method))) {
            return null;
        }

        final Header ifMatch = request.getFirstHeader(HeaderConstants.IF_MATCH);
        if (ifMatch != null) {
            final String val = ifMatch.getValue();
            if (val.startsWith("W/")) {
                return RequestProtocolError.WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR;
            }
        } else {
            final Header ifNoneMatch = request.getFirstHeader(HeaderConstants.IF_NONE_MATCH);
            if (ifNoneMatch == null) {
                return null;
            }

            final String val2 = ifNoneMatch.getValue();
            if (val2.startsWith("W/")) {
                return RequestProtocolError.WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR;
            }
        }

        return null;
    }

    private RequestProtocolError requestContainsNoCacheDirectiveWithFieldName(final HttpRequest request) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equalsIgnoreCase(elt.getName()) && elt.getValue() != null) {
                return RequestProtocolError.NO_CACHE_DIRECTIVE_WITH_FIELD_NAME;
            }
        }
        return null;
    }
}
