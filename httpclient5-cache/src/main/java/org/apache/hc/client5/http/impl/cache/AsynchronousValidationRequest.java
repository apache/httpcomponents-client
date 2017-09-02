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

import java.io.IOException;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class used to represent an asynchronous revalidation event, such as with
 * "stale-while-revalidate"
 */
public class AsynchronousValidationRequest implements Runnable {
    private final AsynchronousValidator parent;
    private final CachingExec cachingExec;
    private final HttpHost target;
    private final ClassicHttpRequest request;
    private final ExecChain.Scope scope;
    private final ExecChain chain;
    private final HttpCacheEntry cacheEntry;
    private final String identifier;
    private final int consecutiveFailedAttempts;

    private final Logger log = LogManager.getLogger(getClass());

    /**
     * Used internally by {@link AsynchronousValidator} to schedule a
     */
    AsynchronousValidationRequest(
            final AsynchronousValidator parent,
            final CachingExec cachingExec,
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final HttpCacheEntry cacheEntry,
            final String identifier,
            final int consecutiveFailedAttempts) {
        this.parent = parent;
        this.cachingExec = cachingExec;
        this.target = target;
        this.request = request;
        this.scope = scope;
        this.chain = chain;
        this.cacheEntry = cacheEntry;
        this.identifier = identifier;
        this.consecutiveFailedAttempts = consecutiveFailedAttempts;
    }

    @Override
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
     * @return {@code true} if the cache entry was successfully validated;
     * otherwise {@code false}
     */
    private boolean revalidateCacheEntry() {
        try {
            try (ClassicHttpResponse httpResponse = cachingExec.revalidateCacheEntry(target, request, scope, chain, cacheEntry)) {
                final int statusCode = httpResponse.getCode();
                return isNotServerError(statusCode) && isNotStale(httpResponse);
            }
        } catch (final IOException ioe) {
            log.debug("Asynchronous revalidation failed due to I/O error", ioe);
            return false;
        } catch (final HttpException pe) {
            log.error("HTTP protocol exception during asynchronous revalidation", pe);
            return false;
        } catch (final RuntimeException re) {
            log.error("RuntimeException thrown during asynchronous revalidation: " + re);
            return false;
        }
    }

    /**
     * Return whether the status code indicates a server error or not.
     * @param statusCode the status code to be checked
     * @return if the status code indicates a server error or not
     */
    private boolean isNotServerError(final int statusCode) {
        return statusCode < 500;
    }

    /**
     * Try to detect if the returned response is generated from a stale cache entry.
     * @param httpResponse the response to be checked
     * @return whether the response is stale or not
     */
    private boolean isNotStale(final HttpResponse httpResponse) {
        final Header[] warnings = httpResponse.getHeaders(HeaderConstants.WARNING);
        if (warnings != null)
        {
            for (final Header warning : warnings)
            {
                /**
                 * warn-codes
                 * 110 = Response is stale
                 * 111 = Revalidation failed
                 */
                final String warningValue = warning.getValue();
                if (warningValue.startsWith("110") || warningValue.startsWith("111"))
                {
                    return false;
                }
            }
        }
        return true;
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * The number of consecutively failed revalidation attempts.
     * @return the number of consecutively failed revalidation attempts.
     */
    public int getConsecutiveFailedAttempts() {
        return consecutiveFailedAttempts;
    }

}
