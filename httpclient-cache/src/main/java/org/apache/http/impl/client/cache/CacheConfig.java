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

/**
 * <p>Java Beans-style configuration for a {@link CachingHttpClient}. Any class
 * in the caching module that has configuration options should take a
 * {@link CacheConfig} argument in one of its constructors. A
 * {@code CacheConfig} instance has sane and conservative defaults, so the
 * easiest way to specify options is to get an instance and then set just
 * the options you want to modify from their defaults.</p>
 * 
 * <p><b>N.B.</b> This class is only for caching-specific configuration; to
 * configure the behavior of the rest of the client, configure the 
 * {@link org.apache.http.client.HttpClient} used as the &quot;backend&quot;
 * for the {@code CachingHttpClient}.</p>
 * 
 * <p>Cache configuration can be grouped into the following categories:</p>
 * 
 * <p><b>Cache size.</b> If the backend storage supports these limits, you
 * can specify the {@link CacheConfig#setMaxCacheEntries maximum number of
 * cache entries} as well as the {@link CacheConfig#setMaxObjectSizeBytes
 * maximum cacheable response body size}.</p>
 * 
 * <p><b>Public/private caching.</b> By default, the caching module considers
 * itself to be a shared (public) cache, and will not, for example, cache
 * responses to requests with {@code Authorization} headers or responses
 * marked with {@code Cache-Control: private}. If, however, the cache
 * is only going to be used by one logical "user" (behaving similarly to a
 * browser cache), then you will want to {@link
 * CacheConfig#setSharedCache(boolean) turn off the shared cache setting}.</p>
 * 
 * <p><b>Heuristic caching</b>. Per RFC2616, a cache may cache certain cache
 * entries even if no explicit cache control headers are set by the origin.
 * This behavior is off by default, but you may want to turn this on if you
 * are working with an origin that doesn't set proper headers but where you
 * still want to cache the responses. You will want to {@link
 * CacheConfig#setHeuristicCachingEnabled(boolean) enable heuristic caching},
 * then specify either a {@link CacheConfig#setHeuristicDefaultLifetime(long)
 * default freshness lifetime} and/or a {@link
 * CacheConfig#setHeuristicCoefficient(float) fraction of the time since
 * the resource was last modified}. See Sections
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.2.2">
 * 13.2.2</a> and <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.2.4">
 * 13.2.4</a> of the HTTP/1.1 RFC for more details on heuristic caching.</p>
 * 
 * <p><b>Background validation</b>. The cache module supports the
 * {@code stale-while-revalidate} directive of
 * <a href="http://tools.ietf.org/html/rfc5861">RFC5861</a>, which allows
 * certain cache entry revalidations to happen in the background. You may
 * want to tweak the settings for the {@link
 * CacheConfig#setAsynchronousWorkersCore(int) minimum} and {@link
 * CacheConfig#setAsynchronousWorkersMax(int) maximum} number of background
 * worker threads, as well as the {@link
 * CacheConfig#setAsynchronousWorkerIdleLifetimeSecs(int) maximum time they
 * can be idle before being reclaimed}. You can also control the {@link
 * CacheConfig#setRevalidationQueueSize(int) size of the queue} used for
 * revalidations when there aren't enough workers to keep up with demand.</b> 
 */
public class CacheConfig {

    /** Default setting for the maximum object size that will be
     * cached, in bytes.
     */
    public final static int DEFAULT_MAX_OBJECT_SIZE_BYTES = 8192;

    /** Default setting for the maximum number of cache entries
     * that will be retained.
     */
    public final static int DEFAULT_MAX_CACHE_ENTRIES = 1000;

    /** Default setting for the number of retries on a failed
     * cache update
     */
    public final static int DEFAULT_MAX_UPDATE_RETRIES = 1;

    /** Default setting for heuristic caching
     */
    public final static boolean DEFAULT_HEURISTIC_CACHING_ENABLED = false;

    /** Default coefficient used to heuristically determine freshness
     * lifetime from the Last-Modified time of a cache entry.
     */
    public final static float DEFAULT_HEURISTIC_COEFFICIENT = 0.1f;

