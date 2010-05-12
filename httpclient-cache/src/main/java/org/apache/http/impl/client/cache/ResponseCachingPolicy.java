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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.Immutable;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;

/**
 * Determines if an HttpResponse can be cached.
 *
 * @since 4.1
 */
@Immutable
public class ResponseCachingPolicy {

    private final int maxObjectSizeBytes;
    private final Log log = LogFactory.getLog(getClass());

    /**
     *
     * @param maxObjectSizeBytes
     */
    public ResponseCachingPolicy(int maxObjectSizeBytes) {
        this.maxObjectSizeBytes = maxObjectSizeBytes;
    }

    /**
     * Determines if an HttpResponse can be cached.
     *
     * @param httpMethod
     * @param response
     * @return <code>true</code> if response is cacheable
     */
    public boolean isResponseCacheable(String httpMethod, HttpResponse response) {
        boolean cacheable = false;

        if (!HeaderConstants.GET_METHOD.equals(httpMethod)) {
            log.debug("Response was not cacheable.");
            return false;
        }

        switch (response.getStatusLine().getStatusCode()) {
        case HttpStatus.SC_OK:
        case HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION:
        case HttpStatus.SC_MULTIPLE_CHOICES:
        case HttpStatus.SC_MOVED_PERMANENTLY:
        case HttpStatus.SC_GONE:
            // these response codes MAY be cached
            cacheable = true;
            log.debug("Response was cacheable");
            break;
        case HttpStatus.SC_PARTIAL_CONTENT:
            // we don't implement Range requests and hence are not
            // allowed to cache partial content
            log.debug("Response was not cacheable (Partial Content)");
            return cacheable;

        default:
            // If the status code is not one of the recognized
            // available codes in HttpStatus Don't Cache
            log.debug("Response was not cacheable (Unknown Status code)");
            return cacheable;
        }

        Header contentLength = response.getFirstHeader(HeaderConstants.CONTENT_LENGTH);
        if (contentLength != null) {
            int contentLengthValue = Integer.parseInt(contentLength.getValue());
            if (contentLengthValue > this.maxObjectSizeBytes)
                return false;
        }

        Header[] ageHeaders = response.getHeaders(HeaderConstants.AGE);

        if (ageHeaders.length > 1)
            return false;

        Header[] expiresHeaders = response.getHeaders(HeaderConstants.EXPIRES);

        if (expiresHeaders.length > 1)
            return false;

        Header[] dateHeaders = response.getHeaders(HeaderConstants.DATE);

        if (dateHeaders.length != 1)
            return false;

        try {
            DateUtils.parseDate(dateHeaders[0].getValue());
        } catch (DateParseException dpe) {
            return false;
        }

        for (Header varyHdr : response.getHeaders(HeaderConstants.VARY)) {
            for (HeaderElement elem : varyHdr.getElements()) {
                if ("*".equals(elem.getName())) {
                    return false;
                }
            }
        }

        if (isExplicitlyNonCacheable(response))
            return false;

        return (cacheable || isExplicitlyCacheable(response));
    }

    protected boolean isExplicitlyNonCacheable(HttpResponse response) {
        Header[] cacheControlHeaders = response.getHeaders(HeaderConstants.CACHE_CONTROL);
        for (Header header : cacheControlHeaders) {
            for (HeaderElement elem : header.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_NO_STORE.equals(elem.getName())
                        || HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elem.getName())
                        || "private".equals(elem.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isExplicitlyCacheable(HttpResponse response) {
        if (response.getFirstHeader(HeaderConstants.EXPIRES) != null)
            return true;
        Header[] cacheControlHeaders = response.getHeaders(HeaderConstants.CACHE_CONTROL);
        for (Header header : cacheControlHeaders) {
            for (HeaderElement elem : header.getElements()) {
                if ("max-age".equals(elem.getName()) || "s-maxage".equals(elem.getName())
                        || "must-revalidate".equals(elem.getName())
                        || "proxy-revalidate".equals(elem.getName())
                        || "public".equals(elem.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param request
     * @param response
     * @return <code>true</code> if response is cacheable
     */
    public boolean isResponseCacheable(HttpRequest request, HttpResponse response) {
        if (requestProtocolGreaterThanAccepted(request)) {
            log.debug("Response was not cacheable.");
            return false;
        }

        if (request.getRequestLine().getUri().contains("?") && !isExplicitlyCacheable(response)) {
            log.debug("Response was not cacheable.");
            return false;
        }

        String method = request.getRequestLine().getMethod();
        return isResponseCacheable(method, response);
    }

    private boolean requestProtocolGreaterThanAccepted(HttpRequest req) {
        return req.getProtocolVersion().compareToVersion(CachingHttpClient.HTTP_1_1) > 0;
    }

}
