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

package org.apache.hc.client5.http.entity.mime;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMultipartContentBody {

    @Test
    public void testStringBody() throws Exception {
        final StringBody b1 = new StringBody("text", ContentType.DEFAULT_TEXT);
        Assertions.assertEquals(4, b1.getContentLength());

        Assertions.assertEquals("UTF-8", b1.getCharset());

        Assertions.assertNull(b1.getFilename());
        Assertions.assertEquals("text/plain", b1.getMimeType());
        Assertions.assertEquals("text", b1.getMediaType());
        Assertions.assertEquals("plain", b1.getSubType());

        final StringBody b2 = new StringBody("more text",
                ContentType.create("text/other", StandardCharsets.ISO_8859_1));
        Assertions.assertEquals(9, b2.getContentLength());
        Assertions.assertEquals(StandardCharsets.ISO_8859_1.name(), b2.getCharset());

        Assertions.assertNull(b2.getFilename());
        Assertions.assertEquals("text/other", b2.getMimeType());
        Assertions.assertEquals("text", b2.getMediaType());
        Assertions.assertEquals("other", b2.getSubType());
    }

    @Test
    public void testInputStreamBody() throws Exception {
        final byte[] stuff = "Stuff".getBytes(StandardCharsets.US_ASCII);
        final InputStreamBody b1 = new InputStreamBody(new ByteArrayInputStream(stuff), "stuff");
        Assertions.assertEquals(-1, b1.getContentLength());

        Assertions.assertNull(b1.getCharset());
        Assertions.assertEquals("stuff", b1.getFilename());
        Assertions.assertEquals("application/octet-stream", b1.getMimeType());
        Assertions.assertEquals("application", b1.getMediaType());
        Assertions.assertEquals("octet-stream", b1.getSubType());

        final InputStreamBody b2 = new InputStreamBody(
                new ByteArrayInputStream(stuff), ContentType.create("some/stuff"), "stuff");
        Assertions.assertEquals(-1, b2.getContentLength());
        Assertions.assertNull(b2.getCharset());
        Assertions.assertEquals("stuff", b2.getFilename());
        Assertions.assertEquals("some/stuff", b2.getMimeType());
        Assertions.assertEquals("some", b2.getMediaType());
        Assertions.assertEquals("stuff", b2.getSubType());
    }

}
