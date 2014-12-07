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

package org.apache.http.impl.client;

import java.util.concurrent.TimeUnit;

import org.apache.http.conn.HttpClientConnectionManager;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link org.apache.http.impl.client.IdleConnectionEvictor}.
 */
public class TestIdleConnectionEvictor {

    @Test
    public void testEvictExpiredAndIdle() throws Exception {
        final HttpClientConnectionManager cm = Mockito.mock(HttpClientConnectionManager.class);
        final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(cm,
                500, TimeUnit.MILLISECONDS, 3, TimeUnit.SECONDS);
        connectionEvictor.start();

        Thread.sleep(1000);

        Mockito.verify(cm, Mockito.atLeast(1)).closeExpiredConnections();
        Mockito.verify(cm, Mockito.atLeast(1)).closeIdleConnections(3000, TimeUnit.MILLISECONDS);

        Assert.assertTrue(connectionEvictor.isRunning());

        connectionEvictor.shutdown();
        connectionEvictor.awaitTermination(1, TimeUnit.SECONDS);
        Assert.assertFalse(connectionEvictor.isRunning());
    }

    @Test
    public void testEvictExpiredOnly() throws Exception {
        final HttpClientConnectionManager cm = Mockito.mock(HttpClientConnectionManager.class);
        final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(cm,
                500, TimeUnit.MILLISECONDS, 0, TimeUnit.SECONDS);
        connectionEvictor.start();

        Thread.sleep(1000);

        Mockito.verify(cm, Mockito.atLeast(1)).closeExpiredConnections();
        Mockito.verify(cm, Mockito.never()).closeIdleConnections(Mockito.anyLong(), Mockito.<TimeUnit>any());

        Assert.assertTrue(connectionEvictor.isRunning());

        connectionEvictor.shutdown();
        connectionEvictor.awaitTermination(1, TimeUnit.SECONDS);
        Assert.assertFalse(connectionEvictor.isRunning());
    }

}
