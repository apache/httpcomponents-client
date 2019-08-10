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

package org.apache.hc.client5.http.impl;

import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Unit tests for {@link IdleConnectionEvictor}.
 */
public class TestIdleConnectionEvictor {

    @Test
    public void testEvictExpiredAndIdle() throws Exception {
        final ConnPoolControl<?> cm = Mockito.mock(ConnPoolControl.class);
        final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(cm,
                TimeValue.ofMilliseconds(500), TimeValue.ofSeconds(3));
        connectionEvictor.start();

        Thread.sleep(1000);

        Mockito.verify(cm, Mockito.atLeast(1)).closeExpired();
        Mockito.verify(cm, Mockito.atLeast(1)).closeIdle(TimeValue.ofSeconds(3));

        Assert.assertTrue(connectionEvictor.isRunning());

        connectionEvictor.shutdown();
        connectionEvictor.awaitTermination(Timeout.ofSeconds(1));
        Assert.assertFalse(connectionEvictor.isRunning());
    }

    @Test
    public void testEvictExpiredOnly() throws Exception {
        final ConnPoolControl<?> cm = Mockito.mock(ConnPoolControl.class);
        final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(cm,
                TimeValue.ofMilliseconds(500), null);
        connectionEvictor.start();

        Thread.sleep(1000);

        Mockito.verify(cm, Mockito.atLeast(1)).closeExpired();
        Mockito.verify(cm, Mockito.never()).closeIdle(ArgumentMatchers.<TimeValue>any());

        Assert.assertTrue(connectionEvictor.isRunning());

        connectionEvictor.shutdown();
        connectionEvictor.awaitTermination(Timeout.ofSeconds(1));
        Assert.assertFalse(connectionEvictor.isRunning());
    }

}
