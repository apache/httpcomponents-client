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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.ParserCursor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestMultipartEntityBuilder {

    @TempDir
    Path tempDir;

    @Test
    void testBasics() {
        final MultipartFormEntity entity = MultipartEntityBuilder.create().buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertTrue(entity.getMultipart() instanceof HttpStrictMultipart);
        Assertions.assertEquals(0, entity.getMultipart().getParts().size());
    }

    @Test
    void testMultipartOptions() {
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
    void testAddBodyPartsFile() {
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
    void testAddBodyPartsPath() throws IOException {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .addTextBody("p1", "stuff")
                .addBinaryBody("p2", Files.createTempFile(tempDir, "test-", ".bin"))
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
    void testMultipartCustomContentType() {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.APPLICATION_XML)
                .setBoundary("blah-blah")
                .setCharset(StandardCharsets.UTF_8)
                .setLaxMode()
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("application/xml; charset=UTF-8; boundary=blah-blah", entity.getContentType());
    }

    @Test
    void testMultipartContentTypeParameter() {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA.withParameters(
                        new BasicNameValuePair("boundary", "yada-yada"),
                        new BasicNameValuePair("charset", "ascii")))
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/form-data; boundary=yada-yada; charset=ascii", entity.getContentType());
        Assertions.assertEquals("yada-yada", entity.getMultipart().boundary);
        Assertions.assertEquals(StandardCharsets.US_ASCII, entity.getMultipart().charset);
    }

    @Test
    void testMultipartDefaultContentTypeOmitsCharset() {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setCharset(StandardCharsets.UTF_8)
                .setBoundary("yada-yada")
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/mixed; boundary=yada-yada", entity.getContentType());
        Assertions.assertEquals("yada-yada", entity.getMultipart().boundary);
    }

    @Test
    void testMultipartFormDataContentTypeOmitsCharset() {
        // Note: org.apache.hc.core5.http.ContentType.MULTIPART_FORM_DATA uses StandardCharsets.ISO_8859_1,
        // so we create a custom ContentType here
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setContentType(ContentType.create("multipart/form-data"))
                .setCharset(StandardCharsets.UTF_8)
                .setBoundary("yada-yada")
                .buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/form-data; boundary=yada-yada", entity.getContentType());
        Assertions.assertEquals("yada-yada", entity.getMultipart().boundary);
    }

    @Test
    void testMultipartCustomContentTypeParameterOverrides() {
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
        Assertions.assertEquals("multipart/form-data; boundary=blah-blah; charset=ascii; my=stuff",
                entity.getContentType());
    }

    @Test
    void testMultipartCustomContentTypeUsingAddParameter() {
        final MultipartEntityBuilder eb = MultipartEntityBuilder.create();
        eb.setMimeSubtype("related");
        eb.addParameter(new BasicNameValuePair("boundary", "yada-yada"));
        eb.addParameter(new BasicNameValuePair("charset", "ascii"));
        eb.addParameter(new BasicNameValuePair("my", "stuff"));
        eb.buildEntity();
        final MultipartFormEntity entity = eb.buildEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals("multipart/related; boundary=yada-yada; charset=ascii; my=stuff",
                entity.getContentType());
    }

    @Test
    void testMultipartWriteTo() throws Exception {
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
    void testMultipartWriteToRFC7578Mode() throws Exception {
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
    void testMultipartWriteToRFC6532Mode() throws Exception {
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
    void testMultipartWriteToWithPreambleAndEpilogue() throws Exception {
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
    void testMultipartWriteToRFC7578ModeWithFilenameStar() throws Exception {
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

    @Test
    void testRandomBoundary() {
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .buildEntity();
        final NameValuePair boundaryParam = extractBoundary(entity.getContentType(), "multipart/mixed");
        final String boundary = boundaryParam.getValue();
        Assertions.assertNotNull(boundary);
        Assertions.assertEquals(56, boundary.length());
        Assertions.assertTrue(boundary.startsWith("httpclient_boundary_"));
        Assertions.assertTrue(boundary.substring(20).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void testRandomBoundaryWriteTo() throws Exception {
        final String helloWorld = "hello world";
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME, helloWorld));
        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setStrictMode()
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody("hello world", ContentType.TEXT_PLAIN))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .buildEntity();

        final NameValuePair boundaryParam = extractBoundary(entity.getContentType(), "multipart/form-data");
        final String boundary = boundaryParam.getValue();
        Assertions.assertNotNull(boundary);
        Assertions.assertEquals(56, boundary.length());
        Assertions.assertTrue(boundary.startsWith("httpclient_boundary_"));
        Assertions.assertTrue(boundary.substring(20).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        Assertions.assertEquals("--" + boundary + "\r\n" +
                "Content-Disposition: multipart/form-data; name=\"test\"; filename=\"hello world\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                helloWorld + "\r\n" +
                "--" + boundary + "--\r\n", out.toString(StandardCharsets.US_ASCII.name()));
    }

    private NameValuePair extractBoundary(final String contentType, final String expectedName) {
        final BasicHeaderValueParser parser = BasicHeaderValueParser.INSTANCE;
        final ParserCursor cursor = new ParserCursor(0, contentType.length());
        final HeaderElement elem = parser.parseHeaderElement(contentType, cursor);
        Assertions.assertEquals(expectedName, elem.getName());
        return elem.getParameterByName("boundary");
    }

    @Test
    void testMultipartWriteToRFC7578ModeWithFilenameStarPreEncodedPassThrough() throws Exception {
        final String body = "hi";
        // Pre-encoded RFC 5987 value (as produced by a previous stage)
        final String preEncoded = "UTF-8''%F0%9F%90%99_inline-%E5%9B%BE%E5%83%8F_%E6%96%87%E4%BB%B6.png";

        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_NAME, "test"));
        // Provide pre-encoded value directly to filename* param
        parameters.add(new BasicNameValuePair(MimeConsts.FIELD_PARAM_FILENAME_START, preEncoded));

        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.EXTENDED)
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addPart(new FormBodyPartBuilder()
                        .setName("test")
                        .setBody(new StringBody(body, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
                        .addField("Content-Disposition", "multipart/form-data", parameters)
                        .build())
                .buildEntity();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        final String wire = out.toString(StandardCharsets.ISO_8859_1.name());

        // Pass-through EXACTLY the given value (no second prefix, no %25-escaping)
        Assertions.assertTrue(wire.contains("filename*=\"" + preEncoded + "\""));
        Assertions.assertFalse(wire.contains("UTF-8''UTF-8%27%27"));
        Assertions.assertFalse(wire.contains("%25F0%9F%90%99")); // octopus emoji must not be re-escaped as %25F0...
    }

    @Test
    void testExtendedModeAddBinaryBodyAddsFilenameAndFilenameStar_NoDoubleEncoding() throws Exception {
        // Non-ASCII filename to trigger RFC 5987 behavior
        final String filename = "🐙_图像_文件.png";
        // Expected percent-encoded for both filename and filename*
        final String pct = "%F0%9F%90%99_%E5%9B%BE%E5%83%8F_%E6%96%87%E4%BB%B6.png";

        final MultipartFormEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.EXTENDED)
                .setBoundary("xxxxxxxxxxxxxxxxxxxxxxxx")
                .addBinaryBody("attachments", new byte[]{1, 2}, ContentType.IMAGE_PNG, filename)
                .buildEntity();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();
        final String wire = out.toString(StandardCharsets.ISO_8859_1.name());

        // Base header
        Assertions.assertTrue(wire.contains("Content-Disposition: form-data; name=\"attachments\""));

        // filename param (percent-encoded for ASCII transport)
        Assertions.assertTrue(wire.contains("filename=\"" + pct + "\""));

        // filename* param (single RFC 5987 value, no double prefix / no %25-escaping)
        Assertions.assertTrue(wire.contains("filename*=\"UTF-8''" + pct + "\""));

        // Guard against regressions
        Assertions.assertFalse(wire.contains("UTF-8''UTF-8%27%27"));
        Assertions.assertFalse(wire.contains("%25F0%9F%90%99"));
    }


}
