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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.MessageSupport;

class ResponseProtocolCompliance {

    private static final String UNEXPECTED_100_CONTINUE = "The incoming request did not contain a "
                    + "100-continue header, but the response was a Status 100, continue.";
    private static final String UNEXPECTED_PARTIAL_CONTENT = "partial content was returned for a request that did not ask for it";

    /**
     * When we get a response from a down stream server (Origin Server)
     * we attempt to see if it is HTTP 1.1 Compliant and if not, attempt to
     * make it so.
     *
     * @param originalRequest The original {@link HttpRequest}
     * @param request The {@link HttpRequest} that generated an origin hit and response
     * @param response The {@link HttpResponse} from the origin server
     * @throws IOException Bad things happened
     */
    public void ensureProtocolCompliance(
            final HttpRequest originalRequest,
            final HttpRequest request,
            final HttpResponse response) throws IOException {
        requestDidNotExpect100ContinueButResponseIsOne(originalRequest, response);

        transferEncodingIsNotReturnedTo1_0Client(originalRequest, response);

        ensurePartialContentIsNotSentToAClientThatDidNotRequestIt(request, response);

        ensure200ForOPTIONSRequestWithNoBodyHasContentLengthZero(request, response);

        ensure206ContainsDateHeader(response);

        ensure304DoesNotContainExtraEntityHeaders(response);

        identityIsNotUsedInContentEncoding(response);

        warningsWithNonMatchingWarnDatesAreRemoved(response);
    }

    private void warningsWithNonMatchingWarnDatesAreRemoved(
            final HttpResponse response) {
        final Date responseDate = DateUtils.parseDate(response, HttpHeaders.DATE);
        if (responseDate == null) {
            return;
        }

        final Header[] warningHeaders = response.getHeaders(HeaderConstants.WARNING);

        if (warningHeaders == null || warningHeaders.length == 0) {
            return;
        }

        final List<Header> newWarningHeaders = new ArrayList<>();
        boolean modified = false;
        for(final Header h : warningHeaders) {
            for(final WarningValue wv : WarningValue.getWarningValues(h)) {
                final Date warnDate = wv.getWarnDate();
                if (warnDate == null || warnDate.equals(responseDate)) {
                    newWarningHeaders.add(new BasicHeader(HeaderConstants.WARNING,wv.toString()));
                } else {
                    modified = true;
                }
            }
        }
        if (modified) {
            response.removeHeaders(HeaderConstants.WARNING);
            for(final Header h : newWarningHeaders) {
                response.addHeader(h);
            }
        }
    }

    private void identityIsNotUsedInContentEncoding(final HttpResponse response) {
        final Header[] hdrs = response.getHeaders(HttpHeaders.CONTENT_ENCODING);
        if (hdrs == null || hdrs.length == 0) {
            return;
        }
        final List<Header> newHeaders = new ArrayList<>();
        boolean modified = false;
        for (final Header h : hdrs) {
            final StringBuilder buf = new StringBuilder();
            boolean first = true;
            for (final HeaderElement elt : MessageSupport.parse(h)) {
                if ("identity".equalsIgnoreCase(elt.getName())) {
                    modified = true;
                } else {
                    if (!first) {
                        buf.append(",");
                    }
                    buf.append(elt.toString());
                    first = false;
                }
            }
            final String newHeaderValue = buf.toString();
            if (!newHeaderValue.isEmpty()) {
                newHeaders.add(new BasicHeader(HttpHeaders.CONTENT_ENCODING, newHeaderValue));
            }
        }
        if (!modified) {
            return;
        }
        response.removeHeaders(HttpHeaders.CONTENT_ENCODING);
        for (final Header h : newHeaders) {
            response.addHeader(h);
        }
    }

    private void ensure206ContainsDateHeader(final HttpResponse response) {
        if (response.getFirstHeader(HttpHeaders.DATE) == null) {
            response.addHeader(HttpHeaders.DATE, DateUtils.formatDate(new Date()));
        }

    }

    private void ensurePartialContentIsNotSentToAClientThatDidNotRequestIt(final HttpRequest request,
            final HttpResponse response) throws IOException {
        if (request.getFirstHeader(HeaderConstants.RANGE) != null
                || response.getCode() != HttpStatus.SC_PARTIAL_CONTENT) {
            return;
        }
        throw new ClientProtocolException(UNEXPECTED_PARTIAL_CONTENT);
    }

    private void ensure200ForOPTIONSRequestWithNoBodyHasContentLengthZero(final HttpRequest request,
            final HttpResponse response) {
        if (!request.getMethod().equalsIgnoreCase(HeaderConstants.OPTIONS_METHOD)) {
            return;
        }

        if (response.getCode() != HttpStatus.SC_OK) {
            return;
        }

        if (response.getFirstHeader(HttpHeaders.CONTENT_LENGTH) == null) {
            response.addHeader(HttpHeaders.CONTENT_LENGTH, "0");
        }
    }

    private void ensure304DoesNotContainExtraEntityHeaders(final HttpResponse response) {
        final String[] disallowedEntityHeaders = { HeaderConstants.ALLOW, HttpHeaders.CONTENT_ENCODING,
                "Content-Language", HttpHeaders.CONTENT_LENGTH, "Content-MD5",
                "Content-Range", HttpHeaders.CONTENT_TYPE, HeaderConstants.LAST_MODIFIED
        };
        if (response.getCode() == HttpStatus.SC_NOT_MODIFIED) {
            for(final String hdr : disallowedEntityHeaders) {
                response.removeHeaders(hdr);
            }
        }
    }

    private void requestDidNotExpect100ContinueButResponseIsOne(
            final HttpRequest originalRequest, final HttpResponse response) throws IOException {
        if (response.getCode() != HttpStatus.SC_CONTINUE) {
            return;
        }

        final Header header = originalRequest.getFirstHeader(HttpHeaders.EXPECT);
        if (header != null && header.getValue().equalsIgnoreCase(HeaderElements.CONTINUE)) {
            return;
        }
        throw new ClientProtocolException(UNEXPECTED_100_CONTINUE);
    }

    private void transferEncodingIsNotReturnedTo1_0Client(
            final HttpRequest originalRequest, final HttpResponse response) {
        final ProtocolVersion version = originalRequest.getVersion() != null ? originalRequest.getVersion() : HttpVersion.DEFAULT;
        if (version.compareToVersion(HttpVersion.HTTP_1_1) >= 0) {
            return;
        }

        removeResponseTransferEncoding(response);
    }

    private void removeResponseTransferEncoding(final HttpResponse response) {
        response.removeHeaders("TE");
        response.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
    }

}
