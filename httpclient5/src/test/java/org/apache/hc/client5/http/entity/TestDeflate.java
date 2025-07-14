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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

import org.apache.hc.client5.http.entity.compress.ContentCodecRegistry;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.entity.compress.Encoder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDeflate {

    @Test
    void testCompressDecompress() throws Exception {

        final String s = "some kind of text";
        final byte[] input = s.getBytes(StandardCharsets.US_ASCII);

        // Compress the bytes
        final byte[] compressed = new byte[input.length * 2];
        final Deflater compresser = new Deflater();
        compresser.setInput(input);
        compresser.finish();
        final int len = compresser.deflate(compressed);

        final HttpEntity entity = new org.apache.hc.client5.http.entity.compress.DecompressingEntity(
                new ByteArrayEntity(compressed, 0, len, ContentType.APPLICATION_OCTET_STREAM),
                ContentCodecRegistry.decoder(ContentCoding.DEFLATE));

        Assertions.assertEquals(s, EntityUtils.toString(entity));
    }

    @Test
    void testEncodeThenDecode() throws Exception {

        final String text = "some kind of text";

        final HttpEntity plain = new StringEntity(text, ContentType.TEXT_PLAIN);
        final Encoder encoder = ContentCodecRegistry.encoder(ContentCoding.DEFLATE);
        Assertions.assertNotNull(encoder, "deflate encoder must exist");

        final HttpEntity deflated = encoder.wrap(plain);

        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        deflated.writeTo(buf);

        final HttpEntity decoded = new org.apache.hc.client5.http.entity.compress.DecompressingEntity(
                new ByteArrayEntity(buf.toByteArray(), ContentType.APPLICATION_OCTET_STREAM),
                ContentCodecRegistry.decoder(ContentCoding.DEFLATE));

        Assertions.assertEquals(text, EntityUtils.toString(decoded, StandardCharsets.US_ASCII));
    }


}
