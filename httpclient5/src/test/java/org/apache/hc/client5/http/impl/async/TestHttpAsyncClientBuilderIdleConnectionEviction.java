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

package org.apache.hc.client5.http.impl.async;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestHttpAsyncClientBuilderIdleConnectionEviction {

    @Test
    void testCustomIdleConnectionEvictionSleepTime() throws Exception {
        final CountDownLatch eviction = new CountDownLatch(1);
        final AsyncClientConnectionManager connectionManager = Mockito.mock(
                AsyncClientConnectionManager.class,
                Mockito.withSettings().extraInterfaces(ConnPoolControl.class));
        final ConnPoolControl<?> connPoolControl = (ConnPoolControl<?>) connectionManager;

        final TimeValue sleepTime = TimeValue.ofMilliseconds(100);
        final TimeValue maxIdleTime = TimeValue.ofHours(1);

        Mockito.doAnswer(invocation -> {
            eviction.countDown();
            return null;
        }).when(connPoolControl).closeIdle(maxIdleTime);

        try (final CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(sleepTime, maxIdleTime)
                .build()) {
            Assertions.assertTrue(
                    eviction.await(3, TimeUnit.SECONDS),
                    "Custom eviction sleep time was not propagated to IdleConnectionEvictor");
        }
    }

    @Test
    void testLegacyIdleConnectionEvictionRestoresDefaultSleepTime() throws Exception {
        final CountDownLatch eviction = new CountDownLatch(1);
        final AsyncClientConnectionManager connectionManager = Mockito.mock(
                AsyncClientConnectionManager.class,
                Mockito.withSettings().extraInterfaces(ConnPoolControl.class));
        final ConnPoolControl<?> connPoolControl = (ConnPoolControl<?>) connectionManager;

        final TimeValue maxIdleTime = TimeValue.ofSeconds(10);

        Mockito.doAnswer(invocation -> {
            eviction.countDown();
            return null;
        }).when(connPoolControl).closeIdle(maxIdleTime);

        try (final CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(TimeValue.ofHours(1), TimeValue.ofHours(1))
                .evictIdleConnections(maxIdleTime)
                .build()) {
            Assertions.assertTrue(
                    eviction.await(5, TimeUnit.SECONDS),
                    "Legacy overload did not restore the calculated eviction sleep time");
        }
    }

}

