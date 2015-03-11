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
package org.apache.http.auth;

import org.apache.http.HttpHost;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link org.apache.http.auth.AuthScope}.
 */
public class TestAuthScope {

    @Test
    public void testBasics() {
        final AuthScope authscope = new AuthScope("somehost", 80, "somerealm", "somescheme");
        Assert.assertEquals("SOMESCHEME", authscope.getScheme());
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(80, authscope.getPort());
        Assert.assertEquals("somerealm", authscope.getRealm());
        Assert.assertEquals("SOMESCHEME 'somerealm'@somehost:80", authscope.toString());
    }

    @Test
    public void testBasicsOptionalRealm() {
        final AuthScope authscope = new AuthScope("somehost", 80, AuthScope.ANY_REALM, "somescheme");
        Assert.assertEquals("SOMESCHEME", authscope.getScheme());
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(80, authscope.getPort());
        Assert.assertEquals(null, authscope.getRealm());
        Assert.assertEquals("SOMESCHEME <any realm>@somehost:80", authscope.toString());
    }

    @Test
    public void testBasicsOptionalScheme() {
        final AuthScope authscope = new AuthScope("somehost", 80, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
        Assert.assertEquals(null, authscope.getScheme());
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(80, authscope.getPort());
        Assert.assertEquals(null, authscope.getRealm());
        Assert.assertEquals("<any realm>@somehost:80", authscope.toString());
    }

    @Test
    public void testBasicsOptionalPort() {
        final AuthScope authscope = new AuthScope("somehost", AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
        Assert.assertEquals(null, authscope.getScheme());
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(-1, authscope.getPort());
        Assert.assertEquals(null, authscope.getRealm());
        Assert.assertEquals("<any realm>@somehost", authscope.toString());
    }

    @Test
    public void testByOrigin() {
        final HttpHost host = new HttpHost("somehost", 8080, "http");
        final AuthScope authscope = new AuthScope(host);
        Assert.assertEquals(null, authscope.getScheme());
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(8080, authscope.getPort());
        Assert.assertEquals(null, authscope.getRealm());
        Assert.assertEquals(host, authscope.getOrigin());
        Assert.assertEquals("<any realm>@somehost:8080", authscope.toString());
    }

    @Test
    public void testMixedCaseHostname() {
        final AuthScope authscope = new AuthScope("SomeHost", 80);
        Assert.assertEquals(null, authscope.getScheme());
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(80, authscope.getPort());
        Assert.assertEquals(null, authscope.getRealm());
        Assert.assertEquals("<any realm>@somehost:80", authscope.toString());
    }

    public void testByOriginMixedCaseHostname() throws Exception {
        final HttpHost host = new HttpHost("SomeHost", 8080, "http");
        final AuthScope authscope = new AuthScope(host);
        Assert.assertEquals("somehost", authscope.getHost());
        Assert.assertEquals(host, authscope.getOrigin());
    }

    @Test
    public void testBasicsOptionalHost() {
        final AuthScope authscope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
        Assert.assertEquals(null, authscope.getScheme());
        Assert.assertEquals(null, authscope.getHost());
        Assert.assertEquals(-1, authscope.getPort());
        Assert.assertEquals(null, authscope.getRealm());
        Assert.assertEquals("<any realm>", authscope.toString());
    }

    @Test
    public void testScopeMatching() {
        final AuthScope authscope1 = new AuthScope("somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope2 = new AuthScope("someotherhost", 80, "somerealm", "somescheme");
        Assert.assertTrue(authscope1.match(authscope2) < 0);

        int m1 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "somescheme"));
        int m2 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "somerealm", AuthScope.ANY_SCHEME));
        Assert.assertTrue(m2 > m1);

        m1 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "somescheme"));
        m2 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "somerealm", AuthScope.ANY_SCHEME));
        Assert.assertTrue(m2 > m1);

        m1 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "somerealm", "somescheme"));
        m2 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, 80, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME));
        Assert.assertTrue(m2 > m1);

        m1 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, 80, "somerealm", "somescheme"));
        m2 = authscope1.match(
                new AuthScope("somehost", AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME));
        Assert.assertTrue(m2 > m1);

        m1 = authscope1.match(AuthScope.ANY);
        m2 = authscope1.match(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "somescheme"));
        Assert.assertTrue(m2 > m1);
    }

    @Test
    public void testEquals() {
        final AuthScope authscope1 = new AuthScope("somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope2 = new AuthScope("someotherhost", 80, "somerealm", "somescheme");
        final AuthScope authscope3 = new AuthScope("somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope4 = new AuthScope("somehost", 8080, "somerealm", "somescheme");
        final AuthScope authscope5 = new AuthScope("somehost", 80, "someotherrealm", "somescheme");
        final AuthScope authscope6 = new AuthScope("somehost", 80, "somerealm", "someotherscheme");
        Assert.assertTrue(authscope1.equals(authscope1));
        Assert.assertFalse(authscope1.equals(authscope2));
        Assert.assertTrue(authscope1.equals(authscope3));
        Assert.assertFalse(authscope1.equals(authscope4));
        Assert.assertFalse(authscope1.equals(authscope5));
        Assert.assertFalse(authscope1.equals(authscope6));
    }

    @Test
    public void testHash() {
        final AuthScope authscope1 = new AuthScope("somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope2 = new AuthScope("someotherhost", 80, "somerealm", "somescheme");
        final AuthScope authscope3 = new AuthScope("somehost", 80, "somerealm", "somescheme");
        final AuthScope authscope4 = new AuthScope("somehost", 8080, "somerealm", "somescheme");
        final AuthScope authscope5 = new AuthScope("somehost", 80, "someotherrealm", "somescheme");
        final AuthScope authscope6 = new AuthScope("somehost", 80, "somerealm", "someotherscheme");
        Assert.assertTrue(authscope1.hashCode() == authscope1.hashCode());
        Assert.assertFalse(authscope1.hashCode() == authscope2.hashCode());
        Assert.assertTrue(authscope1.hashCode() == authscope3.hashCode());
        Assert.assertFalse(authscope1.hashCode() == authscope4.hashCode());
        Assert.assertFalse(authscope1.hashCode() == authscope5.hashCode());
        Assert.assertFalse(authscope1.hashCode() == authscope6.hashCode());
    }

}
