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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BasicCookieStore}.
 */
public class TestBasicCookieStore {

    @Test
    public void testBasics() throws Exception {
        final BasicCookieStore store = new BasicCookieStore();
        store.addCookie(new BasicClientCookie("name1", "value1"));
        store.addCookies(new BasicClientCookie[] {new BasicClientCookie("name2", "value2")});
        List<Cookie> list = store.getCookies();
        Assertions.assertNotNull(list);
        Assertions.assertEquals(2, list.size());
        Assertions.assertEquals("name1", list.get(0).getName());
        Assertions.assertEquals("name2", list.get(1).getName());
        store.clear();
        list = store.getCookies();
        Assertions.assertNotNull(list);
        Assertions.assertEquals(0, list.size());
    }

    @Test
    public void testExpiredCookie() throws Exception {
        final BasicCookieStore store = new BasicCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie("name1", "value1");

        final Instant  minus_10_days = Instant.now().minus(10, ChronoUnit.DAYS);
        cookie.setExpiryDate(minus_10_days);
        store.addCookie(cookie);
        final List<Cookie> list = store.getCookies();
        Assertions.assertNotNull(list);
        Assertions.assertEquals(0, list.size());
    }

    @Test
    public void testSerialization() throws Exception {
        final BasicCookieStore orig = new BasicCookieStore();
        orig.addCookie(new BasicClientCookie("name1", "value1"));
        orig.addCookie(new BasicClientCookie("name2", "value2"));
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BasicCookieStore clone = (BasicCookieStore) inStream.readObject();
        final List<Cookie> expected = orig.getCookies();
        final List<Cookie> clones = clone.getCookies();
        Assertions.assertNotNull(expected);
        Assertions.assertNotNull(clones);
        Assertions.assertEquals(expected.size(), clones.size());
        for (int i = 0; i < expected.size(); i++) {
            Assertions.assertEquals(expected.get(i).getName(), clones.get(i).getName());
            Assertions.assertEquals(expected.get(i).getValue(), clones.get(i).getValue());
        }
    }

}
