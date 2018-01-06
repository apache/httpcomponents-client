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

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

class CacheInvalidatorBase {

    static boolean shouldInvalidateHeadCacheEntry(final HttpRequest req, final HttpCacheEntry parentCacheEntry) {
        return requestIsGet(req) && isAHeadCacheEntry(parentCacheEntry);
    }

    static boolean requestIsGet(final HttpRequest req) {
        return req.getMethod().equals((HeaderConstants.GET_METHOD));
    }

    static boolean isAHeadCacheEntry(final HttpCacheEntry parentCacheEntry) {
        return parentCacheEntry != null && parentCacheEntry.getRequestMethod().equals(HeaderConstants.HEAD_METHOD);
    }

    static boolean isSameHost(final URI requestURI, final URI targetURI) {
        return targetURI.isAbsolute() && targetURI.getAuthority().equalsIgnoreCase(requestURI.getAuthority());
    }

    static boolean requestShouldNotBeCached(final HttpRequest req) {
        final String method = req.getMethod();
        return notGetOrHeadRequest(method);
    }

    static boolean notGetOrHeadRequest(final String method) {
        return !(HeaderConstants.GET_METHOD.equals(method) || HeaderConstants.HEAD_METHOD.equals(method));
    }

    private static URI getLocationURI(final URI requestUri, final HttpResponse response, final String headerName) {
        final Header h = response.getFirstHeader(headerName);
        if (h == null) {
            return null;
        }
        final URI locationUri = HttpCacheSupport.normalizeQuetly(h.getValue());
        if (locationUri == null) {
            return requestUri;
        }
        if (locationUri.isAbsolute()) {
            return locationUri;
        } else {
            return URIUtils.resolve(requestUri, locationUri);
        }
    }

    static URI getContentLocationURI(final URI requestUri, final HttpResponse response) {
        return getLocationURI(requestUri, response, HttpHeaders.CONTENT_LOCATION);
    }

    static URI getLocationURI(final URI requestUri, final HttpResponse response) {
        return getLocationURI(requestUri, response, HttpHeaders.LOCATION);
    }

    static boolean responseAndEntryEtagsDiffer(final HttpResponse response,
            final HttpCacheEntry entry) {
        final Header entryEtag = entry.getFirstHeader(HeaderConstants.ETAG);
        final Header responseEtag = response.getFirstHeader(HeaderConstants.ETAG);
        if (entryEtag == null || responseEtag == null) {
            return false;
        }
        return (!entryEtag.getValue().equals(responseEtag.getValue()));
    }

    static boolean responseDateOlderThanEntryDate(final HttpResponse response, final HttpCacheEntry entry) {
        return DateUtils.isBefore(response, entry, HttpHeaders.DATE);
    }

}
