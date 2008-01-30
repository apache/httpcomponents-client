/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.client.mime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.client.mime.content.FileBody;
import org.apache.http.client.mime.content.InputStreamBody;
import org.apache.http.client.mime.content.StringBody;
import org.apache.james.mime4j.field.Field;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.Header;
import org.apache.james.mime4j.message.Message;
import org.apache.james.mime4j.message.Multipart;

public class TestMultipartForm extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestMultipartForm(final String testName) throws IOException {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestMultipartForm.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestMultipartForm.class);
    }

    public void testMultipartFormLowLevel() throws Exception {
        Message message = new Message();
        Header header = new Header();
        header.addField(
                Field.parse("Content-Type: multipart/form-data; boundary=foo"));
        message.setHeader(header);
        
        Multipart multipart = new HttpMultipart();
        multipart.setParent(message);
        BodyPart p1 = new BodyPart();
        Header h1 = new Header();
        h1.addField(Field.parse("Content-Type: text/plain"));
        p1.setHeader(h1);
        p1.setBody(new StringBody("this stuff"));
        BodyPart p2 = new BodyPart();
        Header h2 = new Header();
        h2.addField(Field.parse("Content-Type: text/plain"));
        p2.setHeader(h2);
        p2.setBody(new StringBody("that stuff"));
        BodyPart p3 = new BodyPart();
        Header h3 = new Header();
        h3.addField(Field.parse("Content-Type: text/plain"));
        p3.setHeader(h3);
        p3.setBody(new StringBody("all kind of stuff"));

        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);
        multipart.addBodyPart(p3);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();
        
        String expected = "\r\n" + 
            "--foo\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "this stuff\r\n" +
            "--foo\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "that stuff\r\n" +
            "--foo\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "all kind of stuff\r\n" +
            "--foo--\r\n" +
            "\r\n";
        String s = out.toString("US-ASCII");
        assertEquals(expected, s);
    }
    
    public void testMultipartFormStringParts() throws Exception {
        Message message = new Message();
        Header header = new Header();
        header.addField(
                Field.parse("Content-Type: multipart/form-data; boundary=foo"));
        message.setHeader(header);
        
        Multipart multipart = new HttpMultipart();
        multipart.setParent(message);
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new StringBody("this stuff"));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new StringBody("that stuff", Charset.forName("US-ASCII")));
        FormBodyPart p3 = new FormBodyPart(
                "field3",
                new StringBody("all kind of stuff"));
        
        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);
        multipart.addBodyPart(p3);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();
        
        String expected = "\r\n" + 
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "this stuff\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"\r\n" +
            "Content-Type: text/plain; charset=US-ASCII\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "that stuff\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field3\"\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "all kind of stuff\r\n" +
            "--foo--\r\n" +
            "\r\n";
        String s = out.toString("US-ASCII");
        assertEquals(expected, s);
    }

    public void testMultipartFormBinaryParts() throws Exception {
        Message message = new Message();
        Header header = new Header();
        header.addField(
                Field.parse("Content-Type: multipart/form-data; boundary=foo"));
        message.setHeader(header);

        File tmpfile = File.createTempFile("tmp", ".bin");
        tmpfile.deleteOnExit();
        Writer writer = new FileWriter(tmpfile);
        try {
            writer.append("some random whatever");
        } finally {
            writer.close();
        }
        
        Multipart multipart = new HttpMultipart();
        multipart.setParent(message);
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
        
        String expected = "\r\n" + 
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
            "--foo--\r\n" +
            "\r\n";
        String s = out.toString("US-ASCII");
        assertEquals(expected, s);
        
        tmpfile.delete();
    }

    public void testMultipartFormBrowserCompatible() throws Exception {
        Message message = new Message();
        Header header = new Header();
        header.addField(
                Field.parse("Content-Type: multipart/form-data; boundary=foo"));
        message.setHeader(header);

        File tmpfile = File.createTempFile("tmp", ".bin");
        tmpfile.deleteOnExit();
        Writer writer = new FileWriter(tmpfile);
        try {
            writer.append("some random whatever");
        } finally {
            writer.close();
        }
        
        HttpMultipart multipart = new HttpMultipart();
        multipart.setParent(message);
        FormBodyPart p1 = new FormBodyPart(
                "field1",
                new FileBody(tmpfile));
        FormBodyPart p2 = new FormBodyPart(
                "field2",
                new InputStreamBody(new FileInputStream(tmpfile), "file.tmp"));
        
        multipart.addBodyPart(p1);
        multipart.addBodyPart(p2);
        
        multipart.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        out.close();
        
        String expected = "\r\n" + 
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field1\"; " +
                "filename=\"" + tmpfile.getName() + "\"\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo\r\n" +
            "Content-Disposition: form-data; name=\"field2\"; " +
                "filename=\"file.tmp\"\r\n" +
            "\r\n" +
            "some random whatever\r\n" +
            "--foo--\r\n" +
            "\r\n";
        String s = out.toString("US-ASCII");
        assertEquals(expected, s);
        
        tmpfile.delete();
    }

}
