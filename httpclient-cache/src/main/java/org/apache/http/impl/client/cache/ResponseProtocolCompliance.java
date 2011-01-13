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
import java.util.Date;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

/**
 * @since 4.1
 */
@Immutable
class ResponseProtocolCompliance {

    /**
     * When we get a response from a down stream server (Origin Server)
     * we attempt to see if it is HTTP 1.1 Compliant and if not, attempt to
     * make it so.
     *
     * @param request The {@link HttpRequest} that generated an origin hit and response
     * @param response The {@link HttpResponse} from the origin server
     * @throws ClientProtocolException when we are unable to 'convert' the response to a compliant one
     */
    public void ensureProtocolCompliance(HttpRequest request, HttpResponse response)
            throws ClientProtocolException {
        if (backendResponseMustNotHaveBody(request, response)) {
            response.setEntity(null);
        }

        authenticationRequiredDidNotHaveAProxyAuthenticationHeader(request, response);

        notAllowedResponseDidNotHaveAnAllowHeader(request, response);

        unauthorizedResponseDidNotHaveAWWWAuthenticateHeader(request, response);

        requestDidNotExpect100ContinueButResponseIsOne(request, response);

        transferEncodingIsNotReturnedTo1_0Client(request, response);

        ensurePartialContentIsNotSentToAClientThatDidNotRequestIt(request, response);

        ensure200ForOPTIONSRequestWithNoBodyHasContentLengthZero(request, response);

        ensure206ContainsDateHeader(response);
        
        ensure304DoesNotContainExtraEntityHeaders(response);

        identityIsNotUsedInContentEncoding(response);

        warningsWithNonMatchingWarnDatesAreRemoved(response);
    }

    private void warningsWithNonMatchingWarnDatesAreRemoved(
            HttpResponse response) {
        Date responseDate = null;
        try {
            responseDate = DateUtils.parseDate(response.getFirstHeader("Date").getValue());
        } catch (DateParseException e) {
        }
        if (responseDate == null) return;
        Header[] warningHeaders = response.getHeaders("Warning");
        if (warningHeaders == null || warningHeaders.length == 0) return;
        List<Header> newWarningHeaders = new ArrayList<Header>();
        boolean modified = false;
        for(Header h : warningHeaders) {
            for(WarningValue wv : WarningValue.getWarningValues(h)) {
                Date warnDate = wv.getWarnDate();
                if (warnDate == null || warnDate.equals(responseDate)) {
                    newWarningHeaders.add(new BasicHeader("Warning",wv.toString()));
                } else {
                    modified = true;
                }
            }
        }
        if (modified) {
            response.removeHeaders("Warning");
            for(Header h : newWarningHeaders) {
                response.addHeader(h);
            }
        }
    }

    private void identityIsNotUsedInContentEncoding(HttpResponse response) {
        Header[] hdrs = response.getHeaders("Content-Encoding");
        if (hdrs == null || hdrs.length == 0) return;
        List<Header> newHeaders = new ArrayList<Header>();
        boolean modified = false;
        for (Header h : hdrs) {
            StringBuilder buf = new StringBuilder();
            boolean first = true;
            for (HeaderElement elt : h.getElements()) {
                if ("identity".equalsIgnoreCase(elt.getName())) {
                    modified = true;
                } else {
                    if (!first) buf.append(",");
                    buf.append(elt.toString());
                    first = false;
                }
            }
            String newHeaderValue = buf.toString();
            if (!"".equals(newHeaderValue)) {
                newHeaders.add(new BasicHeader("Content-Encoding", newHeaderValue));
            }
        }
        if (!modified) return;
        response.removeHeaders("Content-Encoding");
        for (Header h : newHeaders) {
            response.addHeader(h);
        }
    }

