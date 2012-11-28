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

package org.apache.http.impl.client.execchain;

import java.net.URI;

import org.apache.http.annotation.NotThreadSafe;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.protocol.HTTP;

/**
 * A wrapper class for {@link HttpRequest} that can be used to change properties of the current
 * request without modifying the original object.
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpRequestWrapper extends AbstractHttpMessage implements HttpRequest {

    private final HttpRequest original;

    private URI uri;

    private HttpRequestWrapper(final HttpRequest request) {
        super();
        this.original = request;
        if (request instanceof HttpUriRequest) {
            this.uri = ((HttpUriRequest) request).getURI();
        } else {
            this.uri = null;
        }
        setHeaders(request.getAllHeaders());
    }

    public ProtocolVersion getProtocolVersion() {
        return this.original.getProtocolVersion();
    }

    public URI getURI() {
        return this.uri;
    }

    public void setURI(final URI uri) {
        this.uri = uri;
    }

    public RequestLine getRequestLine() {
        ProtocolVersion version = this.original.getRequestLine().getProtocolVersion();
        String method = this.original.getRequestLine().getMethod();
        String uritext = null;
        if (this.uri != null) {
            uritext = this.uri.toASCIIString();
        } else {
            uritext = this.original.getRequestLine().getUri();
        }
        if (uritext == null || uritext.length() == 0) {
            uritext = "/";
        }
        return new BasicRequestLine(method, uritext, version);
    }

    public HttpRequest getOriginal() {
        return this.original;
    }

    public boolean isRepeatable() {
        return true;
    }

    @Override
    public String toString() {
        return getRequestLine() + " " + this.headergroup;
    }

    static class HttpEntityEnclosingRequestWrapper extends HttpRequestWrapper
        implements HttpEntityEnclosingRequest {

        private HttpEntity entity;

        public HttpEntityEnclosingRequestWrapper(final HttpEntityEnclosingRequest request)
            throws ProtocolException {
            super(request);
            setEntity(request.getEntity());
        }

        public HttpEntity getEntity() {
            return this.entity;
        }

        public void setEntity(final HttpEntity entity) {
            this.entity = entity != null ? new RequestEntityWrapper(entity) : null;
        }

        public boolean expectContinue() {
            Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
            return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
        }

        @Override
        public boolean isRepeatable() {
            return this.entity == null || this.entity.isRepeatable();
        }

    }

    public static HttpRequestWrapper wrap(final HttpRequest request) throws ProtocolException {
        if (request == null) {
            return null;
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            return new HttpEntityEnclosingRequestWrapper((HttpEntityEnclosingRequest) request);
        } else {
            return new HttpRequestWrapper(request);
        }
    }

}
