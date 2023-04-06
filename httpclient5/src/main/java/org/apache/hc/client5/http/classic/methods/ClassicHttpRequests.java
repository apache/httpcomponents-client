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

package org.apache.hc.client5.http.classic.methods;

import java.net.URI;

import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.util.Args;

/**
 * Common HTTP methods using {@link HttpUriRequest} as a HTTP request message representation.
 * <p>
 * Each static method creates a request object of the exact subclass of {@link HttpUriRequest}
 * with a non-null URI.
 *
 * @since 5.0
 *
 * @deprecated Use {@link org.apache.hc.core5.http.io.support.ClassicRequestBuilder}
 */
@Deprecated
public final class ClassicHttpRequests {

    /**
     * Creates a new HttpUriRequest for the given {@code Method} and {@code String} URI.
     *
     * @param method A method.
     * @param uri a URI.
     * @return a new HttpUriRequest.
     */
    public static HttpUriRequest create(final Method method, final String uri) {
        return create(method, URI.create(uri));
    }

    /**
     * Creates a new HttpUriRequest for the given {@code Method} and {@code URI}.
     *
     * @param method A method.
     * @param uri a URI.
     * @return a new HttpUriRequest.
     */
    public static HttpUriRequest create(final Method method, final URI uri) {
        final HttpUriRequestExecutor executor;
        switch (Args.notNull(method, "method")) {
        case DELETE:
            executor = new DeleteHttpUriRequest();
            break;
        case GET:
            executor = new GetHttpUriRequest();
            break;
        case HEAD:
            executor = new HeadHttpUriRequest();
            break;
        case OPTIONS:
            executor = new OptionsHttpUriRequest();
            break;
        case PATCH:
            executor = new PatchHttpUriRequest();
            break;
        case POST:
            executor = new PostHttpUriRequest();
            break;
        case PUT:
            executor = new PutHttpUriRequest();
            break;
        case TRACE:
            executor = new TraceHttpUriRequest();
            break;
        default:
            throw new IllegalArgumentException(method.toString());
        }
        return executor.uriRequest(uri);
    }

    /**
     * Creates a new HttpUriRequest for the given {@code method} and {@code String} URI.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request string URI.
     * @throws IllegalArgumentException if the method is not supported.
     * @throws IllegalArgumentException if the string uri is null.
     * @return A new HttpUriRequest.
     */
    public static HttpUriRequest create(final String method, final String uri) {
        return create(Method.normalizedValueOf(method), uri);
    }

    /**
     * Creates a new HttpUriRequest for the given {@code method} and {@code URI}.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request URI.
     * @throws IllegalArgumentException if the method is not supported.
     * @throws IllegalArgumentException if the uri is null.
     * @return A new HttpUriRequest.
     */
    public static HttpUriRequest create(final String method, final URI uri) {
        return create(Method.normalizedValueOf(method), uri);
    }

}
