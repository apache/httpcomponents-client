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
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.support.AbstractRequestBuilder;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link SimpleHttpRequest} instances.
 * <p>
 * Please note that this class treats parameters differently depending on composition
 * of the request: if the request has a content entity explicitly set with
 * {@link #setBody(SimpleBody)} or it is not an entity enclosing method
 * (such as POST or PUT), parameters will be added to the query component
 * of the request URI. Otherwise, parameters will be added as a URL encoded entity.
 * </p>
 *
 * @since 5.0
 */
public class SimpleRequestBuilder extends AbstractRequestBuilder<SimpleHttpRequest> {

    private SimpleBody body;
    private RequestConfig requestConfig;

    SimpleRequestBuilder(final String method) {
        super(method);
    }

    SimpleRequestBuilder(final Method method) {
        super(method);
    }

    SimpleRequestBuilder(final String method, final URI uri) {
        super(method, uri);
    }

    SimpleRequestBuilder(final Method method, final URI uri) {
        super(method, uri);
    }

    SimpleRequestBuilder(final Method method, final String uri) {
        super(method, uri);
    }

    SimpleRequestBuilder(final String method, final String uri) {
        super(method, uri);
    }

    public static SimpleRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new SimpleRequestBuilder(method);
    }

    public static SimpleRequestBuilder create(final Method method) {
        Args.notNull(method, "HTTP method");
        return new SimpleRequestBuilder(method);
    }

    public static SimpleRequestBuilder get() {
        return new SimpleRequestBuilder(Method.GET);
    }

    public static SimpleRequestBuilder get(final URI uri) {
        return new SimpleRequestBuilder(Method.GET, uri);
    }

    public static SimpleRequestBuilder get(final String uri) {
        return new SimpleRequestBuilder(Method.GET, uri);
    }

    public static SimpleRequestBuilder head() {
        return new SimpleRequestBuilder(Method.HEAD);
    }

    public static SimpleRequestBuilder head(final URI uri) {
        return new SimpleRequestBuilder(Method.HEAD, uri);
    }

    public static SimpleRequestBuilder head(final String uri) {
        return new SimpleRequestBuilder(Method.HEAD, uri);
    }

    public static SimpleRequestBuilder patch() {
        return new SimpleRequestBuilder(Method.PATCH);
    }

    public static SimpleRequestBuilder patch(final URI uri) {
        return new SimpleRequestBuilder(Method.PATCH, uri);
    }

    public static SimpleRequestBuilder patch(final String uri) {
        return new SimpleRequestBuilder(Method.PATCH, uri);
    }

    public static SimpleRequestBuilder post() {
        return new SimpleRequestBuilder(Method.POST);
    }

    public static SimpleRequestBuilder post(final URI uri) {
        return new SimpleRequestBuilder(Method.POST, uri);
    }

    public static SimpleRequestBuilder post(final String uri) {
        return new SimpleRequestBuilder(Method.POST, uri);
    }

    public static SimpleRequestBuilder put() {
        return new SimpleRequestBuilder(Method.PUT);
    }

    public static SimpleRequestBuilder put(final URI uri) {
        return new SimpleRequestBuilder(Method.PUT, uri);
    }

    public static SimpleRequestBuilder put(final String uri) {
        return new SimpleRequestBuilder(Method.PUT, uri);
    }

    public static SimpleRequestBuilder delete() {
        return new SimpleRequestBuilder(Method.DELETE);
    }

    public static SimpleRequestBuilder delete(final URI uri) {
        return new SimpleRequestBuilder(Method.DELETE, uri);
    }

    public static SimpleRequestBuilder delete(final String uri) {
        return new SimpleRequestBuilder(Method.DELETE, uri);
    }

    public static SimpleRequestBuilder trace() {
        return new SimpleRequestBuilder(Method.TRACE);
    }

    public static SimpleRequestBuilder trace(final URI uri) {
        return new SimpleRequestBuilder(Method.TRACE, uri);
    }

    public static SimpleRequestBuilder trace(final String uri) {
        return new SimpleRequestBuilder(Method.TRACE, uri);
    }

    public static SimpleRequestBuilder options() {
        return new SimpleRequestBuilder(Method.OPTIONS);
    }

    public static SimpleRequestBuilder options(final URI uri) {
        return new SimpleRequestBuilder(Method.OPTIONS, uri);
    }

    public static SimpleRequestBuilder options(final String uri) {
        return new SimpleRequestBuilder(Method.OPTIONS, uri);
    }

    /**
     * @since 5.1
     */
    public static SimpleRequestBuilder copy(final SimpleHttpRequest request) {
        Args.notNull(request, "HTTP request");
        final SimpleRequestBuilder builder = new SimpleRequestBuilder(request.getMethod());
        builder.digest(request);
        return builder;
    }

    /**
     * @since 5.1
     */
    public static SimpleRequestBuilder copy(final HttpRequest request) {
        Args.notNull(request, "HTTP request");
        final SimpleRequestBuilder builder = new SimpleRequestBuilder(request.getMethod());
        builder.digest(request);
        return builder;
    }

    protected void digest(final SimpleHttpRequest request) {
        super.digest(request);
        setBody(request.getBody());
    }

    protected void digest(final HttpRequest request) {
        super.digest(request);
    }

    @Override
    public SimpleRequestBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public SimpleRequestBuilder setUri(final URI uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public SimpleRequestBuilder setUri(final String uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public SimpleRequestBuilder setScheme(final String scheme) {
        super.setScheme(scheme);
        return this;
    }

    @Override
    public SimpleRequestBuilder setAuthority(final URIAuthority authority) {
        super.setAuthority(authority);
        return this;
    }

    @Override
    public SimpleRequestBuilder setHttpHost(final HttpHost httpHost) {
        super.setHttpHost(httpHost);
        return this;
    }

    @Override
    public SimpleRequestBuilder setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public SimpleRequestBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public SimpleRequestBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public SimpleRequestBuilder addHeader(final String name, final String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public SimpleRequestBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public SimpleRequestBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public SimpleRequestBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public SimpleRequestBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public SimpleRequestBuilder setCharset(final Charset charset) {
        super.setCharset(charset);
        return this;
    }

    @Override
    public SimpleRequestBuilder addParameter(final NameValuePair nvp) {
        super.addParameter(nvp);
        return this;
    }

    @Override
    public SimpleRequestBuilder addParameter(final String name, final String value) {
        super.addParameter(name, value);
        return this;
    }

    @Override
    public SimpleRequestBuilder addParameters(final NameValuePair... nvps) {
        super.addParameters(nvps);
        return this;
    }

    @Override
    public SimpleRequestBuilder setAbsoluteRequestUri(final boolean absoluteRequestUri) {
        super.setAbsoluteRequestUri(absoluteRequestUri);
        return this;
    }

    public SimpleBody getBody() {
        return body;
    }

    public SimpleRequestBuilder setBody(final SimpleBody body) {
        this.body = body;
        return this;
    }

    public SimpleRequestBuilder setBody(final String content, final ContentType contentType) {
        this.body = SimpleBody.create(content, contentType);
        return this;
    }

    public SimpleRequestBuilder setBody(final byte[] content, final ContentType contentType) {
        this.body = SimpleBody.create(content, contentType);
        return this;
    }

    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    public SimpleRequestBuilder setRequestConfig(final RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
        return this;
    }

    public SimpleHttpRequest build() {
        String path = getPath();
        SimpleBody bodyCopy = this.body;
        final String method = getMethod();
        final List<NameValuePair> parameters = getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            final Charset charsetCopy = getCharset();
            if (bodyCopy == null && (Method.POST.isSame(method) || Method.PUT.isSame(method))) {
                final String content = WWWFormCodec.format(
                        parameters,
                        charsetCopy != null ? charsetCopy : ContentType.APPLICATION_FORM_URLENCODED.getCharset());
                bodyCopy = SimpleBody.create(content, ContentType.APPLICATION_FORM_URLENCODED);
            } else {
                try {
                    final URI uri = new URIBuilder(path)
                            .setCharset(charsetCopy)
                            .addParameters(parameters)
                            .build();
                    path = uri.toASCIIString();
                } catch (final URISyntaxException ex) {
                    // should never happen
                }
            }
        }

        if (bodyCopy != null && Method.TRACE.isSame(method)) {
            throw new IllegalStateException(Method.TRACE + " requests may not include an entity");
        }

        final SimpleHttpRequest result = new SimpleHttpRequest(method, getScheme(), getAuthority(), path);
        result.setVersion(getVersion());
        result.setHeaders(getHeaders());
        result.setBody(bodyCopy);
        result.setAbsoluteRequestUri(isAbsoluteRequestUri());
        result.setConfig(requestConfig);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ClassicRequestBuilder [method=");
        builder.append(getMethod());
        builder.append(", scheme=");
        builder.append(getScheme());
        builder.append(", authority=");
        builder.append(getAuthority());
        builder.append(", path=");
        builder.append(getPath());
        builder.append(", parameters=");
        builder.append(getParameters());
        builder.append(", headerGroup=");
        builder.append(Arrays.toString(getHeaders()));
        builder.append(", body=");
        builder.append(body);
        builder.append("]");
        return builder.toString();
    }

}
