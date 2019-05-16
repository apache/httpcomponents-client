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

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestGZip {

    @Test
    public void testBasic() throws Exception {
        final String s = "some kind of text";
        final StringEntity e = new StringEntity(s, ContentType.TEXT_PLAIN, false);
        final GzipCompressingEntity gzipe = new GzipCompressingEntity(e);
        Assert.assertTrue(gzipe.isChunked());
        Assert.assertEquals(-1, gzipe.getContentLength());
        Assert.assertNotNull(gzipe.getContentEncoding());
        Assert.assertEquals("gzip", gzipe.getContentEncoding());
    }

    @Test
    public void testCompressionDecompression() throws Exception {
        final StringEntity in = new StringEntity("some kind of text", ContentType.TEXT_PLAIN);
        final GzipCompressingEntity gzipe = new GzipCompressingEntity(in);
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        gzipe.writeTo(buf);
        final ByteArrayEntity out = new ByteArrayEntity(buf.toByteArray(), ContentType.APPLICATION_OCTET_STREAM);
        final GzipDecompressingEntity gunzipe = new GzipDecompressingEntity(out);
        Assert.assertEquals("some kind of text", EntityUtils.toString(gunzipe, StandardCharsets.US_ASCII));
    }

    @Test
    public void testCompressionIOExceptionLeavesOutputStreamOpen() throws Exception {
        final HttpEntity in = Mockito.mock(HttpEntity.class);
        Mockito.doThrow(new IOException("Ooopsie")).when(in).writeTo(ArgumentMatchers.<OutputStream>any());
        final GzipCompressingEntity gzipe = new GzipCompressingEntity(in);
        final OutputStream out = Mockito.mock(OutputStream.class);
        try {
            gzipe.writeTo(out);
        } catch (final IOException ex) {
            Mockito.verify(out, Mockito.never()).close();
        }
    }

    @Test
    public void testDecompressionWithMultipleGZipStream() throws Exception {
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

        try (final GzipDecompressingEntity entity = new GzipDecompressingEntity(new InputStreamEntity(new ByteArrayInputStream(bytes), ContentType.APPLICATION_OCTET_STREAM))) {
            Assert.assertEquals("stream-1\nstream-2\n", EntityUtils.toString(entity, StandardCharsets.US_ASCII));
        }
    }

}
