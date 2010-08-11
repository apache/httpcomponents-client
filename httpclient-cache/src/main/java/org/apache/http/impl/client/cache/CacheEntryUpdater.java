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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.HTTP;

/**
 * Update a {@link HttpCacheEntry} with new or updated information based on the latest
 * 200 or 304 status responses from the Server.  Use the {@link HttpResponse} to perform
 * the update.
 *
 * @since 4.1
 */
@Immutable
class CacheEntryUpdater {

    private final CacheEntryFactory cacheEntryFactory;

    CacheEntryUpdater() {
        this(new CacheEntryFactory(new HeapResourceFactory()));
    }

    CacheEntryUpdater(final CacheEntryFactory cacheEntryFactory) {
        super();
        this.cacheEntryFactory = cacheEntryFactory;
    }

    /**
     * Update the entry with the new information from the response.
     *
     * @param request id
     * @param entry The cache Entry to be updated
     * @param requestDate When the request was performed
     * @param responseDate When the response was gotten
     * @param response The HttpResponse from the backend server call
     * @return HttpCacheEntry an updated version of the cache entry
     * @throws java.io.IOException if something bad happens while trying to read the body from the original entry
     */
    public HttpCacheEntry updateCacheEntry(
            String requestId,
            HttpCacheEntry entry,
            Date requestDate,
            Date responseDate,
            HttpResponse response) throws IOException {

        Header[] mergedHeaders = mergeHeaders(entry, response);
        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        InputStream instream = entry.getResource().getInputStream();
        byte[] buf = new byte[2048];
        int len;
        while ((len = instream.read(buf)) != -1) {
            outstream.write(buf, 0, len);
        }
        HttpCacheEntry updated = cacheEntryFactory.generate(
                requestId,
                requestDate,
                responseDate,
                entry.getStatusLine(),
                mergedHeaders,
                outstream.toByteArray());
        return updated;
    }

    protected Header[] mergeHeaders(HttpCacheEntry entry, HttpResponse response) {
        List<Header> cacheEntryHeaderList = new ArrayList<Header>(Arrays.asList(entry
                .getAllHeaders()));

        if (entryAndResponseHaveDateHeader(entry, response)
                && entryDateHeaderNewerThenResponse(entry, response)) {
            // Don't merge Headers, keep the entries headers as they are newer.
            removeCacheEntry1xxWarnings(cacheEntryHeaderList, entry);

            return cacheEntryHeaderList.toArray(new Header[cacheEntryHeaderList.size()]);
        }

        removeCacheHeadersThatMatchResponse(cacheEntryHeaderList, response);

        cacheEntryHeaderList.addAll(Arrays.asList(response.getAllHeaders()));
        removeCacheEntry1xxWarnings(cacheEntryHeaderList, entry);

        return cacheEntryHeaderList.toArray(new Header[cacheEntryHeaderList.size()]);
    }

    private void removeCacheHeadersThatMatchResponse(List<Header> cacheEntryHeaderList,
            HttpResponse response) {
        for (Header responseHeader : response.getAllHeaders()) {
            ListIterator<Header> cacheEntryHeaderListIter = cacheEntryHeaderList.listIterator();

            while (cacheEntryHeaderListIter.hasNext()) {
                String cacheEntryHeaderName = cacheEntryHeaderListIter.next().getName();

                if (cacheEntryHeaderName.equals(responseHeader.getName())) {
                    cacheEntryHeaderListIter.remove();
                }
            }
        }
    }

    private void removeCacheEntry1xxWarnings(List<Header> cacheEntryHeaderList, HttpCacheEntry entry) {
        ListIterator<Header> cacheEntryHeaderListIter = cacheEntryHeaderList.listIterator();

        while (cacheEntryHeaderListIter.hasNext()) {
            String cacheEntryHeaderName = cacheEntryHeaderListIter.next().getName();

            if (HeaderConstants.WARNING.equals(cacheEntryHeaderName)) {
                for (Header cacheEntryWarning : entry.getHeaders(HeaderConstants.WARNING)) {
                    if (cacheEntryWarning.getValue().startsWith("1")) {
                        cacheEntryHeaderListIter.remove();
                    }
                }
            }
        }
    }

    private boolean entryDateHeaderNewerThenResponse(HttpCacheEntry entry, HttpResponse response) {
        try {
            Date entryDate = DateUtils.parseDate(entry.getFirstHeader(HTTP.DATE_HEADER)
                    .getValue());
            Date responseDate = DateUtils.parseDate(response.getFirstHeader(HTTP.DATE_HEADER)
                    .getValue());

            if (!entryDate.after(responseDate)) {
                return false;
            }
        } catch (DateParseException e) {
            return false;
        }

        return true;
    }

    private boolean entryAndResponseHaveDateHeader(HttpCacheEntry entry, HttpResponse response) {
        if (entry.getFirstHeader(HTTP.DATE_HEADER) != null
                && response.getFirstHeader(HTTP.DATE_HEADER) != null) {
            return true;
        }

        return false;
    }

}
