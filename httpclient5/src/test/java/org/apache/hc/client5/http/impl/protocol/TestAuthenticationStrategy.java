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
package org.apache.hc.client5.http.impl.protocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeProvider;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.AuthSchemes;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestScheme;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.sync.BasicCredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link DefaultAuthenticationStrategy}.
 */
@SuppressWarnings("boxing") // test code
public class TestAuthenticationStrategy {

    @Test
    public void testSelectInvalidInput() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpClientContext context = HttpClientContext.create();
        try {
            authStrategy.select(null, Collections.<String, AuthChallenge>emptyMap(), context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(ChallengeType.TARGET, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(ChallengeType.TARGET, Collections.<String, AuthChallenge>emptyMap(), null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSelectNoSchemeRegistry() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge(ChallengeType.TARGET, "Basic",
                new BasicNameValuePair("realm", "test")));
        challenges.put("digest", new AuthChallenge(ChallengeType.TARGET, "Digest",
                new BasicNameValuePair("realm", "test"), new BasicNameValuePair("nonce", "1234")));

        final List<AuthScheme> authSchemes = authStrategy.select(ChallengeType.TARGET, challenges, context);
        Assert.assertNotNull(authSchemes);
        Assert.assertEquals(0, authSchemes.size());
    }

    @Test
    public void testUnsupportedScheme() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge(ChallengeType.TARGET, "Basic",
                new BasicNameValuePair("realm", "realm1")));
        challenges.put("digest", new AuthChallenge(ChallengeType.TARGET, "Digest",
                new BasicNameValuePair("realm", "realm2"), new BasicNameValuePair("nonce", "1234")));
        challenges.put("whatever", new AuthChallenge(ChallengeType.TARGET, "Whatever",
                new BasicNameValuePair("realm", "realm3")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80),
                new UsernamePasswordCredentials("user", "pwd".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final List<AuthScheme> authSchemes = authStrategy.select(ChallengeType.TARGET, challenges, context);
        Assert.assertNotNull(authSchemes);
        Assert.assertEquals(2, authSchemes.size());
        final AuthScheme authScheme1 = authSchemes.get(0);
        Assert.assertTrue(authScheme1 instanceof DigestScheme);
        final AuthScheme authScheme2 = authSchemes.get(1);
        Assert.assertTrue(authScheme2 instanceof BasicScheme);
    }

    @Test
    public void testCustomAuthPreference() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final RequestConfig config = RequestConfig.custom()
            .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
            .build();

        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge(ChallengeType.TARGET, "Basic",
                new BasicNameValuePair("realm", "realm1")));
        challenges.put("digest", new AuthChallenge(ChallengeType.TARGET, "Digest",
                new BasicNameValuePair("realm", "realm2"), new BasicNameValuePair("nonce", "1234")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);
        context.setRequestConfig(config);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80),
                new UsernamePasswordCredentials("user", "pwd".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final List<AuthScheme> authSchemes = authStrategy.select(ChallengeType.TARGET, challenges, context);
        Assert.assertNotNull(authSchemes);
        Assert.assertEquals(1, authSchemes.size());
        final AuthScheme authScheme1 = authSchemes.get(0);
        Assert.assertTrue(authScheme1 instanceof BasicScheme);
    }

}
