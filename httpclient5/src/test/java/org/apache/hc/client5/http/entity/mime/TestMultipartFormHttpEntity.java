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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.ParserCursor;
import org.junit.Assert;
import org.junit.Test;

public class TestMultipartFormHttpEntity {

    @Test
    public void testExplictContractorParams() throws Exception {
        final HttpEntity entity = MultipartEntityBuilder.create()
                .setLaxMode()
                .setBoundary("whatever")
                .setCharset(StandardCharsets.UTF_8)
                .build();

        Assert.assertNull(entity.getContentEncoding());
        final String contentType = entity.getContentType();
        final HeaderElement elem = BasicHeaderValueParser.INSTANCE.parseHeaderElement(contentType,
                new ParserCursor(0, contentType.length()));
        Assert.assertEquals("multipart/mixed", elem.getName());
        final NameValuePair p1 = elem.getParameterByName("boundary");
        Assert.assertNotNull(p1);
        Assert.assertEquals("whatever", p1.getValue());
        final NameValuePair p2 = elem.getParameterByName("charset");
        Assert.assertNotNull(p2);
        Assert.assertEquals("UTF-8", p2.getValue());
    }

    @Test
    public void testImplictContractorParams() throws Exception {
        final HttpEntity entity = MultipartEntityBuilder.create().build();
        Assert.assertNull(entity.getContentEncoding());
        final String contentType = entity.getContentType();
        final HeaderElement elem = BasicHeaderValueParser.INSTANCE.parseHeaderElement(contentType,
                new ParserCursor(0, contentType.length()));
        Assert.assertEquals("multipart/mixed", elem.getName());
        final NameValuePair p1 = elem.getParameterByName("boundary");
        Assert.assertNotNull(p1);

        final String boundary = p1.getValue();
        Assert.assertNotNull(boundary);

        Assert.assertTrue(boundary.length() >= 30);
        Assert.assertTrue(boundary.length() <= 40);

        final NameValuePair p2 = elem.getParameterByName("charset");
        Assert.assertNull(p2);
    }

    @Test
    public void testRepeatable() throws Exception {
        final HttpEntity entity = MultipartEntityBuilder.create()
                .addTextBody("p1", "blah blah", ContentType.DEFAULT_TEXT)
                .addTextBody("p2", "yada yada", ContentType.DEFAULT_TEXT)
                .build();
        Assert.assertTrue(entity.isRepeatable());
        Assert.assertFalse(entity.isChunked());
        Assert.assertFalse(entity.isStreaming());

        final long len = entity.getContentLength();
        Assert.assertTrue(len == entity.getContentLength());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();

        byte[] bytes = out.toByteArray();
        Assert.assertNotNull(bytes);
        Assert.assertTrue(bytes.length == len);

        Assert.assertTrue(len == entity.getContentLength());

        out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();

        bytes = out.toByteArray();
        Assert.assertNotNull(bytes);
        Assert.assertTrue(bytes.length == len);
    }

    @Test
    public void testNonRepeatable() throws Exception {
        final HttpEntity entity = MultipartEntityBuilder.create()
            .addPart("p1", new InputStreamBody(
                new ByteArrayInputStream("blah blah".getBytes()), ContentType.DEFAULT_BINARY))
            .addPart("p2", new InputStreamBody(
                new ByteArrayInputStream("yada yada".getBytes()), ContentType.DEFAULT_BINARY))
            .build();
        Assert.assertFalse(entity.isRepeatable());
        Assert.assertTrue(entity.isChunked());
        Assert.assertTrue(entity.isStreaming());

        Assert.assertTrue(entity.getContentLength() == -1);
    }

}
