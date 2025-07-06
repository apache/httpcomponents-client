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

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class H2SharingPerRoutePoolTest {

    static PoolEntry<String, HttpConnection> createMockEntry() {
        final PoolEntry<String, HttpConnection> poolEntry = new PoolEntry<>("some route");
        final HttpConnection conn = Mockito.mock(HttpConnection.class);
        Mockito.when(conn.isOpen()).thenReturn(true);
        poolEntry.assignConnection(conn);
        return poolEntry;
    }

    H2SharingConnPool.PerRoutePool<String, HttpConnection> pool;
    PoolEntry<String, HttpConnection> poolEntry1;
    PoolEntry<String, HttpConnection> poolEntry2;

    @BeforeEach
    void setup() {
        pool = new H2SharingConnPool.PerRoutePool<>();
        poolEntry1 = createMockEntry();
        poolEntry2 = createMockEntry();
    }

    @Test
    void testKeep() {
        Assertions.assertEquals(1, pool.track(poolEntry1));
        Assertions.assertEquals(2, pool.track(poolEntry1));
        Assertions.assertEquals(1, pool.track(poolEntry2));
        Assertions.assertEquals(3, pool.track(poolEntry1));
    }

    @Test
    void testLeaseLeastUsed() {
        pool.track(poolEntry1);
        pool.track(poolEntry1);
        pool.track(poolEntry2);
        Assertions.assertSame(poolEntry2, pool.lease());
        Assertions.assertEquals(2, pool.getCount(poolEntry2));

        final PoolEntry<String, HttpConnection> poolEntry = pool.lease();
        Assertions.assertEquals(3, pool.getCount(poolEntry));
    }

    @Test
    void testLeaseEmptyPool() {
        Assertions.assertNull(pool.lease());
    }

    @Test
    void testReleaseReusable() {
        pool.track(poolEntry1);
        pool.track(poolEntry1);
        pool.track(poolEntry1);

        Assertions.assertEquals(2, pool.release(poolEntry1, true));
        Assertions.assertEquals(1, pool.release(poolEntry1, true));
        Assertions.assertEquals(0, pool.release(poolEntry1, true));
        Assertions.assertEquals(0, pool.release(poolEntry1, true));
    }

    @Test
    void testReleaseNonReusable() {
        pool.track(poolEntry1);
        pool.track(poolEntry1);
        pool.track(poolEntry1);

        Assertions.assertEquals(2, pool.release(poolEntry1, false)); // 3 → 2
    }

    @Test
    void testReleaseNonPresent() {
        Assertions.assertEquals(0, pool.release(poolEntry1, true));
        Assertions.assertEquals(0, pool.release(poolEntry2, true));
    }

    @Test
    void testReleaseConnectionClosed() {
        pool.track(poolEntry1);
        pool.track(poolEntry1);
        pool.track(poolEntry1);

        Mockito.when(poolEntry1.getConnection().isOpen()).thenReturn(false);
        Assertions.assertEquals(2, pool.release(poolEntry1, true));  // 3 → 2
    }

    @Test
    void testReleaseConnectionMissing() {
        pool.track(poolEntry1);
        pool.track(poolEntry1);
        pool.track(poolEntry1);

        poolEntry1.discardConnection(CloseMode.IMMEDIATE);
        Assertions.assertEquals(2, pool.release(poolEntry1, true));  // 3 → 2
    }

}
