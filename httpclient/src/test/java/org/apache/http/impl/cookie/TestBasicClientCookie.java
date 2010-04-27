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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BasicClientCookie}.
 */
public class TestBasicClientCookie {

    @Test
    public void testConstructor() {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        Assert.assertEquals("name", cookie.getName());
        Assert.assertEquals("value", cookie.getValue());
        try {
            new BasicClientCookie(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testCloning() throws Exception {
        BasicClientCookie orig = new BasicClientCookie("name", "value");
        orig.setDomain("domain");
        orig.setPath("/");
        orig.setAttribute("attrib", "stuff");
        BasicClientCookie clone = (BasicClientCookie) orig.clone();
        Assert.assertEquals(orig.getName(), clone.getName());
        Assert.assertEquals(orig.getValue(), clone.getValue());
        Assert.assertEquals(orig.getDomain(), clone.getDomain());
        Assert.assertEquals(orig.getPath(), clone.getPath());
        Assert.assertEquals(orig.getAttribute("attrib"), clone.getAttribute("attrib"));
    }

    @Test
    public void testSerialization() throws Exception {
        BasicClientCookie orig = new BasicClientCookie("name", "value");
        orig.setDomain("domain");
        orig.setPath("/");
        orig.setAttribute("attrib", "stuff");
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        BasicClientCookie clone = (BasicClientCookie) instream.readObject();
        Assert.assertEquals(orig.getName(), clone.getName());
        Assert.assertEquals(orig.getValue(), clone.getValue());
        Assert.assertEquals(orig.getDomain(), clone.getDomain());
        Assert.assertEquals(orig.getPath(), clone.getPath());
        Assert.assertEquals(orig.getAttribute("attrib"), clone.getAttribute("attrib"));
    }

}
