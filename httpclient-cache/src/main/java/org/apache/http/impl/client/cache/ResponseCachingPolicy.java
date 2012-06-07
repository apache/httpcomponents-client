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

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.HTTP;

/**
 * Determines if an HttpResponse can be cached.
 *
 * @since 4.1
 */
@Immutable
class ResponseCachingPolicy {

    private final long maxObjectSizeBytes;
    private final boolean sharedCache;
    private final Log log = LogFactory.getLog(getClass());
    private static final Set<Integer> cacheableStatuses = 
    	new HashSet<Integer>(Arrays.asList(HttpStatus.SC_OK,
    			HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION,
    			HttpStatus.SC_MULTIPLE_CHOICES,
    			HttpStatus.SC_MOVED_PERMANENTLY,
    			HttpStatus.SC_GONE));
    private static final Set<Integer> uncacheableStatuses =
    		new HashSet<Integer>(Arrays.asList(HttpStatus.SC_PARTIAL_CONTENT,
    				HttpStatus.SC_SEE_OTHER));
    	
    /**
     * Define a cache policy that limits the size of things that should be stored
     * in the cache to a maximum of {@link HttpResponse} bytes in size.
     *
     * @param maxObjectSizeBytes the size to limit items into the cache
     * @param sharedCache whether to behave as a shared cache (true) or a
     * non-shared/private cache (false)
     */
    public ResponseCachingPolicy(long maxObjectSizeBytes, boolean sharedCache) {
        this.maxObjectSizeBytes = maxObjectSizeBytes;
        this.sharedCache = sharedCache;
    }

    /**
     * Determines if an HttpResponse can be cached.
     *
     * @param httpMethod What type of request was this, a GET, PUT, other?
     * @param response The origin response
     * @return <code>true</code> if response is cacheable
     */
    public boolean isResponseCacheable(String httpMethod, HttpResponse response) {
        boolean cacheable = false;

        if (!HeaderConstants.GET_METHOD.equals(httpMethod)) {
            log.debug("Response was not cacheable.");
            return false;
        }
        
        int status = response.getStatusLine().getStatusCode();
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
        
        Header contentLength = response.getFirstHeader(HTTP.CONTENT_LEN);
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

        Header[] dateHeaders = response.getHeaders(HTTP.DATE_HEADER);

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

    private boolean unknownStatusCode(int status) {
    	if (status >= 100 && status <= 101) return false;
    	if (status >= 200 && status <= 206) return false;
    	if (status >= 300 && status <= 307) return false;
    	if (status >= 400 && status <= 417) return false;
    	if (status >= 500 && status <= 505) return false;
		return true;
	}

	protected boolean isExplicitlyNonCacheable(HttpResponse response) {
        Header[] cacheControlHeaders = response.getHeaders(HeaderConstants.CACHE_CONTROL);
        for (Header header : cacheControlHeaders) {
            for (HeaderElement elem : header.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_NO_STORE.equals(elem.getName())
                        || HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elem.getName())
                        || (sharedCache && HeaderConstants.PRIVATE.equals(elem.getName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean hasCacheControlParameterFrom(HttpMessage msg, String[] params) {
        Header[] cacheControlHeaders = msg.getHeaders(HeaderConstants.CACHE_CONTROL);
        for (Header header : cacheControlHeaders) {
            for (HeaderElement elem : header.getElements()) {
                for (String param : params) {
                    if (param.equalsIgnoreCase(elem.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected boolean isExplicitlyCacheable(HttpResponse response) {
        if (response.getFirstHeader(HeaderConstants.EXPIRES) != null)
            return true;
        String[] cacheableParams = { HeaderConstants.CACHE_CONTROL_MAX_AGE, "s-maxage",
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
     * @return <code>true</code> if response is cacheable
     */
    public boolean isResponseCacheable(HttpRequest request, HttpResponse response) {
        if (requestProtocolGreaterThanAccepted(request)) {
            log.debug("Response was not cacheable.");
            return false;
        }

        String[] uncacheableRequestDirectives = { HeaderConstants.CACHE_CONTROL_NO_STORE };
        if (hasCacheControlParameterFrom(request,uncacheableRequestDirectives)) {
            return false;
        }

        if (request.getRequestLine().getUri().contains("?") &&
            (!isExplicitlyCacheable(response) || from1_0Origin(response))) {
            log.debug("Response was not cacheable.");
            return false;
        }

        if (expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(response)) {
            return false;
        }

        if (sharedCache) {
            Header[] authNHeaders = request.getHeaders(HeaderConstants.AUTHORIZATION);
            if (authNHeaders != null && authNHeaders.length > 0) {
                String[] authCacheableParams = {
                        "s-maxage", HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE, HeaderConstants.PUBLIC
                };
                return hasCacheControlParameterFrom(response, authCacheableParams);
            }
        }

        String method = request.getRequestLine().getMethod();
        return isResponseCacheable(method, response);
    }

    private boolean expiresHeaderLessOrEqualToDateHeaderAndNoCacheControl(
            HttpResponse response) {
        if (response.getFirstHeader(HeaderConstants.CACHE_CONTROL) != null) return false;
        Header expiresHdr = response.getFirstHeader(HeaderConstants.EXPIRES);
        Header dateHdr = response.getFirstHeader(HTTP.DATE_HEADER);
        if (expiresHdr == null || dateHdr == null) return false;
        try {
            Date expires = DateUtils.parseDate(expiresHdr.getValue());
            Date date = DateUtils.parseDate(dateHdr.getValue());
            return expires.equals(date) || expires.before(date);
        } catch (DateParseException dpe) {
            return false;
        }
    }

    private boolean from1_0Origin(HttpResponse response) {
        Header via = response.getFirstHeader(HeaderConstants.VIA);
        if (via != null) {
            for(HeaderElement elt : via.getElements()) {
                String proto = elt.toString().split("\\s")[0];
                if (proto.contains("/")) {
                    return proto.equals("HTTP/1.0");
                } else {
                    return proto.equals("1.0");
                }
            }
        }
        return HttpVersion.HTTP_1_0.equals(response.getProtocolVersion());
    }

    private boolean requestProtocolGreaterThanAccepted(HttpRequest req) {
        return req.getProtocolVersion().compareToVersion(HttpVersion.HTTP_1_1) > 0;
    }

}
