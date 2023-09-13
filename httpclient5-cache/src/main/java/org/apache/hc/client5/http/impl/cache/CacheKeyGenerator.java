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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.util.Args;

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
            final URI normalizeRequestUri = CacheSupport.normalize(requestUri);
            return normalizeRequestUri.toASCIIString();
        } catch (final URISyntaxException ex) {
            return requestUri.toASCIIString();
        }
    }

    /**
     * Computes a root key for the given {@link HttpHost} and {@link HttpRequest}
     * that can be used as a unique identifier for cached resources.
     *
     * @param host The host for this request
     * @param request the {@link HttpRequest}
     * @return cache key
     */
    public String generateKey(final HttpHost host, final HttpRequest request) {
        final String s = CacheSupport.getRequestUri(request, host);
        try {
            return generateKey(new URI(s));
        } catch (final URISyntaxException ex) {
            return s;
        }
    }

    /**
     * Returns all variant names contained in {@literal VARY} headers of the given message.
     *
     * @since 5.3
     */
    public static List<String> variantNames(final MessageHeaders message) {
        if (message == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final Iterator<Header> it = message.headerIterator(HttpHeaders.VARY); it.hasNext(); ) {
            final Header header = it.next();
            CacheSupport.parseTokens(header, names::add);
        }
        return names;
    }

    /**
     * Computes a "variant key" for the given request and the given variants.
     * @param request originating request
     * @param variantNames variant names
     * @return variant key
     *
     * @since 5.3
     */
    public String generateVariantKey(final HttpRequest request, final Collection<String> variantNames) {
        Args.notNull(variantNames, "Variant names");
        final StringBuilder buf = new StringBuilder("{");
        final AtomicBoolean firstHeader = new AtomicBoolean();
        variantNames.stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .sorted()
                .distinct()
                .forEach(h -> {
                    if (!firstHeader.compareAndSet(false, true)) {
                        buf.append("&");
                    }
                    buf.append(PercentCodec.encode(h, StandardCharsets.UTF_8)).append("=");
                    final List<String> tokens = new ArrayList<>();
                    final Iterator<Header> headerIterator = request.headerIterator(h);
                    while (headerIterator.hasNext()) {
                        final Header header = headerIterator.next();
                        CacheSupport.parseTokens(header, tokens::add);
                    }
                    final AtomicBoolean firstToken = new AtomicBoolean();
                    tokens.stream()
                            .filter(t -> !t.isEmpty())
                            .map(t -> t.toLowerCase(Locale.ROOT))
                            .sorted()
                            .distinct()
                            .forEach(t -> {
                                if (!firstToken.compareAndSet(false, true)) {
                                    buf.append(",");
                                }
                                buf.append(PercentCodec.encode(t, StandardCharsets.UTF_8));
                            });
                });
        buf.append("}");
        return buf.toString();
    }

    /**
     * Computes a "variant key" from the headers of the given request if the given
     * cache entry can have variants ({@code Vary} header is present).
     *
     * @param request originating request
     * @param entry cache entry
     * @return variant key if the given cache entry can have variants, {@code null} otherwise.
     */
    public String generateVariantKey(final HttpRequest request, final HttpCacheEntry entry) {
        if (entry.containsHeader(HttpHeaders.VARY)) {
            final List<String> variantNames = variantNames(entry);
            return generateVariantKey(request, variantNames);
        } else {
            return null;
        }
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
     *
     * @deprecated Use {@link #generateKey(HttpHost, HttpRequest)} or {@link #generateVariantKey(HttpRequest, Collection)}
     */
    @Deprecated
    public String generateKey(final HttpHost host, final HttpRequest request, final HttpCacheEntry entry) {
        final String rootKey = generateKey(host, request);
        final List<String> variantNames = variantNames(entry);
        if (variantNames.isEmpty()) {
            return rootKey;
        } else {
            return generateVariantKey(request, variantNames) + rootKey;
        }
    }

}
