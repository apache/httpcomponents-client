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
package org.apache.hc.client5.http.impl.cache;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;

/**
 * HTTP cache support utilities.
 *
 * @since 5.0
 */
public final class HttpCacheSupport {

    private static final URI BASE_URI = URI.create("http://example.com/");

    /**
     * Returns text representation of the request URI of the given {@link HttpRequest}.
     * This method will use {@link HttpRequest#getPath()}, {@link HttpRequest#getScheme()} and
     * {@link HttpRequest#getAuthority()} values when available or attributes of target
     * {@link HttpHost } in order to construct an absolute URI.
     * <p>
     * This method will not attempt to ensure validity of the resultant text representation.
     *
     * @param request the {@link HttpRequest}
     * @param target target host
     *
     * @return String the request URI
     */
    public static String getRequestUri(final HttpRequest request, final HttpHost target) {
        Args.notNull(request, "HTTP request");
        Args.notNull(target, "Target");
        final StringBuilder buf = new StringBuilder();
        final URIAuthority authority = request.getAuthority();
        if (authority != null) {
            final String scheme = request.getScheme();
            buf.append(scheme != null ? scheme : URIScheme.HTTP.id).append("://");
            buf.append(authority.getHostName());
            if (authority.getPort() >= 0) {
                buf.append(":").append(authority.getPort());
            }
        } else {
            buf.append(target.getSchemeName()).append("://");
            buf.append(target.getHostName());
            if (target.getPort() >= 0) {
                buf.append(":").append(target.getPort());
            }
        }
        final String path = request.getPath();
        if (path == null) {
            buf.append("/");
        } else {
            if (buf.length() > 0 && !path.startsWith("/")) {
                buf.append("/");
            }
            buf.append(path);
        }
        return buf.toString();
    }

    /**
     * Returns normalized representation of the request URI optimized for use as a cache key.
     * This method ensures the resultant URI has an explicit port in the authority component,
     * and explicit path component and no fragment.
     *
     * @param requestUri original request URI
     * @return normalized URI.
     * @throws URISyntaxException
     */
    public static URI normalize(final URI requestUri) throws URISyntaxException {
        Args.notNull(requestUri, "URI");
        final URIBuilder builder = new URIBuilder(requestUri.isAbsolute() ? URIUtils.resolve(BASE_URI, requestUri) : requestUri) ;
        if (builder.getHost() != null) {
            if (builder.getScheme() == null) {
                builder.setScheme(URIScheme.HTTP.id);
            }
            if (builder.getPort() <= -1) {
                if (URIScheme.HTTP.same(builder.getScheme())) {
                    builder.setPort(80);
                } else if (URIScheme.HTTPS.same(builder.getScheme())) {
                    builder.setPort(443);
                }
            }
        }
        builder.setFragment(null);
        if (builder.getPath() == null) {
            builder.setPath("/");
        }
        return builder.build();
    }

    /**
     * Lenient URI parser that normalizes valid {@link URI}s and returns {@code null} for malformed URIs.
     */
    public static URI normalizeQuetly(final String requestUri) {
        if (requestUri == null) {
            return null;
        }
        try {
            return normalize(new URI(requestUri));
        } catch (final URISyntaxException ex) {
            return null;
        }
    }

}
