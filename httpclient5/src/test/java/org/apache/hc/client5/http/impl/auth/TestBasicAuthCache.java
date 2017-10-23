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

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link BasicAuthCache}.
 */
@SuppressWarnings("boxing") // test code
public class TestBasicAuthCache {

    @Test
    public void testBasicStoreRestore() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        final AuthScheme authScheme = new BasicScheme();
        cache.put(new HttpHost("localhost", 80), authScheme);
        Assert.assertNotNull(cache.get(new HttpHost("localhost", 80)));
        cache.remove(new HttpHost("localhost", 80));
        Assert.assertNull(cache.get(new HttpHost("localhost", 80)));
        cache.put(new HttpHost("localhost", 80), authScheme);
        cache.clear();
        Assert.assertNull(cache.get(new HttpHost("localhost", 80)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullKey() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        final AuthScheme authScheme = new BasicScheme();
        cache.put(null, authScheme);
    }

    @Test
    public void testNullAuthScheme() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        cache.put(new HttpHost("localhost", 80), null);
        Assert.assertNull(cache.get(new HttpHost("localhost", 80)));
    }

    @Test
    public void testGetKey() throws Exception {
        final BasicAuthCache cache = new BasicAuthCache();
        final HttpHost target = new HttpHost("localhost", 443, "https");
        Assert.assertSame(target, cache.getKey(target));
        Assert.assertEquals(target, cache.getKey(new HttpHost("localhost", -1, "https")));
    }

    @Test
    public void testGetKeyWithSchemeRegistry() throws Exception {
        final SchemePortResolver schemePortResolver = Mockito.mock(SchemePortResolver.class);
        final BasicAuthCache cache = new BasicAuthCache(schemePortResolver);
        Mockito.when(schemePortResolver.resolve(new HttpHost("localhost", -1, "https"))).thenReturn(443);
        final HttpHost target = new HttpHost("localhost", 443, "https");
        Assert.assertSame(target, cache.getKey(target));
        Assert.assertEquals(target, cache.getKey(new HttpHost("localhost", -1, "https")));
    }

    @Test
    public void testDigestScheme() throws Exception {
        final SchemePortResolver schemePortResolver = Mockito.mock(SchemePortResolver.class);
        final BasicAuthCache cache = new BasicAuthCache(schemePortResolver);
        final HttpHost target = new HttpHost("localhost", 443, "https");
        final HttpContext context = Mockito.mock(HttpContext.class);

        final AuthScheme digest1 = new DigestScheme();
        digest1.processChallenge(new AuthChallenge(ChallengeType.TARGET, "digest", new BasicNameValuePair("nonce", "1234")), context);
        cache.put(target, digest1);
        Assert.assertEquals(digest1, cache.get(target));
        Assert.assertNull(cache.get(target));
        cache.put(target, digest1);
        Assert.assertEquals(digest1, cache.get(target));

        final AuthScheme digest2 = new DigestScheme();
        digest2.processChallenge(new AuthChallenge(ChallengeType.TARGET, "digest", new BasicNameValuePair("nonce", "5678")), context);
        cache.put(target, digest1);
        cache.put(target, digest2);
        Assert.assertEquals(digest2, cache.get(target));
        Assert.assertEquals(digest1, cache.get(target));
        Assert.assertNull(cache.get(target));
    }

    @Test
    public void testLRUBehavior() throws Exception {
        final SchemePortResolver schemePortResolver = Mockito.mock(SchemePortResolver.class);
        final BasicAuthCache cache = new BasicAuthCache(schemePortResolver, 1, 0.75f, 2);

        final HttpHost target1 = new HttpHost("localhost", 443, "https");
        final HttpHost target2 = new HttpHost("localhost", 80, "http");
        final HttpHost target3 = new HttpHost("otherhost", 80, "http");
        final BasicScheme authScheme1 = new BasicScheme();
        authScheme1.initPreemptive(new UsernamePasswordCredentials("user1", "password1".toCharArray()));
        final BasicScheme authScheme2 = new BasicScheme();
        authScheme2.initPreemptive(new UsernamePasswordCredentials("user2", "password2".toCharArray()));
        final BasicScheme authScheme3 = new BasicScheme();
        authScheme3.initPreemptive(new UsernamePasswordCredentials("user3", "password3".toCharArray()));

        cache.put(target1, authScheme1);
        cache.put(target2, authScheme2);
        Assert.assertEquals(authScheme1, cache.get(target1));
        Assert.assertEquals(authScheme2, cache.get(target2));
        Assert.assertNull(cache.get(target3));

        cache.put(target3, authScheme3);
        Assert.assertNull(cache.get(target1));
        Assert.assertEquals(authScheme2, cache.get(target2));
        Assert.assertEquals(authScheme3, cache.get(target3));
    }
}