    private void authenticationRequiredDidNotHaveAProxyAuthenticationHeader(HttpRequest request,
            HttpResponse response) throws ClientProtocolException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED)
            return;

        if (response.getFirstHeader(HeaderConstants.PROXY_AUTHENTICATE) == null)
            throw new ClientProtocolException(
                    "407 Response did not contain a Proxy-Authentication header");
    }

    private void notAllowedResponseDidNotHaveAnAllowHeader(HttpRequest request,
            HttpResponse response) throws ClientProtocolException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_METHOD_NOT_ALLOWED)
            return;

        if (response.getFirstHeader(HeaderConstants.ALLOW) == null)
            throw new ClientProtocolException("405 Response did not contain an Allow header.");
    }

    private void unauthorizedResponseDidNotHaveAWWWAuthenticateHeader(HttpRequest request,
            HttpResponse response) throws ClientProtocolException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_UNAUTHORIZED)
            return;

        if (response.getFirstHeader(HeaderConstants.WWW_AUTHENTICATE) == null) {
            throw new ClientProtocolException(
                    "401 Response did not contain required WWW-Authenticate challenge header");
        }
    }

    private void ensure206ContainsDateHeader(HttpResponse response) {
        if (response.getFirstHeader(HTTP.DATE_HEADER) == null) {
            response.addHeader(HTTP.DATE_HEADER, DateUtils.formatDate(new Date()));
        }

    }

    private void ensurePartialContentIsNotSentToAClientThatDidNotRequestIt(HttpRequest request,
            HttpResponse response) throws ClientProtocolException {
        if (request.getFirstHeader(HeaderConstants.RANGE) != null)
            return;

        if (response.getFirstHeader(HeaderConstants.CONTENT_RANGE) != null) {
            throw new ClientProtocolException(
                    "Content-Range was returned for a request that did not ask for a Content-Range.");
        }

    }

    private void ensure200ForOPTIONSRequestWithNoBodyHasContentLengthZero(HttpRequest request,
            HttpResponse response) {
        if (!request.getRequestLine().getMethod().equalsIgnoreCase(HeaderConstants.OPTIONS_METHOD)) {
            return;
        }

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            return;
        }

        if (response.getFirstHeader(HTTP.CONTENT_LEN) == null) {
            response.addHeader(HTTP.CONTENT_LEN, "0");
        }
    }

    private void ensure304DoesNotContainExtraEntityHeaders(HttpResponse response) {
        String[] disallowedEntityHeaders = { "Allow", "Content-Encoding",
                "Content-Language", "Content-Length", "Content-MD5",
                "Content-Range", "Content-Type", "Last-Modified"
        };
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
            for(String hdr : disallowedEntityHeaders) {
                response.removeHeaders(hdr);
            }
        }
    }

    private boolean backendResponseMustNotHaveBody(HttpRequest request, HttpResponse backendResponse) {
        return HeaderConstants.HEAD_METHOD.equals(request.getRequestLine().getMethod())
                || backendResponse.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT
                || backendResponse.getStatusLine().getStatusCode() == HttpStatus.SC_RESET_CONTENT
                || backendResponse.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED;
    }

    private void requestDidNotExpect100ContinueButResponseIsOne(HttpRequest request,
            HttpResponse response) throws ClientProtocolException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CONTINUE) {
            return;
        }

        if (!requestWasWrapped(request)) {
            return;
        }

        ProtocolVersion originalProtocol = getOriginalRequestProtocol((RequestWrapper) request);

        if (originalProtocol.compareToVersion(HttpVersion.HTTP_1_1) >= 0) {
            return;
        }

        if (originalRequestDidNotExpectContinue((RequestWrapper) request)) {
            throw new ClientProtocolException("The incoming request did not contain a "
                    + "100-continue header, but the response was a Status 100, continue.");

        }
    }

    private void transferEncodingIsNotReturnedTo1_0Client(HttpRequest request, HttpResponse response) {
        if (!requestWasWrapped(request)) {
            return;
        }

        ProtocolVersion originalProtocol = getOriginalRequestProtocol((RequestWrapper) request);

        if (originalProtocol.compareToVersion(HttpVersion.HTTP_1_1) >= 0) {
            return;
        }

        removeResponseTransferEncoding(response);
    }

    private void removeResponseTransferEncoding(HttpResponse response) {
        response.removeHeaders("TE");
        response.removeHeaders(HTTP.TRANSFER_ENCODING);
    }

    private boolean originalRequestDidNotExpectContinue(RequestWrapper request) {

        try {
            HttpEntityEnclosingRequest original = (HttpEntityEnclosingRequest) request
                    .getOriginal();

            return !original.expectContinue();
        } catch (ClassCastException ex) {
            return false;
        }
    }

    private ProtocolVersion getOriginalRequestProtocol(RequestWrapper request) {
        return request.getOriginal().getProtocolVersion();
    }

    private boolean requestWasWrapped(HttpRequest request) {
        return request instanceof RequestWrapper;
    }

}
