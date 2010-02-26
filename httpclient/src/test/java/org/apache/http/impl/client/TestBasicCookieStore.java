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

package org.apache.http.impl.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.List;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link BasicCookieStore}.
 */
public class TestBasicCookieStore extends TestCase {

    public TestBasicCookieStore(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestBasicCookieStore.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestBasicCookieStore.class);
    }

    public void testBasics() throws Exception {
        BasicCookieStore store = new BasicCookieStore();
        store.addCookie(new BasicClientCookie("name1", "value1"));
        store.addCookies(new BasicClientCookie[] {new BasicClientCookie("name2", "value2")});
        List<Cookie> l = store.getCookies();
        assertNotNull(l);
        assertEquals(2, l.size());
        assertEquals("name1", l.get(0).getName());
        assertEquals("name2", l.get(1).getName());
        store.clear();
        l = store.getCookies();
        assertNotNull(l);
        assertEquals(0, l.size());
    }
    
    public void testExpiredCookie() throws Exception {
        BasicCookieStore store = new BasicCookieStore();
        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -10);
        cookie.setExpiryDate(c.getTime());
        store.addCookie(cookie);
        List<Cookie> l = store.getCookies();
        assertNotNull(l);
        assertEquals(0, l.size());
    }
    
    public void testSerialization() throws Exception {
        BasicCookieStore orig = new BasicCookieStore();
        orig.addCookie(new BasicClientCookie("name1", "value1"));
        orig.addCookie(new BasicClientCookie("name2", "value2"));
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        BasicCookieStore clone = (BasicCookieStore) instream.readObject();
        List<Cookie> expected = orig.getCookies();
        List<Cookie> clones = clone.getCookies();
        assertNotNull(expected);
        assertNotNull(clones);
        assertEquals(expected.size(), clones.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getName(), clones.get(i).getName());
            assertEquals(expected.get(i).getValue(), clones.get(i).getValue());
        }
    }
    
}
