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
package org.apache.hc.client5.http.impl.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.aayushatharva.brotli4j.encoder.PreparedDictionary;
import com.github.luben.zstd.ZstdCompressCtx;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.impl.ZstdRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestDictionaryCompressedInputStreams {

    private static final byte[] DCB_MAGIC = {
            (byte) 0xff, 0x44, 0x43, 0x42
    };
    private static final byte[] DCZ_MAGIC = {
            0x5e, 0x2a, 0x4d, 0x18, 0x20, 0x00, 0x00, 0x00
    };

    private static CompressionDictionary dictionary(final byte[] content) {
        return new CompressionDictionary(
                content,
                URI.create("https://example.com/dictionary"),
                "/*",
                "dict-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2099-01-01T00:00:00Z"));
    }

    @Test
    void testDcbRoundTrip() throws Exception {
        Assumptions.assumeTrue(brotliAvailable());
        final byte[] dictionaryContent =
                "shared dictionary words shared dictionary words".getBytes(StandardCharsets.UTF_8);
        final byte[] payload =
                "shared dictionary words plus a response payload".getBytes(StandardCharsets.UTF_8);
        final CompressionDictionary dictionary = dictionary(dictionaryContent);
        final byte[] frame = concat(
                DCB_MAGIC,
                dictionary.getSha256(),
                brotliCompress(dictionaryContent, payload));

        try (InputStream in = new DictionaryBrotliInputStream(
                new ByteArrayInputStream(frame), dictionary)) {
            assertArrayEquals(payload, readAll(in));
        }
    }

    @Test
    void testDcbRejectsWrongDictionaryHash() throws Exception {
        Assumptions.assumeTrue(brotliAvailable());
        final CompressionDictionary dictionary = dictionary(new byte[] {1, 2, 3, 4});
        final byte[] wrongHash = new byte[32];
        final byte[] frame = concat(DCB_MAGIC, wrongHash, new byte[] {0});

        assertThrows(IOException.class, () -> new DictionaryBrotliInputStream(
                new ByteArrayInputStream(frame), dictionary));
    }

    @Test
    void testDczRoundTrip() throws Exception {
        Assumptions.assumeTrue(ZstdRuntime.available());
        final byte[] dictionaryContent =
                "shared zstd dictionary words shared zstd dictionary words".getBytes(StandardCharsets.UTF_8);
        final byte[] payload =
                "shared zstd dictionary words plus a response payload".getBytes(StandardCharsets.UTF_8);
        final CompressionDictionary dictionary = dictionary(dictionaryContent);
        final byte[] frame = concat(
                DCZ_MAGIC,
                dictionary.getSha256(),
                zstdCompress(dictionaryContent, payload));

        try (InputStream in = new DictionaryZstdInputStream(
                new ByteArrayInputStream(frame), dictionary)) {
            assertArrayEquals(payload, readAll(in));
        }
    }

    @Test
    void testDczRejectsWrongDictionaryHash() {
        Assumptions.assumeTrue(ZstdRuntime.available());
        final CompressionDictionary dictionary = dictionary(new byte[] {1, 2, 3, 4});
        final byte[] wrongHash = new byte[32];
        final byte[] frame = concat(DCZ_MAGIC, wrongHash, new byte[] {0});

        assertThrows(IOException.class, () -> new DictionaryZstdInputStream(
                new ByteArrayInputStream(frame), dictionary));
    }

    private static byte[] brotliCompress(
            final byte[] dictionary,
            final byte[] payload) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(dictionary.length);
        buffer.put(dictionary).flip();
        final PreparedDictionary prepared = Encoder.prepareDictionary(buffer, 0);
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try {
            final Encoder.Parameters parameters = Encoder.Parameters.create(6, 24, Encoder.Mode.TEXT);
            try (BrotliOutputStream out = new BrotliOutputStream(compressed, parameters)) {
                out.attachDictionary(prepared);
                out.write(payload);
            }
            return compressed.toByteArray();
        } finally {
            if (prepared instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) prepared).close();
                } catch (final Exception ex) {
                    throw new IOException("Unable to release Brotli dictionary", ex);
                }
            }
        }
    }

    private static byte[] zstdCompress(final byte[] dictionary, final byte[] payload) {
        try (ZstdCompressCtx cctx = new ZstdCompressCtx()) {
            cctx.loadDict(dictionary);
            return cctx.compress(payload);
        }
    }

    private static boolean brotliAvailable() {
        try {
            Brotli4jLoader.ensureAvailability();
            return true;
        } catch (final Throwable ex) {
            return false;
        }
    }

    private static byte[] concat(final byte[]... parts) {
        int length = 0;
        for (final byte[] part : parts) {
            length += part.length;
        }
        final byte[] result = new byte[length];
        int offset = 0;
        for (final byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static byte[] readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
