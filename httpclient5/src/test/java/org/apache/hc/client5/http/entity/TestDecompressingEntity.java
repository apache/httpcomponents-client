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
import org.junit.Assert;
import org.junit.Test;

public class TestDecompressingEntity {

    @Test
    public void testNonStreaming() throws Exception {
        final CRC32 crc32 = new CRC32();
        final StringEntity wrapped = new StringEntity("1234567890", StandardCharsets.US_ASCII);
        final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32);
        Assert.assertFalse(entity.isStreaming());
        final String s = EntityUtils.toString(entity);
        Assert.assertEquals("1234567890", s);
        Assert.assertEquals(639479525L, crc32.getValue());
        final InputStream in1 = entity.getContent();
        final InputStream in2 = entity.getContent();
        Assert.assertTrue(in1 != in2);
    }

    @Test
    public void testStreaming() throws Exception {
        final CRC32 crc32 = new CRC32();
        final ByteArrayInputStream in = new ByteArrayInputStream("1234567890".getBytes(StandardCharsets.US_ASCII));
        final InputStreamEntity wrapped = new InputStreamEntity(in, -1, ContentType.DEFAULT_TEXT);
        final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32);
        Assert.assertTrue(entity.isStreaming());
        final String s = EntityUtils.toString(entity);
        Assert.assertEquals("1234567890", s);
        Assert.assertEquals(639479525L, crc32.getValue());
        final InputStream in1 = entity.getContent();
        final InputStream in2 = entity.getContent();
        Assert.assertTrue(in1 == in2);
        EntityUtils.consume(entity);
        EntityUtils.consume(entity);
    }

    @Test
    public void testWriteToStream() throws Exception {
        final CRC32 crc32 = new CRC32();
        final StringEntity wrapped = new StringEntity("1234567890", StandardCharsets.US_ASCII);
        final ChecksumEntity entity = new ChecksumEntity(wrapped, crc32);
        Assert.assertFalse(entity.isStreaming());

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);

        final String s = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("1234567890", s);
        Assert.assertEquals(639479525L, crc32.getValue());
    }

    static class ChecksumEntity extends DecompressingEntity {

        public ChecksumEntity(final HttpEntity wrapped, final Checksum checksum) {
            super(wrapped, new InputStreamFactory() {

                @Override
                public InputStream create(final InputStream inStream) throws IOException {
                    return new CheckedInputStream(inStream, checksum);
                }

            });
        }

    }

}
