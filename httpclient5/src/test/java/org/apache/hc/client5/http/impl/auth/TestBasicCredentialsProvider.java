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
package org.apache.hc.client5.http.impl.auth;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link BasicCredentialsProvider}.
 */
public class TestBasicCredentialsProvider {

    public final static Credentials CREDS1 =
        new UsernamePasswordCredentials("user1", "pass1".toCharArray());
    public final static Credentials CREDS2 =
        new UsernamePasswordCredentials("user2", "pass2".toCharArray());

    public final static AuthScope SCOPE1 = new AuthScope(null, null, -1, "realm1", null);
    public final static AuthScope SCOPE2 = new AuthScope(null, null, -1, "realm2", null);
    public final static AuthScope BOGUS = new AuthScope(null, null, -1, "bogus", null);
    public final static AuthScope DEFSCOPE = new AuthScope(null, "host", -1, "realm", null);

    @Test
    public void testBasicCredentialsProviderCredentials() {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(SCOPE1, CREDS1);
        state.setCredentials(SCOPE2, CREDS2);
        Assert.assertEquals(CREDS1, state.getCredentials(SCOPE1, null));
        Assert.assertEquals(CREDS2, state.getCredentials(SCOPE2, null));
    }

    @Test
    public void testBasicCredentialsProviderNoCredentials() {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        Assert.assertEquals(null, state.getCredentials(BOGUS, null));
    }

    @Test
    public void testBasicCredentialsProviderDefaultCredentials() {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(new AuthScope(null, null, -1, null ,null), CREDS1);
        state.setCredentials(SCOPE2, CREDS2);
        Assert.assertEquals(CREDS1, state.getCredentials(BOGUS, null));
    }

    @Test
    public void testDefaultCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass".toCharArray());
        state.setCredentials(new AuthScope(null, null, -1, null ,null), expected);
        final Credentials got = state.getCredentials(DEFSCOPE, null);
        Assert.assertEquals(got, expected);
    }

    @Test
    public void testRealmCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass".toCharArray());
        state.setCredentials(DEFSCOPE, expected);
        final Credentials got = state.getCredentials(DEFSCOPE, null);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testHostCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass".toCharArray());
        state.setCredentials(new AuthScope(null, "host", -1, null, null), expected);
        final Credentials got = state.getCredentials(DEFSCOPE, null);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testWrongHostCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass".toCharArray());
        state.setCredentials(new AuthScope(null, "host1", -1, "realm", null), expected);
        final Credentials got = state.getCredentials(new AuthScope(null, "host2", -1, "realm", null), null);
        Assert.assertNotSame(expected, got);
    }

    @Test
    public void testWrongRealmCredentials() throws Exception {
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials cred = new UsernamePasswordCredentials("name", "pass".toCharArray());
        state.setCredentials(new AuthScope(null, "host", -1, "realm1", null), cred);
        final Credentials got = state.getCredentials(new AuthScope(null, "host", -1, "realm2", null), null);
        Assert.assertNotSame(cred, got);
    }

    @Test
    public void testMixedCaseHostname() throws Exception {
        final HttpHost httpHost = new HttpHost("hOsT", 80);
        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        final Credentials expected = new UsernamePasswordCredentials("name", "pass".toCharArray());
        state.setCredentials(new AuthScope(httpHost), expected);
        final Credentials got = state.getCredentials(DEFSCOPE, null);
        Assert.assertEquals(expected, got);
    }

    @Test
    public void testCredentialsMatching() {
        final Credentials creds1 = new UsernamePasswordCredentials("name1", "pass1".toCharArray());
        final Credentials creds2 = new UsernamePasswordCredentials("name2", "pass2".toCharArray());
        final Credentials creds3 = new UsernamePasswordCredentials("name3", "pass3".toCharArray());

        final AuthScope scope1 = new AuthScope(null, null, -1, null, null);
        final AuthScope scope2 = new AuthScope(null, null, -1, "somerealm", null);
        final AuthScope scope3 = new AuthScope(null, "somehost", -1, null, null);

        final BasicCredentialsProvider state = new BasicCredentialsProvider();
        state.setCredentials(scope1, creds1);
        state.setCredentials(scope2, creds2);
        state.setCredentials(scope3, creds3);

        Credentials got = state.getCredentials(new AuthScope("http", "someotherhost", 80, "someotherrealm", StandardAuthScheme.BASIC), null);
        Credentials expected = creds1;
        Assert.assertEquals(expected, got);

        got = state.getCredentials(new AuthScope("http", "someotherhost", 80, "somerealm", StandardAuthScheme.BASIC), null);
        expected = creds2;
        Assert.assertEquals(expected, got);

        got = state.getCredentials(new AuthScope("http", "somehost", 80, "someotherrealm", StandardAuthScheme.BASIC), null);
        expected = creds3;
        Assert.assertEquals(expected, got);
    }

}
