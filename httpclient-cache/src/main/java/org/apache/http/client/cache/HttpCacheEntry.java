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
package org.apache.http.client.cache;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.message.HeaderGroup;

/**
 * Structure used to store an {@link HttpResponse} in a cache. Some entries
 * can optionally depend on system resources that may require explicit
 * deallocation. In such a case {@link #getResource()} should return a non
 * null instance of {@link Resource} that must be deallocated by calling
 * {@link Resource#dispose()} method when no longer used.
 *
 * @since 4.1
 */
@Immutable
public class HttpCacheEntry implements Serializable {

    private static final long serialVersionUID = -6300496422359477413L;

    private final Date requestDate;
    private final Date responseDate;
    private final StatusLine statusLine;
    private final HeaderGroup responseHeaders;
    private final Resource resource;
    private final Map<String,String> variantMap;

    /**
     * Create a new {@link HttpCacheEntry} with variants.
     * @param requestDate
     *          Date/time when the request was made (Used for age
     *            calculations)
     * @param responseDate
     *          Date/time that the response came back (Used for age
     *            calculations)
     * @param statusLine
     *          HTTP status line from origin response
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
            final StatusLine statusLine,
            final Header[] responseHeaders,
            final Resource resource,
            final Map<String,String> variantMap) {
        super();
        if (requestDate == null) {
            throw new IllegalArgumentException("Request date may not be null");
        }
        if (responseDate == null) {
            throw new IllegalArgumentException("Response date may not be null");
        }
        if (statusLine == null) {
            throw new IllegalArgumentException("Status line may not be null");
        }
        if (responseHeaders == null) {
            throw new IllegalArgumentException("Response headers may not be null");
        }
        if (resource == null) {
            throw new IllegalArgumentException("Resource may not be null");
        }
        this.requestDate = requestDate;
        this.responseDate = responseDate;
        this.statusLine = statusLine;
        this.responseHeaders = new HeaderGroup();
        this.responseHeaders.setHeaders(responseHeaders);
        this.resource = resource;
        this.variantMap = variantMap != null
            ? new HashMap<String,String>(variantMap)
            : null;
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
     * @param statusLine
     *          HTTP status line from origin response
     * @param responseHeaders
     *          Header[] from original HTTP Response
     * @param resource representing origin response body
     */
    public HttpCacheEntry(Date requestDate, Date responseDate, StatusLine statusLine,
            Header[] responseHeaders, Resource resource) {
        this(requestDate, responseDate, statusLine, responseHeaders, resource,
                new HashMap<String,String>());
    }

    /**
     * Returns the {@link StatusLine} from the origin {@link HttpResponse}.
     */
    public StatusLine getStatusLine() {
        return this.statusLine;
    }

    /**
     * Returns the {@link ProtocolVersion} from the origin {@link HttpResponse}.
     */
    public ProtocolVersion getProtocolVersion() {
        return this.statusLine.getProtocolVersion();
    }

    /**
     * Gets the reason phrase from the origin {@link HttpResponse}, for example,
     * "Not Modified".
     */
    public String getReasonPhrase() {
        return this.statusLine.getReasonPhrase();
    }

    /**
     * Returns the HTTP response code from the origin {@link HttpResponse}.
     */
    public int getStatusCode() {
        return this.statusLine.getStatusCode();
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
    public Header[] getAllHeaders() {
        return responseHeaders.getAllHeaders();
    }

    /**
     * Returns the first header from the origin response with the given
     * name.
     */
    public Header getFirstHeader(String name) {
        return responseHeaders.getFirstHeader(name);
    }

    /**
     * Gets all the headers with the given name that were on the origin
     * response.
     */
    public Header[] getHeaders(String name) {
        return responseHeaders.getHeaders(name);
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
     * {@link org.apache.http.client.cache.HttpCacheStorage} where that
     * particular variant is stored. The first variant returned is used as
     * the "parent" entry to hold this index of the other variants.
     */
    public Map<String, String> getVariantMap() {
        return Collections.unmodifiableMap(variantMap);
    }

    /**
     * Provides a string representation of this instance suitable for
     * human consumption.
     */
    @Override
    public String toString() {
        return "[request date=" + this.requestDate + "; response date=" + this.responseDate
                + "; statusLine=" + this.statusLine + "]";
    }
}
