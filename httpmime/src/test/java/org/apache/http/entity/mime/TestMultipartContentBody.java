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
import java.nio.charset.Charset;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;

public class TestMultipartContentBody extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestMultipartContentBody(final String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestMultipartContentBody.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestMultipartContentBody.class);
    }

    public void testStringBody() throws Exception {
        StringBody b1 = new StringBody("text");
        assertEquals(4, b1.getContentLength());
        
        Charset defCharset = Charset.defaultCharset();
        
        assertEquals(defCharset.name(), b1.getCharset());
        assertEquals(defCharset.name(), b1.getContentTypeParameters().get("charset"));
        
        assertNull(b1.getFilename());
        assertEquals("text/plain", b1.getMimeType());
        assertEquals("text", b1.getMediaType());
        assertEquals("plain", b1.getSubType());

        assertEquals(MIME.ENC_8BIT, b1.getTransferEncoding());

        StringBody b2 = new StringBody("more text", "text/other", MIME.DEFAULT_CHARSET);
        assertEquals(9, b2.getContentLength());
        assertEquals(MIME.DEFAULT_CHARSET.name(), b2.getCharset());
        assertEquals(MIME.DEFAULT_CHARSET.name(), b2.getContentTypeParameters().get("charset"));
        
        assertNull(b2.getFilename());
        assertEquals("text/other", b2.getMimeType());
        assertEquals("text", b2.getMediaType());
        assertEquals("other", b2.getSubType());

        assertEquals(MIME.ENC_8BIT, b2.getTransferEncoding());
    }

    public void testInputStreamBody() throws Exception {
        byte[] stuff = "Stuff".getBytes("US-ASCII");
        InputStreamBody b1 = new InputStreamBody(new ByteArrayInputStream(stuff), "stuff");
        assertEquals(-1, b1.getContentLength());
        
        assertNull(b1.getCharset());
        assertEquals("stuff", b1.getFilename());
        assertEquals("application/octet-stream", b1.getMimeType());
        assertEquals("application", b1.getMediaType());
        assertEquals("octet-stream", b1.getSubType());

        assertEquals(MIME.ENC_BINARY, b1.getTransferEncoding());

        InputStreamBody b2 = new InputStreamBody(
                new ByteArrayInputStream(stuff), "some/stuff", "stuff");
        assertEquals(-1, b2.getContentLength());
        assertNull(b2.getCharset());
        assertEquals("stuff", b2.getFilename());
        assertEquals("some/stuff", b2.getMimeType());
        assertEquals("some", b2.getMediaType());
        assertEquals("stuff", b2.getSubType());

        assertEquals(MIME.ENC_BINARY, b2.getTransferEncoding());
    }
}
