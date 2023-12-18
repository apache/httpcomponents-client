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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMultipartEntityBuilder {

    @Test
    public void testBasics() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create().buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertTrue(entity.getMultipart() instanceof HttpStrictMultipart);
        Assertions.assertEquals(0, entity.getMultipart().getParts().size());
    }

    @Test
    public void testMultipartOptions() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setBoundary("blah-blah")
                .setCharset(StandardCharsets.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertTrue(entity.getMultipart() instanceof LegacyMultipart);
        Assertions.assertEquals("blah-blah", entity.getMultipart().boundary);
        Assertions.assertEquals(StandardCharsets.UTF_8, entity.getMultipart().charset);
    }

    @Test
    public void testAddBodyParts() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .addTextBody("p1", "stuff")
                .addBinaryBody("p2", new File("stuff"))
                .addBinaryBody("p3", new byte[]{})
                .addBinaryBody("p4", new ByteArrayInputStream(new byte[]{}))
                .addBinaryBody("p5", new ByteArrayInputStream(new byte[]{}), ContentType.DEFAULT_BINARY, "filename")
                .buildEntity();
        Assertions.assertNotNull(entity);
        final List<MultipartPart> bodyParts = entity.getMultipart().getParts();
        Assertions.assertNotNull(bodyParts);
        Assertions.assertEquals(5, bodyParts.size());
    }


    @Test
    public void testMultipartCustomContentType() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.APPLICATION_XML)
                .setBoundary("blah-blah")
                .setCharset(StandardCharsets.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("application/xml; boundary=blah-blah; charset=UTF-8", entity.getContentType());
    }

    @Test
    public void testMultipartContentTypeParameter() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA.withParameters(
                        new BasicNameValuePair("boundary", "yada-yada"),
                        new BasicNameValuePair("charset", "ascii")))
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/form-data; boundary=yada-yada; charset=US-ASCII", entity.getContentType());
        Assertions.assertEquals("yada-yada", entity.getMultipart().boundary);
        Assertions.assertEquals(StandardCharsets.US_ASCII, entity.getMultipart().charset);
    }

    @Test
    public void testMultipartCustomContentTypeParameterOverrides() throws Exception {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA.withParameters(
                        new BasicNameValuePair("boundary", "yada-yada"),
                        new BasicNameValuePair("charset", "ascii"),
                        new BasicNameValuePair("my", "stuff")))
                .setBoundary("blah-blah")
                .setCharset(StandardCharsets.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/form-data; boundary=blah-blah; charset=UTF-8; my=stuff",
                entity.getContentType());
    }

    @Test
    public void testMultipartCustomContentTypeUsingAddParameter() {
        final MultipartEntityBuilder eb = MultipartEntityBuilder.create();
        eb.setMimeSubtype("related");
        eb.addParameter(new BasicNameValuePair("boundary", "yada-yada"));
        eb.addParameter(new BasicNameValuePair("charset", "ascii"));
        eb.addParameter(new BasicNameValuePair("my", "stuff"));
        eb.buildEntity();
        final MultipartFormEntity entity =  eb.buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/related; boundary=yada-yada; charset=US-ASCII; my=stuff",
                entity.getContentType());
    }

    @Test
    public void testMultipartWriteTo() throws Exception {
        final String helloWorld = "hello world";
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME, helloWorld));
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setStrictMode()
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody("hello world", ContentType.TEXT_PLAIN))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .buildEntity();


        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        Assertions.assertEquals("--xxxxxxxxxxxxxxxxxxxxxxxx\r\n" +
                "Content-Disposition: multipart/form-data; name=\"test\"; filename=\"hello world\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                helloWorld + "\r\n" +
                "--xxxxxxxxxxxxxxxxxxxxxxxx--\r\n", out.toString(StandardCharsets.US_ASCII.name()));
    }

    @Test
    public void testMultipartWriteToRFC7578Mode() throws Exception {
        final String helloWorld = "hello \u03BA\u03CC\u03C3\u03BC\u03B5!%";
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME, helloWorld));

        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.EXTENDED)
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody(helloWorld, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .buildEntity();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        Assertions.assertEquals("--xxxxxxxxxxxxxxxxxxxxxxxx\r\n" +
                "Content-Disposition: multipart/form-data; name=\"test\"; filename=\"hello%20%CE%BA%CF%8C%CF%83%CE%BC%CE%B5!%25\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "hello \u00ce\u00ba\u00cf\u008c\u00cf\u0083\u00ce\u00bc\u00ce\u00b5!%\r\n" +
                "--xxxxxxxxxxxxxxxxxxxxxxxx--\r\n", out.toString(StandardCharsets.ISO_8859_1.name()));
    }

    @Test
    public void testMultipartWriteToRFC6532Mode() throws Exception {
        final String helloWorld = "hello \u03BA\u03CC\u03C3\u03BC\u03B5!%";
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME, helloWorld));

        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.EXTENDED)
                .setContentType(ContentType.create("multipart/other"))
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody(helloWorld, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .buildEntity();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        Assertions.assertEquals("--xxxxxxxxxxxxxxxxxxxxxxxx\r\n" +
                "Content-Disposition: multipart/form-data; name=\"test\"; " +
                "filename=\"hello \u00ce\u00ba\u00cf\u008c\u00cf\u0083\u00ce\u00bc\u00ce\u00b5!%\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "hello \u00ce\u00ba\u00cf\u008c\u00cf\u0083\u00ce\u00bc\u00ce\u00b5!%\r\n" +
                "--xxxxxxxxxxxxxxxxxxxxxxxx--\r\n", out.toString(StandardCharsets.ISO_8859_1.name()));
    }

    @Test
    public void testMultipartWriteToWithPreambleAndEpilogue() throws Exception {
        final String helloWorld = "hello \u03BA\u03CC\u03C3\u03BC\u03B5!%";
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME, helloWorld));

        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.EXTENDED)
                .setContentType(ContentType.create("multipart/other"))
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody(helloWorld, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .addPreamble("This is the preamble.")
                .addEpilogue("This is the epilogue.")
                .buildEntity();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        Assertions.assertEquals("This is the preamble.\r\n" +
                "--xxxxxxxxxxxxxxxxxxxxxxxx\r\n" +
                "Content-Disposition: multipart/form-data; name=\"test\"; " +
                "filename=\"hello \u00ce\u00ba\u00cf\u008c\u00cf\u0083\u00ce\u00bc\u00ce\u00b5!%\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "hello \u00ce\u00ba\u00cf\u008c\u00cf\u0083\u00ce\u00bc\u00ce\u00b5!%\r\n" +
                "--xxxxxxxxxxxxxxxxxxxxxxxx--\r\n" +
                "This is the epilogue.\r\n", out.toString(StandardCharsets.ISO_8859_1.name()));
    }

    @Test
    public void testMultipartWriteToRFC7578ModeWithFilenameStar() throws Exception {
        final String helloWorld = "hello \u03BA\u03CC\u03C3\u03BC\u03B5!%";
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME_START, helloWorld));

        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.EXTENDED)
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody(helloWorld, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .buildEntity();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        Assertions.assertEquals("--xxxxxxxxxxxxxxxxxxxxxxxx\r\n" +
                "Content-Disposition: multipart/form-data; name=\"test\"; filename*=\"UTF-8''hello%20%CE%BA%CF%8C%CF%83%CE%BC%CE%B5!%25\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "hello \u00ce\u00ba\u00cf\u008c\u00cf\u0083\u00ce\u00bc\u00ce\u00b5!%\r\n" +
                "--xxxxxxxxxxxxxxxxxxxxxxxx--\r\n", out.toString(StandardCharsets.ISO_8859_1.name()));
    }

}
