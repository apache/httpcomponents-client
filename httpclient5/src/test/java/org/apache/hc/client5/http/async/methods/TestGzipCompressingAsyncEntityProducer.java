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

import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestGzipCompressingAsyncEntityProducer {

    @Mock
    private DataStreamChannel channel;

    private GzipCompressingAsyncEntityProducer producer;

    private String payload = "Hello, world!";

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        final StringAsyncEntityProducer delegate = new StringAsyncEntityProducer(payload, ContentType.TEXT_PLAIN);
        producer = new GzipCompressingAsyncEntityProducer(delegate);
    }

    @Test
    void testGetContentEncoding() {
        Assertions.assertEquals("gzip", producer.getContentEncoding());
    }

    @Test
    void testIsChunked() {
        Assertions.assertTrue(producer.isChunked());
    }

    @Test
    void testContentLengthUnknown() {
        Assertions.assertEquals(-1, producer.getContentLength());
    }

    @Test
    void testProduceCompressedData() throws IOException {
        final ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();

        // Mock write to simulate partial writes (small chunks)
        Mockito.when(channel.write(any(ByteBuffer.class))).thenAnswer(invocation -> {
            final ByteBuffer buf = invocation.getArgument(0);
            final int remaining = buf.remaining();
            if (remaining > 0) {
                final int written = Math.min(remaining, 1); // Simulate writing 1 byte at a time to force multiple calls
                final byte[] tmp = new byte[written];
                buf.get(tmp);
                compressedOut.write(tmp);
                return written;
            }
            return 0;
        });

        // Mock endStream to do nothing
        Mockito.doNothing().when(channel).endStream();

        // Simulate produce calls until endStream is called
        boolean endCalled = false;
        final int maxCalls = 10000; // Large limit
        int callCount = 0;
        while (!endCalled && callCount < maxCalls) {
            producer.produce(channel);
            callCount++;
            // Check if endStream was called
            try {
                Mockito.verify(channel, Mockito.atLeast(1)).endStream();
                endCalled = true;
            } catch (final AssertionError e) {
                // Not yet called
            }
        }
        Assertions.assertTrue(endCalled, "endStream was not called within limit");

        // Decompress to verify
        final byte[] compressedBytes = compressedOut.toByteArray();
        Assertions.assertTrue(compressedBytes.length > 10, "Compressed data too short for GZIP header");
        try (final GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
            final ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            int len;
            while ((len = gzipIn.read(buf)) > 0) {
                decompressedOut.write(buf, 0, len);
            }
            Assertions.assertEquals(payload, decompressedOut.toString("UTF-8"));
        }
    }

    @Test
    void testReleaseResources() {
        producer.releaseResources();
        Assertions.assertFalse(producer.isRepeatable());
    }
}