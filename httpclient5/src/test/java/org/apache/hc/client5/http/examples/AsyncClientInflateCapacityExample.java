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
package org.apache.hc.client5.http.examples;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.client5.http.async.methods.InflatingAsyncDataConsumer;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * Demonstrates how the inflating {@code AsyncDataConsumer}s adjust reactor back-pressure.
 *
 * <p>A downstream consumer advertises capacity in <em>decompressed</em> bytes, but the increment is
 * applied to the <em>compressed</em> stream read from the I/O reactor. Forwarding it unchanged would
 * let the reactor deliver that many compressed bytes, which decompress into several times as many
 * bytes and overshoot the downstream consumer's advertised capacity. The inflating consumer
 * therefore scales the requested increment down before handing it to the reactor.</p>
 *
 * <p>This example wires an {@link InflatingAsyncDataConsumer} in front of a downstream consumer that
 * requests 64 KiB of decompressed capacity and prints the (smaller) increment that reaches the
 * reactor's {@link CapacityChannel}. No network access is required.</p>
 *
 * @since 5.7
 */
public class AsyncClientInflateCapacityExample {

    public static void main(final String[] args) throws Exception {

        // Stands in for the I/O reactor: reports the number of compressed bytes actually requested.
        final CapacityChannel reactorChannel = new CapacityChannel() {

            @Override
            public void update(final int increment) {
                System.out.println("Reactor asked for " + increment + " compressed bytes");
            }
        };

        // A downstream consumer that, once it is handed a channel, asks for 64 KiB of
        // decompressed capacity.
        final AsyncDataConsumer downstream = new AsyncDataConsumer() {

            @Override
            public void updateCapacity(final CapacityChannel channel) throws IOException {
                final int decompressedCapacity = 64 * 1024;
                System.out.println("Downstream requested " + decompressedCapacity + " decompressed bytes");
                channel.update(decompressedCapacity);
            }

            @Override
            public void consume(final ByteBuffer src) {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) {
            }

            @Override
            public void releaseResources() {
            }
        };

        final InflatingAsyncDataConsumer inflating = new InflatingAsyncDataConsumer(downstream, null);
        try {
            // The reactor hands its capacity channel to the inflating consumer, which forwards a
            // scaled-down wrapper to the downstream consumer.
            inflating.updateCapacity(reactorChannel);
        } finally {
            inflating.releaseResources();
        }
    }
}
