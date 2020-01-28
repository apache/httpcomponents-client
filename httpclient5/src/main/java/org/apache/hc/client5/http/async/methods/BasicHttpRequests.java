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

package org.apache.hc.client5.http.async.methods;

import java.net.URI;
import java.util.Locale;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.util.Args;

/**
 * Common HTTP methods using {@link BasicHttpRequest} as a HTTP request message representation.
 *
 * @since 5.0
 */
public final class BasicHttpRequests {

    // TODO Next version of HttpCore:
    // Method.normalizedValueOf(method)
    private static Method normalizedValueOf(final String method) {
        return Method.valueOf(Args.notNull(method, "method").toUpperCase(Locale.ROOT));
    }

    /**
     * Creates a new BasicHttpRequest for the given {@code method} and {@code String} URI.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request string URI.
     * @return A new BasicHttpRequest.
     */
    public static BasicHttpRequest create(final String method, final String uri) {
        // TODO Next version of HttpCore:
        // return create(Method.normalizedValueOf(method), uri);
        return create(normalizedValueOf(method), uri);
    }

    /**
     * Creates a new BasicHttpRequest for the given {@code method} and {@code URI}.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request URI.
     * @return A new BasicHttpRequest.
     */
    public static BasicHttpRequest create(final String method, final URI uri) {
        // TODO Next version of HttpCore:
        // return create(Method.normalizedValueOf(method), uri);
        return create(normalizedValueOf(method), uri);
    }

    public static BasicHttpRequest delete(final String uri) {
        return delete(URI.create(uri));
    }

    public static BasicHttpRequest delete(final URI uri) {
        return create(Method.DELETE, uri);
    }

    public static BasicHttpRequest delete(final HttpHost host, final String path) {
        return create(Method.DELETE, host, path);
    }

    public static BasicHttpRequest get(final String uri) {
        return get(URI.create(uri));
    }

    public static BasicHttpRequest get(final URI uri) {
        return create(Method.GET, uri);
    }

    public static BasicHttpRequest get(final HttpHost host, final String path) {
        return create(Method.GET, host, path);
    }

    public static BasicHttpRequest head(final String uri) {
        return head(URI.create(uri));
    }

    public static BasicHttpRequest head(final URI uri) {
        return create(Method.HEAD, uri);
    }

    public static BasicHttpRequest head(final HttpHost host, final String path) {
        return create(Method.HEAD, host, path);
    }

    public static BasicHttpRequest options(final String uri) {
        return options(URI.create(uri));
    }

    public static BasicHttpRequest options(final URI uri) {
        return create(Method.OPTIONS, uri);
    }

    public static BasicHttpRequest options(final HttpHost host, final String path) {
        return create(Method.OPTIONS, host, path);
    }

    public static BasicHttpRequest patch(final String uri) {
        return patch(URI.create(uri));
    }

    public static BasicHttpRequest patch(final URI uri) {
        return create(Method.PATCH, uri);
    }

    public static BasicHttpRequest patch(final HttpHost host, final String path) {
        return create(Method.PATCH, host, path);
    }

    public static BasicHttpRequest post(final String uri) {
        return post(URI.create(uri));
    }

    public static BasicHttpRequest post(final URI uri) {
        return create(Method.POST, uri);
    }

    public static BasicHttpRequest post(final HttpHost host, final String path) {
        return create(Method.POST, host, path);
    }

    public static BasicHttpRequest put(final String uri) {
        return put(URI.create(uri));
    }

    public static BasicHttpRequest put(final URI uri) {
        return create(Method.PUT, uri);
    }

    public static BasicHttpRequest put(final HttpHost host, final String path) {
        return create(Method.PUT, host, path);
    }

    public static BasicHttpRequest trace(final String uri) {
        return trace(URI.create(uri));
    }

    public static BasicHttpRequest trace(final URI uri) {
        return create(Method.TRACE, uri);
    }

    public static BasicHttpRequest trace(final HttpHost host, final String path) {
        return create(Method.TRACE, host, path);
    }

    /**
     * Creates a request object of the exact subclass of {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI String.
     * @return a new subclass of BasicHttpRequest
     */
    public static BasicHttpRequest create(final Method method, final String uri) {
        return create(method, URI.create(uri));
    }

    /**
     * Creates a request object of the exact subclass of {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new subclass of BasicHttpRequest
     */
    public static BasicHttpRequest create(final Method method, final URI uri) {
        return new BasicHttpRequest(method, uri);
    }

    /**
     * Creates a request object of the exact subclass of {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new subclass of BasicHttpRequest
     */
    public static BasicHttpRequest create(final Method method, final HttpHost host, final String path) {
        return new BasicHttpRequest(method, host, path);
    }

}
