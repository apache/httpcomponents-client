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

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * <p>Configuration for HTTP caches</p>
 *
 * <p>Cache configuration can be grouped into the following categories:</p>
 *
 * <p><b>Protocol options.</b> I some cases the HTTP protocol allows for
 * conditional behaviors or optional protocol extensions. Such conditional
 * protocol behaviors or extensions can be turned on or off here.
 * See {@link CacheConfig#isNeverCacheHTTP10ResponsesWithQuery()},
 * {@link CacheConfig#isNeverCacheHTTP11ResponsesWithQuery()},
 * {@link CacheConfig#isStaleIfErrorEnabled()}</p>
 *
 * <p><b>Cache size.</b> If the backend storage supports these limits, one
 * can specify the {@link CacheConfig#getMaxCacheEntries maximum number of
 * cache entries} as well as the {@link CacheConfig#getMaxObjectSize()}
 * maximum cacheable response body size}.</p>
 *
 * <p><b>Public/private caching.</b> By default, the caching module considers
 * itself to be a shared (public) cache, and will not, for example, cache
 * responses to requests with {@code Authorization} headers or responses
 * marked with {@code Cache-Control: private}. If, however, the cache
 * is only going to be used by one logical "user" (behaving similarly to a
 * browser cache), then one may want to {@link CacheConfig#isSharedCache()}
 * turn off the shared cache setting}.</p>
 *
 * <p><b>Heuristic caching</b>. Per HTTP caching specification, a cache may
 * cache certain cache entries even if no explicit cache control headers are
 * set by the origin. This behavior is off by default, but you may want to
 * turn this on if you are working with an origin that doesn't set proper
 * headers but where one may still want to cache the responses. Use {@link
 * CacheConfig#isHeuristicCachingEnabled()} to enable heuristic caching},
 * then specify either a {@link CacheConfig#getHeuristicDefaultLifetime()
 * default freshness lifetime} and/or a {@link
 * CacheConfig#getHeuristicCoefficient() fraction of the time since
 * the resource was last modified}.
 *
 * <p><b>Background validation</b>. The cache module supports the
 * {@code stale-while-revalidate} directive, which allows certain cache entry
 * revalidations to happen in the background. Asynchronous validation is enabled
 * by default but it could be disabled by setting the number of re-validation
 * workers to {@code 0} with {@link CacheConfig#getAsynchronousWorkers()}
 * parameter</p>
 */
public class CacheConfig implements Cloneable {

    /** Default setting for the maximum object size that will be
     * cached, in bytes.
     */
    public final static int DEFAULT_MAX_OBJECT_SIZE_BYTES = 8192;

    /** Default setting for the maximum number of cache entries
     * that will be retained.
     */
    public final static int DEFAULT_MAX_CACHE_ENTRIES = 1000;

    /** Default setting for the number of retries on a failed
     * cache processChallenge
     */
    public final static int DEFAULT_MAX_UPDATE_RETRIES = 1;

    /**
     * @deprecated No longer applicable. Do not use.
     */
    @Deprecated
    public final static boolean DEFAULT_303_CACHING_ENABLED = false;

    /**
     * @deprecated No longer applicable. Do not use.
     */
    @Deprecated
    public final static boolean DEFAULT_WEAK_ETAG_ON_PUTDELETE_ALLOWED = false;

    /** Default setting for heuristic caching
     */
    public final static boolean DEFAULT_HEURISTIC_CACHING_ENABLED = false;

    /** Default coefficient used to heuristically determine freshness
     * lifetime from the Last-Modified time of a cache entry.
     */
    public final static float DEFAULT_HEURISTIC_COEFFICIENT = 0.1f;

    /** Default lifetime to be assumed when we cannot calculate
     * freshness heuristically.
     */
    public final static TimeValue DEFAULT_HEURISTIC_LIFETIME = TimeValue.ZERO_MILLISECONDS;

    /** Default number of worker threads to allow for background revalidations
     * resulting from the stale-while-revalidate directive.
     */
    public static final int DEFAULT_ASYNCHRONOUS_WORKERS = 1;

    public static final CacheConfig DEFAULT = new Builder().build();

    private final long maxObjectSize;
    private final int maxCacheEntries;
    private final int maxUpdateRetries;
    private final boolean heuristicCachingEnabled;
    private final float heuristicCoefficient;
    private final TimeValue heuristicDefaultLifetime;
    private final boolean sharedCache;
    private final boolean freshnessCheckEnabled;
    private final int asynchronousWorkers;
    private final boolean neverCacheHTTP10ResponsesWithQuery;
    private final boolean staleIfErrorEnabled;


    /**
     * A constant indicating whether HTTP/1.1 responses with a query string should never be cached.
     *
     */
    private final boolean neverCacheHTTP11ResponsesWithQuery;

    CacheConfig(
            final long maxObjectSize,
            final int maxCacheEntries,
            final int maxUpdateRetries,
            final boolean heuristicCachingEnabled,
            final float heuristicCoefficient,
            final TimeValue heuristicDefaultLifetime,
            final boolean sharedCache,
            final boolean freshnessCheckEnabled,
            final int asynchronousWorkers,
            final boolean neverCacheHTTP10ResponsesWithQuery,
            final boolean neverCacheHTTP11ResponsesWithQuery,
            final boolean staleIfErrorEnabled) {
        super();
        this.maxObjectSize = maxObjectSize;
        this.maxCacheEntries = maxCacheEntries;
        this.maxUpdateRetries = maxUpdateRetries;
        this.heuristicCachingEnabled = heuristicCachingEnabled;
        this.heuristicCoefficient = heuristicCoefficient;
        this.heuristicDefaultLifetime = heuristicDefaultLifetime;
        this.sharedCache = sharedCache;
        this.freshnessCheckEnabled = freshnessCheckEnabled;
        this.asynchronousWorkers = asynchronousWorkers;
        this.neverCacheHTTP10ResponsesWithQuery = neverCacheHTTP10ResponsesWithQuery;
        this.neverCacheHTTP11ResponsesWithQuery = neverCacheHTTP11ResponsesWithQuery;
        this.staleIfErrorEnabled = staleIfErrorEnabled;
    }

    /**
     * Returns the current maximum response body size that will be cached.
     * @return size in bytes
     *
     * @since 4.2
     */
    public long getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * Returns whether the cache will never cache HTTP 1.0 responses with a query string or not.
     * @return {@code true} to not cache query string responses, {@code false} to cache if explicit cache headers are
     * found
     */
    public boolean isNeverCacheHTTP10ResponsesWithQuery() {
        return neverCacheHTTP10ResponsesWithQuery;
    }

    /**
     * Determines whether HTTP/1.1 responses with query strings should never be cached by the
     * client. By default, caching of such responses is allowed. Enabling this option may improve
     * security by preventing responses with sensitive information from being cached.
     * <p>
     * Note that this option only applies to HTTP/1.1.
     * </p>
     *
     * @return {@code true} if HTTP/1.1 responses with query strings should never be cached;
     * {@code false} otherwise.
     * @since 5.4
     */
    public boolean isNeverCacheHTTP11ResponsesWithQuery() {
        return neverCacheHTTP11ResponsesWithQuery;
    }

    /**
     * Returns a boolean value indicating whether the stale-if-error cache
     * directive is enabled. If this option is enabled, cached responses that
     * have become stale due to an error (such as a server error or a network
     * failure) will be returned instead of generating a new request. This can
     * help to reduce the load on the origin server and improve performance.
     * @return {@code true} if the stale-if-error directive is enabled, or
     * {@code false} otherwise.
     */
    public boolean isStaleIfErrorEnabled() {
        return this.staleIfErrorEnabled;
    }

    /**
     * Returns the maximum number of cache entries the cache will retain.
     */
    public int getMaxCacheEntries() {
        return maxCacheEntries;
    }

    /**
     * Returns the number of times to retry a cache processChallenge on failure
     */
    public int getMaxUpdateRetries(){
        return maxUpdateRetries;
    }

    /**
     * @deprecated No longer applicable. Do not use.
     */
    @Deprecated
    public boolean is303CachingEnabled() {
        return true;
    }

    /**
     * Returns whether weak etags is allowed with PUT/DELETE methods.
     * @return {@code true} if it is allowed.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public boolean isWeakETagOnPutDeleteAllowed() {
        return true;
    }

    /**
     * Returns whether heuristic caching is enabled.
     * @return {@code true} if it is enabled.
     */
    public boolean isHeuristicCachingEnabled() {
        return heuristicCachingEnabled;
    }

    /**
     * Returns lifetime coefficient used in heuristic freshness caching.
     */
    public float getHeuristicCoefficient() {
        return heuristicCoefficient;
    }

    /**
     * Get the default lifetime to be used if heuristic freshness calculation is
     * not possible.
     */
    public TimeValue getHeuristicDefaultLifetime() {
        return heuristicDefaultLifetime;
    }

    /**
     * Returns whether the cache will behave as a shared cache or not.
     * @return {@code true} for a shared cache, {@code false} for a non-
     * shared (private) cache
     */
    public boolean isSharedCache() {
        return sharedCache;
    }

    /**
     * Returns whether the cache will perform an extra cache entry freshness check
     * upon cache update in case of a cache miss
     *
     * @since 5.0
     */
    public boolean isFreshnessCheckEnabled() {
        return freshnessCheckEnabled;
    }

    /**
     * Returns the maximum number of threads to allow for background
     * revalidations due to the {@code stale-while-revalidate} directive. A
     * value of 0 means background revalidations are disabled.
     */
    public int getAsynchronousWorkers() {
        return asynchronousWorkers;
    }

    @Override
    protected CacheConfig clone() throws CloneNotSupportedException {
        return (CacheConfig) super.clone();
    }

    public static Builder custom() {
        return new Builder();
    }

    public static Builder copy(final CacheConfig config) {
        Args.notNull(config, "Cache config");
        return new Builder()
            .setMaxObjectSize(config.getMaxObjectSize())
            .setMaxCacheEntries(config.getMaxCacheEntries())
            .setMaxUpdateRetries(config.getMaxUpdateRetries())
            .setHeuristicCachingEnabled(config.isHeuristicCachingEnabled())
            .setHeuristicCoefficient(config.getHeuristicCoefficient())
            .setHeuristicDefaultLifetime(config.getHeuristicDefaultLifetime())
            .setSharedCache(config.isSharedCache())
            .setAsynchronousWorkers(config.getAsynchronousWorkers())
            .setNeverCacheHTTP10ResponsesWithQueryString(config.isNeverCacheHTTP10ResponsesWithQuery())
            .setNeverCacheHTTP11ResponsesWithQueryString(config.isNeverCacheHTTP11ResponsesWithQuery())
            .setStaleIfErrorEnabled(config.isStaleIfErrorEnabled());
    }

    public static class Builder {

        private long maxObjectSize;
        private int maxCacheEntries;
        private int maxUpdateRetries;
        private boolean heuristicCachingEnabled;
        private float heuristicCoefficient;
        private TimeValue heuristicDefaultLifetime;
        private boolean sharedCache;
        private boolean freshnessCheckEnabled;
        private int asynchronousWorkers;
        private boolean neverCacheHTTP10ResponsesWithQuery;
        private boolean neverCacheHTTP11ResponsesWithQuery;
        private boolean staleIfErrorEnabled;

        Builder() {
            this.maxObjectSize = DEFAULT_MAX_OBJECT_SIZE_BYTES;
            this.maxCacheEntries = DEFAULT_MAX_CACHE_ENTRIES;
            this.maxUpdateRetries = DEFAULT_MAX_UPDATE_RETRIES;
            this.heuristicCachingEnabled = DEFAULT_HEURISTIC_CACHING_ENABLED;
            this.heuristicCoefficient = DEFAULT_HEURISTIC_COEFFICIENT;
            this.heuristicDefaultLifetime = DEFAULT_HEURISTIC_LIFETIME;
            this.sharedCache = true;
            this.freshnessCheckEnabled = true;
            this.asynchronousWorkers = DEFAULT_ASYNCHRONOUS_WORKERS;
            this.staleIfErrorEnabled = false;
        }

        /**
         * Specifies the maximum response body size that will be eligible for caching.
         * @param maxObjectSize size in bytes
         */
        public Builder setMaxObjectSize(final long maxObjectSize) {
            this.maxObjectSize = maxObjectSize;
            return this;
        }

        /**
         * Sets the maximum number of cache entries the cache will retain.
         */
        public Builder setMaxCacheEntries(final int maxCacheEntries) {
            this.maxCacheEntries = maxCacheEntries;
            return this;
        }

        /**
         * Sets the number of times to retry a cache processChallenge on failure
         */
        public Builder setMaxUpdateRetries(final int maxUpdateRetries) {
            this.maxUpdateRetries = maxUpdateRetries;
            return this;
        }

        /**
         * @deprecated Has no effect. Do not use.
         */
        @Deprecated
        public Builder setAllow303Caching(final boolean allow303Caching) {
            return this;
        }

        /**
         * @deprecated No longer applicable. Do not use.
         */
        @Deprecated
        public Builder setWeakETagOnPutDeleteAllowed(final boolean weakETagOnPutDeleteAllowed) {
            return this;
        }

        /**
         * Enables or disables heuristic caching.
         * @param heuristicCachingEnabled should be {@code true} to
         *   permit heuristic caching, {@code false} to enable it.
         */
        public Builder setHeuristicCachingEnabled(final boolean heuristicCachingEnabled) {
            this.heuristicCachingEnabled = heuristicCachingEnabled;
            return this;
        }

        /**
         * Sets coefficient to be used in heuristic freshness caching. This is
         * interpreted as the fraction of the time between the {@code Last-Modified}
         * and {@code Date} headers of a cached response during which the cached
         * response will be considered heuristically fresh.
         * @param heuristicCoefficient should be between {@code 0.0} and
         *   {@code 1.0}.
         */
        public Builder setHeuristicCoefficient(final float heuristicCoefficient) {
            this.heuristicCoefficient = heuristicCoefficient;
            return this;
        }

        /**
         * Sets default lifetime to be used if heuristic freshness calculation
         * is not possible. Explicit cache control directives on either the
         * request or origin response will override this, as will the heuristic
         * {@code Last-Modified} freshness calculation if it is available.
         *
         * @param heuristicDefaultLifetime is the number to consider a
         *   cache-eligible response fresh in the absence of other information.
         *   Set this to {@code 0} to disable this style of heuristic caching.
         */
        public Builder setHeuristicDefaultLifetime(final TimeValue heuristicDefaultLifetime) {
            this.heuristicDefaultLifetime = heuristicDefaultLifetime;
            return this;
        }

        /**
         * Sets whether the cache should behave as a shared cache or not.
         * @param sharedCache true to behave as a shared cache, false to
         * behave as a non-shared (private) cache. To have the cache
         * behave like a browser cache, you want to set this to {@code false}.
         */
        public Builder setSharedCache(final boolean sharedCache) {
            this.sharedCache = sharedCache;
            return this;
        }

        /**
         * Sets the maximum number of threads to allow for background
         * revalidations due to the {@code stale-while-revalidate} directive.
         * @param asynchronousWorkers number of threads; a value of 0 disables background
         * revalidations.
         */
        public Builder setAsynchronousWorkers(final int asynchronousWorkers) {
            this.asynchronousWorkers = asynchronousWorkers;
            return this;
        }

        /**
         * Sets whether the cache should never cache HTTP 1.0 responses with a query string or not.
         * @param neverCacheHTTP10ResponsesWithQuery true to never cache responses with a query
         * string, false to cache if explicit cache headers are found.  Set this to {@code true}
         * to better emulate IE, which also never caches responses, regardless of what caching
         * headers may be present.
         */
        public Builder setNeverCacheHTTP10ResponsesWithQueryString(
                final boolean neverCacheHTTP10ResponsesWithQuery) {
            this.neverCacheHTTP10ResponsesWithQuery = neverCacheHTTP10ResponsesWithQuery;
            return this;
        }

        /**
         * Enables or disables the stale-if-error cache directive. If this option
         * is enabled, cached responses that have become stale due to an error (such
         * as a server error or a network failure) will be returned instead of
         * generating a new request. This can help to reduce the load on the origin
         * server and improve performance.
         * <p>
         * By default, the stale-if-error directive is disabled.
         *
         * @param enabled a boolean value indicating whether the stale-if-error
         *                directive should be enabled.
         * @return the builder object
         */
        public Builder setStaleIfErrorEnabled(final boolean enabled) {
            this.staleIfErrorEnabled = enabled;
            return this;
        }

        public Builder setFreshnessCheckEnabled(final boolean freshnessCheckEnabled) {
            this.freshnessCheckEnabled = freshnessCheckEnabled;
            return this;
        }

        /**
         * Sets the flag indicating whether HTTP/1.1 responses with a query string should never be cached.
         *
         * @param neverCacheHTTP11ResponsesWithQuery whether to never cache HTTP/1.1 responses with a query string
         * @return the builder object
         */
        public Builder setNeverCacheHTTP11ResponsesWithQueryString(
                final boolean neverCacheHTTP11ResponsesWithQuery) {
            this.neverCacheHTTP11ResponsesWithQuery = neverCacheHTTP11ResponsesWithQuery;
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(
                    maxObjectSize,
                    maxCacheEntries,
                    maxUpdateRetries,
                    heuristicCachingEnabled,
                    heuristicCoefficient,
                    heuristicDefaultLifetime,
                    sharedCache,
                    freshnessCheckEnabled,
                    asynchronousWorkers,
                    neverCacheHTTP10ResponsesWithQuery,
                    neverCacheHTTP11ResponsesWithQuery,
                    staleIfErrorEnabled);
        }

    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[maxObjectSize=").append(this.maxObjectSize)
                .append(", maxCacheEntries=").append(this.maxCacheEntries)
                .append(", maxUpdateRetries=").append(this.maxUpdateRetries)
                .append(", heuristicCachingEnabled=").append(this.heuristicCachingEnabled)
                .append(", heuristicCoefficient=").append(this.heuristicCoefficient)
                .append(", heuristicDefaultLifetime=").append(this.heuristicDefaultLifetime)
                .append(", sharedCache=").append(this.sharedCache)
                .append(", freshnessCheckEnabled=").append(this.freshnessCheckEnabled)
                .append(", asynchronousWorkers=").append(this.asynchronousWorkers)
                .append(", neverCacheHTTP10ResponsesWithQuery=").append(this.neverCacheHTTP10ResponsesWithQuery)
                .append(", neverCacheHTTP11ResponsesWithQuery=").append(this.neverCacheHTTP11ResponsesWithQuery)
                .append(", staleIfErrorEnabled=").append(this.staleIfErrorEnabled)
                .append("]");
        return builder.toString();
    }

}
