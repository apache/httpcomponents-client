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

package org.apache.hc.client5.http.impl.cookie;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BasicClientCookie}.
 */
public class TestBasicClientCookie {

    @SuppressWarnings("unused")
    @Test
    public void testConstructor() {
        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        Assertions.assertEquals("name", cookie.getName());
        Assertions.assertEquals("value", cookie.getValue());
        Assertions.assertThrows(NullPointerException.class, () -> new BasicClientCookie(null, null));
    }

    @Test
    public void testCloning() throws Exception {
        final BasicClientCookie orig = new BasicClientCookie("name", "value");
        orig.setDomain("domain");
        orig.setPath("/");
        orig.setAttribute("attrib", "stuff");
        final BasicClientCookie clone = (BasicClientCookie) orig.clone();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
        Assertions.assertEquals(orig.getDomain(), clone.getDomain());
        Assertions.assertEquals(orig.getPath(), clone.getPath());
        Assertions.assertEquals(orig.getAttribute("attrib"), clone.getAttribute("attrib"));
    }

    @Test
    public void testSerialization() throws Exception {
        final BasicClientCookie orig = new BasicClientCookie("name", "value");
        orig.setDomain("domain");
        orig.setPath("/");
        orig.setAttribute("attrib", "stuff");
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BasicClientCookie clone = (BasicClientCookie) inStream.readObject();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
        Assertions.assertEquals(orig.getDomain(), clone.getDomain());
        Assertions.assertEquals(orig.getPath(), clone.getPath());
        Assertions.assertEquals(orig.getAttribute("attrib"), clone.getAttribute("attrib"));
    }

}
