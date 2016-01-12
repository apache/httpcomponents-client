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

package org.apache.http.client.methods;

import java.net.URI;

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.RequestLine;
import org.apache.hc.core5.http.message.AbstractHttpMessage;
import org.apache.hc.core5.http.message.BasicRequestLine;
import org.apache.hc.core5.util.Args;

/**
 * A wrapper class for {@link HttpRequest} that can be used to change properties of the current
 * request without modifying the original object.
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpRequestWrapper extends AbstractHttpMessage implements HttpUriRequest {

    private final HttpRequest original;
    private final HttpHost target;
    private final String method;
    private RequestLine requestLine;
    private ProtocolVersion version;
    private URI uri;
    private HttpEntity entity;

    private HttpRequestWrapper(final HttpRequest request, final HttpHost target) {
        super();
        this.original = Args.notNull(request, "HTTP request");
        this.target = target;
        this.version = this.original.getRequestLine().getProtocolVersion();
        this.method = this.original.getRequestLine().getMethod();
        if (request instanceof HttpUriRequest) {
            this.uri = ((HttpUriRequest) request).getURI();
        } else {
            this.uri = null;
        }
        setHeaders(request.getAllHeaders());
        this.entity = request.getEntity();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return this.version != null ? this.version : this.original.getProtocolVersion();
    }

    public void setProtocolVersion(final ProtocolVersion version) {
        this.version = version;
        this.requestLine = null;
    }

    @Override
    public URI getURI() {
        return this.uri;
    }

    public void setURI(final URI uri) {
        this.uri = uri;
        this.requestLine = null;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public void abort() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAborted() {
        return false;
    }

    @Override
    public RequestLine getRequestLine() {
        if (this.requestLine == null) {
            String requestUri;
            if (this.uri != null) {
                requestUri = this.uri.toASCIIString();
            } else {
                requestUri = this.original.getRequestLine().getUri();
            }
            if (requestUri == null || requestUri.isEmpty()) {
                requestUri = "/";
            }
            this.requestLine = new BasicRequestLine(this.method, requestUri, getProtocolVersion());
        }
        return this.requestLine;
    }

    @Override
    public HttpEntity getEntity() {
        return this.entity;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

    public HttpRequest getOriginal() {
        return this.original;
    }

    /**
     * @since 4.4
     */
    public HttpHost getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return getRequestLine() + " " + super.toString();
    }

    /**
     * Creates a mutable wrapper of the original request.
     *
     * @param request original request
     * @return mutable request wrappering the original one
     */
    public static HttpRequestWrapper wrap(final HttpRequest request, final HttpHost target) {
        return new HttpRequestWrapper(request, target);
    }

}
