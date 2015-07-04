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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthChallenge;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.CredentialsProvider;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.message.BasicNameValuePair;
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
        final HttpHost authhost = new HttpHost("locahost", 80);
        final HttpClientContext context = HttpClientContext.create();
        try {
            authStrategy.select(null, authhost, Collections.<String, AuthChallenge>emptyMap(), context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(ChallengeType.TARGET, null, Collections.<String, AuthChallenge>emptyMap(), context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(ChallengeType.TARGET, authhost, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(ChallengeType.TARGET, authhost, Collections.<String, AuthChallenge>emptyMap(), null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSelectNoSchemeRegistry() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpHost authhost = new HttpHost("locahost", 80);
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge("Basic",
                new BasicNameValuePair("realm", "test")));
        challenges.put("digest", new AuthChallenge("Digest",
                new BasicNameValuePair("realm", "test"), new BasicNameValuePair("nonce", "1234")));

        final Queue<AuthOption> options = authStrategy.select(ChallengeType.TARGET, authhost, challenges, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(0, options.size());
    }

    @Test
    public void testSelectNoCredentialsProvider() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpHost authhost = new HttpHost("locahost", 80);
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge("Basic",
                new BasicNameValuePair("realm", "test")));
        challenges.put("digest", new AuthChallenge("Digest",
                new BasicNameValuePair("realm", "test"), new BasicNameValuePair("nonce", "1234")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);

        final Queue<AuthOption> options = authStrategy.select(ChallengeType.TARGET, authhost, challenges, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(0, options.size());
    }

    @Test
    public void testNoCredentials() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpHost authhost = new HttpHost("locahost", 80);
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge("Basic",
                new BasicNameValuePair("realm", "realm1")));
        challenges.put("digest", new AuthChallenge("Digest",
                new BasicNameValuePair("realm", "realm2"), new BasicNameValuePair("nonce", "1234")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        context.setCredentialsProvider(credentialsProvider);

        final Queue<AuthOption> options = authStrategy.select(ChallengeType.TARGET, authhost, challenges, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(0, options.size());
    }

    @Test
    public void testCredentialsFound() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpHost authhost = new HttpHost("somehost", 80);
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge("Basic",
                new BasicNameValuePair("realm", "realm1")));
        challenges.put("digest", new AuthChallenge("Digest",
                new BasicNameValuePair("realm", "realm2"), new BasicNameValuePair("nonce", "1234")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80, "realm2"),
                new UsernamePasswordCredentials("user", "pwd"));
        context.setCredentialsProvider(credentialsProvider);

        final Queue<AuthOption> options = authStrategy.select(ChallengeType.TARGET, authhost, challenges, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(1, options.size());
        final AuthOption option = options.remove();
        Assert.assertTrue(option.getAuthScheme() instanceof DigestScheme);
    }

    @Test
    public void testUnsupportedScheme() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final HttpHost authhost = new HttpHost("somehost", 80);
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge("Basic",
                new BasicNameValuePair("realm", "realm1")));
        challenges.put("digest", new AuthChallenge("Digest",
                new BasicNameValuePair("realm", "realm2"), new BasicNameValuePair("nonce", "1234")));
        challenges.put("whatever", new AuthChallenge("Whatever",
                new BasicNameValuePair("realm", "realm3")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80),
                new UsernamePasswordCredentials("user", "pwd"));
        context.setCredentialsProvider(credentialsProvider);

        final Queue<AuthOption> options = authStrategy.select(ChallengeType.TARGET, authhost, challenges, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(2, options.size());
        final AuthOption option1 = options.remove();
        Assert.assertTrue(option1.getAuthScheme() instanceof DigestScheme);
        final AuthOption option2 = options.remove();
        Assert.assertTrue(option2.getAuthScheme() instanceof BasicScheme);
    }

    @Test
    public void testCustomAuthPreference() throws Exception {
        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();
        final RequestConfig config = RequestConfig.custom()
            .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
            .build();

        final HttpHost authhost = new HttpHost("somehost", 80);
        final HttpClientContext context = HttpClientContext.create();

        final Map<String, AuthChallenge> challenges = new HashMap<>();
        challenges.put("basic", new AuthChallenge("Basic",
                new BasicNameValuePair("realm", "realm1")));
        challenges.put("digest", new AuthChallenge("Digest",
                new BasicNameValuePair("realm", "realm2"), new BasicNameValuePair("nonce", "1234")));

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory()).build();
        context.setAuthSchemeRegistry(authSchemeRegistry);
        context.setRequestConfig(config);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80),
                new UsernamePasswordCredentials("user", "pwd"));
        context.setCredentialsProvider(credentialsProvider);

        final Queue<AuthOption> options = authStrategy.select(ChallengeType.TARGET, authhost, challenges, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(1, options.size());
        final AuthOption option1 = options.remove();
        Assert.assertTrue(option1.getAuthScheme() instanceof BasicScheme);
    }
    
}