    /** Default lifetime in seconds to be assumed when we cannot calculate
     * freshness heuristically.
     */
    public final static long DEFAULT_HEURISTIC_LIFETIME = 0;

    /** Default number of worker threads to allow for background revalidations
     * resulting from the stale-while-revalidate directive.
     */
    public static final int DEFAULT_ASYNCHRONOUS_WORKERS_MAX = 1; 

    /** Default minimum number of worker threads to allow for background
     * revalidations resulting from the stale-while-revalidate directive.
     */
    public static final int DEFAULT_ASYNCHRONOUS_WORKERS_CORE = 1;
    
    /** Default maximum idle lifetime for a background revalidation thread
     * before it gets reclaimed.
     */
    public static final int DEFAULT_ASYNCHRONOUS_WORKER_IDLE_LIFETIME_SECS = 60;
    
    /** Default maximum queue length for background revalidation requests. 
     */
    public static final int DEFAULT_REVALIDATION_QUEUE_SIZE = 100;
    
    private int maxObjectSizeBytes = DEFAULT_MAX_OBJECT_SIZE_BYTES;
    private int maxCacheEntries = DEFAULT_MAX_CACHE_ENTRIES;
    private int maxUpdateRetries = DEFAULT_MAX_UPDATE_RETRIES;
    private boolean heuristicCachingEnabled = false;
    private float heuristicCoefficient = DEFAULT_HEURISTIC_COEFFICIENT;
    private long heuristicDefaultLifetime = DEFAULT_HEURISTIC_LIFETIME;
    private boolean isSharedCache = true;
    private int asynchronousWorkersMax = DEFAULT_ASYNCHRONOUS_WORKERS_MAX;
    private int asynchronousWorkersCore = DEFAULT_ASYNCHRONOUS_WORKERS_CORE;
    private int asynchronousWorkerIdleLifetimeSecs = DEFAULT_ASYNCHRONOUS_WORKER_IDLE_LIFETIME_SECS;
    private int revalidationQueueSize = DEFAULT_REVALIDATION_QUEUE_SIZE;

    /**
     * Returns the current maximum response body size that will be cached.
     * @return size in bytes
     */
    public int getMaxObjectSizeBytes() {
        return maxObjectSizeBytes;
    }

    /**
     * Specifies the maximum response body size that will be eligible for caching.
     * @param maxObjectSizeBytes size in bytes
     */
    public void setMaxObjectSizeBytes(int maxObjectSizeBytes) {
        this.maxObjectSizeBytes = maxObjectSizeBytes;
    }

    /**
     * Returns whether the cache will behave as a shared cache or not.
     * @return {@code true} for a shared cache, {@code false} for a non-
     * shared (private) cache
     */
    public boolean isSharedCache() {
        return isSharedCache;
    }

    /**
     * Sets whether the cache should behave as a shared cache or not.
     * @param isSharedCache true to behave as a shared cache, false to
     * behave as a non-shared (private) cache. To have the cache
     * behave like a browser cache, you want to set this to {@code false}.
     */
    public void setSharedCache(boolean isSharedCache) {
        this.isSharedCache = isSharedCache;
    }

    /**
     * Returns the maximum number of cache entries the cache will retain.
     */
    public int getMaxCacheEntries() {
        return maxCacheEntries;
    }

    /**
     * Sets the maximum number of cache entries the cache will retain.
     */
    public void setMaxCacheEntries(int maxCacheEntries) {
        this.maxCacheEntries = maxCacheEntries;
    }

    /**
     * Returns the number of times to retry a cache update on failure
     */
    public int getMaxUpdateRetries(){
        return maxUpdateRetries;
    }

    /**
     * Sets the number of times to retry a cache update on failure
     */
    public void setMaxUpdateRetries(int maxUpdateRetries){
        this.maxUpdateRetries = maxUpdateRetries;
    }

    /**
     * Returns whether heuristic caching is enabled.
     * @return {@code true} if it is enabled.
     */
    public boolean isHeuristicCachingEnabled() {
        return heuristicCachingEnabled;
    }

