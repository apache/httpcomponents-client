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

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;

/**
 * Common HTTP methods using {@link BasicHttpRequest} as a HTTP request message representation.
 *
 * @since 5.0
 *
 * @deprecated Use {@link org.apache.hc.core5.http.support.BasicRequestBuilder}.
 */
@Deprecated
public final class BasicHttpRequests {

    /**
     * Creates a new BasicHttpRequest for the given {@code method} and {@code String} URI.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request string URI.
     * @return A new BasicHttpRequest.
     */
    public static BasicHttpRequest create(final String method, final String uri) {
        return create(Method.normalizedValueOf(method), uri);
    }

    /**
     * Creates a new BasicHttpRequest for the given {@code method} and {@code URI}.
     *
     * @param method A method supported by this class.
     * @param uri a non-null request URI.
     * @return A new BasicHttpRequest.
     */
    public static BasicHttpRequest create(final String method, final URI uri) {
        return create(Method.normalizedValueOf(method), uri);
    }

    /**
     * Creates a new DELETE {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest delete(final String uri) {
        return delete(URI.create(uri));
    }

    /**
     * Creates a new DELETE {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest delete(final URI uri) {
        return create(Method.DELETE, uri);
    }

    /**
     * Creates a new DELETE {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest delete(final HttpHost host, final String path) {
        return create(Method.DELETE, host, path);
    }

    /**
     * Creates a new GET {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest get(final String uri) {
        return get(URI.create(uri));
    }

    /**
     * Creates a new GET {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest get(final URI uri) {
        return create(Method.GET, uri);
    }

    /**
     * Creates a new HEAD {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest get(final HttpHost host, final String path) {
        return create(Method.GET, host, path);
    }

    /**
     * Creates a new HEAD {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest head(final String uri) {
        return head(URI.create(uri));
    }

    /**
     * Creates a new HEAD {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest head(final URI uri) {
        return create(Method.HEAD, uri);
    }

    /**
     * Creates a new HEAD {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest head(final HttpHost host, final String path) {
        return create(Method.HEAD, host, path);
    }

    /**
     * Creates a new OPTIONS {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest options(final String uri) {
        return options(URI.create(uri));
    }

    /**
     * Creates a new OPTIONS {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest options(final URI uri) {
        return create(Method.OPTIONS, uri);
    }

    /**
     * Creates a new OPTIONS {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest options(final HttpHost host, final String path) {
        return create(Method.OPTIONS, host, path);
    }

    /**
     * Creates a new PATCH {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest patch(final String uri) {
        return patch(URI.create(uri));
    }

    /**
     * Creates a new PATCH {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest patch(final URI uri) {
        return create(Method.PATCH, uri);
    }

    /**
     * Creates a new PATCH {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest patch(final HttpHost host, final String path) {
        return create(Method.PATCH, host, path);
    }

    /**
     * Creates a new POST {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest post(final String uri) {
        return post(URI.create(uri));
    }

    /**
     * Creates a new POST {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest post(final URI uri) {
        return create(Method.POST, uri);
    }

    /**
     * Creates a new POST {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest post(final HttpHost host, final String path) {
        return create(Method.POST, host, path);
    }

    /**
     * Creates a new PUT {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest put(final String uri) {
        return put(URI.create(uri));
    }

    /**
     * Creates a new PUT {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest put(final URI uri) {
        return create(Method.PUT, uri);
    }

    /**
     * Creates a new PUT {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest put(final HttpHost host, final String path) {
        return create(Method.PUT, host, path);
    }

    /**
     * Creates a new TRACE {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest trace(final String uri) {
        return trace(URI.create(uri));
    }

    /**
     * Creates a new TRACE {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest trace(final URI uri) {
        return create(Method.TRACE, uri);
    }

    /**
     * Creates a new TRACE {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest trace(final HttpHost host, final String path) {
        return create(Method.TRACE, host, path);
    }

    /**
     * Creates a new {@link BasicHttpRequest}.
     *
     * @param method a non-null HTTP method.
     * @param uri a non-null URI String.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest create(final Method method, final String uri) {
        return create(method, URI.create(uri));
    }

    /**
     * Creates a new {@link BasicHttpRequest}.
     *
     * @param method a non-null HTTP method.
     * @param uri a non-null URI.
     * @return a new BasicHttpRequest
     */
    public static BasicHttpRequest create(final Method method, final URI uri) {
        return new BasicHttpRequest(method, uri);
    }

    /**
     * Creates a new {@link BasicHttpRequest}.
     *
     * @param method a non-null HTTP method.
     * @param host HTTP host.
     * @param path request path.
     * @return a new subclass of BasicHttpRequest
     */
    public static BasicHttpRequest create(final Method method, final HttpHost host, final String path) {
        return new BasicHttpRequest(method, host, path);
    }

}
