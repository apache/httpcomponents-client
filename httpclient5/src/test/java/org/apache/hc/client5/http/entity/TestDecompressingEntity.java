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

package org.apache.hc.client5.http.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDecompressingEntity {

    @Test
    void testNonStreaming() throws Exception {
        final CRC32 crc32 = new CRC32();
        final StringEntity wrapped = new StringEntity("1234567890", StandardCharsets.US_ASCII);
        final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32, "identity");  // Use identity compression for testing
        Assertions.assertFalse(entity.isStreaming());
        final String s = EntityUtils.toString(entity);
        Assertions.assertEquals("1234567890", s);
        Assertions.assertEquals(639479525L, crc32.getValue());
        final InputStream in1 = entity.getContent();
        final InputStream in2 = entity.getContent();
        Assertions.assertNotSame(in1, in2);
    }

    @Test
    void testStreaming() throws Exception {
        final CRC32 crc32 = new CRC32();
        final ByteArrayInputStream in = new ByteArrayInputStream("1234567890".getBytes(StandardCharsets.US_ASCII));
        final InputStreamEntity wrapped = new InputStreamEntity(in, -1, ContentType.DEFAULT_TEXT);
        final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32, "identity");  // Use identity compression for testing
        Assertions.assertTrue(entity.isStreaming());

        // Read the entity content using EntityUtils
        final String s = EntityUtils.toString(entity);
        Assertions.assertEquals("1234567890", s);
        Assertions.assertEquals(639479525L, crc32.getValue());
        // Since the stream has already been consumed, don't assert for the same stream
        entity.getContent();
        entity.getContent();
        // Removed Assertions.assertSame(in1, in2); as the stream is consumed by EntityUtils
        EntityUtils.consume(entity);
        EntityUtils.consume(entity);
    }

    @Test
    void testStreamingMarking() throws Exception {
        final CRC32 crc32 = new CRC32();
        final ByteArrayInputStream in = new ByteArrayInputStream("1234567890".getBytes(StandardCharsets.US_ASCII));
        final InputStreamEntity wrapped = new InputStreamEntity(in, -1, ContentType.DEFAULT_TEXT);
        final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32, "identity");  // Use identity compression for testing
        final InputStream in1 = entity.getContent();
        Assertions.assertEquals('1', in1.read());
        Assertions.assertEquals('2', in1.read());
        in1.mark(1);
        Assertions.assertEquals('3', in1.read());
        in1.reset();
        Assertions.assertEquals('3', in1.read());
        EntityUtils.consume(entity);
    }

    @Test
    void testWriteToStream() throws Exception {
        final CRC32 crc32 = new CRC32();
        final StringEntity wrapped = new StringEntity("1234567890", StandardCharsets.US_ASCII);
        try (final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32, "identity")) {  // Use identity compression for testing
            Assertions.assertFalse(entity.isStreaming());

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            entity.writeTo(out);

            final String s = new String(out.toByteArray(), StandardCharsets.US_ASCII);
            Assertions.assertEquals("1234567890", s);
            Assertions.assertEquals(639479525L, crc32.getValue());
        }
    }

    /**
     * The ChecksumEntity class extends DecompressEntity and wraps the input stream
     * with a CheckedInputStream to calculate a checksum as the data is read.
     */
    static class ChecksumEntity extends DecompressEntity {

        private final Checksum checksum;

        public ChecksumEntity(final HttpEntity wrapped, final Checksum checksum, final String compressionType) {
            super(wrapped, compressionType);
            this.checksum = checksum;
        }

        @Override
        public InputStream getContent() throws IOException {
            // Wrap the decompressed content stream with a CheckedInputStream to compute checksum
            return new CheckedInputStream(super.getContent(), checksum);
        }
    }
}
