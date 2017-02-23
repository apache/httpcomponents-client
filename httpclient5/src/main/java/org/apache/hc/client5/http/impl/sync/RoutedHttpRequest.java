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

package org.apache.hc.client5.http.impl.sync;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

/**
 * A wrapper class for {@link ClassicHttpRequest} that can be used to change properties of the current
 * request without modifying the original object.
 *
 * @since 4.3
 */
public class RoutedHttpRequest extends HeaderGroup implements ClassicHttpRequest {

    private static final long serialVersionUID = 1L;

    private final ClassicHttpRequest original;
    private final HttpRoute route;
    private final String method;

    private ProtocolVersion version;
    private String scheme;
    private URIAuthority authority;
    private String path;
    private HttpEntity entity;
    private URI uri;

    private RoutedHttpRequest(final ClassicHttpRequest request, final HttpRoute route) {
        super();
        this.original = Args.notNull(request, "HTTP request");
        this.route = Args.notNull(route, "HTTP route");
        this.method = request.getMethod();
        this.scheme = request.getScheme();
        this.authority = request.getAuthority();
        this.path = request.getPath();
        this.version = request.getVersion() != null ? request.getVersion() : HttpVersion.DEFAULT;
        setHeaders(request.getAllHeaders());
        setEntity(request.getEntity());
    }

    public ClassicHttpRequest getOriginal() {
        return this.original;
    }

    public HttpRoute getRoute() {
        return this.route;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public void setVersion(final ProtocolVersion version) {
        this.version = version;
    }

    @Override
    public ProtocolVersion getVersion() {
        return version;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public void setScheme(final String scheme) {
        this.scheme = scheme;
        this.uri = null;
    }

    @Override
    public URIAuthority getAuthority() {
        return authority;
    }

    @Override
    public void setAuthority(final URIAuthority authority) {
        this.authority = authority;
        this.uri = null;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void setPath(final String path) {
        this.path = path;
        this.uri = null;
    }

    @Override
    public void addHeader(final String name, final Object value) {
        addHeader(new BasicHeader(name, value));
    }

    @Override
    public void setHeader(final String name, final Object value) {
        setHeader(new BasicHeader(name, value));
    }

    @Override
    public HttpEntity getEntity() {
        return entity;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

    private String getRequestUriString() {
        final StringBuilder buf = new StringBuilder();
        if (this.authority != null) {
            buf.append(this.scheme != null ? this.scheme : "http").append("://");
            buf.append(this.authority.getHostName());
            if (this.authority.getPort() >= 0) {
                buf.append(":").append(this.authority.getPort());
            }
        }
        buf.append(this.path != null ? this.path : "/");
        return buf.toString();
    }

    @Override
    public String getRequestUri() {
        if (route.getProxyHost() != null && !route.isTunnelled()) {
            return getRequestUriString();
        } else {
            return this.path;
        }
    }

    @Override
    public URI getUri() throws URISyntaxException {
        if (this.uri == null) {
            this.uri = new URI(getRequestUriString());
        }
        return this.uri;
    }

    public HttpHost getTargetHost() {
        return this.authority != null ? new HttpHost(this.authority, this.scheme) : this.route.getTargetHost();
    }

    public static RoutedHttpRequest adapt(final ClassicHttpRequest request, final HttpRoute route) {
        return new RoutedHttpRequest(request, route);
    }

}
