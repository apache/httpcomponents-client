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

import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.HttpRequestWrapper;
import org.apache.hc.core5.util.Args;

public final class SimpleHttpRequest extends HttpRequestWrapper {

    private final String body;
    private final ContentType contentType;

    public static SimpleHttpRequest get(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.GET, requestUri, null, null);
    }

    public static SimpleHttpRequest get(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.GET, URI.create(requestUri), null, null);
    }

    public static SimpleHttpRequest get(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.GET, host, path, null, null);
    }

    public static SimpleHttpRequest head(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.HEAD, requestUri, null, null);
    }

    public static SimpleHttpRequest head(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.HEAD, URI.create(requestUri), null, null);
    }

    public static SimpleHttpRequest head(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.HEAD, host, path, null, null);
    }

    public static SimpleHttpRequest post(final URI requestUri, final String body, final ContentType contentType) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.POST, requestUri, body, contentType);
    }

    public static SimpleHttpRequest post(final String requestUri, final String body, final ContentType contentType) {
        return new SimpleHttpRequest(StandardMethods.POST, URI.create(requestUri), body, contentType);
    }

    public static SimpleHttpRequest post(final HttpHost host, final String path, final String body, final ContentType contentType) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.POST, host, path, body, contentType);
    }

    public static SimpleHttpRequest PUT(final URI requestUri, final String body, final ContentType contentType) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.PUT, requestUri, body, contentType);
    }

    public static SimpleHttpRequest put(final String requestUri, final String body, final ContentType contentType) {
        return new SimpleHttpRequest(StandardMethods.PUT, URI.create(requestUri), body, contentType);
    }

    public static SimpleHttpRequest put(final HttpHost host, final String path, final String body, final ContentType contentType) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.PUT, host, path, body, contentType);
    }

    public static SimpleHttpRequest delete(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.DELETE, requestUri, null, null);
    }

    public static SimpleHttpRequest delete(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.DELETE, URI.create(requestUri), null, null);
    }

    public static SimpleHttpRequest delete(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.DELETE, host, path, null, null);
    }

    public static SimpleHttpRequest trace(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.TRACE, requestUri, null, null);
    }

    public static SimpleHttpRequest trace(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.TRACE, URI.create(requestUri), null, null);
    }

    public static SimpleHttpRequest trace(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.TRACE, host, path, null, null);
    }

    public static SimpleHttpRequest options(final URI requestUri) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.OPTIONS, requestUri, null, null);
    }

    public static SimpleHttpRequest options(final String requestUri) {
        return new SimpleHttpRequest(StandardMethods.OPTIONS, URI.create(requestUri), null, null);
    }

    public static SimpleHttpRequest options(final HttpHost host, final String path) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.OPTIONS, host, path, null, null);
    }

    public static SimpleHttpRequest patch(final URI requestUri, final String body, final ContentType contentType) {
        Args.notNull(requestUri, "Request URI");
        return new SimpleHttpRequest(StandardMethods.PATCH, requestUri, body, contentType);
    }

    public static SimpleHttpRequest patch(final String requestUri, final String body, final ContentType contentType) {
        return new SimpleHttpRequest(StandardMethods.PATCH, URI.create(requestUri), body, contentType);
    }

    public static SimpleHttpRequest patch(final HttpHost host, final String path, final String body, final ContentType contentType) {
        Args.notNull(host, "Host");
        return new SimpleHttpRequest(StandardMethods.PATCH, host, path, body, contentType);
    }

    public SimpleHttpRequest(final HttpRequest head, final String body, final ContentType contentType) {
        super(head);
        this.body = body;
        this.contentType = contentType;
    }

    public SimpleHttpRequest(
            final String method,
            final HttpHost host,
            final String path,
            final String body,
            final ContentType contentType) {
        super(new BasicHttpRequest(method, host, path));
        this.body = body;
        this.contentType = contentType;
    }

    SimpleHttpRequest(
            final StandardMethods method,
            final HttpHost host,
            final String path,
            final String body,
            final ContentType contentType) {
        this(method.name(), host, path, body, contentType);
    }

    public SimpleHttpRequest(final String method, final URI requestUri, final String body, final ContentType contentType) {
        super(new BasicHttpRequest(method, requestUri));
        this.body = body;
        this.contentType = contentType;
    }

    SimpleHttpRequest(final StandardMethods method, final URI requestUri, final String body, final ContentType contentType) {
        this(method.name(), requestUri, body, contentType);
    }

    public String getBody() {
        return body;
    }

    public ContentType getContentType() {
        return contentType;
    }

}

