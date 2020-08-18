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

import java.io.IOException;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.HeaderGroup;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;

/**
 * Update a {@link HttpCacheEntry} with new or updated information based on the latest
 * 304 status response from the Server.  Use the {@link HttpResponse} to perform
 * the update.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
class CacheEntryUpdater {

    private final ResourceFactory resourceFactory;

    CacheEntryUpdater() {
        this(new HeapResourceFactory());
    }

    CacheEntryUpdater(final ResourceFactory resourceFactory) {
        super();
        this.resourceFactory = resourceFactory;
    }

    /**
     * Update the entry with the new information from the response.  Should only be used for
     * 304 responses.
     *
     * @param requestId
     * @param entry The cache Entry to be updated
     * @param requestDate When the request was performed
     * @param responseDate When the response was gotten
     * @param response The HttpResponse from the backend server call
     * @return HttpCacheEntry an updated version of the cache entry
     * @throws java.io.IOException if something bad happens while trying to read the body from the original entry
     */
    public HttpCacheEntry updateCacheEntry(
            final String requestId,
            final HttpCacheEntry entry,
            final Date requestDate,
            final Date responseDate,
            final HttpResponse response) throws IOException {
        Args.check(response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED,
                "Response must have 304 status code");
        final Header[] mergedHeaders = mergeHeaders(entry, response);
        Resource resource = null;
        if (entry.getResource() != null) {
            resource = resourceFactory.copy(requestId, entry.getResource());
        }
        return new HttpCacheEntry(
                requestDate,
                responseDate,
                entry.getStatusLine(),
                mergedHeaders,
                resource,
                entry.getRequestMethod());
    }

    protected Header[] mergeHeaders(final HttpCacheEntry entry, final HttpResponse response) {
        if (entryAndResponseHaveDateHeader(entry, response)
                && entryDateHeaderNewerThenResponse(entry, response)) {
            // Don't merge headers, keep the entry's headers as they are newer.
            return entry.getAllHeaders();
        }

        final HeaderGroup headerGroup = new HeaderGroup();
        headerGroup.setHeaders(entry.getAllHeaders());
        // Remove cache headers that match response
        for (final HeaderIterator it = response.headerIterator(); it.hasNext(); ) {
            final Header responseHeader = it.nextHeader();
            // Since we do not expect a content in a 304 response, should retain the original Content-Encoding and Content-Length header
            if (HTTP.CONTENT_ENCODING.equals(responseHeader.getName())
                    || HTTP.CONTENT_LEN.equals(responseHeader.getName())) {
                continue;
            }
            final Header[] matchingHeaders = headerGroup.getHeaders(responseHeader.getName());
            for (final Header matchingHeader : matchingHeaders) {
                headerGroup.removeHeader(matchingHeader);
            }

        }
        // remove cache entry 1xx warnings
        for (final HeaderIterator it = headerGroup.iterator(); it.hasNext(); ) {
            final Header cacheHeader = it.nextHeader();
            if (HeaderConstants.WARNING.equalsIgnoreCase(cacheHeader.getName())) {
                final String warningValue = cacheHeader.getValue();
                if (warningValue != null && warningValue.startsWith("1")) {
                    it.remove();
                }
            }
        }
        for (final HeaderIterator it = response.headerIterator(); it.hasNext(); ) {
            final Header responseHeader = it.nextHeader();
            // Since we do not expect a content in a 304 response, should avoid updating Content-Encoding and Content-Length header
            if (HTTP.CONTENT_ENCODING.equals(responseHeader.getName())
                    || HTTP.CONTENT_LEN.equals(responseHeader.getName())) {
                continue;
            }
            headerGroup.addHeader(responseHeader);
        }
        return headerGroup.getAllHeaders();
    }

    private boolean entryDateHeaderNewerThenResponse(final HttpCacheEntry entry, final HttpResponse response) {
        final Date entryDate = DateUtils.parseDate(entry.getFirstHeader(HTTP.DATE_HEADER)
                .getValue());
        final Date responseDate = DateUtils.parseDate(response.getFirstHeader(HTTP.DATE_HEADER)
                .getValue());
        return entryDate != null && responseDate != null && entryDate.after(responseDate);
    }

    private boolean entryAndResponseHaveDateHeader(final HttpCacheEntry entry, final HttpResponse response) {
        return entry.getFirstHeader(HTTP.DATE_HEADER) != null
                && response.getFirstHeader(HTTP.DATE_HEADER) != null;

    }

}
