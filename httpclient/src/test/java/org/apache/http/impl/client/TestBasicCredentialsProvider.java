/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.http.impl.client;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link BasicCredentialsProvider}.
 */
public class TestBasicCredentialsProvider {

    public final static Credentials CREDS1 =
        new UsernamePasswordCredentials("user1", "pass1");
    public final static Credentials CREDS2 =
        new UsernamePasswordCredentials("user2", "pass2");

    public final static AuthScope SCOPE1 =
        new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "realm1");
    public final static AuthScope SCOPE2 =
        new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "realm2");
    public final static AuthScope BOGUS =
        new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "bogus");
    public final static AuthScope DEFSCOPE =
        new AuthScope("host", AuthScope.ANY_PORT, "realm");

    @Test
    public void testBasicCredentialsProviderCredentials() {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(SCOPE1, CREDS1);
        state.setCredentials(SCOPE2, CREDS2);
        Assert.assertEquals(CREDS1, state.getCredentials(SCOPE1));
        Assert.assertEquals(CREDS2, state.getCredentials(SCOPE2));
    }

    @Test
    public void testBasicCredentialsProviderNoCredentials() {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        Assert.assertEquals(null, state.getCredentials(BOGUS));
    }

    @Test
    public void testBasicCredentialsProviderDefaultCredentials() {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(AuthScope.ANY, CREDS1);
        state.setCredentials(SCOPE2, CREDS2);
        Assert.assertEquals(CREDS1, state.getCredentials(BOGUS));
    }

    @Test
    public void testDefaultCredentials() throws Exception {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(AuthScope.ANY, expected);
        Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(got, expected);
    }

    @Test
    public void testRealmCredentials() throws Exception {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(DEFSCOPE, expected);
        Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testHostCredentials() throws Exception {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(
            new AuthScope("host", AuthScope.ANY_PORT, AuthScope.ANY_REALM), expected);
        Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testWrongHostCredentials() throws Exception {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(
            new AuthScope("host1", AuthScope.ANY_PORT, "realm"), expected);
        Credentials got = state.getCredentials(
            new AuthScope("host2", AuthScope.ANY_PORT, "realm"));
        Assert.assertNotSame(expected, got);
    }

    @Test
    public void testWrongRealmCredentials() throws Exception {
        BasicCredentialsProvider state = new BasicCredentialsProvider();
        Credentials cred = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(
            new AuthScope("host", AuthScope.ANY_PORT, "realm1"), cred);
        Credentials got = state.getCredentials(
            new AuthScope("host", AuthScope.ANY_PORT, "realm2"));
        Assert.assertNotSame(cred, got);
    }

    @Test
    public void testScopeMatching() {
        AuthScope authscope1 = new AuthScope("somehost", 80, "somerealm", "somescheme");
        AuthScope authscope2 = new AuthScope("someotherhost", 80, "somerealm", "somescheme");
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
    public void testCredentialsMatching() {
        Credentials creds1 = new UsernamePasswordCredentials("name1", "pass1");
        Credentials creds2 = new UsernamePasswordCredentials("name2", "pass2");
        Credentials creds3 = new UsernamePasswordCredentials("name3", "pass3");

        AuthScope scope1 = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM);
        AuthScope scope2 = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "somerealm");
        AuthScope scope3 = new AuthScope("somehost", AuthScope.ANY_PORT, AuthScope.ANY_REALM);

        BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(scope1, creds1);
        state.setCredentials(scope2, creds2);
        state.setCredentials(scope3, creds3);

        Credentials got = state.getCredentials(
            new AuthScope("someotherhost", 80, "someotherrealm", "basic"));
        Credentials expected = creds1;
        Assert.assertEquals(expected, got);

        got = state.getCredentials(
            new AuthScope("someotherhost", 80, "somerealm", "basic"));
        expected = creds2;
        Assert.assertEquals(expected, got);

        got = state.getCredentials(
            new AuthScope("somehost", 80, "someotherrealm", "basic"));
        expected = creds3;
        Assert.assertEquals(expected, got);
    }

}
