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
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

/**
 * @since 4.1
 */
@Immutable
public class RequestProtocolCompliance {

    /**
     *
     * @param request
     * @return list of {@link RequestProtocolError}
     */
    public List<RequestProtocolError> requestIsFatallyNonCompliant(HttpRequest request) {
        List<RequestProtocolError> theErrors = new ArrayList<RequestProtocolError>();

        RequestProtocolError anError = requestContainsBodyButNoLength(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        anError = requestHasWeakETagAndRange(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        anError = requestHasWeekETagForPUTOrDELETEIfMatch(request);
        if (anError != null) {
            theErrors.add(anError);
        }

        return theErrors;
    }

    /**
     *
     * @param request
     * @return the updated request
     * @throws ProtocolException
     */
    public HttpRequest makeRequestCompliant(HttpRequest request) throws ProtocolException {
        if (requestMustNotHaveEntity(request)) {
            ((HttpEntityEnclosingRequest) request).setEntity(null);
        }

        verifyRequestWithExpectContinueFlagHas100continueHeader(request);
        verifyOPTIONSRequestWithBodyHasContentType(request);
        decrementOPTIONSMaxForwardsIfGreaterThen0(request);

        if (requestVersionIsTooLow(request)) {
            return upgradeRequestTo(request, CachingHttpClient.HTTP_1_1);
        }

        if (requestMinorVersionIsTooHighMajorVersionsMatch(request)) {
            return downgradeRequestTo(request, CachingHttpClient.HTTP_1_1);
        }

        return request;
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
            ((AbstractHttpEntity) request.getEntity()).setContentType("application/octet-stream");
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

        Header[] expectHeaders = request.getHeaders(HeaderConstants.EXPECT);
        List<HeaderElement> expectElementsThatAreNot100Continue = new ArrayList<HeaderElement>();

        for (Header h : expectHeaders) {
            for (HeaderElement elt : h.getElements()) {
                if (!("100-continue".equalsIgnoreCase(elt.getName()))) {
                    expectElementsThatAreNot100Continue.add(elt);
                } else {
                    hasHeader = true;
                }
            }

            if (hasHeader) {
                request.removeHeader(h);
                for (HeaderElement elt : expectElementsThatAreNot100Continue) {
                    BasicHeader newHeader = new BasicHeader(HeaderConstants.EXPECT, elt.getName());
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

        for (Header h : request.getHeaders(HeaderConstants.EXPECT)) {
            for (HeaderElement elt : h.getElements()) {
                if ("100-continue".equalsIgnoreCase(elt.getName())) {
                    hasHeader = true;
                }
            }
        }

        if (!hasHeader) {
            request.addHeader(HeaderConstants.EXPECT, "100-continue");
        }
    }

    private HttpRequest upgradeRequestTo(HttpRequest request, ProtocolVersion version)
            throws ProtocolException {
        RequestWrapper newRequest = new RequestWrapper(request);
        newRequest.setProtocolVersion(version);

        return newRequest;
    }

    private HttpRequest downgradeRequestTo(HttpRequest request, ProtocolVersion version)
            throws ProtocolException {
        RequestWrapper newRequest = new RequestWrapper(request);
        newRequest.setProtocolVersion(version);

        return newRequest;
    }

    protected boolean requestMinorVersionIsTooHighMajorVersionsMatch(HttpRequest request) {
        ProtocolVersion requestProtocol = request.getProtocolVersion();
        if (requestProtocol.getMajor() != CachingHttpClient.HTTP_1_1.getMajor()) {
            return false;
        }

        if (requestProtocol.getMinor() > CachingHttpClient.HTTP_1_1.getMinor()) {
            return true;
        }

        return false;
    }

    protected boolean requestVersionIsTooLow(HttpRequest request) {
        return request.getProtocolVersion().compareToVersion(CachingHttpClient.HTTP_1_1) < 0;
    }

    public HttpResponse getErrorForRequest(RequestProtocolError errorCheck) {
        switch (errorCheck) {
        case BODY_BUT_NO_LENGTH_ERROR:
            return new BasicHttpResponse(new BasicStatusLine(CachingHttpClient.HTTP_1_1,
                    HttpStatus.SC_LENGTH_REQUIRED, ""));

        case WEAK_ETAG_AND_RANGE_ERROR:
            return new BasicHttpResponse(new BasicStatusLine(CachingHttpClient.HTTP_1_1,
                    HttpStatus.SC_BAD_REQUEST, "Weak eTag not compatible with byte range"));

        case WEAK_ETAG_ON_PUTDELETE_METHOD_ERROR:
            return new BasicHttpResponse(new BasicStatusLine(CachingHttpClient.HTTP_1_1,
                    HttpStatus.SC_BAD_REQUEST,
                    "Weak eTag not compatible with PUT or DELETE requests"));

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

    private RequestProtocolError requestContainsBodyButNoLength(HttpRequest request) {
        if (!(request instanceof HttpEntityEnclosingRequest)) {
            return null;
        }

        if (request.getFirstHeader(HeaderConstants.CONTENT_LENGTH) != null
                && ((HttpEntityEnclosingRequest) request).getEntity() != null)
            return null;

        return RequestProtocolError.BODY_BUT_NO_LENGTH_ERROR;
    }
}
