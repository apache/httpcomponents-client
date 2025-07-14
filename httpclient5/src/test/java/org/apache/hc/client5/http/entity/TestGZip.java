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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.entity.compress.ContentCodecRegistry;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.entity.compress.Encoder;
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
    void testBasic() throws Exception {
        final String s = "some kind of text";
        final StringEntity e = new StringEntity(s, ContentType.TEXT_PLAIN, false);
        try (final GzipCompressingEntity gzipe = new GzipCompressingEntity(e)) {
            Assertions.assertTrue(gzipe.isChunked());
            Assertions.assertEquals(-1, gzipe.getContentLength());
            Assertions.assertNotNull(gzipe.getContentEncoding());
            Assertions.assertEquals("gzip", gzipe.getContentEncoding());
        }
    }

    @Test
    void testCompressionDecompression() throws Exception {
        final StringEntity in = new StringEntity("some kind of text", ContentType.TEXT_PLAIN);
        try (final GzipCompressingEntity gzipe = new GzipCompressingEntity(in)) {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            gzipe.writeTo(buf);
            final ByteArrayEntity out = new ByteArrayEntity(buf.toByteArray(), ContentType.APPLICATION_OCTET_STREAM);
            final org.apache.hc.client5.http.entity.compress.DecompressingEntity gunzipe = new org.apache.hc.client5.http.entity.compress.DecompressingEntity(
                    out, ContentCodecRegistry.decoder(ContentCoding.GZIP));
            Assertions.assertEquals("some kind of text",
                    EntityUtils.toString(gunzipe, StandardCharsets.US_ASCII));
        }
    }


    @Test
    void testCompressionIOExceptionLeavesOutputStreamOpen() throws Exception {
        final HttpEntity in = Mockito.mock(HttpEntity.class);
        Mockito.doThrow(new IOException("Ooopsie")).when(in).writeTo(ArgumentMatchers.any());
        try (final GzipCompressingEntity gzipe = new GzipCompressingEntity(in)) {
            final OutputStream out = Mockito.mock(OutputStream.class);
            try {
                gzipe.writeTo(out);
            } catch (final IOException ex) {
                Mockito.verify(out, Mockito.never()).close();
            }
        }
    }

    @Test
    void testDecompressionWithMultipleGZipStream() throws Exception {
        final int[] data = new int[] {
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

        try (final org.apache.hc.client5.http.entity.compress.DecompressingEntity entity = new org.apache.hc.client5.http.entity.compress.DecompressingEntity(
                new InputStreamEntity(new ByteArrayInputStream(bytes),
                        ContentType.APPLICATION_OCTET_STREAM),
                ContentCodecRegistry.decoder(ContentCoding.GZIP))) {
            Assertions.assertEquals("stream-1\nstream-2\n",
                    EntityUtils.toString(entity, StandardCharsets.US_ASCII));
        }
    }

    @Test
    void testEncodeThenDecode() throws Exception {

        final String txt = "some kind of text";

        final HttpEntity plain = new StringEntity(txt, ContentType.TEXT_PLAIN);
        final Encoder gzipEn = ContentCodecRegistry.encoder(ContentCoding.GZIP);
        Assertions.assertNotNull(gzipEn, "gzip encoder must exist");

        final HttpEntity gzipped = gzipEn.wrap(plain);

        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        gzipped.writeTo(buf);

        final HttpEntity ungzip = new org.apache.hc.client5.http.entity.compress.DecompressingEntity(
                new ByteArrayEntity(buf.toByteArray(), ContentType.APPLICATION_OCTET_STREAM),
                ContentCodecRegistry.decoder(ContentCoding.GZIP));

        Assertions.assertEquals(txt, EntityUtils.toString(ungzip, StandardCharsets.US_ASCII));
    }

    @Test
    void testUnwrapHelper() throws Exception {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gout = new java.util.zip.GZIPOutputStream(baos)) {
            gout.write("unwrap check".getBytes(StandardCharsets.US_ASCII));
        }
        final byte[] gzBytes = baos.toByteArray();

        final InputStream decoded = ContentCodecRegistry.unwrap(
                ContentCoding.GZIP,
                new ByteArrayInputStream(gzBytes));

        Assertions.assertNotNull(decoded, "unwrap returned null");

        final HttpEntity decodedEntity = new InputStreamEntity(
                decoded, -1, ContentType.APPLICATION_OCTET_STREAM);

        Assertions.assertEquals("unwrap check",
                EntityUtils.toString(decodedEntity, StandardCharsets.US_ASCII));
    }

}
