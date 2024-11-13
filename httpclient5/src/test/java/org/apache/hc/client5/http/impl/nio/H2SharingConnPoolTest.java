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

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class H2SharingConnPoolTest {

    static final String DEFAULT_ROUTE = "DEFAULT_ROUTE";

    @Mock
    ManagedConnPool<String, HttpConnection> connPool;
    @Mock
    FutureCallback<PoolEntry<String, HttpConnection>> callback;
    @Mock
    HttpConnection connection;
    H2SharingConnPool<String, HttpConnection> h2SharingPool;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        h2SharingPool = new H2SharingConnPool<>(connPool);
    }

    @Test
    void testLeaseFutureReturned() throws Exception {
        Mockito.when(connPool.lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenReturn(new BasicFuture<>(null));

        final Future<PoolEntry<String, HttpConnection>> result = h2SharingPool.lease(DEFAULT_ROUTE, null, Timeout.ONE_MILLISECOND, callback);
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isDone());

        Mockito.verify(connPool).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.eq(null),
                Mockito.eq(Timeout.ONE_MILLISECOND),
                Mockito.any());
        Mockito.verify(callback, Mockito.never()).completed(
                Mockito.any());
    }

    @Test
    void testLeaseExistingConnectionReturned() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);

        final Future<PoolEntry<String, HttpConnection>> future = h2SharingPool.lease(DEFAULT_ROUTE, null, Timeout.ONE_MILLISECOND, callback);
        Assertions.assertNotNull(future);
        Assertions.assertSame(poolEntry, future.get());

        Mockito.verify(connPool, Mockito.never()).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any());
        Mockito.verify(callback).completed(
                Mockito.same(poolEntry));
    }

    @Test
    void testLeaseWithStateCacheBypassed() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);

        Mockito.when(connPool.lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenReturn(new BasicFuture<>(null));

        final Future<PoolEntry<String, HttpConnection>> result = h2SharingPool.lease(DEFAULT_ROUTE, "stuff", Timeout.ONE_MILLISECOND, callback);
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isDone());

        Mockito.verify(connPool).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.eq("stuff"),
                Mockito.eq(Timeout.ONE_MILLISECOND),
                Mockito.any());
        Mockito.verify(callback, Mockito.never()).completed(
                Mockito.any());
    }

    @Test
    void testLeaseNewConnectionReturnedAndCached() throws Exception {
        final AtomicReference<BasicFuture<PoolEntry<String, HttpConnection>>> futureRef = new AtomicReference<>();
        Mockito.when(connPool.lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenAnswer(invocationOnMock -> {
                    final BasicFuture<PoolEntry<String, HttpConnection>> future = new BasicFuture<>(invocationOnMock.getArgument(3));
                    futureRef.set(future);
                    return future;
                });

        final Future<PoolEntry<String, HttpConnection>> result = h2SharingPool.lease(DEFAULT_ROUTE, null, Timeout.ONE_MILLISECOND, callback);
        final BasicFuture<PoolEntry<String, HttpConnection>> future = futureRef.get();
        Assertions.assertNotNull(future);

        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.getProtocolVersion()).thenReturn(HttpVersion.HTTP_2);
        future.completed(poolEntry);

        Assertions.assertTrue(result.isDone());

        Mockito.verify(connPool).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.eq(null),
                Mockito.eq(Timeout.ONE_MILLISECOND),
                Mockito.any());
        Mockito.verify(callback).completed(
                Mockito.any());

        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        Assertions.assertEquals(1, routePool.getCount(poolEntry));
    }

    @Test
    void testLeaseNewConnectionReturnedAndNotCached() throws Exception {
        final AtomicReference<BasicFuture<PoolEntry<String, HttpConnection>>> futureRef = new AtomicReference<>();
        Mockito.when(connPool.lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenAnswer(invocationOnMock -> {
            final BasicFuture<PoolEntry<String, HttpConnection>> future = new BasicFuture<>(invocationOnMock.getArgument(3));
            futureRef.set(future);
            return future;
        });

        final Future<PoolEntry<String, HttpConnection>> result = h2SharingPool.lease(DEFAULT_ROUTE, null, Timeout.ONE_MILLISECOND, callback);
        final BasicFuture<PoolEntry<String, HttpConnection>> future = futureRef.get();
        Assertions.assertNotNull(future);

        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.getProtocolVersion()).thenReturn(HttpVersion.HTTP_1_1);
        future.completed(poolEntry);

        Assertions.assertTrue(result.isDone());

        Mockito.verify(connPool).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.eq(null),
                Mockito.eq(Timeout.ONE_MILLISECOND),
                Mockito.any());
        Mockito.verify(callback).completed(
                Mockito.any());

        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        Assertions.assertEquals(0, routePool.getCount(poolEntry));
    }

    @Test
    void testLeaseNoConnection() throws Exception {
        final AtomicReference<BasicFuture<PoolEntry<String, HttpConnection>>> futureRef = new AtomicReference<>();
        Mockito.when(connPool.lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenAnswer(invocationOnMock -> {
            final BasicFuture<PoolEntry<String, HttpConnection>> future = new BasicFuture<>(invocationOnMock.getArgument(3));
            futureRef.set(future);
            return future;
        });

        final Future<PoolEntry<String, HttpConnection>> result = h2SharingPool.lease(DEFAULT_ROUTE, null, Timeout.ONE_MILLISECOND, callback);
        final BasicFuture<PoolEntry<String, HttpConnection>> future = futureRef.get();
        Assertions.assertNotNull(future);

        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.discardConnection(CloseMode.IMMEDIATE);
        future.completed(poolEntry);

        Assertions.assertTrue(result.isDone());

        Mockito.verify(connPool).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.eq(null),
                Mockito.eq(Timeout.ONE_MILLISECOND),
                Mockito.any());
        Mockito.verify(callback).completed(
                Mockito.any());

        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        Assertions.assertEquals(0, routePool.getCount(poolEntry));
    }

    @Test
    void testLeaseWithStateNewConnectionReturnedAndNotCached() throws Exception {
        final AtomicReference<BasicFuture<PoolEntry<String, HttpConnection>>> futureRef = new AtomicReference<>();
        Mockito.when(connPool.lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.any(),
                Mockito.any(),
                Mockito.any())).thenAnswer(invocationOnMock -> {
            final BasicFuture<PoolEntry<String, HttpConnection>> future = new BasicFuture<>(invocationOnMock.getArgument(3));
            futureRef.set(future);
            return future;
        });

        final Future<PoolEntry<String, HttpConnection>> result = h2SharingPool.lease(DEFAULT_ROUTE, "stuff", Timeout.ONE_MILLISECOND, callback);
        final BasicFuture<PoolEntry<String, HttpConnection>> future = futureRef.get();
        Assertions.assertNotNull(future);

        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.getProtocolVersion()).thenReturn(HttpVersion.HTTP_2);
        future.completed(poolEntry);

        Assertions.assertTrue(result.isDone());

        Mockito.verify(connPool).lease(
                Mockito.eq(DEFAULT_ROUTE),
                Mockito.eq("stuff"),
                Mockito.eq(Timeout.ONE_MILLISECOND),
                Mockito.any());
        Mockito.verify(callback).completed(
                Mockito.any());

        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        Assertions.assertEquals(0, routePool.getCount(poolEntry));
    }

    @Test
    void testReleaseReusableNoCacheReturnedToPool() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.isOpen()).thenReturn(true);

        h2SharingPool.release(poolEntry, true);

        Mockito.verify(connPool).release(
                Mockito.same(poolEntry),
                Mockito.eq(true));
    }

    @Test
    void testReleaseReusableNotInCacheReturnedToPool() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.isOpen()).thenReturn(true);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);

        h2SharingPool.release(poolEntry, true);

        Mockito.verify(connPool).release(
                Mockito.same(poolEntry),
                Mockito.eq(true));
    }

    @Test
    void testReleaseReusableInCacheNotReturnedToPool() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.isOpen()).thenReturn(true);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);
        routePool.track(poolEntry);

        h2SharingPool.release(poolEntry, true);

        Mockito.verify(connPool, Mockito.never()).release(
                Mockito.same(poolEntry),
                Mockito.anyBoolean());
    }

    @Test
    void testReleaseNonReusableInCacheReturnedToPool() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.isOpen()).thenReturn(true);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);
        routePool.track(poolEntry);

        h2SharingPool.release(poolEntry, false);

        Mockito.verify(connPool).release(
                Mockito.same(poolEntry),
                Mockito.eq(false));
    }

    @Test
    void testReleaseReusableAndClosedInCacheReturnedToPool() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.isOpen()).thenReturn(false);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);
        routePool.track(poolEntry);

        h2SharingPool.release(poolEntry, true);

        Mockito.verify(connPool).release(
                Mockito.same(poolEntry),
                Mockito.eq(true));
    }

    @Test
    void testClose() throws Exception {
        h2SharingPool.close();

        Mockito.verify(connPool).close();
    }

    @Test
    void testCloseMode() throws Exception {
        h2SharingPool.close(CloseMode.IMMEDIATE);

        Mockito.verify(connPool).close(CloseMode.IMMEDIATE);
    }

    @Test
    void testLeasePoolClosed() throws Exception {
        h2SharingPool.close();

        Assertions.assertThrows(IllegalStateException.class, () -> h2SharingPool.lease(DEFAULT_ROUTE, null, Timeout.ONE_MILLISECOND, callback));
    }

    @Test
    void testReleasePoolClosed() throws Exception {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>(DEFAULT_ROUTE);
        poolEntry.assignConnection(connection);
        Mockito.when(connection.isOpen()).thenReturn(false);
        final H2SharingConnPool.PerRoutePool<String, HttpConnection> routePool = h2SharingPool.getPerRoutePool(DEFAULT_ROUTE);
        routePool.track(poolEntry);

        h2SharingPool.close();

        h2SharingPool.release(poolEntry, true);

        Mockito.verify(connPool).release(
                Mockito.same(poolEntry),
                Mockito.eq(true));
    }

}
