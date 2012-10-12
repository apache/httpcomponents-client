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

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.utils.CloneUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;
import org.apache.http.params.HttpParams;

/**
 * @since 4.3
 */
@NotThreadSafe
public class RequestBuilder {

    private String method;
    private ProtocolVersion version;
    private URI uri;
    private HeaderGroup headergroup;
    private HttpEntity entity;
    private HttpParams params;

    RequestBuilder(final String method) {
        super();
        this.method = method;
    }

    RequestBuilder() {
        this(null);
    }

    public static RequestBuilder create(final String method) {
        return new RequestBuilder(method);
    }

    public static RequestBuilder createGet() {
        return new RequestBuilder(HttpGet.METHOD_NAME);
    }

    public static RequestBuilder createHead() {
        return new RequestBuilder(HttpHead.METHOD_NAME);
    }

    public static RequestBuilder createPost() {
        return new RequestBuilder(HttpPost.METHOD_NAME);
    }

    public static RequestBuilder createPut() {
        return new RequestBuilder(HttpPut.METHOD_NAME);
    }

    public static RequestBuilder createDelete() {
        return new RequestBuilder(HttpDelete.METHOD_NAME);
    }

    public static RequestBuilder createTrace() {
        return new RequestBuilder(HttpTrace.METHOD_NAME);
    }

    public static RequestBuilder createOptions() {
        return new RequestBuilder(HttpOptions.METHOD_NAME);
    }

    public static RequestBuilder copy(final HttpRequest request) {
        return new RequestBuilder().doCopy(request);
    }

    public static RequestBuilder clone(final HttpRequest request) throws CloneNotSupportedException {
        return new RequestBuilder().doCopy(CloneUtils.cloneObject(request));
    }

    private RequestBuilder doCopy(final HttpRequest request) {
        if (request == null) {
            return this;
        }
        method = request.getRequestLine().getMethod();
        version = request.getRequestLine().getProtocolVersion();
        if (request instanceof HttpUriRequest) {
            uri = ((HttpUriRequest) request).getURI();
        } else {
            uri = URI.create(request.getRequestLine().getMethod());
        }
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        headergroup.clear();
        headergroup.setHeaders(request.getAllHeaders());
        if (request instanceof HttpEntityEnclosingRequest) {
            entity = ((HttpEntityEnclosingRequest) request).getEntity();
        } else {
            entity = null;
        }
        params = request.getParams();
        return this;
    }

    public String getMethod() {
        return method;
    }

    public RequestBuilder setMethod(final String method) {
        this.method = method;
        return this;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public RequestBuilder setVersion(final ProtocolVersion version) {
        this.version = version;
        return this;
    }

    public URI getUri() {
        return uri;
    }

    public RequestBuilder setUri(final URI uri) {
        this.uri = uri;
        return this;
    }

    public RequestBuilder setUri(final String uri) {
        this.uri = uri != null ? URI.create(uri) : null;
        return this;
    }

    public Header getFirstHeader(final String name) {
        return headergroup != null ? headergroup.getFirstHeader(name) : null;
    }

    public Header getLastHeader(final String name) {
        return headergroup != null ? headergroup.getLastHeader(name) : null;
    }

    public Header[] getHeaders(final String name) {
        return headergroup != null ? headergroup.getHeaders(name) : null;
    }

    public RequestBuilder addHeader(final Header header) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        headergroup.addHeader(header);
        return this;
    }

    public RequestBuilder addHeader(final String name, final String value) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        this.headergroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public RequestBuilder removeHeader(Header header) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        headergroup.removeHeader(header);
        return this;
    }

    public RequestBuilder removeHeaders(final String name) {
        if (name == null || headergroup == null) {
            return this;
        }
        for (HeaderIterator i = headergroup.iterator(); i.hasNext(); ) {
            Header header = i.nextHeader();
            if (name.equalsIgnoreCase(header.getName())) {
                i.remove();
            }
        }
        return this;
    }

    public RequestBuilder setHeader(final Header header) {
        if (headergroup != null) {
            headergroup = new HeaderGroup();
        }
        this.headergroup.updateHeader(header);
        return this;
    }

    public RequestBuilder setHeader(final String name, final String value) {
        if (headergroup != null) {
            headergroup = new HeaderGroup();
        }
        this.headergroup.updateHeader(new BasicHeader(name, value));
        return this;
    }

    public HttpEntity getEntity() {
        return entity;
    }

    public RequestBuilder setEntity(final HttpEntity entity) {
        this.entity = entity;
        return this;
    }

    public HttpParams getParams() {
        return params;
    }

    public RequestBuilder setParams(final HttpParams params) {
        this.params = params;
        return this;
    }

    private String getMethodName() {
        return this.method != null ? this.method : 
            (this.entity != null ? HttpPost.METHOD_NAME : HttpGet.METHOD_NAME);
    }

    public HttpUriRequest build() {
        HttpRequestBase result;
        String methodName = getMethodName();
        if (this.entity == null) {
            InternalRequest request = new InternalRequest(methodName);
            result = request;
        } else {
            InternalEntityEclosingRequest request = new InternalEntityEclosingRequest(methodName);
            request.setEntity(this.entity);
            result = request;
        }
        result.setProtocolVersion(this.version);
        result.setURI(this.uri != null ? this.uri : URI.create("/"));
        if (this.headergroup != null) {
            result.setHeaders(this.headergroup.getAllHeaders());
        }
        if (this.params != null) {
            result.setParams(this.params);
        }
        return result;
    }

    static class InternalRequest extends HttpRequestBase {

        private final String method;

        InternalRequest(final String method) {
            super();
            this.method = method;
        }

        @Override
        public String getMethod() {
            return this.method;
        }

    }

    static class InternalEntityEclosingRequest extends HttpEntityEnclosingRequestBase {

        private final String method;

        InternalEntityEclosingRequest(final String method) {
            super();
            this.method = method;
        }

        @Override
        public String getMethod() {
            return this.method;
        }

    }

}
