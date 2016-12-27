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

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;

/**
 * Determines if an HttpResponse can be cached.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class ResponseCachingPolicy {

    private static final String[] AUTH_CACHEABLE_PARAMS = {
            "s-maxage", HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE, HeaderConstants.PUBLIC
    };
    private final long maxObjectSizeBytes;
    private final boolean sharedCache;
    private final boolean neverCache1_0ResponsesWithQueryString;
    private final Log log = LogFactory.getLog(getClass());
    private static final Set<Integer> cacheableStatuses =
        new HashSet<>(Arrays.asList(HttpStatus.SC_OK,
                HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION,
                HttpStatus.SC_MULTIPLE_CHOICES,
                HttpStatus.SC_MOVED_PERMANENTLY,
                HttpStatus.SC_GONE));
    private final Set<Integer> uncacheableStatuses;

    /**
     * Define a cache policy that limits the size of things that should be stored
     * in the cache to a maximum of {@link HttpResponse} bytes in size.
     *
     * @param maxObjectSizeBytes the size to limit items into the cache
     * @param sharedCache whether to behave as a shared cache (true) or a
     * non-shared/private cache (false)
     * @param neverCache1_0ResponsesWithQueryString true to never cache HTTP 1.0 responses with a query string, false
     * to cache if explicit cache headers are found.
     * @param allow303Caching if this policy is permitted to cache 303 response
     */
    public ResponseCachingPolicy(final long maxObjectSizeBytes,
            final boolean sharedCache,
            final boolean neverCache1_0ResponsesWithQueryString,
            final boolean allow303Caching) {
        this.maxObjectSizeBytes = maxObjectSizeBytes;
        this.sharedCache = sharedCache;
        this.neverCache1_0ResponsesWithQueryString = neverCache1_0ResponsesWithQueryString;
        if (allow303Caching) {
            uncacheableStatuses = new HashSet<>(
                    Arrays.asList(HttpStatus.SC_PARTIAL_CONTENT));
        } else {
            uncacheableStatuses = new HashSet<>(Arrays.asList(
                    HttpStatus.SC_PARTIAL_CONTENT, HttpStatus.SC_SEE_OTHER));
        }
    }

    /**
     * Determines if an HttpResponse can be cached.
     *
     * @param httpMethod What type of request was this, a GET, PUT, other?
     * @param response The origin response
     * @return {@code true} if response is cacheable
     */
    public boolean isResponseCacheable(final String httpMethod, final HttpResponse response) {
        boolean cacheable = false;

        if (!(HeaderConstants.GET_METHOD.equals(httpMethod) ||
                HeaderConstants.HEAD_METHOD.equals(httpMethod))) {
            log.debug("Response was not cacheable.");
            return false;
        }

        final int status = response.getCode();
        if (cacheableStatuses.contains(status)) {
            // these response codes MAY be cached
            cacheable = true;
        } else if (uncacheableStatuses.contains(status)) {
            return false;
        } else if (unknownStatusCode(status)) {
            // a response with an unknown status code MUST NOT be
            // cached
            return false;
        }

        final Header contentLength = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            final int contentLengthValue = Integer.parseInt(contentLength.getValue());
            if (contentLengthValue > this.maxObjectSizeBytes) {
                return false;
            }
        }

        final Header[] ageHeaders = response.getHeaders(HeaderConstants.AGE);

        if (ageHeaders.length > 1) {
            return false;
        }

        final Header[] expiresHeaders = response.getHeaders(HeaderConstants.EXPIRES);

        if (expiresHeaders.length > 1) {
            return false;
        }

        final Header[] dateHeaders = response.getHeaders(HttpHeaders.DATE);

        if (dateHeaders.length != 1) {
            return false;
        }

        final Date date = DateUtils.parseDate(dateHeaders[0].getValue());
        if (date == null) {
            return false;
        }

        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderConstants.VARY);
        while (it.hasNext()) {
            final HeaderElement elem = it.next();
            if ("*".equals(elem.getName())) {
                return false;
            }
        }

        if (isExplicitlyNonCacheable(response)) {
            return false;
        }

        return (cacheable || isExplicitlyCacheable(response));
    }

    private boolean unknownStatusCode(final int status) {
        if (status >= 100 && status <= 101) {
            return false;
        }
        if (status >= 200 && status <= 206) {
            return false;
        }
        if (status >= 300 && status <= 307) {
            return false;
        }
        if (status >= 400 && status <= 417) {
            return false;
        }
        if (status >= 500 && status <= 505) {
            return false;
        }
        return true;
    }

    protected boolean isExplicitlyNonCacheable(final HttpResponse response) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elem = it.next();
            if (HeaderConstants.CACHE_CONTROL_NO_STORE.equals(elem.getName())
                    || HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elem.getName())
                    || (sharedCache && HeaderConstants.PRIVATE.equals(elem.getName()))) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasCacheControlParameterFrom(final HttpMessage msg, final String[] params) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(msg, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement elem = it.next();
            for (final String param : params) {
                if (param.equalsIgnoreCase(elem.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isExplicitlyCacheable(final HttpResponse response) {
        if (response.getFirstHeader(HeaderConstants.EXPIRES) != null) {
            return true;
        }
        final String[] cacheableParams = { HeaderConstants.CACHE_CONTROL_MAX_AGE, "s-maxage",
                HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE,
                HeaderConstants.CACHE_CONTROL_PROXY_REVALIDATE,
                HeaderConstants.PUBLIC
        };
        return hasCacheControlParameterFrom(response, cacheableParams);
    }

    /**
     * Determine if the {@link HttpResponse} gotten from the origin is a
     * cacheable response.
     *
     * @param request the {@link HttpRequest} that generated an origin hit
     * @param response the {@link HttpResponse} from the origin
     * @return {@code true} if response is cacheable
     */
    public boolean isResponseCacheable(final HttpRequest request, final HttpResponse response) {
        if (requestProtocolGreaterThanAccepted(request)) {
            log.debug("Response was not cacheable.");
            return false;
        }

        final String[] uncacheableRequestDirectives = { HeaderConstants.CACHE_CONTROL_NO_STORE };
        if (hasCacheControlParameterFrom(request,uncacheableRequestDirectives)) {
            return false;
        }

        if (request.getRequestUri().contains("?")) {
            if (neverCache1_0ResponsesWithQueryString && from1_0Origin(response)) {
                log.debug("Response was not cacheable as it had a query string.");
                return false;
            } else if (!isExplicitlyCacheable(response)) {
                log.debug("Response was not cacheable as it is missing explicit caching headers.");
                return false;
            }
        }

        if (expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(response)) {
            return false;
        }

        if (sharedCache) {
            final Header[] authNHeaders = request.getHeaders(HeaderConstants.AUTHORIZATION);
            if (authNHeaders != null && authNHeaders.length > 0
                    && !hasCacheControlParameterFrom(response, AUTH_CACHEABLE_PARAMS)) {
                return false;
            }
        }

        final String method = request.getMethod();
        return isResponseCacheable(method, response);
    }

    private boolean expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(
            final HttpResponse response) {
        if (response.getFirstHeader(HeaderConstants.CACHE_CONTROL) != null) {
            return false;
        }
        final Header expiresHdr = response.getFirstHeader(HeaderConstants.EXPIRES);
        final Header dateHdr = response.getFirstHeader(HttpHeaders.DATE);
        if (expiresHdr == null || dateHdr == null) {
            return false;
        }
        final Date expires = DateUtils.parseDate(expiresHdr.getValue());
        final Date date = DateUtils.parseDate(dateHdr.getValue());
        if (expires == null || date == null) {
            return false;
        }
        return expires.equals(date) || expires.before(date);
    }

    private boolean from1_0Origin(final HttpResponse response) {
        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderConstants.VIA);
        while (it.hasNext()) {
            final HeaderElement elt = it.next();
            final String proto = elt.toString().split("\\s")[0];
            if (proto.contains("/")) {
                return proto.equals("HTTP/1.0");
            } else {
                return proto.equals("1.0");
            }
        }
        final ProtocolVersion version = response.getVersion() != null ? response.getVersion() : HttpVersion.DEFAULT;
        return HttpVersion.HTTP_1_0.equals(version);
    }

    private boolean requestProtocolGreaterThanAccepted(final HttpRequest req) {
        final ProtocolVersion version = req.getVersion() != null ? req.getVersion() : HttpVersion.DEFAULT;
        return version.compareToVersion(HttpVersion.HTTP_1_1) > 0;
    }

}
