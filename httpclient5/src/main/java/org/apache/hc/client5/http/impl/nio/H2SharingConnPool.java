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
package org.apache.hc.client5.http.impl.nio;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.CompletedFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental connections pool implementation that acts as a caching facade in front of
 * a standard {@link ManagedConnPool} and shares already leased connections to multiplex
 * message exchanges over active HTTP/2 connections.
 * @param <T> route
 * @param <C> connection object
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.SAFE)
@Experimental
public class H2SharingConnPool<T, C extends HttpConnection> implements ManagedConnPool<T, C> {

    private static final Logger LOG = LoggerFactory.getLogger(H2SharingConnPool.class);

    private final ManagedConnPool<T, C> pool;
    private final ConcurrentMap<T, PerRoutePool<T, C>> perRouteCache;
    private final AtomicBoolean closed;

    public H2SharingConnPool(final ManagedConnPool<T, C> pool) {
        this.pool = Args.notNull(pool, "Connection pool");
        this.perRouteCache = new ConcurrentHashMap<>();
        this.closed = new AtomicBoolean();
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (closed.compareAndSet(false, true)) {
            perRouteCache.clear();
            pool.close(closeMode);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            perRouteCache.clear();
            pool.close();
        }
    }

    PerRoutePool<T, C> getPerRoutePool(final T route) {
        return perRouteCache.computeIfAbsent(route, r -> new PerRoutePool<>());
    }

    @Override
    public Future<PoolEntry<T, C>> lease(final T route,
                                         final Object state,
                                         final Timeout requestTimeout,
                                         final FutureCallback<PoolEntry<T, C>> callback) {
        Asserts.check(!closed.get(), "Connection pool shut down");
        if (state == null) {
            final PerRoutePool<T, C> perRoutePool = perRouteCache.get(route);
            if (perRoutePool != null) {
                final PoolEntry<T, C> entry = perRoutePool.lease();
                if (entry != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Sharing connection {} for message exchange multiplexing (lease count = {})",
                                ConnPoolSupport.getId(entry.getConnection()), perRoutePool.getCount(entry));
                    }
                    final Future<PoolEntry<T, C>> future = new CompletedFuture<>(entry);
                    if (callback != null) {
                        callback.completed(entry);
                    }
                    return future;
                }
            }
        }
        LOG.debug("No shared connection available");
        return pool.lease(route,
                state,
                requestTimeout,
                new CallbackContribution<PoolEntry<T, C>>(callback) {

                    @Override
                    public void completed(final PoolEntry<T, C> entry) {
                        if (state == null) {
                            final C connection = entry.getConnection();
                            final ProtocolVersion ver = connection != null ? connection.getProtocolVersion() : null;
                            if (ver == HttpVersion.HTTP_2_0) {
                                final PerRoutePool<T, C> perRoutePool = getPerRoutePool(route);
                                final long count = perRoutePool.track(entry);
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Connection {} can be shared for message exchange multiplexing (lease count = {})",
                                            ConnPoolSupport.getId(entry.getConnection()), count);
                                }
                            }
                        }
                        if (callback != null) {
                            callback.completed(entry);
                        }
                    }

                });
    }

    @Override
    public void release(final PoolEntry<T, C> entry, final boolean reusable) {
        if (entry == null) {
            return;
        }
        if (closed.get()) {
            pool.release(entry, reusable);
            return;
        }
        final T route = entry.getRoute();
        final PerRoutePool<T, C> perRoutePool = perRouteCache.get(route);
        if (perRoutePool != null) {
            final long count = perRoutePool.release(entry, reusable);
            if (count > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Connection {} is being shared for message exchange multiplexing (lease count = {})",
                            ConnPoolSupport.getId(entry.getConnection()), count);
                }
                return;
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Releasing connection {} back to the pool", ConnPoolSupport.getId(entry.getConnection()));
        }
        pool.release(entry, reusable);
    }

    @Override
    public void setMaxTotal(final int max) {
        pool.setMaxTotal(max);
    }

    @Override
    public int getMaxTotal() {
        return pool.getMaxTotal();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        pool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return pool.getDefaultMaxPerRoute();
    }

    @Override
    public void setMaxPerRoute(final T route, final int max) {
        pool.setMaxPerRoute(route, max);
    }

    @Override
    public int getMaxPerRoute(final T route) {
        return pool.getMaxPerRoute(route);
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        pool.closeIdle(idleTime);
    }

    @Override
    public void closeExpired() {
        pool.closeExpired();
    }

    @Override
    public Set<T> getRoutes() {
        return pool.getRoutes();
    }

    @Override
    public PoolStats getTotalStats() {
        return pool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final T route) {
        return pool.getStats(route);
    }

    @Override
    public String toString() {
        return pool.toString();
    }

    static class PerRoutePool<T, C extends HttpConnection> {

        private final Map<PoolEntry<T, C>, AtomicLong> entryMap;
        private final Lock lock;

        PerRoutePool() {
            this.entryMap = new HashMap<>();
            this.lock = new ReentrantLock();
        }

        AtomicLong getCounter(final PoolEntry<T, C> entry) {
            return entryMap.computeIfAbsent(entry, e -> new AtomicLong());
        }

        long track(final PoolEntry<T, C> entry) {
            lock.lock();
            try {
                final AtomicLong counter = getCounter(entry);
                return counter.incrementAndGet();
            } finally {
                lock.unlock();
            }
        }

        PoolEntry<T, C> lease() {
            lock.lock();
            try {
                final PoolEntry<T, C> entry = entryMap.entrySet().stream()
                        .min(Comparator.comparingLong(e -> e.getValue().get()))
                        .map(Map.Entry::getKey)
                        .orElse(null);
                if (entry == null) {
                    return null;
                }
                final AtomicLong counter = getCounter(entry);
                counter.incrementAndGet();
                return entry;
            } finally {
                lock.unlock();
            }
        }

        long release(final PoolEntry<T, C> entry, final boolean reusable) {
            lock.lock();
            try {
                final C connection = entry.getConnection();
                if (!reusable || connection == null || !connection.isOpen()) {
                    entryMap.remove(entry);
                    return 0;
                } else {
                    final AtomicLong counter = entryMap.compute(entry, (e, c) -> {
                        if (c == null) {
                            return null;
                        }
                        final long count = c.decrementAndGet();
                        return count > 0 ? c : null;
                    });
                    return counter != null ? counter.get() : 0L;
                }
            } finally {
                lock.unlock();
            }
        }

        long getCount(final PoolEntry<T, C> entry) {
            lock.lock();
            try {
                final AtomicLong counter = entryMap.get(entry);
                return counter == null ? 0L : counter.get();
            } finally {
                lock.unlock();
            }
        }

    }

}
