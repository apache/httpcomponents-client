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

import org.apache.http.HttpHost;
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
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(SCOPE1, CREDS1);
        state.setCredentials(SCOPE2, CREDS2);
        Assert.assertEquals(CREDS1, state.getCredentials(SCOPE1));
        Assert.assertEquals(CREDS2, state.getCredentials(SCOPE2));
    }

    @Test
    public void testBasicCredentialsProviderNoCredentials() {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        Assert.assertEquals(null, state.getCredentials(BOGUS));
    }

    @Test
    public void testBasicCredentialsProviderDefaultCredentials() {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(AuthScope.ANY, CREDS1);
        state.setCredentials(SCOPE2, CREDS2);
        Assert.assertEquals(CREDS1, state.getCredentials(BOGUS));
    }

    @Test
    public void testDefaultCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(AuthScope.ANY, expected);
        final Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(got, expected);
    }

    @Test
    public void testRealmCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(DEFSCOPE, expected);
        final Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testHostCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(
            new AuthScope("host", AuthScope.ANY_PORT, AuthScope.ANY_REALM), expected);
        final Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testWrongHostCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(
            new AuthScope("host1", AuthScope.ANY_PORT, "realm"), expected);
        final Credentials got = state.getCredentials(
            new AuthScope("host2", AuthScope.ANY_PORT, "realm"));
        Assert.assertNotSame(expected, got);
    }

    @Test
    public void testWrongRealmCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials cred = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(
            new AuthScope("host", AuthScope.ANY_PORT, "realm1"), cred);
        final Credentials got = state.getCredentials(
            new AuthScope("host", AuthScope.ANY_PORT, "realm2"));
        Assert.assertNotSame(cred, got);
    }

    @Test
    public void testMixedCaseHostname() throws Exception {
        final HttpHost httpHost = new HttpHost("hOsT", 80);
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass");
        state.setCredentials(new AuthScope(httpHost), expected);
        final Credentials got = state.getCredentials(DEFSCOPE);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testCredentialsMatching() {
        final Credentials creds1 = new UsernamePasswordCredentials("name1", "pass1");
        final Credentials creds2 = new UsernamePasswordCredentials("name2", "pass2");
        final Credentials creds3 = new UsernamePasswordCredentials("name3", "pass3");

        final AuthScope scope1 = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM);
        final AuthScope scope2 = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "somerealm");
        final AuthScope scope3 = new AuthScope("somehost", AuthScope.ANY_PORT, AuthScope.ANY_REALM);

        final BasicCredentialsProvider state = new BasicCredentialsProvider();
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
