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
package org.apache.hc.client5.http.cache;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.client5.http.impl.cache.CacheKeyGenerator;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;

/**
 * {@link HttpCacheEntry} factory.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class HttpCacheEntryFactory {

    public static final HttpCacheEntryFactory INSTANCE = new HttpCacheEntryFactory();

    private static HeaderGroup headers(final Iterator<Header> it) {
        final HeaderGroup headerGroup = new HeaderGroup();
        while (it.hasNext()) {
            headerGroup.addHeader(it.next());
        }
        return headerGroup;
    }

    HeaderGroup mergeHeaders(final HttpCacheEntry entry, final HttpResponse response) {
        final HeaderGroup headerGroup = new HeaderGroup();
        for (final Iterator<Header> it = entry.headerIterator(); it.hasNext(); ) {
            final Header entryHeader = it.next();
            final String headerName = entryHeader.getName();
            if (!response.containsHeader(headerName)) {
                headerGroup.addHeader(entryHeader);
            }
        }
        final Set<String> responseHopByHop = MessageSupport.hopByHopConnectionSpecific(response);
        for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
            final Header responseHeader = it.next();
            final String headerName = responseHeader.getName();
            if (!responseHopByHop.contains(headerName.toLowerCase(Locale.ROOT))) {
                headerGroup.addHeader(responseHeader);
            }
        }
        return headerGroup;
    }

    /**
     * This method should be provided by the core
     */
    static HeaderGroup filterHopByHopHeaders(final HttpMessage message) {
        final Set<String> hopByHop = MessageSupport.hopByHopConnectionSpecific(message);
        final HeaderGroup headerGroup = new HeaderGroup();
        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            if (!hopByHop.contains(header.getName())) {
                headerGroup.addHeader(header);
            }
        }
        return headerGroup;
    }

    static void ensureDate(final HeaderGroup headers, final Instant instant) {
        if (!headers.containsHeader(HttpHeaders.DATE)) {
            headers.addHeader(new BasicHeader(HttpHeaders.DATE, DateUtils.formatStandardDate(instant)));
        }
    }

    /**
     * Creates a new root {@link HttpCacheEntry} (parent of multiple variants).
     *
     * @param latestVariant    The most recently created variant entry
     * @param variants         describing cache entries that are variants of this parent entry; this
     *                         maps a "variant key" (derived from the varying request headers) to a
     *                         "cache key" (where in the cache storage the particular variant is
     *                         located)
     */
    public HttpCacheEntry createRoot(final HttpCacheEntry latestVariant,
                                     final Collection<String> variants) {
        Args.notNull(latestVariant, "Request");
        Args.notNull(variants, "Variants");
        return new HttpCacheEntry(
                latestVariant.getRequestInstant(),
                latestVariant.getResponseInstant(),
                latestVariant.getRequestMethod(),
                latestVariant.getRequestURI(),
                headers(latestVariant.requestHeaderIterator()),
                latestVariant.getStatus(),
                headers(latestVariant.headerIterator()),
                null,
                variants);
    }

    /**
     * Create a new {@link HttpCacheEntry} with the given {@link Resource}.
     *
     * @param requestInstant   Date/time when the request was made (Used for age calculations)
     * @param responseInstant  Date/time that the response came back (Used for age calculations)
     * @param host             Target host
     * @param request          Original client request (a deep copy of this object is made)
     * @param response         Origin response (a deep copy of this object is made)
     * @param resource         Resource representing origin response body
     */
    public HttpCacheEntry create(final Instant requestInstant,
                                 final Instant responseInstant,
                                 final HttpHost host,
                                 final HttpRequest request,
                                 final HttpResponse response,
                                 final Resource resource) {
        Args.notNull(requestInstant, "Request instant");
        Args.notNull(responseInstant, "Response instant");
        Args.notNull(host, "Host");
        Args.notNull(request, "Request");
        Args.notNull(response, "Origin response");
        final String s = CacheKeyGenerator.getRequestUri(host, request);
        final URI uri = CacheKeyGenerator.normalize(s);
        final HeaderGroup requestHeaders = filterHopByHopHeaders(request);
        // Strip AUTHORIZATION from request headers
        requestHeaders.removeHeaders(HttpHeaders.AUTHORIZATION);
        final HeaderGroup responseHeaders = filterHopByHopHeaders(response);
        ensureDate(responseHeaders, responseInstant);
        return new HttpCacheEntry(
                requestInstant,
                responseInstant,
                request.getMethod(),
                uri.toASCIIString(),
                requestHeaders,
                response.getCode(),
                responseHeaders,
                resource,
                null);
    }

    /**
     * Creates updated entry with the new information from the response. Should only be used for
     * 304 responses.
     *
     * @param requestInstant   Date/time when the request was made (Used for age calculations)
     * @param responseInstant  Date/time that the response came back (Used for age calculations)
     * @param host             Target host
     * @param request          Original client request (a deep copy of this object is made)
     * @param response         Origin response (a deep copy of this object is made)
     * @param entry            Existing cache entry.
     */
    public HttpCacheEntry createUpdated(
            final Instant requestInstant,
            final Instant responseInstant,
            final HttpHost host,
            final HttpRequest request,
            final HttpResponse response,
            final HttpCacheEntry entry) {
        Args.notNull(requestInstant, "Request instant");
        Args.notNull(responseInstant, "Response instant");
        Args.notNull(response, "Origin response");
        Args.check(response.getCode() == HttpStatus.SC_NOT_MODIFIED,
                "Response must have 304 status code");
        Args.notNull(entry, "Cache entry");
        if (HttpCacheEntry.isNewer(entry, response)) {
            return entry;
        }
        final String s = CacheKeyGenerator.getRequestUri(host, request);
        final URI uri = CacheKeyGenerator.normalize(s);
        final HeaderGroup requestHeaders = filterHopByHopHeaders(request);
        // Strip AUTHORIZATION from request headers
        requestHeaders.removeHeaders(HttpHeaders.AUTHORIZATION);
        final HeaderGroup mergedHeaders = mergeHeaders(entry, response);
        return new HttpCacheEntry(
                requestInstant,
                responseInstant,
                request.getMethod(),
                uri.toASCIIString(),
                requestHeaders,
                entry.getStatus(),
                mergedHeaders,
                entry.getResource(),
                null);
    }

    /**
     * Creates a copy of the given {@link HttpCacheEntry}. Please note the underlying
     * {@link Resource} is copied by reference.
     */
    public HttpCacheEntry copy(final HttpCacheEntry entry) {
        if (entry == null) {
            return null;
        }
        return new HttpCacheEntry(
                entry.getRequestInstant(),
                entry.getResponseInstant(),
                entry.getRequestMethod(),
                entry.getRequestURI(),
                headers(entry.requestHeaderIterator()),
                entry.getStatus(),
                headers(entry.headerIterator()),
                entry.getResource(),
                entry.hasVariants() ? new HashSet<>(entry.getVariants()) : null);
    }

}
