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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h1>RequestTraceInterceptor</h1>
 *
 * <p>This class serves as an interceptor for HTTP TRACE requests, ensuring they adhere to specific security and protocol guidelines.</p>
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Validates TRACE requests by checking for sensitive headers such as {@code Authorization} and {@code Cookie}.</li>
 *   <li>Ensures that TRACE requests do not contain a request body, throwing a {@link ProtocolException} if a body is present.</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and therefore thread-safe, as indicated by its {@code ThreadingBehavior.STATELESS} annotation.</p>
 *
 * <p><strong>Interceptor Behavior:</strong></p>
 * <ul>
 *   <li>If the HTTP method is TRACE, the interceptor throws a {@link ProtocolException} if any {@code Authorization} or {@code Cookie} headers are present to prevent sensitive data leakage.</li>
 *   <li>If a TRACE request contains a body, a {@link ProtocolException} is thrown.</li>
 * </ul>
 *
 * @version 5.4
 * @see HttpRequestInterceptor
 * @see HttpException
 * @see IOException
 * @see ProtocolException
 * @see Method#TRACE
 * @see HttpHeaders#AUTHORIZATION
 * @see HttpHeaders#COOKIE
 *//**
 * <h1>RequestTraceInterceptor</h1>
 *
 * <p>This class serves as an interceptor for HTTP TRACE requests, ensuring they adhere to specific security and protocol guidelines.</p>
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Validates TRACE requests by checking for sensitive headers such as {@code Authorization} and {@code Cookie}.</li>
 *   <li>Ensures that TRACE requests do not contain a request body, throwing a {@link ProtocolException} if a body is present.</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and therefore thread-safe, as indicated by its {@code ThreadingBehavior.STATELESS} annotation.</p>
 *
 * <p><strong>Interceptor Behavior:</strong></p>
 * <ul>
 *   <li>If the HTTP method is TRACE, the interceptor throws a {@link ProtocolException} if any {@code Authorization} or {@code Cookie} headers are present to prevent sensitive data leakage.</li>
 *   <li>If a TRACE request contains a body, a {@link ProtocolException} is thrown.</li>
 * </ul>
 *
 * @version 5.4
 * @see HttpRequestInterceptor
 * @see HttpException
 * @see IOException
 * @see ProtocolException
 * @see Method#TRACE
 * @see HttpHeaders#AUTHORIZATION
 * @see HttpHeaders#COOKIE
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class RequestValidateTrace implements HttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestValidateTrace.class);

    /**
     * Singleton instance of {@link RequestValidateTrace}.
     */
    public static final HttpRequestInterceptor INSTANCE = new RequestValidateTrace();

    /**
     * Default constructor.
     */
    public RequestValidateTrace() {
        super();
    }

    /**
     * Processes an incoming HTTP request. If the request is of type TRACE, it performs the following actions:
     * <ul>
     *   <li>Throws a {@link ProtocolException} if the request contains an {@code Authorization} header to prevent sensitive data leakage.</li>
     *   <li>Throws a {@link ProtocolException} if the request contains a {@code Cookie} header to prevent sensitive data leakage.</li>
     *   <li>Throws a {@link ProtocolException} if the request contains a body.</li>
     * </ul>
     *
     * @param request The incoming HTTP request. Cannot be {@code null}.
     * @param entity  Details of the request entity. Can be {@code null}.
     * @param context The HTTP context.
     * @throws HttpException If a protocol error occurs.
     * @throws IOException   If an I/O error occurs.
     */
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {

        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        // Check if the request method is TRACE
        if (Method.TRACE.isSame(request.getMethod())) {

            // A client MUST NOT send content in a TRACE request.
            if (entity != null) {
                throw new ProtocolException("TRACE request MUST NOT contain a request body.");
            }

            // Check for sensitive headers
            final Header authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                throw new ProtocolException("TRACE request MUST NOT contain an Authorization header.");
            }

            // Check for cookies
            final Header cookieHeader = request.getHeader(HttpHeaders.COOKIE);
            if (cookieHeader != null) {
                throw new ProtocolException("TRACE request MUST NOT contain a Cookie header.");
            }
        }
    }
}
