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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.protocol.HttpContext;

/**
 * Class used to represent an asynchronous revalidation event, such as with
 * "stale-while-revalidate"
 */
public class AsynchronousValidationRequest implements Runnable {
    private final AsynchronousValidator parent;
    private final CachingHttpClient cachingClient;
    private final HttpHost target;
    private final HttpRequest request;
    private final HttpContext context;
    private final HttpCacheEntry cacheEntry;
    private final String identifier;
    private final int consecutiveFailedAttempts;

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Used internally by {@link AsynchronousValidator} to schedule a
     * revalidation.
     * @param cachingClient
     * @param target
     * @param request
     * @param context
     * @param cacheEntry
     * @param identifier
     * @param consecutiveFailedAttempts
     */
    AsynchronousValidationRequest(AsynchronousValidator parent,
            CachingHttpClient cachingClient, HttpHost target,
            HttpRequest request, HttpContext context,
            HttpCacheEntry cacheEntry,
            String identifier, int consecutiveFailedAttempts) {
        this.parent = parent;
        this.cachingClient = cachingClient;
        this.target = target;
        this.request = request;
        this.context = context;
        this.cacheEntry = cacheEntry;
        this.identifier = identifier;
        this.consecutiveFailedAttempts = consecutiveFailedAttempts;
    }

    public void run() {
        try {
            if (revalidateCacheEntry()) {
                parent.jobSuccessful(identifier);
            } else {
                parent.jobFailed(identifier);
            }
        } finally {
            parent.markComplete(identifier);
        }
    }

    /**
     * Revalidate the cache entry and return if the operation was successful.
     * Success means a connection to the server was established and replay did
     * not indicate a server error.
     * @return <code>true</code> if the cache entry was successfully validated;
     * otherwise <code>false</code>
     */
    protected boolean revalidateCacheEntry() {
        try {
            HttpResponse httpResponse = cachingClient.revalidateCacheEntry(target, request, context, cacheEntry);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            return isNotServerError(statusCode) && isNotStale(httpResponse);
        } catch (IOException ioe) {
            log.debug("Asynchronous revalidation failed due to exception: " + ioe);
            return false;
        } catch (ProtocolException pe) {
            log.error("ProtocolException thrown during asynchronous revalidation: " + pe);
            return false;
        } catch (RuntimeException re) {
            log.error("RuntimeException thrown during asynchronous revalidation: " + re);
            return false;
        }
    }

    private boolean isNotServerError(int statusCode) {
        return statusCode < 500;
    }

    private boolean isNotStale(HttpResponse httpResponse) {
        Header[] warnings = httpResponse.getHeaders(HeaderConstants.WARNING);
        return warnings == null || warnings.length == 0;
    }

    String getIdentifier() {
        return identifier;
    }

    /**
     * The number of consecutivly failed revalidation attempts.
     * @return the number of consecutively failed revalidation attempts.
     */
    public int getConsecutiveFailedAttempts() {
        return consecutiveFailedAttempts;
    }
}
