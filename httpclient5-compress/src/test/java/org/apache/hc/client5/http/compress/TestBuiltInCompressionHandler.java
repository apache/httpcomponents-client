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

package org.apache.hc.client5.http.compress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestBuiltInCompressionHandler {

    private BuiltInCompressionHandler handler;
    private static final String TEST_STRING = "Hello, World! This is a test string for compression. " +
            "We need a longer string with some repetitive content to ensure good compression. " +
            "Compression works best when there are patterns that can be replaced with shorter codes. " +
            "So we'll repeat some words like compression, compression, compression, and more compression. " +
            "The more repetitive the content, the better the compression ratio will be.";
    private static final byte[] TEST_DATA = TEST_STRING.getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        handler = new BuiltInCompressionHandler();
    }

    @Test
    void testSupportedFormats() {
        final Set<String> inputFormats = handler.getSupportedInputFormats();
        final Set<String> outputFormats = handler.getSupportedOutputFormats();

        assertNotNull(inputFormats);
        assertNotNull(outputFormats);
        assertFalse(inputFormats.isEmpty());
        assertFalse(outputFormats.isEmpty());
        assertTrue(inputFormats.contains("gz"));
        assertTrue(inputFormats.contains("deflate"));
        assertTrue(outputFormats.contains("gz"));
        assertTrue(outputFormats.contains("deflate"));
    }

    @Test
    void testRoundTripGzip() throws IOException {
        // Test GZIP compression/decompression
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (final OutputStream encoder = new GZIPOutputStream(compressed)) {
            encoder.write(TEST_DATA);
        }

        final byte[] compressedData = compressed.toByteArray();
        assertTrue(compressedData.length > 0);
        assertTrue(compressedData.length < TEST_DATA.length); // Should be smaller when compressed

        final ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedData);
        final InputStream decompressor = handler.createDecompressorStream("gzip", compressedInput, false);
        assertNotNull(decompressor);
        final ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = decompressor.read(buffer)) > 0) {
            decompressed.write(buffer, 0, len);
        }

        assertArrayEquals(TEST_DATA, decompressed.toByteArray());
    }

    @Test
    void testRoundTripDeflate() throws IOException {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (final OutputStream encoder = handler.createCompressorStream("deflate", compressed)) {
            encoder.write(TEST_DATA);
        }

        final byte[] compressedData = compressed.toByteArray();
        assertTrue(compressedData.length > 0);
        assertTrue(compressedData.length < TEST_DATA.length); // Should be smaller when compressed

        final ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedData);
        final InputStream decompressor = handler.createDecompressorStream("deflate", compressedInput, false);
        final ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = decompressor.read(buffer)) > 0) {
            decompressed.write(buffer, 0, len);
        }

        assertArrayEquals(TEST_DATA, decompressed.toByteArray());
    }

    @Test
    void testUnsupportedFormat() throws IOException {
        final ByteArrayInputStream input = new ByteArrayInputStream(TEST_DATA);
        final InputStream decompressor = handler.createDecompressorStream("unsupported", input, false);
        assertNull(decompressor);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final OutputStream compressor = handler.createCompressorStream("unsupported", output);
        assertNull(compressor);
    }

    private static void assertArrayEquals(final byte[] expected, final byte[] actual) {
        assertEquals(expected.length, actual.length, "Array lengths differ");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "Arrays differ at index " + i);
        }
    }
}