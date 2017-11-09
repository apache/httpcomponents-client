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

import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.util.Args;

public final class SimpleHttpRequest extends ConfigurableHttpRequest {

    private RequestConfig requestConfig;
    private SimpleBody body;

    public static SimpleHttpRequest get(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.GET, requestUri);
    }

    public static SimpleHttpRequest get(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.GET, URI.create(requestUri));
    }

    public static SimpleHttpRequest get(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.GET, host, path);
    }

    public static SimpleHttpRequest post(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.POST, requestUri);
    }

    public static SimpleHttpRequest post(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.POST, URI.create(requestUri));
    }

    public static SimpleHttpRequest post(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.POST, host, path);
    }

    public static SimpleHttpRequest put(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.PUT, requestUri);
    }

    public static SimpleHttpRequest put(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.PUT, URI.create(requestUri));
    }

    public static SimpleHttpRequest put(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.PUT, host, path);
    }

    public static SimpleHttpRequest head(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.HEAD, requestUri);
    }

    public static SimpleHttpRequest head(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.HEAD, URI.create(requestUri));
    }

    public static SimpleHttpRequest head(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.HEAD, host, path);
    }

    public static SimpleHttpRequest delete(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.DELETE, requestUri);
    }

    public static SimpleHttpRequest delete(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.DELETE, URI.create(requestUri));
    }

    public static SimpleHttpRequest delete(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.DELETE, host, path);
    }

    public static SimpleHttpRequest trace(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.TRACE, requestUri);
    }

    public static SimpleHttpRequest trace(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.TRACE, URI.create(requestUri));
    }

    public static SimpleHttpRequest trace(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.TRACE, host, path);
    }

    public static SimpleHttpRequest options(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.OPTIONS, requestUri);
    }

    public static SimpleHttpRequest options(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.OPTIONS, URI.create(requestUri));
    }

    public static SimpleHttpRequest options(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.OPTIONS, host, path);
    }

    public static SimpleHttpRequest patch(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.PATCH, requestUri);
    }

    public static SimpleHttpRequest patch(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.PATCH, URI.create(requestUri));
    }

    public static SimpleHttpRequest patch(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.PATCH, host, path);
    }

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

    public SimpleHttpRequest(final String method, final String path) {
        super(method, path);
    }

    public SimpleHttpRequest(final String method, final HttpHost host, final String path) {
        super(method, host, path);
    }

    public SimpleHttpRequest(final String method, final URI requestUri) {
        super(method, requestUri);
    }

    SimpleHttpRequest(final StandardMethods method, final URI requestUri) {
        this(method.name(), requestUri);
    }

    SimpleHttpRequest(final StandardMethods method, final HttpHost host, final String path) {
        this(method.name(), host, path);
    }

    public void setBody(final SimpleBody body) {
        this.body = body;
    }

    public void setBodyBytes(final byte[] bodyBytes, final ContentType contentType) {
        this.body = SimpleBody.create(bodyBytes, contentType);
    }

    public void setBodyText(final String bodyText, final ContentType contentType) {
        this.body = SimpleBody.create(bodyText, contentType);
    }

    public SimpleBody getBody() {
        return body;
    }

    public ContentType getContentType() {
        return body != null ? body.getContentType() : null;
    }

    public String getBodyText() {
        return body != null ? body.getBodyText() : null;
    }

    public byte[] getBodyBytes() {
        return body != null ? body.getBodyBytes() : null;
    }

}

