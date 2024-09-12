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
        switch (Args.notNull(method, "method")) {
        case DELETE:
            return delete(uri);
        case GET:
            return get(uri);
        case HEAD:
            return head(uri);
        case OPTIONS:
            return options(uri);
        case PATCH:
            return patch(uri);
        case POST:
            return post(uri);
        case PUT:
            return put(uri);
        case TRACE:
            return trace(uri);
        default:
            throw new IllegalArgumentException(method.toString());
        }
    }

    /**
     * Creates a new HttpUriRequest for the given {@code method} and {@code String} URI.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request string URI.
     * @throws IllegalArgumentException if the method is not supported.
     * @throws IllegalArgumentException if the string URI is null.
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
     * @throws IllegalArgumentException if the URI is null.
     * @return A new HttpUriRequest.
     */
    public static HttpUriRequest create(final String method, final URI uri) {
        return create(Method.normalizedValueOf(method), uri);
    }

    /**
     * Constructs a new {@code "DELETE"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "DELETE" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest delete(final String uri) {
        return delete(URI.create(uri));
    }

    /**
     * Constructs a new {@code "DELETE"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "DELETE" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest delete(final URI uri) {
        return new HttpDelete(uri);
    }

    /**
     * Constructs a new {@code "GET"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "GET" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest get(final String uri) {
        return get(URI.create(uri));
    }

    /**
     * Constructs a new {@code "GET"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "GET" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest get(final URI uri) {
        return new HttpGet(uri);
    }

    /**
     * Constructs a new {@code "HEAD"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "HEAD" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest head(final String uri) {
        return head(URI.create(uri));
    }

    /**
     * Constructs a new {@code "HEAD"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "HEAD" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest head(final URI uri) {
        return new HttpHead(uri);
    }

    /**
     * Constructs a new {@code "OPTIONS"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "OPTIONS" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest options(final String uri) {
        return options(URI.create(uri));
    }

    /**
     * Constructs a new {@code "OPTIONS"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "OPTIONS" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest options(final URI uri) {
        return new HttpOptions(uri);
    }

    /**
     * Constructs a new {@code "PATCH"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "PATCH" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest patch(final String uri) {
        return patch(URI.create(uri));
    }

    /**
     * Constructs a new {@code "PATCH"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "PATCH" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest patch(final URI uri) {
        return new HttpPatch(uri);
    }

    /**
     * Constructs a new {@code "POST"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "POST" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest post(final String uri) {
        return post(URI.create(uri));
    }

    /**
     * Constructs a new {@code "POST"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "POST" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest post(final URI uri) {
        return new HttpPost(uri);
    }

    /**
     * Constructs a new {@code "PUT"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "PUT" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest put(final String uri) {
        return put(URI.create(uri));
    }

    /**
     * Constructs a new {@code "PUT"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "PUT" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest put(final URI uri) {
        return new HttpPut(uri);
    }

    /**
     * Constructs a new {@code "TRACE"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "TRACE" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest trace(final String uri) {
        return trace(URI.create(uri));
    }

    /**
     * Constructs a new {@code "TRACE"} HttpUriRequest initialized with the given URI.
     *
     * @param uri a non-null request URI.
     * @return a new "TRACE" HttpUriRequest.
     * @throws IllegalArgumentException if the URI is null.
     */
    public static HttpUriRequest trace(final URI uri) {
        return new HttpTrace(uri);
    }

}
