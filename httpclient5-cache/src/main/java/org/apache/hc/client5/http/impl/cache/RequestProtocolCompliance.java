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
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.MessageSupport;

/**
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
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
     * @throws ClientProtocolException when we have trouble making the request compliant
     */
    public void makeRequestCompliant(final RoutedHttpRequest request)
        throws ClientProtocolException {

        if (requestMustNotHaveEntity(request)) {
            request.setEntity(null);
        }

        verifyRequestWithExpectContinueFlagHas100continueHeader(request);
        verifyOPTIONSRequestWithBodyHasContentType(request);
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

    private boolean requestMustNotHaveEntity(final HttpRequest request) {
        return HeaderConstants.TRACE_METHOD.equals(request.getMethod());
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

    private void verifyOPTIONSRequestWithBodyHasContentType(final RoutedHttpRequest request) {
        if (!HeaderConstants.OPTIONS_METHOD.equals(request.getMethod())) {
            return;
        }

        addContentTypeHeaderIfMissing(request);
    }

    private void addContentTypeHeaderIfMissing(final RoutedHttpRequest request) {
        final HttpEntity entity = request.getEntity();
        if (entity != null && entity.getContentType() == null) {
            ((AbstractHttpEntity) entity).setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
        }
    }

    private void verifyRequestWithExpectContinueFlagHas100continueHeader(final RoutedHttpRequest request) {
        if (request.containsHeader(HttpHeaders.EXPECT) && request.getEntity() != null) {
            add100ContinueHeaderIfMissing(request);
        } else {
            remove100ContinueHeaderIfExists(request);
        }
    }

    private void remove100ContinueHeaderIfExists(final HttpRequest request) {
        boolean hasHeader = false;

        final Header[] expectHeaders = request.getHeaders(HttpHeaders.EXPECT);
        List<HeaderElement> expectElementsThatAreNot100Continue = new ArrayList<>();

        for (final Header h : expectHeaders) {
            for (final HeaderElement elt : MessageSupport.parse(h)) {
                if (!(HeaderElements.CONTINUE.equalsIgnoreCase(elt.getName()))) {
                    expectElementsThatAreNot100Continue.add(elt);
                } else {
                    hasHeader = true;
                }
            }

            if (hasHeader) {
                request.removeHeader(h);
                for (final HeaderElement elt : expectElementsThatAreNot100Continue) {
                    final BasicHeader newHeader = new BasicHeader(HeaderElements.CONTINUE, elt.getName());
                    request.addHeader(newHeader);
                }
                return;
            } else {
                expectElementsThatAreNot100Continue = new ArrayList<>();
            }
        }
    }

    private void add100ContinueHeaderIfMissing(final HttpRequest request) {
        boolean hasHeader = false;

        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HttpHeaders.EXPECT);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            if (HeaderElements.CONTINUE.equalsIgnoreCase(elt.getName())) {
                hasHeader = true;
            }
        }

        if (!hasHeader) {
            request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        }
    }

    protected boolean requestMinorVersionIsTooHighMajorVersionsMatch(final HttpRequest request) {
        final ProtocolVersion requestProtocol = request.getVersion();
        if (requestProtocol.getMajor() != HttpVersion.HTTP_1_1.getMajor()) {
            return false;
        }

        if (requestProtocol.getMinor() > HttpVersion.HTTP_1_1.getMinor()) {
            return true;
        }

        return false;
    }

    protected boolean requestVersionIsTooLow(final HttpRequest request) {
        return request.getVersion().compareToVersion(HttpVersion.HTTP_1_1) < 0;
    }

    /**
     * Extract error information about the {@link HttpRequest} telling the 'caller'
     * that a problem occured.
     *
     * @param errorCheck What type of error should I get
     * @return The {@link ClassicHttpResponse} that is the error generated
     */
    public ClassicHttpResponse getErrorForRequest(final RequestProtocolError errorCheck) {
        switch (errorCheck) {
            case BODY_BUT_NO_LENGTH_ERROR:
                return new BasicClassicHttpResponse(HttpStatus.SC_LENGTH_REQUIRED, "");

            case WEAK_ETAG_AND_RANGE_ERROR:
                return new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST,
                        "Weak eTag not compatible with byte range");

            case WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR:
                return new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST,
                        "Weak eTag not compatible with PUT or DELETE requests");

            case NO_CACHE_DIRECTIVE_WITH_FIELD_NAME:
                return new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST,
                        "No-Cache directive MUST NOT include a field name");

            default:
                throw new IllegalStateException(
                        "The request was compliant, therefore no error can be generated for it.");

        }
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
