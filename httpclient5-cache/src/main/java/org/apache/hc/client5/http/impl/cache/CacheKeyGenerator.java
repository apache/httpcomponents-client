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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHeaderElementIterator;
import org.apache.hc.core5.http.message.BasicHeaderValueFormatter;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;

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
     * Returns text representation of the request URI of the given {@link HttpRequest}.
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
     */
    @Internal
    public static String getRequestUri(final HttpHost target, final HttpRequest request) {
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

    /**
     * Returns normalized representation of the request URI optimized for use as a cache key.
     * This method ensures the resultant URI has an explicit port in the authority component,
     * and explicit path component and no fragment.
     */
    @Internal
    public static URI normalize(final URI requestUri) throws URISyntaxException {
        Args.notNull(requestUri, "URI");
        final URIBuilder builder = new URIBuilder(requestUri);
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
        return builder.optimize().build();
    }

    /**
     * Lenient URI parser that normalizes valid {@link URI}s and returns {@code null} for malformed URIs.
     */
    @Internal
    public static URI normalize(final String requestUri) {
        if (requestUri == null) {
            return null;
        }
        try {
            return CacheKeyGenerator.normalize(new URI(requestUri));
        } catch (final URISyntaxException ex) {
            return null;
        }
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
            final URI normalizeRequestUri = normalize(requestUri);
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
        final String s = getRequestUri(host, request);
        try {
            return generateKey(new URI(s));
        } catch (final URISyntaxException ex) {
            return s;
        }
    }

    /**
     * Returns all variant names contained in {@literal VARY} headers of the given message.
     *
     * @since 5.4
     */
    public static List<String> variantNames(final MessageHeaders message) {
        if (message == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final Iterator<Header> it = message.headerIterator(HttpHeaders.VARY); it.hasNext(); ) {
            final Header header = it.next();
            MessageSupport.parseTokens(header, names::add);
        }
        return names;
    }

    @Internal
    public static void normalizeElements(final MessageHeaders message, final String headerName, final Consumer<String> consumer) {
        // User-Agent as a special case due to its grammar
        if (headerName.equalsIgnoreCase(HttpHeaders.USER_AGENT)) {
            final Header header = message.getFirstHeader(headerName);
            if (header != null) {
                consumer.accept(header.getValue().toLowerCase(Locale.ROOT));
            }
        } else {
            normalizeElements(message.headerIterator(headerName), consumer);
        }
    }

    @Internal
    public static void normalizeElements(final Iterator<Header> iterator, final Consumer<String> consumer) {
        final Iterator<HeaderElement> it = new BasicHeaderElementIterator(iterator);
        StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.NONNULL), false)
                .filter(e -> !TextUtils.isBlank(e.getName()))
                .map(e -> {
                    if (e.getValue() == null && e.getParameterCount() == 0) {
                        return e.getName().toLowerCase(Locale.ROOT);
                    } else {
                        final CharArrayBuffer buf = new CharArrayBuffer(1024);
                        BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(
                                buf,
                                new BasicNameValuePair(
                                    e.getName().toLowerCase(Locale.ROOT),
                                    !TextUtils.isBlank(e.getValue()) ? e.getValue() : null),
                                false);
                        if (e.getParameterCount() > 0) {
                            for (final NameValuePair nvp : e.getParameters()) {
                                if (!TextUtils.isBlank(nvp.getName())) {
                                    buf.append(';');
                                    BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(
                                            buf,
                                            new BasicNameValuePair(
                                                    nvp.getName().toLowerCase(Locale.ROOT),
                                                    !TextUtils.isBlank(nvp.getValue()) ? nvp.getValue() : null),
                                            false);
                                }
                            }
                        }
                        return buf.toString();
                    }
                })
                .sorted()
                .distinct()
                .forEach(consumer);
    }

    /**
     * Computes a "variant key" for the given request and the given variants.
     * @param request originating request
     * @param variantNames variant names
     * @return variant key
     *
     * @since 5.4
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
                    final AtomicBoolean firstToken = new AtomicBoolean();
                    normalizeElements(request, h, t -> {
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
