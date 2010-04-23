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

package org.apache.http.impl.cookie;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link BasicClientCookie2}.
 */
public class TestBasicClientCookie2 extends TestCase {

    public TestBasicClientCookie2(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestBasicClientCookie2.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestBasicClientCookie2.class);
    }

    public void testConstructor() {
        BasicClientCookie2 cookie = new BasicClientCookie2("name", "value");
        assertEquals("name", cookie.getName());
        assertEquals("value", cookie.getValue());
        try {
            new BasicClientCookie2(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    public void testCloning() throws Exception {
        BasicClientCookie2 orig = new BasicClientCookie2("name", "value");
        orig.setDomain("domain");
        orig.setPath("/");
        orig.setAttribute("attrib", "stuff");
        orig.setPorts(new int[] {80, 8080});
        BasicClientCookie2 clone = (BasicClientCookie2) orig.clone();
        assertEquals(orig.getName(), clone.getName());
        assertEquals(orig.getValue(), clone.getValue());
        assertEquals(orig.getDomain(), clone.getDomain());
        assertEquals(orig.getPath(), clone.getPath());
        assertEquals(orig.getAttribute("attrib"), clone.getAttribute("attrib"));
        assertEquals(orig.getPorts().length, clone.getPorts().length);
        assertEquals(orig.getPorts()[0], clone.getPorts()[0]);
        assertEquals(orig.getPorts()[1], clone.getPorts()[1]);
    }

    public void testSerialization() throws Exception {
        BasicClientCookie2 orig = new BasicClientCookie2("name", "value");
        orig.setDomain("domain");
        orig.setPath("/");
        orig.setAttribute("attrib", "stuff");
        orig.setPorts(new int[] {80, 8080});
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        BasicClientCookie2 clone = (BasicClientCookie2) instream.readObject();
        assertEquals(orig.getName(), clone.getName());
        assertEquals(orig.getValue(), clone.getValue());
        assertEquals(orig.getDomain(), clone.getDomain());
        assertEquals(orig.getPath(), clone.getPath());
        assertEquals(orig.getAttribute("attrib"), clone.getAttribute("attrib"));
        int[] expected = orig.getPorts();
        int[] clones = clone.getPorts();
        assertNotNull(expected);
        assertNotNull(clones);
        assertEquals(expected.length, clones.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], clones[i]);
        }
    }

}
