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

package org.apache.hc.client5.http.cache;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents the value of the Cache-Control header in an HTTP request containing cache
 * control directives.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class RequestCacheControl implements CacheControl {

    private final long maxAge;
    private final long maxStale;
    private final long minFresh;
    private final boolean noCache;
    private final boolean noStore;
    private final boolean onlyIfCached;
    private final long staleIfError;

    RequestCacheControl(final long maxAge, final long maxStale, final long minFresh, final boolean noCache,
                               final boolean noStore, final boolean onlyIfCached, final long staleIfError) {
        this.maxAge = maxAge;
        this.maxStale = maxStale;
        this.minFresh = minFresh;
        this.noCache = noCache;
        this.noStore = noStore;
        this.onlyIfCached = onlyIfCached;
        this.staleIfError = staleIfError;
    }

    /**
     * Returns the max-age value from the Cache-Control header.
     *
     * @return The max-age value.
     */
    @Override
    public long getMaxAge() {
        return maxAge;
    }

    /**
     * Returns the max-stale value from the Cache-Control header.
     *
     * @return The max-stale value.
     */
    public long getMaxStale() {
        return maxStale;
    }

    /**
     * Returns the min-fresh value from the Cache-Control header.
     *
     * @return The min-fresh value.
     */
    public long getMinFresh() {
        return minFresh;
    }

    /**
     * Returns the no-cache flag from the Cache-Control header.
     *
     * @return The no-cache flag.
     */
    @Override
    public boolean isNoCache() {
        return noCache;
    }

    /**
     * Returns the no-store flag from the Cache-Control header.
     *
     * @return The no-store flag.
     */
    @Override
    public boolean isNoStore() {
        return noStore;
    }

    /**
     * Returns the only-if-cached flag from the Cache-Control header.
     *
     * @return The only-if-cached flag.
     */
    public boolean isOnlyIfCached() {
        return onlyIfCached;
    }

    /**
     * Returns the stale-if-error value from the Cache-Control header.
     *
     * @return The stale-if-error value.
     */
    @Override
    public long getStaleIfError() {
        return staleIfError;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[");
        if (maxAge >= 0) {
            buf.append("max-age=").append(maxAge).append(",");
        }
        if (maxStale >= 0) {
            buf.append("max-stale=").append(maxStale).append(",");
        }
        if (minFresh >= 0) {
            buf.append("min-fresh=").append(minFresh).append(",");
        }
        if (noCache) {
            buf.append("no-cache").append(",");
        }
        if (noStore) {
            buf.append("no-store").append(",");
        }
        if (onlyIfCached) {
            buf.append("only-if-cached").append(",");
        }
        if (staleIfError >= 0) {
            buf.append("stale-if-error").append(staleIfError).append(",");
        }
        if (buf.charAt(buf.length() - 1) == ',') {
            buf.setLength(buf.length() - 1);
        }
        buf.append("]");
        return buf.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final RequestCacheControl DEFAULT = builder().build();

    public static class Builder {

        private long maxAge = -1;
        private long maxStale = -1;
        private long minFresh = -1;
        private boolean noCache;
        private boolean noStore;
        private boolean onlyIfCached;
        private long staleIfError = -1;

        Builder() {
        }

        public long getMaxAge() {
            return maxAge;
        }

        public Builder setMaxAge(final long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public long getMaxStale() {
            return maxStale;
        }

        public Builder setMaxStale(final long maxStale) {
            this.maxStale = maxStale;
            return this;
        }

        public long getMinFresh() {
            return minFresh;
        }

        public Builder setMinFresh(final long minFresh) {
            this.minFresh = minFresh;
            return this;
        }

        public boolean isNoCache() {
            return noCache;
        }

        public Builder setNoCache(final boolean noCache) {
            this.noCache = noCache;
            return this;
        }

        public boolean isNoStore() {
            return noStore;
        }

        public Builder setNoStore(final boolean noStore) {
            this.noStore = noStore;
            return this;
        }

        public boolean isOnlyIfCached() {
            return onlyIfCached;
        }

        public Builder setOnlyIfCached(final boolean onlyIfCached) {
            this.onlyIfCached = onlyIfCached;
            return this;
        }

        public long getStaleIfError() {
            return staleIfError;
        }

        public Builder setStaleIfError(final long staleIfError) {
            this.staleIfError = staleIfError;
            return this;
        }

        public RequestCacheControl build() {
            return new RequestCacheControl(maxAge, maxStale, minFresh, noCache, noStore, onlyIfCached, staleIfError);
        }

    }
}