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

import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines if an HttpRequest is allowed to be served from the cache.
 */
class CacheableRequestPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(CacheableRequestPolicy.class);

    /**
     * Determines if an HttpRequest can be served from the cache.
     *
     * @param request
     *            an HttpRequest
     * @return boolean Is it possible to serve this request from cache
     */
    public boolean canBeServedFromCache(final RequestCacheControl cacheControl, final HttpRequest request) {
        final String method = request.getMethod();

        final ProtocolVersion pv = request.getVersion() != null ? request.getVersion() : HttpVersion.DEFAULT;
        if (HttpVersion.HTTP_1_1.compareToVersion(pv) != 0) {
            LOG.debug("non-HTTP/1.1 request cannot be served from cache");
            return false;
        }

        if (!Method.GET.isSame(method) && !Method.HEAD.isSame(method)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} request cannot be served from cache", method);
            }
            return false;
        }

        if (cacheControl.isNoStore()) {
            LOG.debug("Request with no-store cannot be served from cache");
            return false;
        }
        if (cacheControl.isNoCache()) {
            LOG.debug("Request with no-cache cannot be served from cache");
            return false;
        }
        LOG.debug("Request can be served from cache");
        return true;
    }

}
