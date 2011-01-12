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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;

/**
 * @since 4.1
 */
@Immutable
class RequestProtocolCompliance {

    private static final List<String> disallowedWithNoCache =
        Arrays.asList("min-fresh", "max-stale", "max-age");

    /**
     * Test to see if the {@link HttpRequest} is HTTP1.1 compliant or not
     * and if not, we can not continue.
     *
     * @param request the HttpRequest Object
     * @return list of {@link RequestProtocolError}
     */
    public List<RequestProtocolError> requestIsFatallyNonCompliant(HttpRequest request) {
        List<RequestProtocolError> theErrors = new ArrayList<RequestProtocolError>();

        RequestProtocolError anError = requestHasWeakETagAndRange(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        anError = requestHasWeekETagForPUTOrDELETEIfMatch(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        anError = requestContainsNoCacheDirectiveWithFieldName(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        return theErrors;
    }

    /**
     * If the {@link HttpRequest} is non-compliant but 'fixable' we go ahead and
     * fix the request here.  Returning the updated one.
     *
     * @param request the request to check for compliance
     * @return the updated request
     * @throws ClientProtocolException when we have trouble making the request compliant
     */
    public HttpRequest makeRequestCompliant(HttpRequest request)
        throws ClientProtocolException {
    
        if (requestMustNotHaveEntity(request)) {
            ((HttpEntityEnclosingRequest) request).setEntity(null);
        }

        verifyRequestWithExpectContinueFlagHas100continueHeader(request);
        verifyOPTIONSRequestWithBodyHasContentType(request);
        decrementOPTIONSMaxForwardsIfGreaterThen0(request);
        stripOtherFreshnessDirectivesWithNoCache(request);

        if (requestVersionIsTooLow(request)) {
            return upgradeRequestTo(request, HttpVersion.HTTP_1_1);
        }

        if (requestMinorVersionIsTooHighMajorVersionsMatch(request)) {
            return downgradeRequestTo(request, HttpVersion.HTTP_1_1);
        }

        return request;
    }
    
    private void stripOtherFreshnessDirectivesWithNoCache(HttpRequest request) {
        List<HeaderElement> outElts = new ArrayList<HeaderElement>();
        boolean shouldStrip = false;
        for(Header h : request.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if (!disallowedWithNoCache.contains(elt.getName())) {
                    outElts.add(elt);
                }
                if ("no-cache".equals(elt.getName())) {
                    shouldStrip = true;
                }
            }
        }
        if (!shouldStrip) return;
        request.removeHeaders("Cache-Control");
        request.setHeader("Cache-Control", buildHeaderFromElements(outElts));
    }

    private String buildHeaderFromElements(List<HeaderElement> outElts) {
        StringBuilder newHdr = new StringBuilder("");
        boolean first = true;
        for(HeaderElement elt : outElts) {
            if (!first) {
                newHdr.append(",");
            } else {
                first = false;
            }
            newHdr.append(elt.toString());
        }
        return newHdr.toString();
    }

    private boolean requestMustNotHaveEntity(HttpRequest request) {
        return HeaderConstants.TRACE_METHOD.equals(request.getRequestLine().getMethod())
                && request instanceof HttpEntityEnclosingRequest;
    }

    private void decrementOPTIONSMaxForwardsIfGreaterThen0(HttpRequest request) {
        if (!HeaderConstants.OPTIONS_METHOD.equals(request.getRequestLine().getMethod())) {
            return;
        }

        Header maxForwards = request.getFirstHeader(HeaderConstants.MAX_FORWARDS);
        if (maxForwards == null) {
            return;
        }

        request.removeHeaders(HeaderConstants.MAX_FORWARDS);
        int currentMaxForwards = Integer.parseInt(maxForwards.getValue());

        request.setHeader(HeaderConstants.MAX_FORWARDS, Integer.toString(currentMaxForwards - 1));
    }

    private void verifyOPTIONSRequestWithBodyHasContentType(HttpRequest request) {
        if (!HeaderConstants.OPTIONS_METHOD.equals(request.getRequestLine().getMethod())) {
            return;
        }

        if (!(request instanceof HttpEntityEnclosingRequest)) {
            return;
        }

        addContentTypeHeaderIfMissing((HttpEntityEnclosingRequest) request);
    }

    private void addContentTypeHeaderIfMissing(HttpEntityEnclosingRequest request) {
        if (request.getEntity().getContentType() == null) {
            ((AbstractHttpEntity) request.getEntity()).setContentType(HTTP.OCTET_STREAM_TYPE);
        }
    }

    private void verifyRequestWithExpectContinueFlagHas100continueHeader(HttpRequest request) {
        if (request instanceof HttpEntityEnclosingRequest) {

            if (((HttpEntityEnclosingRequest) request).expectContinue()
                    && ((HttpEntityEnclosingRequest) request).getEntity() != null) {
                add100ContinueHeaderIfMissing(request);
            } else {
                remove100ContinueHeaderIfExists(request);
            }
        } else {
            remove100ContinueHeaderIfExists(request);
        }
    }

    private void remove100ContinueHeaderIfExists(HttpRequest request) {
        boolean hasHeader = false;

        Header[] expectHeaders = request.getHeaders(HTTP.EXPECT_DIRECTIVE);
        List<HeaderElement> expectElementsThatAreNot100Continue = new ArrayList<HeaderElement>();

        for (Header h : expectHeaders) {
            for (HeaderElement elt : h.getElements()) {
                if (!(HTTP.EXPECT_CONTINUE.equalsIgnoreCase(elt.getName()))) {
                    expectElementsThatAreNot100Continue.add(elt);
                } else {
                    hasHeader = true;
                }
            }

            if (hasHeader) {
                request.removeHeader(h);
                for (HeaderElement elt : expectElementsThatAreNot100Continue) {
                    BasicHeader newHeader = new BasicHeader(HTTP.EXPECT_DIRECTIVE, elt.getName());
                    request.addHeader(newHeader);
                }
                return;
            } else {
                expectElementsThatAreNot100Continue = new ArrayList<HeaderElement>();
            }
        }
    }

    private void add100ContinueHeaderIfMissing(HttpRequest request) {
        boolean hasHeader = false;

        for (Header h : request.getHeaders(HTTP.EXPECT_DIRECTIVE)) {
            for (HeaderElement elt : h.getElements()) {
                if (HTTP.EXPECT_CONTINUE.equalsIgnoreCase(elt.getName())) {
                    hasHeader = true;
                }
            }
        }

        if (!hasHeader) {
            request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        }
    }

    private HttpRequest upgradeRequestTo(HttpRequest request, ProtocolVersion version)
            throws ClientProtocolException {
        RequestWrapper newRequest;
        try {
            newRequest = new RequestWrapper(request);
        } catch (ProtocolException pe) {
            throw new ClientProtocolException(pe);
        }
        newRequest.setProtocolVersion(version);

        return newRequest;
    }

    private HttpRequest downgradeRequestTo(HttpRequest request, ProtocolVersion version)
            throws ClientProtocolException {
        RequestWrapper newRequest;
        try {
            newRequest = new RequestWrapper(request);
        } catch (ProtocolException pe) {
            throw new ClientProtocolException(pe);
        }
        newRequest.setProtocolVersion(version);

        return newRequest;
    }

    protected boolean requestMinorVersionIsTooHighMajorVersionsMatch(HttpRequest request) {
        ProtocolVersion requestProtocol = request.getProtocolVersion();
        if (requestProtocol.getMajor() != HttpVersion.HTTP_1_1.getMajor()) {
            return false;
        }

        if (requestProtocol.getMinor() > HttpVersion.HTTP_1_1.getMinor()) {
            return true;
        }

        return false;
    }

    protected boolean requestVersionIsTooLow(HttpRequest request) {
        return request.getProtocolVersion().compareToVersion(HttpVersion.HTTP_1_1) < 0;
    }

    /**
     * Extract error information about the {@link HttpRequest} telling the 'caller'
     * that a problem occured.
     *
     * @param errorCheck What type of error should I get
     * @return The {@link HttpResponse} that is the error generated
     */
    public HttpResponse getErrorForRequest(RequestProtocolError errorCheck) {
        switch (errorCheck) {
            case BODY_BUT_NO_LENGTH_ERROR:
                return new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
                        HttpStatus.SC_LENGTH_REQUIRED, ""));

            case WEAK_ETAG_AND_RANGE_ERROR:
                return new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
                        HttpStatus.SC_BAD_REQUEST, "Weak eTag not compatible with byte range"));

