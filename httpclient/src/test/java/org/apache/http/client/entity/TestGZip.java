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

package org.apache.http.client.entity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGZip {

    @Test
    public void testBasic() throws Exception {
        final String s = "some kind of text";
        final StringEntity e = new StringEntity(s, ContentType.TEXT_PLAIN);
        e.setChunked(false);
        final GzipCompressingEntity gzipe = new GzipCompressingEntity(e);
        Assert.assertTrue(gzipe.isChunked());
        Assert.assertEquals(-1, gzipe.getContentLength());
        Assert.assertNotNull(gzipe.getContentEncoding());
        Assert.assertEquals("gzip", gzipe.getContentEncoding().getValue());
    }

    @Test
    public void testCompressionDecompression() throws Exception {
        final StringEntity in = new StringEntity("some kind of text", ContentType.TEXT_PLAIN);
        final GzipCompressingEntity gzipe = new GzipCompressingEntity(in);
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        gzipe.writeTo(buf);
        final ByteArrayEntity out = new ByteArrayEntity(buf.toByteArray());
        final GzipDecompressingEntity gunzipe = new GzipDecompressingEntity(out);
        Assert.assertEquals("some kind of text", EntityUtils.toString(gunzipe, Consts.ASCII));
    }

    @Test
    public void testCompressionIOExceptionLeavesOutputStreamOpen() throws Exception {
        final HttpEntity in = Mockito.mock(HttpEntity.class);
        Mockito.doThrow(new IOException("Ooopsie")).when(in).writeTo(Mockito.<OutputStream>any());
        final GzipCompressingEntity gzipe = new GzipCompressingEntity(in);
        final OutputStream out = Mockito.mock(OutputStream.class);
        try {
            gzipe.writeTo(out);
        } catch (final IOException ex) {
            Mockito.verify(out, Mockito.never()).close();
        }
    }

}
