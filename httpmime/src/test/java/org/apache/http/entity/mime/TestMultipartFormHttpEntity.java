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
import java.io.ByteArrayOutputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.james.mime4j.util.CharsetUtil;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestMultipartFormHttpEntity extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestMultipartFormHttpEntity(final String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestMultipartFormHttpEntity.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestMultipartFormHttpEntity.class);
    }

    public void testExplictContractorParams() throws Exception {
        MultipartEntity entity = new MultipartEntity(
                HttpMultipartMode.BROWSER_COMPATIBLE,
                "whatever",
                CharsetUtil.getCharset("UTF-8"));

        assertNull(entity.getContentEncoding());
        assertNotNull(entity.getContentType());
        Header header = entity.getContentType();
        HeaderElement[] elems = header.getElements();
        assertNotNull(elems);
        assertEquals(1, elems.length);

        HeaderElement elem = elems[0];
        assertEquals("multipart/form-data", elem.getName());
        NameValuePair p1 = elem.getParameterByName("boundary");
        assertNotNull(p1);
        assertEquals("whatever", p1.getValue());
        NameValuePair p2 = elem.getParameterByName("charset");
        assertNotNull(p2);
        assertEquals("UTF-8", p2.getValue());
    }

    public void testImplictContractorParams() throws Exception {
        MultipartEntity entity = new MultipartEntity();
        assertNull(entity.getContentEncoding());
        assertNotNull(entity.getContentType());
        Header header = entity.getContentType();
        HeaderElement[] elems = header.getElements();
        assertNotNull(elems);
        assertEquals(1, elems.length);

        HeaderElement elem = elems[0];
        assertEquals("multipart/form-data", elem.getName());
        NameValuePair p1 = elem.getParameterByName("boundary");
        assertNotNull(p1);

        String boundary = p1.getValue();
        assertNotNull(boundary);

        assertTrue(boundary.length() >= 30);
        assertTrue(boundary.length() <= 40);

        NameValuePair p2 = elem.getParameterByName("charset");
        assertNull(p2);
    }

    public void testRepeatable() throws Exception {
        MultipartEntity entity = new MultipartEntity();
        entity.addPart("p1", new StringBody("blah blah"));
        entity.addPart("p2", new StringBody("yada yada"));
        assertTrue(entity.isRepeatable());
        assertFalse(entity.isChunked());
        assertFalse(entity.isStreaming());

        long len = entity.getContentLength();
        assertTrue(len == entity.getContentLength());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();

        byte[] bytes = out.toByteArray();
        assertNotNull(bytes);
        assertTrue(bytes.length == len);

        assertTrue(len == entity.getContentLength());

        out = new ByteArrayOutputStream();
        entity.writeTo(out);
        out.close();

        bytes = out.toByteArray();
        assertNotNull(bytes);
        assertTrue(bytes.length == len);
    }

    public void testNonRepeatable() throws Exception {
        MultipartEntity entity = new MultipartEntity();
        entity.addPart("p1", new InputStreamBody(
                new ByteArrayInputStream("blah blah".getBytes()), null));
        entity.addPart("p2", new InputStreamBody(
                new ByteArrayInputStream("yada yada".getBytes()), null));
        assertFalse(entity.isRepeatable());
        assertTrue(entity.isChunked());
        assertTrue(entity.isStreaming());

        assertTrue(entity.getContentLength() == -1);
    }

}
