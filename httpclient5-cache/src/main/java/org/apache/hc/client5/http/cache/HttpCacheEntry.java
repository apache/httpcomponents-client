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

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.validator.ETag;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.util.Args;

/**
 * Structure used to store an {@link org.apache.hc.core5.http.HttpResponse} in a cache.
 * Some entries can optionally depend on system resources that may require
 * explicit deallocation. In such a case {@link #getResource()} should return
 * a non null instance of {@link Resource} that must be deallocated by calling
 * {@link Resource#dispose()} method when no longer used.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class HttpCacheEntry implements MessageHeaders, Serializable {

    private static final long serialVersionUID = -6300496422359477413L;

    private final Instant requestDate;
    private final Instant responseDate;
    private final String method;
    private final String requestURI;
    private final HeaderGroup requestHeaders;
    private final int status;
    private final HeaderGroup responseHeaders;
    private final Resource resource;
    private final Set<String> variants;
    private final AtomicReference<Instant> dateRef;
    private final AtomicReference<Instant> expiresRef;
    private final AtomicReference<Instant> lastModifiedRef;

    private final AtomicReference<ETag> eTagRef;

    /**
     * Internal constructor that makes no validation of the input parameters and makes
     * no copies of the original client request and the origin response.
     */
    @Internal
    public HttpCacheEntry(
            final Instant requestDate,
            final Instant responseDate,
            final String method,
            final String requestURI,
            final HeaderGroup requestHeaders,
            final int status,
            final HeaderGroup responseHeaders,
            final Resource resource,
            final Collection<String> variants) {
        super();
        this.requestDate = requestDate;
        this.responseDate = responseDate;
        this.method = method;
        this.requestURI = requestURI;
        this.requestHeaders = requestHeaders;
        this.status = status;
        this.responseHeaders = responseHeaders;
        this.resource = resource;
        this.variants = variants != null ? Collections.unmodifiableSet(new HashSet<>(variants)) : null;
        this.dateRef = new AtomicReference<>();
        this.expiresRef = new AtomicReference<>();
        this.lastModifiedRef = new AtomicReference<>();
        this.eTagRef = new AtomicReference<>();
    }

    /**
     * Create a new {@link HttpCacheEntry} with variants.
     * @param requestDate
     *          Date/time when the request was made (Used for age
     *            calculations)
     * @param responseDate
     *          Date/time that the response came back (Used for age
     *            calculations)
     * @param status
     *          HTTP status from origin response
     * @param responseHeaders
     *          Header[] from original HTTP Response
     * @param resource representing origin response body
     * @param variantMap describing cache entries that are variants
     *   of this parent entry; this maps a "variant key" (derived
     *   from the varying request headers) to a "cache key" (where
     *   in the cache storage the particular variant is located)
     * @deprecated  Use {{@link HttpCacheEntryFactory}
     */
    @Deprecated
    public HttpCacheEntry(
            final Date requestDate,
            final Date responseDate,
            final int status,
            final Header[] responseHeaders,
            final Resource resource,
            final Map<String, String> variantMap) {
        this(DateUtils.toInstant(requestDate), DateUtils.toInstant(responseDate), status, responseHeaders, resource, variantMap);
    }

    /**
     * Create a new {@link HttpCacheEntry} with variants.
     *
     * @param requestDate     Date/time when the request was made (Used for age calculations)
     * @param responseDate    Date/time that the response came back (Used for age calculations)
     * @param status          HTTP status from origin response
     * @param responseHeaders Header[] from original HTTP Response
     * @param resource        representing origin response body
     * @param variantMap      describing cache entries that are variants of this parent entry; this
     *                        maps a "variant key" (derived from the varying request headers) to a
     *                        "cache key" (where in the cache storage the particular variant is
     *                        located)
     * @deprecated  Use {{@link HttpCacheEntryFactory}
     */
    @Deprecated
    public HttpCacheEntry(
            final Instant requestDate,
            final Instant responseDate,
            final int status,
            final Header[] responseHeaders,
            final Resource resource,
            final Map<String, String> variantMap) {
        super();
        Args.notNull(requestDate, "Request date");
        Args.notNull(responseDate, "Response date");
        Args.check(status >= HttpStatus.SC_SUCCESS, "Status code");
        Args.notNull(responseHeaders, "Response headers");
        this.requestDate = requestDate;
        this.responseDate = responseDate;
        this.method = Method.GET.name();
        this.requestURI = "/";
        this.requestHeaders = new HeaderGroup();
        this.status = status;
        this.responseHeaders = new HeaderGroup();
        this.responseHeaders.setHeaders(responseHeaders);
        this.resource = resource;
        this.variants = variantMap != null ? Collections.unmodifiableSet(new HashSet<>(variantMap.keySet())) : null;
        this.dateRef = new AtomicReference<>();
        this.expiresRef = new AtomicReference<>();
        this.lastModifiedRef = new AtomicReference<>();
        this.eTagRef = new AtomicReference<>();
    }

    /**
     * Create a new {@link HttpCacheEntry}.
     *
     * @param requestDate     Date/time when the request was made (Used for age calculations)
     * @param responseDate    Date/time that the response came back (Used for age calculations)
     * @param status          HTTP status from origin response
     * @param responseHeaders Header[] from original HTTP Response
     * @param resource        representing origin response body
     * @deprecated  Use {{@link HttpCacheEntryFactory}
     */
    @Deprecated
    public HttpCacheEntry(final Date requestDate, final Date responseDate, final int status,
                          final Header[] responseHeaders, final Resource resource) {
        this(requestDate, responseDate, status, responseHeaders, resource, new HashMap<>());
    }

    /**
     * Create a new {@link HttpCacheEntry}.
     *
     * @param requestDate
     *          Date/time when the request was made (Used for age
     *            calculations)
     * @param responseDate
     *          Date/time that the response came back (Used for age
     *            calculations)
     * @param status
     *          HTTP status from origin response
     * @param responseHeaders
     *          Header[] from original HTTP Response
     * @param resource representing origin response body
     *
     * @deprecated  Use {{@link HttpCacheEntryFactory}
     */
    @Deprecated
    public HttpCacheEntry(final Instant requestDate, final Instant responseDate, final int status,
                          final Header[] responseHeaders, final Resource resource) {
        this(requestDate, responseDate, status, responseHeaders, resource, new HashMap<>());
    }

    /**
     * Returns the status from the origin {@link org.apache.hc.core5.http.HttpResponse}.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the time the associated origin request was initiated by the
     * caching module.
     * @return {@link Date}
     * @deprecated USe {@link #getRequestInstant()}
     */
    @Deprecated
    public Date getRequestDate() {
        return DateUtils.toDate(requestDate);
    }

    /**
     * Returns the time the associated origin request was initiated by the
     * caching module.
     * @return {@link Instant}
     * @since 5.2
     */
    public Instant getRequestInstant() {
        return requestDate;
    }

    /**
     * Returns the time the origin response was received by the caching module.
     * @return {@link Date}
     * @deprecated  Use {@link #getResponseInstant()}
     */
    @Deprecated
    public Date getResponseDate() {
        return DateUtils.toDate(responseDate);
    }

    /**
     * Returns the time the origin response was received by the caching module.
     *
     * @return {@link Instant}
     * @since 5.2
     */
    public Instant getResponseInstant() {
        return responseDate;
    }

    /**
     * Returns all the headers that were on the origin response.
     */
    @Override
    public Header[] getHeaders() {
        return responseHeaders.getHeaders();
    }

    /**
     * Returns the first header from the origin response with the given
     * name.
     */
    @Override
    public Header getFirstHeader(final String name) {
        return responseHeaders.getFirstHeader(name);
    }

    /**
     * @since 5.0
     */
    @Override
    public Header getLastHeader(final String name) {
        return responseHeaders.getLastHeader(name);
    }

    /**
     * Gets all the headers with the given name that were on the origin
     * response.
     */
    @Override
    public Header[] getHeaders(final String name) {
        return responseHeaders.getHeaders(name);
    }

    /**
     * @since 5.0
     */
    @Override
    public boolean containsHeader(final String name) {
        return responseHeaders.containsHeader(name);
    }

    /**
     * @since 5.0
     */
    @Override
    public int countHeaders(final String name) {
        return responseHeaders.countHeaders(name);
    }

    /**
     * @since 5.0
     */
    @Override
    public Header getHeader(final String name) throws ProtocolException {
        return responseHeaders.getHeader(name);
    }

    /**
     * @since 5.0
     */
    @Override
    public Iterator<Header> headerIterator() {
        return responseHeaders.headerIterator();
    }

    /**
     * @since 5.0
     */
    @Override
    public Iterator<Header> headerIterator(final String name) {
        return responseHeaders.headerIterator(name);
    }

    /**
     * @since 5.4
     */
    public MessageHeaders responseHeaders() {
        return responseHeaders;
    }

    /**
     * Gets the Date value of the "Date" header or null if the header is missing or cannot be
     * parsed.
     *
     * @since 4.3
     */
    public Date getDate() {
        return DateUtils.toDate(getInstant());
    }

    private static final Instant NON_VALUE = Instant.ofEpochSecond(Instant.MIN.getEpochSecond(), 0);

    private Instant getInstant(final AtomicReference<Instant> ref, final String headerName) {
        Instant instant = ref.get();
        if (instant == null) {
            instant = DateUtils.parseStandardDate(this, headerName);
            if (instant == null) {
                instant = NON_VALUE;
            }
            if (!ref.compareAndSet(null, instant)) {
                instant = ref.get();
            }
        }
        return instant != null && instant != NON_VALUE ? instant : null;
    }

    /**
     * @since 5.2
     */
    public Instant getInstant() {
        return getInstant(dateRef, HttpHeaders.DATE);
    }

    /**
     * @since 5.4
     */
    public Instant getExpires() {
        return getInstant(expiresRef, HttpHeaders.EXPIRES);
    }

    /**
     * @since 5.4
     */
    public Instant getLastModified() {
        return getInstant(lastModifiedRef, HttpHeaders.LAST_MODIFIED);
    }

    /**
     * @since 5.4
     */
    public ETag getETag() {
        ETag eTag = eTagRef.get();
        if (eTag == null) {
            eTag = ETag.get(this);
            if (eTag == null) {
                return null;
            }
            if (!eTagRef.compareAndSet(null, eTag)) {
                eTag = eTagRef.get();
            }
        }
        return eTag;
    }

    /**
     * Returns the {@link Resource} containing the origin response body.
     */
    public Resource getResource() {
        return this.resource;
    }

    /**
     * Indicates whether the origin response indicated the associated
     * resource had variants (i.e. that the Vary header was set on the
     * origin response).
     */
    public boolean hasVariants() {
        return variants != null;
    }

    /**
     * Returns all known variants.
     *
     * @since 5.4
     */
    public Set<String> getVariants() {
        return variants != null ? variants : Collections.emptySet();
    }

    /**
     * @deprecated No longer applicable. Use {@link #getVariants()} instead.
     */
    @Deprecated
    public Map<String, String> getVariantMap() {
        return variants != null ? variants.stream()
                .collect(Collectors.toMap(e -> e, e -> e + requestURI)) : Collections.emptyMap();
    }

    /**
     * Returns the HTTP request method that was used to create the cached
     * response entry.
     *
     * @since 4.4
     */
    public String getRequestMethod() {
        return method;
    }

    /**
     * @since 5.4
     */
    public String getRequestURI() {
        return requestURI;
    }

    /**
     * @since 5.4
     */
    public MessageHeaders requestHeaders() {
        return requestHeaders;
    }

    /**
     * @since 5.4
     */
    public Iterator<Header> requestHeaderIterator() {
        return requestHeaders.headerIterator();
    }

    /**
     * @since 5.4
     */
    public Iterator<Header> requestHeaderIterator(final String headerName) {
        return requestHeaders.headerIterator(headerName);
    }

    /**
     * Tests if the given {@link HttpCacheEntry} is newer than the given {@link MessageHeaders}
     * by comparing values of their {@literal DATE} header. In case the given entry, or the message,
     * or their {@literal DATE} headers are null, this method returns {@code false}.
     *
     * @since 5.4
     */
    public static boolean isNewer(final HttpCacheEntry entry, final MessageHeaders message) {
        if (entry == null || message == null) {
            return false;
        }
        final Instant cacheDate = entry.getInstant();
        if (cacheDate == null) {
            return false;
        }
        final Instant messageDate = DateUtils.parseStandardDate(message, HttpHeaders.DATE);
        if (messageDate == null) {
            return false;
        }
        return cacheDate.compareTo(messageDate) > 0;
    }

    /**
     * Provides a string representation of this instance suitable for
     * human consumption.
     */
    @Override
    public String toString() {
        return "HttpCacheEntry{" +
                "requestDate=" + requestDate +
                ", responseDate=" + responseDate +
                ", method='" + method + '\'' +
                ", requestURI='" + requestURI + '\'' +
                ", requestHeaders=" + requestHeaders +
                ", status=" + status +
                ", responseHeaders=" + responseHeaders +
                ", resource=" + resource +
                ", variants=" + variants +
                '}';
    }
}
