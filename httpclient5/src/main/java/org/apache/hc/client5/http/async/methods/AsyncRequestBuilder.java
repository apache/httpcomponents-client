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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * Builder for {@link AsyncRequestProducer} instances.
 * <p>
 * Please note that this class treats parameters differently depending on composition
 * of the request: if the request has a content entity explicitly set with
 * {@link #setEntity(AsyncEntityProducer)} or it is not an entity enclosing method
 * (such as POST or PUT), parameters will be added to the query component of the request URI.
 * Otherwise, parameters will be added as a URL encoded entity.
 *
 * @since 5.0
 */
public class AsyncRequestBuilder {

    private enum METHOD { GET, HEAD, POST, PUT, DELETE, PATCH, TRACE, OPTIONS }

    private HttpHost host;
    private String path;
    private URI uri;
    private String method;
    private Charset charset;
    private ProtocolVersion version;
    private HeaderGroup headergroup;
    private AsyncEntityProducer entityProducer;
    private List<NameValuePair> parameters;
    private RequestConfig config;

    AsyncRequestBuilder() {
    }

    AsyncRequestBuilder(final String method) {
        super();
        this.method = method;
    }

    AsyncRequestBuilder(final METHOD method) {
        this(method.name());
    }

    AsyncRequestBuilder(final String method, final URI uri) {
        super();
        this.method = method;
        this.uri = uri;
    }

    AsyncRequestBuilder(final METHOD method, final HttpHost host, final String path) {
        super();
        this.method = method.name();
        this.host = host;
        this.path = path;
    }

    AsyncRequestBuilder(final METHOD method, final URI uri) {
        this(method.name(), uri);
    }

    AsyncRequestBuilder(final METHOD method, final String uri) {
        this(method.name(), uri != null ? URI.create(uri) : null);
    }

    AsyncRequestBuilder(final String method, final String uri) {
        this(method, uri != null ? URI.create(uri) : null);
    }

    public static AsyncRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new AsyncRequestBuilder(method);
    }

    public static AsyncRequestBuilder get() {
        return new AsyncRequestBuilder(METHOD.GET);
    }

    public static AsyncRequestBuilder get(final URI uri) {
        return new AsyncRequestBuilder(METHOD.GET, uri);
    }

    public static AsyncRequestBuilder get(final String uri) {
        return new AsyncRequestBuilder(METHOD.GET, uri);
    }

    public static AsyncRequestBuilder get(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.GET, host, path);
    }

    public static AsyncRequestBuilder head() {
        return new AsyncRequestBuilder(METHOD.HEAD);
    }

    public static AsyncRequestBuilder head(final URI uri) {
        return new AsyncRequestBuilder(METHOD.HEAD, uri);
    }

    public static AsyncRequestBuilder head(final String uri) {
        return new AsyncRequestBuilder(METHOD.HEAD, uri);
    }

    public static AsyncRequestBuilder head(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.HEAD, host, path);
    }

    public static AsyncRequestBuilder patch() {
        return new AsyncRequestBuilder(METHOD.PATCH);
    }

    public static AsyncRequestBuilder patch(final URI uri) {
        return new AsyncRequestBuilder(METHOD.PATCH, uri);
    }

    public static AsyncRequestBuilder patch(final String uri) {
        return new AsyncRequestBuilder(METHOD.PATCH, uri);
    }

    public static AsyncRequestBuilder patch(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.PATCH, host, path);
    }

    public static AsyncRequestBuilder post() {
        return new AsyncRequestBuilder(METHOD.POST);
    }

    public static AsyncRequestBuilder post(final URI uri) {
        return new AsyncRequestBuilder(METHOD.POST, uri);
    }

    public static AsyncRequestBuilder post(final String uri) {
        return new AsyncRequestBuilder(METHOD.POST, uri);
    }

    public static AsyncRequestBuilder post(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.POST, host, path);
    }

    public static AsyncRequestBuilder put() {
        return new AsyncRequestBuilder(METHOD.PUT);
    }

    public static AsyncRequestBuilder put(final URI uri) {
        return new AsyncRequestBuilder(METHOD.PUT, uri);
    }

    public static AsyncRequestBuilder put(final String uri) {
        return new AsyncRequestBuilder(METHOD.PUT, uri);
    }

    public static AsyncRequestBuilder put(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.PUT, host, path);
    }

    public static AsyncRequestBuilder delete() {
        return new AsyncRequestBuilder(METHOD.DELETE);
    }

    public static AsyncRequestBuilder delete(final URI uri) {
        return new AsyncRequestBuilder(METHOD.DELETE, uri);
    }

    public static AsyncRequestBuilder delete(final String uri) {
        return new AsyncRequestBuilder(METHOD.DELETE, uri);
    }

    public static AsyncRequestBuilder delete(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.DELETE, host, path);
    }

    public static AsyncRequestBuilder trace() {
        return new AsyncRequestBuilder(METHOD.TRACE);
    }

    public static AsyncRequestBuilder trace(final URI uri) {
        return new AsyncRequestBuilder(METHOD.TRACE, uri);
    }

    public static AsyncRequestBuilder trace(final String uri) {
        return new AsyncRequestBuilder(METHOD.TRACE, uri);
    }

    public static AsyncRequestBuilder trace(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.TRACE, host, path);
    }

    public static AsyncRequestBuilder options() {
        return new AsyncRequestBuilder(METHOD.OPTIONS);
    }

    public static AsyncRequestBuilder options(final URI uri) {
        return new AsyncRequestBuilder(METHOD.OPTIONS, uri);
    }

    public static AsyncRequestBuilder options(final String uri) {
        return new AsyncRequestBuilder(METHOD.OPTIONS, uri);
    }

    public static AsyncRequestBuilder options(final HttpHost host, final String path) {
        return new AsyncRequestBuilder(METHOD.OPTIONS, host, path);
    }

    public AsyncRequestBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public AsyncRequestBuilder setUri(final URI uri) {
        this.uri = uri;
        this.host = null;
        this.path = null;
        return this;
    }

    public AsyncRequestBuilder setUri(final String uri) {
        this.uri = uri != null ? URI.create(uri) : null;
        this.host = null;
        this.path = null;
        return this;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public AsyncRequestBuilder setVersion(final ProtocolVersion version) {
        this.version = version;
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

    public AsyncRequestBuilder addHeader(final Header header) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        headergroup.addHeader(header);
        return this;
    }

    public AsyncRequestBuilder addHeader(final String name, final String value) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        this.headergroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public AsyncRequestBuilder removeHeader(final Header header) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        headergroup.removeHeader(header);
        return this;
    }

    public AsyncRequestBuilder removeHeaders(final String name) {
        if (name == null || headergroup == null) {
            return this;
        }
        for (final Iterator<Header> i = headergroup.headerIterator(); i.hasNext(); ) {
            final Header header = i.next();
            if (name.equalsIgnoreCase(header.getName())) {
                i.remove();
            }
        }
        return this;
    }

    public AsyncRequestBuilder setHeader(final Header header) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        this.headergroup.setHeader(header);
        return this;
    }

    public AsyncRequestBuilder setHeader(final String name, final String value) {
        if (headergroup == null) {
            headergroup = new HeaderGroup();
        }
        this.headergroup.setHeader(new BasicHeader(name, value));
        return this;
    }

    public List<NameValuePair> getParameters() {
        return parameters != null ? new ArrayList<>(parameters) :
            new ArrayList<NameValuePair>();
    }

    public AsyncRequestBuilder addParameter(final NameValuePair nvp) {
        Args.notNull(nvp, "Name value pair");
        if (parameters == null) {
            parameters = new LinkedList<>();
        }
        parameters.add(nvp);
        return this;
    }

    public AsyncRequestBuilder addParameter(final String name, final String value) {
        return addParameter(new BasicNameValuePair(name, value));
    }

    public AsyncRequestBuilder addParameters(final NameValuePair... nvps) {
        for (final NameValuePair nvp: nvps) {
            addParameter(nvp);
        }
        return this;
    }

    public RequestConfig getConfig() {
        return config;
    }

    public AsyncRequestBuilder setConfig(final RequestConfig config) {
        this.config = config;
        return this;
    }

    public AsyncEntityProducer getEntity() {
        return entityProducer;
    }

    public AsyncRequestBuilder setEntity(final AsyncEntityProducer entityProducer) {
        this.entityProducer = entityProducer;
        return this;
    }

    public AsyncRequestBuilder setEntity(final String content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncRequestBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entityProducer = new BasicAsyncEntityProducer(content, contentType);
        return this;
    }

    public AsyncRequestProducer build() {
        AsyncEntityProducer entityProducerCopy = this.entityProducer;
        if (parameters != null && !parameters.isEmpty()) {
            if (entityProducerCopy == null && (METHOD.POST.name().equalsIgnoreCase(method)
                    || METHOD.PUT.name().equalsIgnoreCase(method))) {
                final String content = URLEncodedUtils.format(
                        parameters,
                        charset != null ? charset : ContentType.APPLICATION_FORM_URLENCODED.getCharset());
                entityProducerCopy = new StringAsyncEntityProducer(
                        content,
                        ContentType.APPLICATION_FORM_URLENCODED);
            } else {
                try {
                    uri = new URIBuilder(uri)
                      .setCharset(this.charset)
                      .addParameters(parameters)
                      .build();
                } catch (final URISyntaxException ex) {
                    // should never happen
                }
            }
        }
        final BasicHttpRequest request = host != null ?
                new BasicHttpRequest(method, host, !TextUtils.isBlank(path) ? path : "/") :
                new BasicHttpRequest(method, uri != null ? uri : URI.create("/"));
        if (this.headergroup != null) {
            request.setHeaders(this.headergroup.getAllHeaders());
        }
        if (version != null) {
            request.setVersion(version);
        }
        return new DefaultAsyncRequestProducer(request, entityProducerCopy, config);
    }

}
