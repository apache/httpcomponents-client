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

package org.apache.http.entity.mime;

import java.io.ByteArrayInputStream;

import org.apache.http.Consts;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.junit.Assert;
import org.junit.Test;

public class TestMultipartContentBody {

    @Test
    public void testStringBody() throws Exception {
        final StringBody b1 = new StringBody("text", ContentType.DEFAULT_TEXT);
        Assert.assertEquals(4, b1.getContentLength());

        Assert.assertEquals("ISO-8859-1", b1.getCharset());

        Assert.assertNull(b1.getFilename());
        Assert.assertEquals("text/plain", b1.getMimeType());
        Assert.assertEquals("text", b1.getMediaType());
        Assert.assertEquals("plain", b1.getSubType());

        Assert.assertEquals(MIME.ENC_8BIT, b1.getTransferEncoding());

        final StringBody b2 = new StringBody("more text",
                ContentType.create("text/other", MIME.DEFAULT_CHARSET));
        Assert.assertEquals(9, b2.getContentLength());
        Assert.assertEquals(MIME.DEFAULT_CHARSET.name(), b2.getCharset());

        Assert.assertNull(b2.getFilename());
        Assert.assertEquals("text/other", b2.getMimeType());
        Assert.assertEquals("text", b2.getMediaType());
        Assert.assertEquals("other", b2.getSubType());

        Assert.assertEquals(MIME.ENC_8BIT, b2.getTransferEncoding());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testStringBodyInvalidConstruction1() throws Exception {
        Assert.assertNotNull(new StringBody(null, ContentType.DEFAULT_TEXT)); // avoid unused warning
    }

    @Test(expected=IllegalArgumentException.class)
    public void testStringBodyInvalidConstruction2() throws Exception {
        Assert.assertNotNull(new StringBody("stuff", (ContentType) null)); // avoid unused warning
    }

    @Test
    public void testInputStreamBody() throws Exception {
        final byte[] stuff = "Stuff".getBytes(Consts.ASCII);
        final InputStreamBody b1 = new InputStreamBody(new ByteArrayInputStream(stuff), "stuff");
        Assert.assertEquals(-1, b1.getContentLength());

        Assert.assertNull(b1.getCharset());
        Assert.assertEquals("stuff", b1.getFilename());
        Assert.assertEquals("application/octet-stream", b1.getMimeType());
        Assert.assertEquals("application", b1.getMediaType());
        Assert.assertEquals("octet-stream", b1.getSubType());

        Assert.assertEquals(MIME.ENC_BINARY, b1.getTransferEncoding());

        final InputStreamBody b2 = new InputStreamBody(
                new ByteArrayInputStream(stuff), ContentType.create("some/stuff"), "stuff");
        Assert.assertEquals(-1, b2.getContentLength());
        Assert.assertNull(b2.getCharset());
        Assert.assertEquals("stuff", b2.getFilename());
        Assert.assertEquals("some/stuff", b2.getMimeType());
        Assert.assertEquals("some", b2.getMediaType());
        Assert.assertEquals("stuff", b2.getSubType());

        Assert.assertEquals(MIME.ENC_BINARY, b2.getTransferEncoding());
    }

}
