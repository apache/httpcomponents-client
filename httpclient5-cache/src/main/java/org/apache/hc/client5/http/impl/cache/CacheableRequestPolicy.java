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

import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;

/**
 * Determines if an HttpRequest is allowed to be served from the cache.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class CacheableRequestPolicy {

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Determines if an HttpRequest can be served from the cache.
     *
     * @param request
     *            an HttpRequest
     * @return boolean Is it possible to serve this request from cache
     */
    public boolean isServableFromCache(final HttpRequest request) {
        final String method = request.getMethod();

        final ProtocolVersion pv = request.getVersion() != null ? request.getVersion() : HttpVersion.DEFAULT;
        if (HttpVersion.HTTP_1_1.compareToVersion(pv) != 0) {
            log.trace("non-HTTP/1.1 request was not serveable from cache");
            return false;
        }

        if (!(method.equals(HeaderConstants.GET_METHOD) || method
                .equals(HeaderConstants.HEAD_METHOD))) {
            log.trace("non-GET or non-HEAD request was not serveable from cache");
            return false;
        }

        if (request.getHeaders(HeaderConstants.PRAGMA).length > 0) {
            log.trace("request with Pragma header was not serveable from cache");
            return false;
        }

        final Iterator<HeaderElement> it = MessageSupport.iterate(request, HeaderConstants.CACHE_CONTROL);
        while (it.hasNext()) {
            final HeaderElement cacheControlElement = it.next();
            if (HeaderConstants.CACHE_CONTROL_NO_STORE.equalsIgnoreCase(cacheControlElement
                    .getName())) {
                log.trace("Request with no-store was not serveable from cache");
                return false;
            }
            if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equalsIgnoreCase(cacheControlElement
                    .getName())) {
                log.trace("Request with no-cache was not serveable from cache");
                return false;
            }
        }

        log.trace("Request was serveable from cache");
        return true;
    }

}
