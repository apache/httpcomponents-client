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

import java.io.IOException;

import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

/**
 * {@link CapacityChannel} decorator used by the inflating {@code AsyncDataConsumer}s. A downstream
 * consumer advertises capacity in decompressed bytes, but the increment is applied to the compressed
 * stream read from the I/O reactor. Forwarding it unchanged lets the reactor deliver that many
 * compressed bytes, which decompress into several times as many bytes and overshoot the downstream
 * consumer's advertised capacity. This decorator scales the requested increment down by a fixed
 * expansion factor so the amount of decompressed data delivered per round stays closer to what the
 * downstream consumer asked for.
 */
final class InflatingCapacityChannel implements CapacityChannel {

    /**
     * Assumed decompressed-to-compressed size ratio. A conservative estimate for typical HTTP
     * payloads; a larger value trades throughput for tighter capacity predictability.
     */
    static final int DEFAULT_EXPANSION_FACTOR = 4;

    private final CapacityChannel delegate;
    private final int expansionFactor;

    InflatingCapacityChannel(final CapacityChannel delegate) {
        this(delegate, DEFAULT_EXPANSION_FACTOR);
    }

    InflatingCapacityChannel(final CapacityChannel delegate, final int expansionFactor) {
        this.delegate = Args.notNull(delegate, "Capacity channel");
        this.expansionFactor = Args.positive(expansionFactor, "Expansion factor");
    }

    @Override
    public void update(final int increment) throws IOException {
        // Integer.MAX_VALUE is the idiomatic "unbounded" capacity request; forward it (and any
        // non-positive value) unchanged so scaling does not turn "no limit" into a finite bound.
        if (increment <= 0 || increment == Integer.MAX_VALUE) {
            delegate.update(increment);
            return;
        }
        // Request at least one byte so a small increment still lets the stream make progress.
        delegate.update(Math.max(1, increment / expansionFactor));
    }
}
