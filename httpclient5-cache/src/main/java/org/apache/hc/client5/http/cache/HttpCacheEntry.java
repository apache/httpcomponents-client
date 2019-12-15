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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MessageHeaders;
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
    private static final String REQUEST_METHOD_HEADER_NAME = "Hc-Request-Method";

    private final Date requestDate;
    private final Date responseDate;
    private final int status;
    private final HeaderGroup responseHeaders;
    private final Resource resource;
    private final Map<String, String> variantMap;
    private final Date date;

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
     */
    public HttpCacheEntry(
            final Date requestDate,
            final Date responseDate,
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
        this.status = status;
        this.responseHeaders = new HeaderGroup();
        this.responseHeaders.setHeaders(responseHeaders);
        this.resource = resource;
        this.variantMap = variantMap != null ? new HashMap<>(variantMap) : null;
        this.date = parseDate();
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
     */
    public HttpCacheEntry(final Date requestDate, final Date responseDate, final int status,
            final Header[] responseHeaders, final Resource resource) {
        this(requestDate, responseDate, status, responseHeaders, resource, new HashMap<String,String>());
    }

    /**
     * Find the "Date" response header and parse it into a java.util.Date
     * @return the Date value of the header or null if the header is not present
     */
    private Date parseDate() {
        return DateUtils.parseDate(this, HttpHeaders.DATE);
    }

    /**
     * Returns the status from the origin {@link org.apache.hc.core5.http.HttpResponse}.
     */
    public int getStatus() {
        return this.status;
    }

    /**
     * Returns the time the associated origin request was initiated by the
     * caching module.
     * @return {@link Date}
     */
    public Date getRequestDate() {
        return requestDate;
    }

    /**
     * Returns the time the origin response was received by the caching module.
     * @return {@link Date}
     */
    public Date getResponseDate() {
        return responseDate;
    }

    /**
     * Returns all the headers that were on the origin response.
     */
    @Override
    public Header[] getHeaders() {
        final HeaderGroup filteredHeaders = new HeaderGroup();
        for (final Iterator<Header> iterator = responseHeaders.headerIterator(); iterator.hasNext();) {
            final Header header = iterator.next();
            if (!REQUEST_METHOD_HEADER_NAME.equals(header.getName())) {
                filteredHeaders.addHeader(header);
            }
        }
        return filteredHeaders.getHeaders();
    }

    /**
     * Returns the first header from the origin response with the given
     * name.
     */
    @Override
    public Header getFirstHeader(final String name) {
        if (REQUEST_METHOD_HEADER_NAME.equalsIgnoreCase(name)) {
            return null;
        }
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
        if (REQUEST_METHOD_HEADER_NAME.equalsIgnoreCase(name)) {
            return new Header[0];
        }
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
     * Gets the Date value of the "Date" header or null if the header is missing or cannot be
     * parsed.
     *
     * @since 4.3
     */
    public Date getDate() {
        return date;
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
     * @return {@code true} if this cached response was a variant
     */
    public boolean hasVariants() {
        return getFirstHeader(HeaderConstants.VARY) != null;
    }

    /**
     * Returns an index about where in the cache different variants for
     * a given resource are stored. This maps "variant keys" to "cache keys",
     * where the variant key is derived from the varying request headers,
     * and the cache key is the location in the
     * {@link HttpCacheStorage} where that
     * particular variant is stored. The first variant returned is used as
     * the "parent" entry to hold this index of the other variants.
     */
    public Map<String, String> getVariantMap() {
        return Collections.unmodifiableMap(variantMap);
    }

    /**
     * Returns the HTTP request method that was used to create the cached
     * response entry.
     *
     * @since 4.4
     */
    public String getRequestMethod() {
        final Header requestMethodHeader = responseHeaders.getFirstHeader(REQUEST_METHOD_HEADER_NAME);
        if (requestMethodHeader != null) {
            return requestMethodHeader.getValue();
        }
        return HeaderConstants.GET_METHOD;
    }

    /**
     * Provides a string representation of this instance suitable for
     * human consumption.
     */
    @Override
    public String toString() {
        return "[request date=" + this.requestDate + "; response date=" + this.responseDate
                + "; status=" + this.status + "]";
    }

}