            case WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR:
                return new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
                        HttpStatus.SC_BAD_REQUEST,
                        "Weak eTag not compatible with PUT or DELETE requests"));

            case NO_CACHE_DIRECTIVE_WITH_FIELD_NAME:
                return new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
                        HttpStatus.SC_BAD_REQUEST,
                        "No-Cache directive MUST NOT include a field name"));

            default:
                throw new IllegalStateException(
                        "The request was compliant, therefore no error can be generated for it.");

        }
    }

    private RequestProtocolError requestHasWeakETagAndRange(HttpRequest request) {
        // TODO: Should these be looking at all the headers marked as Range?
        String method = request.getRequestLine().getMethod();
        if (!(HeaderConstants.GET_METHOD.equals(method))) {
            return null;
        }

        Header range = request.getFirstHeader(HeaderConstants.RANGE);
        if (range == null)
            return null;

        Header ifRange = request.getFirstHeader(HeaderConstants.IF_RANGE);
        if (ifRange == null)
            return null;

        String val = ifRange.getValue();
        if (val.startsWith("W/")) {
            return RequestProtocolError.WEAK_ETAG_AND_RANGE_ERROR;
        }

        return null;
    }

    private RequestProtocolError requestHasWeekETagForPUTOrDELETEIfMatch(HttpRequest request) {
        // TODO: Should these be looking at all the headers marked as If-Match/If-None-Match?

        String method = request.getRequestLine().getMethod();
        if (!(HeaderConstants.PUT_METHOD.equals(method) || HeaderConstants.DELETE_METHOD
                .equals(method))) {
            return null;
        }

        Header ifMatch = request.getFirstHeader(HeaderConstants.IF_MATCH);
        if (ifMatch != null) {
            String val = ifMatch.getValue();
            if (val.startsWith("W/")) {
                return RequestProtocolError.WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR;
            }
        } else {
            Header ifNoneMatch = request.getFirstHeader(HeaderConstants.IF_NONE_MATCH);
            if (ifNoneMatch == null)
                return null;

            String val2 = ifNoneMatch.getValue();
            if (val2.startsWith("W/")) {
                return RequestProtocolError.WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR;
            }
        }

        return null;
    }

    private RequestProtocolError requestContainsNoCacheDirectiveWithFieldName(HttpRequest request) {
        for(Header h : request.getHeaders("Cache-Control")) {
            for(HeaderElement elt : h.getElements()) {
                if ("no-cache".equalsIgnoreCase(elt.getName())
                    && elt.getValue() != null) {
                    return RequestProtocolError.NO_CACHE_DIRECTIVE_WITH_FIELD_NAME;
                }
            }
        }
        return null;
    }
}
