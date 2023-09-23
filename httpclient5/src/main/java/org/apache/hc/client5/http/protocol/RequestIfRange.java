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


package org.apache.hc.client5.http.protocol;


import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * <p>
 * The {@code RequestIfRange} interceptor ensures that the request adheres to the RFC guidelines for the 'If-Range' header.
 * The "If-Range" header field is used in conjunction with the Range header to conditionally request parts of a representation.
 * If the validator given in the "If-Range" header matches the current validator for the representation, then the server should respond with the specified range of the document.
 * If they do not match, the server should return the entire representation.
 * </p>
 *
 * <p>
 * Key points:
 * <ul>
 *     <li>A client MUST NOT generate an If-Range header field in a request that does not contain a Range header field.</li>
 *     <li>An origin server MUST ignore an If-Range header field received in a request for a target resource that does not support Range requests.</li>
 *     <li>A client MUST NOT generate an If-Range header field containing an entity tag that is marked as weak.</li>
 *     <li>A client MUST NOT generate an If-Range header field containing an HTTP-date unless the client has no entity tag for the corresponding representation and the date is a strong validator.</li>
 *     <li>A server that receives an If-Range header field on a Range request MUST evaluate the condition before performing the method.</li>
 * </ul>
 * </p>
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestIfRange implements HttpRequestInterceptor {

    /**
     * This {@link DateTimeFormatter} is used to format and parse date-time objects in a specific format commonly
     * used in HTTP protocol messages. The format includes the day of the week, day of the month, month, year, and time
     * of day, all represented in GMT time. An example of a date-time string in this format is "Tue, 15 Nov 1994 08:12:31 GMT".
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * Singleton instance.
     *
     * @since 5.4
     */
    public static final RequestIfRange INSTANCE = new RequestIfRange();

    public RequestIfRange() {
        super();
    }

    /**
     * Processes the given request to ensure it adheres to the RFC guidelines for the 'If-Range' header.
     *
     * @param request The HTTP request to be processed.
     * @param entity  The entity details of the request.
     * @param context The HTTP context.
     * @throws HttpException If the request does not adhere to the RFC guidelines.
     * @throws IOException   If an I/O error occurs.
     */
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final Header ifRangeHeader = request.getFirstHeader(HttpHeaders.IF_RANGE);

        // If there's no If-Range header, just return
        if (ifRangeHeader == null) {
            return;
        }

        // If there's an If-Range header but no Range header, throw an exception
        if (!request.containsHeader(HttpHeaders.RANGE)) {
            throw new ProtocolException("Request with 'If-Range' header must also contain a 'Range' header.");
        }

        final Header eTag = request.getFirstHeader(HttpHeaders.ETAG);

        // If there's a weak ETag in the If-Range header, throw an exception
        if (eTag != null && eTag.getValue().startsWith("W/")) {
            throw new ProtocolException("'If-Range' header must not contain a weak entity tag.");
        }

        final Header dateHeader = request.getFirstHeader(HttpHeaders.DATE);

        if (dateHeader == null) {
            return;
        }


        final Instant lastModifiedInstant;
        final Instant dateInstant;
        final Header lastModifiedHeader = request.getFirstHeader(HttpHeaders.LAST_MODIFIED);

        if (lastModifiedHeader != null) {
            final String lastModifiedValue = lastModifiedHeader.getValue();
            lastModifiedInstant = FORMATTER.parse(lastModifiedValue, Instant::from);
        }
        else {
            // If there's no Last-Modified header, we exit early because we can't deduce that it is strong.
            return;
        }

        final String dateValue = dateHeader.getValue();
        dateInstant = FORMATTER.parse(dateValue, Instant::from);

        long difference = 0;
        if (lastModifiedInstant != null && dateInstant != null) {
            difference = Duration.between(lastModifiedInstant, dateInstant).getSeconds();
        }

        // If the difference between the Last-Modified and Date headers is less than 1 second, throw an exception
        if (difference < 1 && eTag!= null) {
            throw new ProtocolException("'If-Range' header with a Date must be a strong validator.");
        }
    }

}
