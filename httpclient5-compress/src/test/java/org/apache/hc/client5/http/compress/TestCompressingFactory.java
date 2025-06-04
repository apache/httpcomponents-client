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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestCompressingFactory {

    private CompressingFactory factory;
    private static final String TEST_STRING = "Hello, World! This is a test string for compression. " +
            "We need a longer string with some repetitive content to ensure good compression. " +
            "Compression works best when there are patterns that can be replaced with shorter codes. " +
            "So we'll repeat some words like compression, compression, compression, and more compression. " +
            "The more repetitive the content, the better the compression ratio will be.";
    private static final byte[] TEST_DATA = TEST_STRING.getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        factory = CompressingFactory.INSTANCE;
    }

    @Test
    void testAvailableProviders() {
        final Set<String> inputProviders = factory.getAvailableInputProviders();
        final Set<String> outputProviders = factory.getAvailableOutputProviders();

        assertNotNull(inputProviders);
        assertNotNull(outputProviders);
        assertFalse(inputProviders.isEmpty());
        assertFalse(outputProviders.isEmpty());
        assertTrue(inputProviders.contains("gz"));
        assertTrue(inputProviders.contains("deflate"));
        assertTrue(outputProviders.contains("gz"));
        assertTrue(outputProviders.contains("deflate"));
    }

    @Test
    void testRoundTripGzip() throws IOException {
        // Test GZIP compression/decompression
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (final OutputStream encoder = factory.getCompressorOutputStream("gzip", compressed)) {
            encoder.write(TEST_DATA);
        }

        final byte[] compressedData = compressed.toByteArray();
        assertTrue(compressedData.length > 0);
        assertTrue(compressedData.length < TEST_DATA.length); // Should be smaller when compressed

        final ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedData);
        final InputStream decompressor = factory.getDecompressorInputStream("gzip", compressedInput, false);
        final ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = decompressor.read(buffer)) > 0) {
            decompressed.write(buffer, 0, len);
        }

        assertArrayEquals(TEST_DATA, decompressed.toByteArray());
    }

    @Test
    void testEntityCompression() throws IOException {
        final HttpEntity original = new ByteArrayEntity(TEST_DATA, ContentType.APPLICATION_OCTET_STREAM);
        final HttpEntity compressed = factory.compressEntity(original, "gzip");
        assertTrue(compressed instanceof CompressingEntity);
        assertEquals("gz", compressed.getContentEncoding());

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        compressed.writeTo(output);
        final byte[] compressedData = output.toByteArray();
        assertTrue(compressedData.length > 0);
        assertTrue(compressedData.length < TEST_DATA.length); // Should be smaller when compressed
    }

    @Test
    void testEntityDecompression() throws IOException {
        // First compress the data
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (final OutputStream encoder = factory.getCompressorOutputStream("gzip", compressed)) {
            encoder.write(TEST_DATA);
        }

        // Create an entity with the compressed data
        final HttpEntity compressedEntity = new ByteArrayEntity(compressed.toByteArray(), ContentType.APPLICATION_OCTET_STREAM, "gzip");
        final HttpEntity decompressed = factory.decompressEntity(compressedEntity, "gzip");
        assertTrue(decompressed instanceof DecompressingEntity);

        // Read and verify the decompressed content
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        decompressed.writeTo(output);
        assertArrayEquals(TEST_DATA, output.toByteArray());
    }

    @Test
    void testUnsupportedFormat() throws IOException {
        final ByteArrayInputStream input = new ByteArrayInputStream(TEST_DATA);
        final InputStream decompressor = factory.getDecompressorInputStream("unsupported", input, false);
        assertSame(input, decompressor);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final OutputStream compressor = factory.getCompressorOutputStream("unsupported", output);
        assertSame(output, compressor);

        final HttpEntity entity = new ByteArrayEntity(TEST_DATA, ContentType.APPLICATION_OCTET_STREAM);
        final HttpEntity compressedEntity = factory.compressEntity(entity, "unsupported");
        assertSame(entity, compressedEntity);

        final HttpEntity decompressedEntity = factory.decompressEntity(entity, "unsupported");
        assertSame(entity, decompressedEntity);
    }

    private static void assertArrayEquals(final byte[] expected, final byte[] actual) {
        assertEquals(expected.length, actual.length, "Array lengths differ");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "Arrays differ at index " + i);
        }
    }
}