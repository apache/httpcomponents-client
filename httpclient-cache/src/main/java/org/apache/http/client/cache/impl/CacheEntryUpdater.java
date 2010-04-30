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
package org.apache.http.client.cache.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;

/**
 * @since 4.1
 */
public class CacheEntryUpdater {

    public void updateCacheEntry(CacheEntry entry, Date requestDate, Date responseDate,
            HttpResponse response) {
        entry.setRequestDate(requestDate);
        entry.setResponseDate(responseDate);
        mergeHeaders(entry, response);
    }

    protected void mergeHeaders(CacheEntry entry, HttpResponse response) {
        List<Header> cacheEntryHeaderList = new ArrayList<Header>(Arrays.asList(entry
                .getAllHeaders()));

        if (entryAndResponseHaveDateHeader(entry, response)
                && entryDateHeaderNewerThenResponse(entry, response)) {
            // Don't merge Headers, keep the entries headers as they are newer.
            removeCacheEntry1xxWarnings(cacheEntryHeaderList, entry);

            return;
        }

        removeCacheHeadersThatMatchResponse(cacheEntryHeaderList, response);

        cacheEntryHeaderList.addAll(Arrays.asList(response.getAllHeaders()));
        removeCacheEntry1xxWarnings(cacheEntryHeaderList, entry);

        entry.setResponseHeaders(cacheEntryHeaderList.toArray(new Header[cacheEntryHeaderList
                .size()]));
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

    private void removeCacheEntry1xxWarnings(List<Header> cacheEntryHeaderList, CacheEntry entry) {
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

    private boolean entryDateHeaderNewerThenResponse(CacheEntry entry, HttpResponse response) {
        try {
            Date entryDate = DateUtils.parseDate(entry.getFirstHeader(HeaderConstants.DATE)
                    .getValue());
            Date responseDate = DateUtils.parseDate(response.getFirstHeader(HeaderConstants.DATE)
                    .getValue());

            if (!entryDate.after(responseDate)) {
                return false;
            }
        } catch (DateParseException e) {
            return false;
        }

        return true;
    }

    private boolean entryAndResponseHaveDateHeader(CacheEntry entry, HttpResponse response) {
        if (entry.getFirstHeader(HeaderConstants.DATE) != null
                && response.getFirstHeader(HeaderConstants.DATE) != null) {
            return true;
        }

        return false;
    }

}
