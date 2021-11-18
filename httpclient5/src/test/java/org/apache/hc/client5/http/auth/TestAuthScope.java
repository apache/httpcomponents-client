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
package org.apache.hc.client5.http.auth;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link org.apache.hc.client5.http.auth.AuthScope}.
 */
public class TestAuthScope {

    @Test
    public void testBasics() {
        final AuthScope authscope = new AuthScope("http", "somehost", 80, "somerealm", "SomeScheme");
        Assertions.assertEquals("SomeScheme", authscope.getSchemeName());
        Assertions.assertEquals("http", authscope.getProtocol());
        Assertions.assertEquals("somehost", authscope.getHost());
        Assertions.assertEquals(80, authscope.getPort());
        Assertions.assertEquals("somerealm", authscope.getRealm());
        Assertions.assertEquals("SomeScheme 'somerealm' http://somehost:80", authscope.toString());
    }

    @Test
    public void testByOrigin() {
        final HttpHost host = new HttpHost("http", "somehost", 8080);
        final AuthScope authscope = new AuthScope(host);
        Assertions.assertNull(authscope.getSchemeName());
        Assertions.assertEquals("somehost", authscope.getHost());
        Assertions.assertEquals(8080, authscope.getPort());
        Assertions.assertNull(authscope.getRealm());
        Assertions.assertEquals("http", authscope.getProtocol());
        Assertions.assertEquals("<any auth scheme> <any realm> http://somehost:8080", authscope.toString());
    }

    @Test
    public void testMixedCaseHostname() {
        final AuthScope authscope = new AuthScope("SomeHost", 80);
        Assertions.assertNull(authscope.getSchemeName());
        Assertions.assertEquals("somehost", authscope.getHost());
        Assertions.assertEquals(80, authscope.getPort());
        Assertions.assertNull(authscope.getRealm());
        Assertions.assertEquals("<any auth scheme> <any realm> <any protocol>://somehost:80", authscope.toString());
    }

    @Test
    public void testByOriginMixedCaseHostname() throws Exception {
        final HttpHost host = new HttpHost("http", "SomeHost", 8080);
        final AuthScope authscope = new AuthScope(host);
        Assertions.assertEquals("somehost", authscope.getHost());
    }

    @Test
    public void testBasicsAllOptional() {
        final AuthScope authscope = new AuthScope(null, null, -1, null, null);
        Assertions.assertNull(authscope.getSchemeName());
        Assertions.assertNull(authscope.getHost());
        Assertions.assertEquals(-1, authscope.getPort());
        Assertions.assertNull(authscope.getRealm());
        Assertions.assertEquals("<any auth scheme> <any realm> <any protocol>://<any host>:<any port>", authscope.toString());
    }

    @Test
    public void testScopeMatching() {
        final AuthScope authscope1 = new AuthScope("http", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope2 = new AuthScope("http", "someotherhost", 80, "somerealm", "somescheme");
        Assertions.assertTrue(authscope1.match(authscope2) < 0);

        int m1 = authscope1.match(new AuthScope(null, null, -1, null, "somescheme"));
        int m2 = authscope1.match(new AuthScope(null, null, -1, "somerealm", null));
        Assertions.assertTrue(m2 > m1);

        m1 = authscope1.match(new AuthScope(null, null, -1, null, "somescheme"));
        m2 = authscope1.match(new AuthScope(null, null, -1, "somerealm", null));
        Assertions.assertTrue(m2 > m1);

        m1 = authscope1.match(new AuthScope(null, null, -1, "somerealm", "somescheme"));
        m2 = authscope1.match(new AuthScope(null, null, 80, null, null));
        Assertions.assertTrue(m2 > m1);

        m1 = authscope1.match(new AuthScope(null, null, 80, "somerealm", "somescheme"));
        m2 = authscope1.match(new AuthScope(null, "somehost", -1, null, null));
        Assertions.assertTrue(m2 > m1);

        m1 = authscope1.match(new AuthScope(null, null, 80, "somerealm", "somescheme"));
        m2 = authscope1.match(new AuthScope(null, "somehost", -1, null, null));
        Assertions.assertTrue(m2 > m1);

        m1 = authscope1.match(new AuthScope(null, null, -1, null, null));
        m2 = authscope1.match(new AuthScope(null, null, -1, null, "somescheme"));
        Assertions.assertTrue(m2 > m1);
    }

    @Test
    public void testEquals() {
        final AuthScope authscope1 = new AuthScope("http", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope2 = new AuthScope("http", "someotherhost", 80, "somerealm", "somescheme");
        final AuthScope authscope3 = new AuthScope("http", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope4 = new AuthScope("http", "somehost", 8080, "somerealm", "somescheme");
        final AuthScope authscope5 = new AuthScope("http", "somehost", 80, "someotherrealm", "somescheme");
        final AuthScope authscope6 = new AuthScope("http", "somehost", 80, "somerealm", "someotherscheme");
        final AuthScope authscope7 = new AuthScope("https", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope8 = new AuthScope("https", "somehost", 80, "somerealm", "SomeScheme");
        Assertions.assertEquals(authscope1, authscope1);
        Assertions.assertNotEquals(authscope1, authscope2);
        Assertions.assertEquals(authscope1, authscope3);
        Assertions.assertNotEquals(authscope1, authscope4);
        Assertions.assertNotEquals(authscope1, authscope5);
        Assertions.assertNotEquals(authscope1, authscope6);
        Assertions.assertNotEquals(authscope1, authscope7);
        Assertions.assertEquals(authscope7, authscope8);
    }

    @Test
    public void testHash() {
        final AuthScope authscope1 = new AuthScope("http", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope2 = new AuthScope("http", "someotherhost", 80, "somerealm", "somescheme");
        final AuthScope authscope3 = new AuthScope("http", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope4 = new AuthScope("http", "somehost", 8080, "somerealm", "somescheme");
        final AuthScope authscope5 = new AuthScope("http", "somehost", 80, "someotherrealm", "somescheme");
        final AuthScope authscope6 = new AuthScope("http", "somehost", 80, "somerealm", "someotherscheme");
        final AuthScope authscope7 = new AuthScope("https", "somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope8 = new AuthScope("https", "somehost", 80, "somerealm", "SomeScheme");
        Assertions.assertEquals(authscope1.hashCode(), authscope1.hashCode());
        Assertions.assertNotEquals(authscope1.hashCode(), authscope2.hashCode());
        Assertions.assertEquals(authscope1.hashCode(), authscope3.hashCode());
        Assertions.assertNotEquals(authscope1.hashCode(), authscope4.hashCode());
        Assertions.assertNotEquals(authscope1.hashCode(), authscope5.hashCode());
        Assertions.assertNotEquals(authscope1.hashCode(), authscope6.hashCode());
        Assertions.assertNotEquals(authscope1.hashCode(), authscope7.hashCode());
        Assertions.assertEquals(authscope7.hashCode(), authscope8.hashCode());
    }

}
