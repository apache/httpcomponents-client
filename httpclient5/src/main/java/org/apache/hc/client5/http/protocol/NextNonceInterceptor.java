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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * {@code NextNonceInterceptor} is an HTTP response interceptor that extracts the {@code nextnonce}
 * parameter from the {@code Authentication-Info} header of an HTTP response. This parameter is used
 * in HTTP Digest Access Authentication to provide an additional nonce value that the client is expected
 * to use in subsequent authentication requests. By retrieving and storing this {@code nextnonce} value,
 * the interceptor facilitates one-time nonce implementations and prevents replay attacks by ensuring that
 * each request/response interaction includes a fresh nonce.
 * <p>
 * If present, the extracted {@code nextnonce} value is stored in the {@link HttpContext} under the attribute
 * {@code auth-nextnonce}, allowing it to be accessed in subsequent requests. If the header does not contain
 * the {@code nextnonce} parameter, no context attribute is set.
 * </p>
 *
 * <p>This implementation adheres to the HTTP/1.1 specification, particularly focusing on the {@code Digest}
 * scheme as defined in HTTP Digest Authentication, and parses header tokens using the {@link Tokenizer}
 * utility class for robust token parsing.</p>
 *
 * <p>In the context of HTTP Digest Access Authentication, the {@code nextnonce} parameter is
 * a critical part of the security mechanism, designed to mitigate replay attacks and enhance mutual
 * authentication security. It provides the server with the ability to set and enforce single-use or
 * session-bound nonces, prompting the client to use the provided {@code nextnonce} in its next request.
 * This setup helps secure communication by forcing new cryptographic material in each transaction.
 * </p>
 *
 * <p>This interceptor is stateless and thread-safe, making it suitable for use across multiple
 * threads and HTTP requests. It should be registered with the HTTP client to enable support
 * for advanced authentication mechanisms that require tracking of nonce values.</p>
 *
 * @since 5.5
 */

@Contract(threading = ThreadingBehavior.STATELESS)
public class NextNonceInterceptor implements HttpResponseInterceptor {

    public static final HttpResponseInterceptor INSTANCE = new NextNonceInterceptor();

    private final Tokenizer tokenParser = Tokenizer.INSTANCE;


    private static final String AUTHENTICATION_INFO_HEADER = "Authentication-Info";

    private static final Tokenizer.Delimiter TOKEN_DELIMS = Tokenizer.delimiters('=', ',');
    private static final Tokenizer.Delimiter VALUE_DELIMS = Tokenizer.delimiters(',');

    /**
     * Processes the HTTP response and extracts the {@code nextnonce} parameter from the
     * {@code Authentication-Info} header if available, storing it in the provided {@code context}.
     *
     * @param response the HTTP response containing the {@code Authentication-Info} header
     * @param entity   the response entity, ignored by this interceptor
     * @param context  the HTTP context in which to store the {@code nextnonce} parameter
     * @throws NullPointerException if either {@code response} or {@code context} is null
     */
    @Override
    public void process(final HttpResponse response, final EntityDetails entity, final HttpContext context) {
        Args.notNull(response, "HTTP response");
        Args.notNull(context, "HTTP context");

        final Header header = response.getFirstHeader(AUTHENTICATION_INFO_HEADER);
        if (header != null) {
            final String nextNonce;
            if (header instanceof FormattedHeader) {
                final CharSequence buf = ((FormattedHeader) header).getBuffer();
                final ParserCursor cursor = new ParserCursor(((FormattedHeader) header).getValuePos(), buf.length());
                nextNonce = parseNextNonce(buf, cursor);
            } else {
                final CharSequence headerValue = header.getValue();
                final ParserCursor cursor = new ParserCursor(0, headerValue.length());
                nextNonce = parseNextNonce(headerValue, cursor);
            }
            if (!TextUtils.isBlank(nextNonce)) {
                HttpClientContext.castOrCreate(context).setNextNonce(nextNonce);
            }
        }
    }

    /**
     * Parses the {@code Authentication-Info} header content represented by a {@link CharArrayBuffer}
     * to extract the {@code nextnonce} parameter.
     *
     * @param buffer the {@link CharArrayBuffer} containing the value of the {@code Authentication-Info} header
     * @param cursor the {@link ParserCursor} used to navigate through the buffer content
     * @return the extracted {@code nextnonce} parameter value, or {@code null} if the parameter is not found
     */
    private String parseNextNonce(final CharSequence buffer, final ParserCursor cursor) {
        while (!cursor.atEnd()) {
            final String name = tokenParser.parseToken(buffer, cursor, TOKEN_DELIMS);
            if ("nextnonce".equals(name)) {
                cursor.updatePos(cursor.getPos() + 1);
                return tokenParser.parseValue(buffer, cursor, VALUE_DELIMS);
            }
            if (!cursor.atEnd()) {
                cursor.updatePos(cursor.getPos() + 1);
            }
        }
        return null;
    }

}
