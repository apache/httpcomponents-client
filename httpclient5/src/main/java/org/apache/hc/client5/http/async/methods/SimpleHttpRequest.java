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
import org.apache.hc.core5.util.Args;

/**
 * HTTP request that can enclose a body represented as a simple text string or an array of bytes.
 *
 * @since 5.0
 *
 * @see SimpleBody
 */
public final class SimpleHttpRequest extends ConfigurableHttpRequest {

    private static final long serialVersionUID = 1L;
    private SimpleBody body;

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

    SimpleHttpRequest(final Method method, final URI requestUri) {
        this(method.name(), requestUri);
    }

    SimpleHttpRequest(final Method method, final HttpHost host, final String path) {
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