    /**
     * Enables or disables heuristic caching.
     * @param heuristicCachingEnabled should be {@code true} to
     *   permit heuristic caching, {@code false} to enable it.
     */
    public void setHeuristicCachingEnabled(boolean heuristicCachingEnabled) {
        this.heuristicCachingEnabled = heuristicCachingEnabled;
    }

    /**
     * Returns lifetime coefficient used in heuristic freshness caching.
     */
    public float getHeuristicCoefficient() {
        return heuristicCoefficient;
    }

    /**
     * Sets coefficient to be used in heuristic freshness caching. This is
     * interpreted as the fraction of the time between the {@code Last-Modified}
     * and {@code Date} headers of a cached response during which the cached
     * response will be considered heuristically fresh.
     * @param heuristicCoefficient should be between {@code 0.0} and
     *   {@code 1.0}. 
     */
    public void setHeuristicCoefficient(float heuristicCoefficient) {
        this.heuristicCoefficient = heuristicCoefficient;
    }

    /**
     * Get the default lifetime to be used if heuristic freshness calculation is
     * not possible.
     */
    public long getHeuristicDefaultLifetime() {
        return heuristicDefaultLifetime;
    }

    /**
     * Sets default lifetime in seconds to be used if heuristic freshness
     * calculation is not possible. Explicit cache control directives on
     * either the request or origin response will override this, as will
     * the heuristic {@code Last-Modified} freshness calculation if it is
     * available. 
     * @param heuristicDefaultLifetimeSecs is the number of seconds to
     *   consider a cache-eligible response fresh in the absence of other
     *   information. Set this to {@code 0} to disable this style of
     *   heuristic caching.
     */
    public void setHeuristicDefaultLifetime(long heuristicDefaultLifetimeSecs) {
        this.heuristicDefaultLifetime = heuristicDefaultLifetimeSecs;
    }

    /**
     * Returns the maximum number of threads to allow for background
     * revalidations due to the {@code stale-while-revalidate} directive. A
     * value of 0 means background revalidations are disabled.
     */
    public int getAsynchronousWorkersMax() {
        return asynchronousWorkersMax;
    }

    /**
     * Sets the maximum number of threads to allow for background
     * revalidations due to the {@code stale-while-revalidate} directive. 
     * @param max number of threads; a value of 0 disables background
     * revalidations. 
     */
    public void setAsynchronousWorkersMax(int max) {
        this.asynchronousWorkersMax = max;
    }

    /**
     * Returns the minimum number of threads to keep alive for background
     * revalidations due to the {@code stale-while-revalidate} directive. 
     */
    public int getAsynchronousWorkersCore() {
        return asynchronousWorkersCore;
    }

    /**
     * Sets the minimum number of threads to keep alive for background
     * revalidations due to the {@code stale-while-revalidate} directive.
     * @param min should be greater than zero and less than or equal
     *   to <code>getAsynchronousWorkersMax()</code> 
     */
    public void setAsynchronousWorkersCore(int min) {
        this.asynchronousWorkersCore = min;
    }

    /**
     * Returns the current maximum idle lifetime in seconds for a
     * background revalidation worker thread. If a worker thread is idle
     * for this long, and there are more than the core number of worker
     * threads alive, the worker will be reclaimed.
     */
    public int getAsynchronousWorkerIdleLifetimeSecs() {
        return asynchronousWorkerIdleLifetimeSecs;
    }

    /**
     * Sets the current maximum idle lifetime in seconds for a
     * background revalidation worker thread. If a worker thread is idle
     * for this long, and there are more than the core number of worker
     * threads alive, the worker will be reclaimed.
     * @param secs idle lifetime in seconds
     */
    public void setAsynchronousWorkerIdleLifetimeSecs(int secs) {
        this.asynchronousWorkerIdleLifetimeSecs = secs;
    }

    /**
     * Returns the current maximum queue size for background revalidations.
     */
    public int getRevalidationQueueSize() {
        return revalidationQueueSize;
    }

    /**
     * Sets the current maximum queue size for background revalidations.
     */
    public void setRevalidationQueueSize(int size) {
        this.revalidationQueueSize = size;
    }

    
}
