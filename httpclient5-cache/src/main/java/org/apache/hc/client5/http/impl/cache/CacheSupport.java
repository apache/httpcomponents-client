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
import java.util.Objects;

import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;

/**
 * HTTP cache support utilities.
 *
 * @since 5.4
 */
@Internal
public final class CacheSupport {

    /**
     * Returns raw representation of the request URI of the given {@link HttpRequest}.
     * This method will use {@link HttpRequest#getPath()}, {@link HttpRequest#getScheme()} and
     * {@link HttpRequest#getAuthority()} values when available or attributes of target
     * {@link HttpHost } in order to construct an absolute URI.
     * <p>
     * This method will not attempt to ensure validity of the resultant text representation.
     *
     * @param target target host
     * @param request the {@link HttpRequest}
     *
     * @return String the request URI
     *
     * @since 5.7
     */
    static String requestUriRaw(final HttpHost target, final HttpRequest request) {
        Args.notNull(target, "Target");
        Args.notNull(request, "HTTP request");
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

    private static URIBuilder parse(final String uriRaw) throws URISyntaxException {
        final URIBuilder uriBuilder = new URIBuilder(uriRaw);
        return normalize(uriBuilder);
    }

    /**
     * Returns the request URI of the given {@link HttpRequest} normalized and validated.
     *
     * @since 5.7
     */
    public static String requestUriNormalized(final HttpHost target, final HttpRequest request) {
        Args.notNull(target, "Target");
        Args.notNull(request, "HTTP request");
        final String uriRaw = requestUriRaw(target, request);
        try {
            return parse(uriRaw).toString();
        } catch (final URISyntaxException ignore) {
            return uriRaw;
        }
    }

    /**
     * Returns the request URI of the given {@link HttpRequest} normalized and validated
     * or {@code null} if the request URI is malformed / invalid.
     *
     * @since 5.7
     */
    public static URI requestUriNormalizedOrNull(final HttpHost target, final HttpRequest request) {
        Args.notNull(target, "Target");
        Args.notNull(request, "HTTP request");
        final String uriRaw = requestUriRaw(target, request);
        try {
            return parse(uriRaw).build();
        } catch (final URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Returns normalized representation of the request URI optimized for use as a cache key.
     * This method ensures the resultant URI has an explicit port in the authority component,
     * and explicit path component and no fragment.
     *
     * @since 5.7
     */
    public static URI normalize(final URI requestUri) throws URISyntaxException {
        Args.notNull(requestUri, "URI");
        return normalize(new URIBuilder(requestUri)).build();
    }

    static URIBuilder normalize(final URIBuilder builder) {
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
        builder.optimize();
        return builder;
    }

    public static URI getLocationURI(final URI requestUri, final MessageHeaders response, final String headerName) {
        final Header h = response.getFirstHeader(headerName);
        if (h == null) {
            return null;
        }
        final String locationRaw = h.getValue();
        if (TextUtils.isEmpty(locationRaw)) {
            return null;
        }
        final URI locationUri;
        try {
            locationUri = normalize(new URI(locationRaw));
        } catch (final URISyntaxException ignore) {
            return requestUri;
        }
        if (locationUri == null) {
            return requestUri;
        }
        if (locationUri.isAbsolute()) {
            return locationUri;
        }
        return URIUtils.resolve(requestUri, locationUri);
    }

    public static boolean isSameOrigin(final URI requestURI, final URI targetURI) {
        return targetURI.isAbsolute() && Objects.equals(requestURI.getAuthority(), targetURI.getAuthority());
    }

    public static final TimeValue MAX_AGE = TimeValue.ofSeconds(Integer.MAX_VALUE + 1L);

    public static long deltaSeconds(final String s) {
        if (TextUtils.isEmpty(s)) {
            return -1;
        }
        try {
            long ageValue = Long.parseLong(s);
            if (ageValue < 0) {
                ageValue = -1;  // Handle negative age values as invalid
            } else if (ageValue > Integer.MAX_VALUE) {
                ageValue = MAX_AGE.toSeconds();
            }
            return ageValue;
        } catch (final NumberFormatException ignore) {
        }
        return 0;
    }

}
