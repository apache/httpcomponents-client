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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;

/**
 * Determines whether a given response can be cached.
 *
 * @since 4.1
 */
@Immutable
public class CachedResponseSuitabilityChecker {

    private static final Log LOG = LogFactory.getLog(CachedResponseSuitabilityChecker.class);

    /**
     * @param host
     *            {@link HttpHost}
     * @param request
     *            {@link HttpRequest}
     * @param entry
     *            {@link CacheEntry}
     * @return boolean yes/no answer
     */
    public boolean canCachedResponseBeUsed(HttpHost host, HttpRequest request, CacheEntry entry) {
        if (!entry.isResponseFresh()) {
            LOG.debug("CachedResponseSuitabilityChecker: Cache Entry was NOT fresh enough");
            return false;
        }

        if (!entry.contentLengthHeaderMatchesActualLength()) {
            LOG
                    .debug("CachedResponseSuitabilityChecker: Cache Entry Content Length and header information DO NOT match.");
            return false;
        }

        if (entry.modifiedSince(request)) {
            LOG
                    .debug("CachedResponseSuitabilityChecker: Cache Entry modified times didn't line up.  Cache Entry should NOT be used.");
            return false;
        }

        for (Header ccHdr : request.getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for (HeaderElement elt : ccHdr.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(elt.getName())) {
                    LOG
                            .debug("CachedResponseSuitabilityChecker: Response contained NO CACHE directive, cache was NOT suitable.");
                    return false;
                }

                if (HeaderConstants.CACHE_CONTROL_NO_STORE.equals(elt.getName())) {
                    LOG
                            .debug("CachedResponseSuitabilityChecker: Response contained NO SORE directive, cache was NOT suitable.");
                    return false;
                }

                if (HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName())) {
                    try {
                        int maxage = Integer.parseInt(elt.getValue());
                        if (entry.getCurrentAgeSecs() > maxage) {
                            LOG
                                    .debug("CachedResponseSuitabilityChecker: Response from cache was NOT suitable.");
                            return false;
                        }
                    } catch (NumberFormatException nfe) {
                        // err conservatively
                        LOG
                                .debug("CachedResponseSuitabilityChecker: Response from cache was NOT suitable.");
                        return false;
                    }
                }

                if (HeaderConstants.CACHE_CONTROL_MAX_STALE.equals(elt.getName())) {
                    try {
                        int maxstale = Integer.parseInt(elt.getValue());
                        if (entry.getFreshnessLifetimeSecs() > maxstale) {
                            LOG
                                    .debug("CachedResponseSuitabilityChecker: Response from cache was NOT suitable.");
                            return false;
                        }
                    } catch (NumberFormatException nfe) {
                        // err conservatively
                        LOG
                                .debug("CachedResponseSuitabilityChecker: Response from cache was NOT suitable.");
                        return false;
                    }
                }

                if (HeaderConstants.CACHE_CONTROL_MIN_FRESH.equals(elt.getName())) {
                    try {
                        int minfresh = Integer.parseInt(elt.getValue());
                        if (entry.getFreshnessLifetimeSecs() < minfresh) {
                            LOG
                                    .debug("CachedResponseSuitabilityChecker: Response from cache was NOT suitable.");
                            return false;
                        }
                    } catch (NumberFormatException nfe) {
                        // err conservatively
                        LOG
                                .debug("CachedResponseSuitabilityChecker: Response from cache was NOT suitable.");
                        return false;
                    }
                }
            }
        }

        LOG.debug("CachedResponseSuitabilityChecker: Response from cache was suitable.");
        return true;
    }
}
