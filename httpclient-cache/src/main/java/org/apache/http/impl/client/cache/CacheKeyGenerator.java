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
package org.apache.http.impl.client.cache;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.Args;

/**
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class CacheKeyGenerator {

    private static final URI BASE_URI = URI.create("http://example.com/");

    static URIBuilder getRequestUriBuilder(final HttpRequest request) throws URISyntaxException {
        if (request instanceof HttpUriRequest) {
            final URI uri = ((HttpUriRequest) request).getURI();
            if (uri != null) {
                return new URIBuilder(uri);
            }
        }
        return new URIBuilder(request.getRequestLine().getUri());
    }

    static URI getRequestUri(final HttpRequest request, final HttpHost target) throws URISyntaxException {
        Args.notNull(request, "HTTP request");
        Args.notNull(target, "Target");
        final URIBuilder uriBuilder = getRequestUriBuilder(request);

        // Decode path segments to preserve original behavior for backward compatibility
        final String path = uriBuilder.getPath();
        if (path != null) {
            uriBuilder.setPathSegments(URLEncodedUtils.parsePathSegments(path));
        }

        if (!uriBuilder.isAbsolute()) {
            uriBuilder.setScheme(target.getSchemeName());
            uriBuilder.setHost(target.getHostName());
            uriBuilder.setPort(target.getPort());
        }
        return uriBuilder.build();
    }

    static URI normalize(final URI requestUri) throws URISyntaxException {
        Args.notNull(requestUri, "URI");
        final URIBuilder builder = new URIBuilder(requestUri.isAbsolute() ? URIUtils.resolve(BASE_URI, requestUri) : requestUri) ;
        if (builder.getHost() != null) {
            if (builder.getScheme() == null) {
                builder.setScheme("http");
            }
            if (builder.getPort() <= -1) {
                if ("http".equalsIgnoreCase(builder.getScheme())) {
                    builder.setPort(80);
                } else if ("https".equalsIgnoreCase(builder.getScheme())) {
                    builder.setPort(443);
                }
            }
        }
        builder.setFragment(null);
        return builder.build();
    }

    /**
     * For a given {@link HttpHost} and {@link HttpRequest} get a URI from the
     * pair that I can use as an identifier KEY into my HttpCache
     *
     * @param host The host for this request
     * @param req the {@link HttpRequest}
     * @return String the extracted URI
     */
    public String getURI(final HttpHost host, final HttpRequest req) {
        try {
            final URI uri = normalize(getRequestUri(req, host));
            return uri.toASCIIString();
        } catch (final URISyntaxException ex) {
            return req.getRequestLine().getUri();
        }
    }

    public String canonicalizeUri(final String uri) {
        try {
            final URI normalized = normalize(URIUtils.resolve(BASE_URI, uri));
            return normalized.toASCIIString();
        } catch (final URISyntaxException ex) {
            return uri;
        }
    }

    protected String getFullHeaderValue(final Header[] headers) {
        if (headers == null) {
            return "";
        }

        final StringBuilder buf = new StringBuilder("");
        boolean first = true;
        for (final Header hdr : headers) {
            if (!first) {
                buf.append(", ");
            }
            buf.append(hdr.getValue().trim());
            first = false;

        }
        return buf.toString();
    }

    /**
     * For a given {@link HttpHost} and {@link HttpRequest} if the request has a
     * VARY header - I need to get an additional URI from the pair of host and
     * request so that I can also store the variant into my HttpCache.
     *
     * @param host The host for this request
     * @param req the {@link HttpRequest}
     * @param entry the parent entry used to track the variants
     * @return String the extracted variant URI
     */
    public String getVariantURI(final HttpHost host, final HttpRequest req, final HttpCacheEntry entry) {
        if (!entry.hasVariants()) {
            return getURI(host, req);
        }
        return getVariantKey(req, entry) + getURI(host, req);
    }

    /**
     * Compute a "variant key" from the headers of a given request that are
     * covered by the Vary header of a given cache entry. Any request whose
     * varying headers match those of this request should have the same
     * variant key.
     * @param req originating request
     * @param entry cache entry in question that has variants
     * @return a {@code String} variant key
     */
    public String getVariantKey(final HttpRequest req, final HttpCacheEntry entry) {
        final List<String> variantHeaderNames = new ArrayList<String>();
        for (final Header varyHdr : entry.getHeaders(HeaderConstants.VARY)) {
            for (final HeaderElement elt : varyHdr.getElements()) {
                variantHeaderNames.add(elt.getName());
            }
        }
        Collections.sort(variantHeaderNames);

        final StringBuilder buf;
        try {
            buf = new StringBuilder("{");
            boolean first = true;
            for (final String headerName : variantHeaderNames) {
                if (!first) {
                    buf.append("&");
                }
                buf.append(URLEncoder.encode(headerName, Consts.UTF_8.name()));
                buf.append("=");
                buf.append(URLEncoder.encode(getFullHeaderValue(req.getHeaders(headerName)),
                        Consts.UTF_8.name()));
                first = false;
            }
            buf.append("}");
        } catch (final UnsupportedEncodingException uee) {
            throw new RuntimeException("couldn't encode to UTF-8", uee);
        }
        return buf.toString();
    }

}
