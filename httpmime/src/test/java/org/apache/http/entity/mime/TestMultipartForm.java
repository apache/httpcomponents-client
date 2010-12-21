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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.junit.Assert;
import org.junit.Test;

public class TestMultipartForm {

    @Test
    public void testMultipartFormStringParts() throws Exception {
        HttpMultipart multipart = new HttpMultipart("form-data", "foo");
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new StringBody("this stuff"));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new StringBody("that stuff", Charset.forName("UTF-8")));
        FormBodyPart p3 = new FormBodyPart(
                "field3",
                new StringBody("all kind of stuff"));

        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);
        multipart.addBodyPart(p3);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();

        String expected =
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"\r\n" +
            "Content-Type: text/plain; charset=US-ASCII\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "this stuff\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "that stuff\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field3\"\r\n" +
            "Content-Type: text/plain; charset=US-ASCII\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "all kind of stuff\r\n" +
            "--foo--\r\n";
        String s = out.toString("US-ASCII");
        Assert.assertEquals(expected, s);
        Assert.assertEquals(s.length(), multipart.getTotalLength());
    }

    @Test
    public void testMultipartFormBinaryParts() throws Exception {
        File tmpfile = File.createTempFile("tmp", ".bin");
        tmpfile.deleteOnExit();
        Writer writer = new FileWriter(tmpfile);
        try {
            writer.append("some random whatever");
        } finally {
            writer.close();
        }

        HttpMultipart multipart = new HttpMultipart("form-data", "foo");
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new FileBody(tmpfile));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new InputStreamBody(new FileInputStream(tmpfile), "file.tmp"));

        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();

        String expected =
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"; " +
                "filename=\"" + tmpfile.getName() + "\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Transfer-Encoding: binary\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"; " +
                "filename=\"file.tmp\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Transfer-Encoding: binary\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo--\r\n";
        String s = out.toString("US-ASCII");
        Assert.assertEquals(expected, s);
        Assert.assertEquals(-1, multipart.getTotalLength());

        tmpfile.delete();
    }

    @Test
    public void testMultipartFormBrowserCompatible() throws Exception {
        File tmpfile = File.createTempFile("tmp", ".bin");
        tmpfile.deleteOnExit();
        Writer writer = new FileWriter(tmpfile);
        try {
            writer.append("some random whatever");
        } finally {
            writer.close();
        }

        HttpMultipart multipart = new HttpMultipart("form-data", null, "foo", HttpMultipartMode.STRICT);
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new FileBody(tmpfile));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new FileBody(tmpfile, "test-file", "text/plain", "ANSI_X3.4-1968"));
        FormBodyPart p3 = new FormBodyPart(
                "field3",
                new InputStreamBody(new FileInputStream(tmpfile), "file.tmp"));

        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);
        multipart.addBodyPart(p3);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();

        String expected =
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"; " +
                "filename=\"" + tmpfile.getName() + "\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Transfer-Encoding: binary\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"; " +
                "filename=\"test-file\"\r\n" +
            "Content-Type: text/plain; charset=ANSI_X3.4-1968\r\n" +
            "Content-Transfer-Encoding: binary\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field3\"; " +
                "filename=\"file.tmp\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Transfer-Encoding: binary\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo--\r\n";
        String s = out.toString("US-ASCII");
        Assert.assertEquals(expected, s);
        Assert.assertEquals(-1, multipart.getTotalLength());

        tmpfile.delete();
    }

    private static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    private static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(int [] unicodeChars) {
        StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testMultipartFormBrowserCompatibleNonASCIIHeaders() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);

        File tmpfile = File.createTempFile("tmp", ".bin");
        tmpfile.deleteOnExit();
        Writer writer = new FileWriter(tmpfile);
        try {
            writer.append("some random whatever");
        } finally {
            writer.close();
        }

        HttpMultipart multipart = new HttpMultipart("form-data", Charset.forName("UTF-8"), "foo", HttpMultipartMode.BROWSER_COMPATIBLE);
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new InputStreamBody(new FileInputStream(tmpfile), s1 + ".tmp"));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new InputStreamBody(new FileInputStream(tmpfile), s2 + ".tmp"));

        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();

        String expected =
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"; " +
                "filename=\"" + s1 + ".tmp\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"; " +
                "filename=\"" + s2 + ".tmp\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo--\r\n";
        String s = out.toString("UTF-8");
        Assert.assertEquals(expected, s);
        Assert.assertEquals(-1, multipart.getTotalLength());

        tmpfile.delete();
    }

    @Test
    public void testMultipartFormStringPartsMultiCharsets() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);

        HttpMultipart multipart = new HttpMultipart("form-data", "foo");
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new StringBody(s1, Charset.forName("ISO-8859-1")));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new StringBody(s2, Charset.forName("KOI8-R")));

        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);

        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        multipart.writeTo(out1);
        out1.close();

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();

        out2.write((
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"\r\n" +
            "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n").getBytes("US-ASCII"));
        out2.write(s1.getBytes("ISO-8859-1"));
        out2.write(("\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"\r\n" +
            "Content-Type: text/plain; charset=KOI8-R\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n").getBytes("US-ASCII"));
        out2.write(s2.getBytes("KOI8-R"));
        out2.write(("\r\n" +
            "--foo--\r\n").getBytes("US-ASCII"));
        out2.close();

        byte[] actual = out1.toByteArray();
        byte[] expected = out2.toByteArray();

        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            Assert.assertEquals(expected[i], actual[i]);
        }
        Assert.assertEquals(expected.length, multipart.getTotalLength());
    }

}
