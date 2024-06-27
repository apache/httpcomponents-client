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

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.BearerToken;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Bearer authentication test cases.
 */
class TestBearerScheme {

    @Test
    void testBearerAuthenticationEmptyChallenge() throws Exception {
        final AuthChallenge authChallenge = new AuthChallenge(ChallengeType.TARGET, "BEARER");
        final AuthScheme authscheme = new BearerScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertNull(authscheme.getRealm());
    }

    @Test
    void testBearerAuthentication() throws Exception {
        final AuthChallenge authChallenge = new AuthChallenge(ChallengeType.TARGET, "Bearer",
                new BasicNameValuePair("realm", "test"));

        final AuthScheme authscheme = new BearerScheme();
        authscheme.processChallenge(authChallenge, null);

        final HttpHost host  = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "test", null), new BearerToken("some token"))
                .build();

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);

        Assertions.assertEquals("test", authscheme.getRealm());
        Assertions.assertTrue(authscheme.isChallengeComplete());
        Assertions.assertFalse(authscheme.isConnectionBased());
    }

    @Test
    void testStateStorage() throws Exception {
        final AuthChallenge authChallenge = new AuthChallenge(ChallengeType.TARGET, "Bearer",
                new BasicNameValuePair("realm", "test"),
                new BasicNameValuePair("code", "read"));

        final BearerScheme authscheme = new BearerScheme();
        authscheme.processChallenge(authChallenge, null);

        final BearerScheme.State state = authscheme.store();
        final BearerScheme cached = new BearerScheme();
        cached.restore(state);

        Assertions.assertEquals(cached.getName(), cached.getName());
        Assertions.assertEquals(cached.getRealm(), cached.getRealm());
        Assertions.assertEquals(cached.isChallengeComplete(), cached.isChallengeComplete());
    }

}
