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
import java.io.File;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

public class TestMultipartEntityBuilder {

    @Test
    public void testBasics() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create().buildEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity.getMultipart() instanceof HttpStrictMultipart);
        Assert.assertEquals(0, entity.getMultipart().getBodyParts().size());
    }

    @Test
    public void testMultipartOptions() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setBoundary("blah-blah")
                .setCharset(Consts.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity.getMultipart() instanceof HttpBrowserCompatibleMultipart);
        Assert.assertEquals("blah-blah", entity.getMultipart().boundary);
        Assert.assertEquals(Consts.UTF_8, entity.getMultipart().charset);
    }

    @Test
    public void testAddBodyParts() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .addTextBody("p1", "stuff")
                .addBinaryBody("p2", new File("stuff"))
                .addBinaryBody("p3", new byte[]{})
                .addBinaryBody("p4", new ByteArrayInputStream(new byte[]{}))
                .buildEntity();
        Assert.assertNotNull(entity);
        final List<FormBodyPart> bodyParts = entity.getMultipart().getBodyParts();
        Assert.assertNotNull(bodyParts);
        Assert.assertEquals(4, bodyParts.size());
    }

    @Test
    public void testMultipartCustomContentType() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.APPLICATION_XML)
                .setBoundary("blah-blah")
                .setCharset(Consts.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assert.assertNotNull(entity);
        final Header contentType = entity.getContentType();
        Assert.assertNotNull(contentType);
        Assert.assertEquals("application/xml; boundary=blah-blah; charset=UTF-8", contentType.getValue());
    }

    @Test
    public void testMultipartContentTypeParameter() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA.withParameters(
                        new BasicNameValuePair("boundary", "yada-yada"),
                        new BasicNameValuePair("charset", "ascii")))
                .buildEntity();
        Assert.assertNotNull(entity);
        final Header contentType = entity.getContentType();
        Assert.assertNotNull(contentType);
        Assert.assertEquals("multipart/form-data; boundary=yada-yada; charset=US-ASCII",
                contentType.getValue());
        Assert.assertEquals("yada-yada", entity.getMultipart().boundary);
        Assert.assertEquals(Consts.ASCII, entity.getMultipart().charset);
    }

    @Test
    public void testMultipartCustomContentTypeParameterOverrides() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA.withParameters(
                        new BasicNameValuePair("boundary", "yada-yada"),
                        new BasicNameValuePair("charset", "ascii"),
                        new BasicNameValuePair("my", "stuff")))
                .setBoundary("blah-blah")
                .setCharset(Consts.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assert.assertNotNull(entity);
        final Header contentType = entity.getContentType();
        Assert.assertNotNull(contentType);
        Assert.assertEquals("multipart/form-data; boundary=blah-blah; charset=UTF-8; my=stuff",
                contentType.getValue());
    }

}
