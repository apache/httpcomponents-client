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
import java.util.Iterator;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

/**
 * HTTP request that can enclose a body represented as a simple text string or an array of bytes.
 * <p>
 * IMPORTANT: {@link SimpleHttpRequest}s are intended for simple scenarios where entities inclosed
 * in requests are known to be small. It is generally recommended to use
 * {@link org.apache.hc.core5.http.nio.support.AsyncRequestBuilder} and streaming
 * {@link org.apache.hc.core5.http.nio.AsyncEntityProducer}s.
 *
 * @since 5.0
 *
 * @see SimpleBody
 * @see org.apache.hc.core5.http.nio.support.AsyncRequestBuilder
 * @see org.apache.hc.core5.http.nio.AsyncEntityProducer
 */
public final class SimpleHttpRequest extends ConfigurableHttpRequest {

    private static final long serialVersionUID = 1L;
    private SimpleBody body;

    /**
     * Creates a new request message with the given method and request path.
     *
     * @param method request method.
     * @param uri request URI.
     * @return a new SimpleHttpRequest.
     * @since 5.1
     */
    public static SimpleHttpRequest create(final String method, final String uri) {
        return new SimpleHttpRequest(method, uri);
    }

    /**
     * Creates a new request message with the given method and request path.
     *
     * @param method request method.
     * @param uri request URI.
     * @return a new SimpleHttpRequest.
     * @since 5.1
     */
    public static SimpleHttpRequest create(final String method, final URI uri) {
        return new SimpleHttpRequest(method, uri);
    }

    /**
     * Creates a new request message with the given method and request path.
     *
     * @param method request method.
     * @param uri request URI.
     * @return a new SimpleHttpRequest.
     * @since 5.1
     */
    public static SimpleHttpRequest create(final Method method, final URI uri) {
        return new SimpleHttpRequest(method, uri);
    }

    /**
     * Creates a new request message with the given method, host, and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     * @return a new SimpleHttpRequest.
     * @since 5.1
     */
    public static SimpleHttpRequest create(final Method method, final HttpHost host, final String path) {
        return new SimpleHttpRequest(method, host, path);
    }

    /**
     * Creates a new request message with the given method, scheme, authority, and request path.
     *
     * @param method request method.
     * @param scheme request host.
     * @param authority request URI authority.
     * @param path request path.
     * @return a new SimpleHttpRequest.
     * @since 5.1
     */
    public static SimpleHttpRequest create(final String method, final String scheme, final URIAuthority authority, final String path) {
        return new SimpleHttpRequest(method, scheme, authority, path);
    }

    /**
     * Copies the given HttpRequest.
     *
     * @param original the source to copy.
     * @return a new SimpleHttpRequest.
     * @deprecated Use {@link SimpleRequestBuilder}
     */
    @Deprecated
    public static SimpleHttpRequest copy(final HttpRequest original) {
        Args.notNull(original, "HTTP request");
        final SimpleHttpRequest copy = new SimpleHttpRequest(original.getMethod(), original.getRequestUri());
        copy.setVersion(original.getVersion());
        for (final Iterator<Header> it = original.headerIterator(); it.hasNext(); ) {
            copy.addHeader(it.next());
        }
        copy.setScheme(original.getScheme());
        copy.setAuthority(original.getAuthority());
        return copy;
    }

    /**
     * Constructs a new request message with the given method and request path.
     *
     * @param method request method.
     * @param path request path.
     */
    public SimpleHttpRequest(final String method, final String path) {
        super(method, path);
    }

    /**
     * Constructs a new request message with the given method, host, and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     */
    public SimpleHttpRequest(final String method, final HttpHost host, final String path) {
        super(method, host, path);
    }

    /**
     * Constructs a new request message with the given method, and request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     * @since 5.1
     */
    public SimpleHttpRequest(final String method, final URI requestUri) {
        super(method, requestUri);
    }

    /**
     * Constructs a new request message with the given method, and request URI.
     *
     * @param method request method.
     * @param requestUri request URI.
     * @since 5.1
     */
    public SimpleHttpRequest(final Method method, final URI requestUri) {
        this(method.name(), requestUri);
    }

    /**
     * Constructs a new request message with the given method, host, and request path.
     *
     * @param method request method.
     * @param host request host.
     * @param path request path.
     * @since 5.1
     */
    public SimpleHttpRequest(final Method method, final HttpHost host, final String path) {
        this(method.name(), host, path);
    }

    /**
     * Constructs a new request message with the given method, scheme, authority, and request path.
     *
     * @param method request method.
     * @param scheme request host.
     * @param authority request URI authority.
     * @param path request path.
     * @since 5.1
     */
    public SimpleHttpRequest(final String method, final String scheme, final URIAuthority authority, final String path) {
        super(method, scheme, authority, path);
    }

    /**
     * Sets the message body and content type.
     *
     * @param body request body.
     */
    public void setBody(final SimpleBody body) {
        this.body = body;
    }

    /**
     * Sets the message body and content type.
     *
     * @param bodyBytes request body.
     * @param contentType request content type.
     */
    public void setBody(final byte[] bodyBytes, final ContentType contentType) {
        this.body = SimpleBody.create(bodyBytes, contentType);
    }

    /**
     * Sets the message body and content type.
     *
     * @param bodyText request body.
     * @param contentType request content type.
     */
    public void setBody(final String bodyText, final ContentType contentType) {
        this.body = SimpleBody.create(bodyText, contentType);
    }

    /**
     * Gets the request body.
     *
     * @return the request body.
     */
    public SimpleBody getBody() {
        return body;
    }

    /**
     * Gets the request content type.
     *
     * @return the request content type.
     */
    public ContentType getContentType() {
        return body != null ? body.getContentType() : null;
    }

    /**
     * Gets the request body as a String.
     *
     * @return the request body.
     */
    public String getBodyText() {
        return body != null ? body.getBodyText() : null;
    }

    /**
     * Gets the request body as a byte array.
     *
     * @return the request body.
     */
    public byte[] getBodyBytes() {
        return body != null ? body.getBodyBytes() : null;
    }

}

