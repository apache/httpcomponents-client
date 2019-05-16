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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.MessageSupport;

/**
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class CacheKeyGenerator implements Resolver<URI, String> {

    public static final CacheKeyGenerator INSTANCE = new CacheKeyGenerator();

    @Override
    public String resolve(final URI uri) {
        return generateKey(uri);
    }

    /**
     * Computes a key for the given request {@link URI} that can be used as
     * a unique identifier for cached resources. The URI is expected to
     * in an absolute form.
     *
     * @param requestUri request URI
     * @return cache key
     */
    public String generateKey(final URI requestUri) {
        try {
            final URI normalizeRequestUri = HttpCacheSupport.normalize(requestUri);
            return normalizeRequestUri.toASCIIString();
        } catch (final URISyntaxException ex) {
            return requestUri.toASCIIString();
        }
    }

    /**
     * Computes a key for the given {@link HttpHost} and {@link HttpRequest}
     * that can be used as a unique identifier for cached resources.
     *
     * @param host The host for this request
     * @param request the {@link HttpRequest}
     * @return cache key
     */
    public String generateKey(final HttpHost host, final HttpRequest request) {
        final String s = HttpCacheSupport.getRequestUri(request, host);
        try {
            return generateKey(new URI(s));
        } catch (final URISyntaxException ex) {
            return s;
        }
    }

    private String getFullHeaderValue(final Header[] headers) {
        if (headers == null) {
            return "";
        }
        final StringBuilder buf = new StringBuilder("");
        for (int i = 0; i < headers.length; i++) {
            final Header hdr = headers[i];
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(hdr.getValue().trim());
        }
        return buf.toString();
    }

    /**
     * Computes a key for the given {@link HttpHost} and {@link HttpRequest}
     * that can be used as a unique identifier for cached resources. if the request has a
     * {@literal VARY} header the identifier will also include variant key.
     *
     * @param host The host for this request
     * @param request the {@link HttpRequest}
     * @param entry the parent entry used to track the variants
     * @return cache key
     */
    public String generateKey(final HttpHost host, final HttpRequest request, final HttpCacheEntry entry) {
        if (!entry.hasVariants()) {
            return generateKey(host, request);
        }
        return generateVariantKey(request, entry) + generateKey(host, request);
    }

    /**
     * Computes a "variant key" from the headers of a given request that are
     * covered by the Vary header of a given cache entry. Any request whose
     * varying headers match those of this request should have the same
     * variant key.
     * @param req originating request
     * @param entry cache entry in question that has variants
     * @return variant key
     */
    public String generateVariantKey(final HttpRequest req, final HttpCacheEntry entry) {
        final List<String> variantHeaderNames = new ArrayList<>();
        final Iterator<HeaderElement> it = MessageSupport.iterate(entry, HeaderConstants.VARY);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            variantHeaderNames.add(elt.getName());
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
                buf.append(URLEncoder.encode(headerName, StandardCharsets.UTF_8.name()));
                buf.append("=");
                buf.append(URLEncoder.encode(getFullHeaderValue(req.getHeaders(headerName)),
                        StandardCharsets.UTF_8.name()));
                first = false;
            }
            buf.append("}");
        } catch (final UnsupportedEncodingException uee) {
            throw new RuntimeException("couldn't encode to UTF-8", uee);
        }
        return buf.toString();
    }

}
