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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.entity.compress.CompressingFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestGZip {

    @Test
    void testBasic() {
        final String s = "some kind of text";
        final HttpEntity entity = CompressingFactory.INSTANCE.decompressEntity(new StringEntity(s, ContentType.TEXT_PLAIN, false), "gz");
        Assertions.assertEquals(17, entity.getContentLength());
        Assertions.assertNotNull(entity.getContentEncoding());
        Assertions.assertEquals("gz", entity.getContentEncoding());
    }

    @Test
    void testCompressionDecompression() throws Exception {
        final StringEntity in = new StringEntity("some kind of text", ContentType.TEXT_PLAIN);

        // Compress the input entity using the factory
        final HttpEntity gzipe = CompressingFactory.INSTANCE.compressEntity(in, "gz");

        // Write the compressed content to a ByteArrayOutputStream
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        gzipe.writeTo(buf);

        // Create a new entity using the compressed content
        final ByteArrayEntity out = new ByteArrayEntity(buf.toByteArray(), ContentType.APPLICATION_OCTET_STREAM);

        // Decompress the entity
        final HttpEntity gunzipe = CompressingFactory.INSTANCE.decompressEntity(out, "gz");

        // Verify the decompressed content
        Assertions.assertEquals("some kind of text", EntityUtils.toString(gunzipe, StandardCharsets.US_ASCII));
    }

    @Test
    void testCompressionIOExceptionDoesNotCloseOuterOutputStream() throws Exception {
        final HttpEntity in = Mockito.mock(HttpEntity.class);
        Mockito.doThrow(new IOException("Ooopsie")).when(in).writeTo(ArgumentMatchers.any());

        // Compress the mocked entity
        final HttpEntity gzipe = CompressingFactory.INSTANCE.compressEntity(in, "gz");

        // Mock the output stream
        final OutputStream out = Mockito.mock(OutputStream.class);
        try {
            gzipe.writeTo(out);
        } catch (final IOException ex) {
            Mockito.verify(out, Mockito.atLeastOnce()).close();
        }
    }

    @Test
    void testDecompressionWithMultipleGZipStream() throws Exception {
        final int[] data = new int[]{
                0x1f, 0x8b, 0x08, 0x08, 0x03, 0xf1, 0x55, 0x5a, 0x00, 0x03, 0x74, 0x65, 0x73, 0x74, 0x31, 0x00,
                0x2b, 0x2e, 0x29, 0x4a, 0x4d, 0xcc, 0xd5, 0x35, 0xe4, 0x02, 0x00, 0x03, 0x61, 0xf0, 0x5f, 0x09,
                0x00, 0x00, 0x00, 0x1f, 0x8b, 0x08, 0x08, 0x08, 0xf1, 0x55, 0x5a, 0x00, 0x03, 0x74, 0x65, 0x73,
                0x74, 0x32, 0x00, 0x2b, 0x2e, 0x29, 0x4a, 0x4d, 0xcc, 0xd5, 0x35, 0xe2, 0x02, 0x00, 0xc0, 0x32,
                0xdd, 0x74, 0x09, 0x00, 0x00, 0x00
        };
        final byte[] bytes = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            bytes[i] = (byte) (data[i] & 0xff);
        }

        // Decompress multiple GZip streams using the factory
        final HttpEntity entity = CompressingFactory.INSTANCE.decompressEntity(
                new InputStreamEntity(new ByteArrayInputStream(bytes), ContentType.APPLICATION_OCTET_STREAM),
                "gz");

        // Verify the decompressed content
        Assertions.assertEquals("stream-1\nstream-2\n", EntityUtils.toString(entity, StandardCharsets.US_ASCII));
    }

}
