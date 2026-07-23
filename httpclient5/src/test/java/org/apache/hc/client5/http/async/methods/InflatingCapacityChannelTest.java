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
package org.apache.hc.client5.http.async.methods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.Test;

class InflatingCapacityChannelTest {

    private static final class RecordingCapacityChannel implements CapacityChannel {

        final List<Integer> increments = new ArrayList<>();

        @Override
        public void update(final int increment) {
            increments.add(increment);
        }
    }

    @Test
    void scalesDownByDefaultExpansionFactor() throws Exception {
        final RecordingCapacityChannel delegate = new RecordingCapacityChannel();
        final CapacityChannel channel = new InflatingCapacityChannel(delegate);

        channel.update(400);

        assertEquals(1, delegate.increments.size());
        assertEquals(400 / InflatingCapacityChannel.DEFAULT_EXPANSION_FACTOR,
                delegate.increments.get(0).intValue());
    }

    @Test
    void scalesDownByExplicitExpansionFactor() throws Exception {
        final RecordingCapacityChannel delegate = new RecordingCapacityChannel();
        final CapacityChannel channel = new InflatingCapacityChannel(delegate, 8);

        channel.update(800);

        assertEquals(100, delegate.increments.get(0).intValue());
    }

    @Test
    void smallIncrementStillRequestsAtLeastOneByte() throws Exception {
        final RecordingCapacityChannel delegate = new RecordingCapacityChannel();
        final CapacityChannel channel = new InflatingCapacityChannel(delegate, 4);

        channel.update(2);

        assertEquals(1, delegate.increments.get(0).intValue());
    }

    @Test
    void nonPositiveIncrementIsForwardedUnchanged() throws Exception {
        final RecordingCapacityChannel delegate = new RecordingCapacityChannel();
        final CapacityChannel channel = new InflatingCapacityChannel(delegate, 4);

        channel.update(0);

        assertEquals(0, delegate.increments.get(0).intValue());
    }

    @Test
    void unboundedCapacityIsForwardedUnchanged() throws Exception {
        final RecordingCapacityChannel delegate = new RecordingCapacityChannel();
        final CapacityChannel channel = new InflatingCapacityChannel(delegate, 4);

        channel.update(Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, delegate.increments.get(0).intValue());
    }

    @Test
    void rejectsNonPositiveExpansionFactor() {
        assertThrows(IllegalArgumentException.class,
                () -> new InflatingCapacityChannel(new RecordingCapacityChannel(), 0));
    }

    @Test
    void rejectsNullDelegate() {
        assertThrows(NullPointerException.class, () -> new InflatingCapacityChannel(null));
    }
}
