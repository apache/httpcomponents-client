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
package org.apache.hc.client5.http.impl.schedule;

import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestExponentialBackingOffSchedulingStrategy {

    private ExponentialBackOffSchedulingStrategy impl;

    @Before
    public void setUp() {
        impl = new ExponentialBackOffSchedulingStrategy(
                ExponentialBackOffSchedulingStrategy.DEFAULT_BACK_OFF_RATE,
                ExponentialBackOffSchedulingStrategy.DEFAULT_INITIAL_EXPIRY,
                ExponentialBackOffSchedulingStrategy.DEFAULT_MAX_EXPIRY
        );
    }

    @Test
    public void testSchedule() {
        Assert.assertEquals(TimeValue.ofMilliseconds(0), impl.schedule(0));
        Assert.assertEquals(TimeValue.ofMilliseconds(6000), impl.schedule(1));
        Assert.assertEquals(TimeValue.ofMilliseconds(60000), impl.schedule(2));
        Assert.assertEquals(TimeValue.ofMilliseconds(600000), impl.schedule(3));
        Assert.assertEquals(TimeValue.ofMilliseconds(6000000), impl.schedule(4));
        Assert.assertEquals(TimeValue.ofMilliseconds(60000000), impl.schedule(5));
        Assert.assertEquals(TimeValue.ofMilliseconds(86400000), impl.schedule(6));
        Assert.assertEquals(TimeValue.ofMilliseconds(86400000), impl.schedule(Integer.MAX_VALUE));
    }

}
