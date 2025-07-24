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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestDeflateDecompressingStringAsyncEntityConsumer {

    @Mock
    private EntityDetails entityDetails;

    @Mock
    private CapacityChannel capacityChannel;

    private DeflateDecompressingStringAsyncEntityConsumer consumer;

    private CountDownLatch latch;

    private String capturedResult;

    private Exception capturedException;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(entityDetails.getContentEncoding()).thenReturn("deflate");

        consumer = new DeflateDecompressingStringAsyncEntityConsumer();

        latch = new CountDownLatch(1);
        capturedResult = null;
        capturedException = null;

        final FutureCallback<String> callback = new FutureCallback<String>() {

            @Override
            public void completed(final String result) {
                capturedResult = result;
                latch.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                capturedException = ex;
                latch.countDown();
            }

            @Override
            public void cancelled() {
                capturedException = new InterruptedException("Cancelled");
                latch.countDown();
            }
        };

        try {
            consumer.streamStart(entityDetails, callback);
        } catch (final HttpException | IOException e) {
            Assertions.fail(e);
        }
    }

    @Test
    void testDecompressChunkedInput() throws IOException, HttpException, InterruptedException {
        // Generate compressed data (raw deflate, no header/trailer)
        final String original = "Hello, decompressed world!";
        final ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // nowrap: true
        deflater.setInput(original.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        final byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            final int len = deflater.deflate(buf);
            compressedOut.write(buf, 0, len);
        }
        deflater.end();
        final byte[] compressed = compressedOut.toByteArray();

        // Feed in chunks
        final int chunkSize = 5;
        for (int i = 0; i < compressed.length; i += chunkSize) {
            final int len = Math.min(chunkSize, compressed.length - i);
            final ByteBuffer chunk = ByteBuffer.wrap(compressed, i, len);
            consumer.consume(chunk);
        }

        // End stream
        consumer.streamEnd(Collections.emptyList());

        // Wait for callback
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assertions.assertNull(capturedException);
        Assertions.assertEquals(original, capturedResult);
        Assertions.assertEquals(original, consumer.getContent());
    }

    @Test
    void testUnsupportedEncoding() {
        Mockito.when(entityDetails.getContentEncoding()).thenReturn("gzip");
        Assertions.assertThrows(HttpException.class, () -> consumer.streamStart(entityDetails, null));
    }

    @Test
    void testReleaseResources() {
        consumer.releaseResources();
        Assertions.assertNull(consumer.getContent());
    }
}