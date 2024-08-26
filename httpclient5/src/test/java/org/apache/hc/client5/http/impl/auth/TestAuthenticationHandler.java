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

import java.util.LinkedList;
import java.util.Queue;

import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

class TestAuthenticationHandler {

    @AuthStateCacheable
    abstract class CacheableAuthState implements AuthScheme {

        @Override
        public String getName() {
            return StandardAuthScheme.BASIC;
        }

    }

    private AuthExchange authExchange;
    private CacheableAuthState authScheme;
    private HttpClientContext context;
    private HttpHost defaultHost;
    private CredentialsProvider credentialsProvider;
    private Lookup<AuthSchemeFactory> authSchemeRegistry;
    private AuthenticationHandler httpAuthenticator;

    @BeforeEach
    void setUp() {
        this.authExchange = new AuthExchange();
        this.authScheme = Mockito.mock(CacheableAuthState.class, Mockito.withSettings()
                .defaultAnswer(Answers.CALLS_REAL_METHODS));
        Mockito.when(this.authScheme.isChallengeComplete()).thenReturn(Boolean.TRUE);
        this.context = HttpClientContext.create();
        this.defaultHost = new HttpHost("localhost", 80);
        this.credentialsProvider = Mockito.mock(CredentialsProvider.class);
        this.context.setCredentialsProvider(this.credentialsProvider);
        this.authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
            .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
            .build();
        this.context.setAuthSchemeRegistry(this.authSchemeRegistry);
        this.httpAuthenticator = new AuthenticationHandler();
    }

    @Test
    void testUpdateAuthExchange() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        Assertions.assertTrue(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authExchange, this.context));
    }

    @Test
    void testAuthenticationRequestedAfterSuccess() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");

        this.authExchange.select(this.authScheme);
        this.authExchange.setState(AuthExchange.State.SUCCESS);

        Assertions.assertTrue(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authExchange, this.context));
    }

    @Test
    void testAuthenticationNotRequestedUnchallenged() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");

        Assertions.assertFalse(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, this.authExchange.getState());
    }

    @Test
    void testAuthenticationNotRequestedSuccess1() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        this.authExchange.select(this.authScheme);
        this.authExchange.setState(AuthExchange.State.CHALLENGED);

        Assertions.assertFalse(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.SUCCESS, this.authExchange.getState());
    }

    @Test
    void testAuthenticationNotRequestedSuccess2() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        this.authExchange.select(this.authScheme);
        this.authExchange.setState(AuthExchange.State.HANDSHAKE);

        Assertions.assertFalse(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.SUCCESS, this.authExchange.getState());
    }

    @Test
    void testAuthentication() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final Credentials credentials = new UsernamePasswordCredentials("user", "pass".toCharArray());
        Mockito.when(this.credentialsProvider.getCredentials(Mockito.any(),
                                                             Mockito.any())).thenReturn(credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertTrue(this.httpAuthenticator.handleResponse(host, ChallengeType.TARGET, response, authStrategy,
                                                                     this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.CHALLENGED, this.authExchange.getState());

        final Queue<AuthScheme> options = this.authExchange.getAuthOptions();
        Assertions.assertNotNull(options);
        final AuthScheme authScheme1 = options.poll();
        Assertions.assertNotNull(authScheme1);
        Assertions.assertEquals(StandardAuthScheme.DIGEST, authScheme1.getName());
        final AuthScheme authScheme2 = options.poll();
        Assertions.assertNotNull(authScheme2);
        Assertions.assertEquals(StandardAuthScheme.BASIC, authScheme2.getName());
        Assertions.assertNull(options.poll());
    }

    @Test
    void testAuthenticationCredentialsForBasic() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response =
            new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));

        final Credentials credentials = new UsernamePasswordCredentials("user", "pass".toCharArray());
        Mockito.when(this.credentialsProvider.getCredentials(Mockito.eq(new AuthScope(host, "test", StandardAuthScheme.BASIC)),
                                                             Mockito.any())).thenReturn(credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertTrue(this.httpAuthenticator.handleResponse(host, ChallengeType.TARGET, response, authStrategy,
                                                                     this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.CHALLENGED, this.authExchange.getState());

        final Queue<AuthScheme> options = this.authExchange.getAuthOptions();
        Assertions.assertNotNull(options);
        final AuthScheme authScheme1 = options.poll();
        Assertions.assertNotNull(authScheme1);
        Assertions.assertEquals(StandardAuthScheme.BASIC, authScheme1.getName());
        Assertions.assertNull(options.poll());
    }

    @Test
    void testAuthenticationNoChallenges() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));
    }

    @Test
    void testAuthenticationNoSupportedChallenges() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "This realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "That realm=\"realm1\", nonce=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));
    }

    @Test
    void testAuthenticationNoCredentials() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));
    }

    @Test
    void testAuthenticationFailed() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));

        this.authExchange.setState(AuthExchange.State.CHALLENGED);
        this.authExchange.select(this.authScheme);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));

        Assertions.assertEquals(AuthExchange.State.FAILURE, this.authExchange.getState());
    }

    @Test
    void testAuthenticationFailedPreviously() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));

        this.authExchange.setState(AuthExchange.State.FAILURE);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));

        Assertions.assertEquals(AuthExchange.State.FAILURE, this.authExchange.getState());
    }

    @Test
    void testAuthenticationFailure() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authExchange.setState(AuthExchange.State.CHALLENGED);
        this.authExchange.select(new BasicScheme());

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.FAILURE, this.authExchange.getState());
    }

    @Test
    void testAuthenticationHandshaking() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", stale=true, nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authExchange.setState(AuthExchange.State.CHALLENGED);
        this.authExchange.select(new DigestScheme());

        Assertions.assertTrue(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));

        Assertions.assertEquals(AuthExchange.State.HANDSHAKE, this.authExchange.getState());
    }

    @Test
    void testAuthenticationNoMatchingChallenge() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final Credentials credentials = new UsernamePasswordCredentials("user", "pass".toCharArray());
        Mockito.when(this.credentialsProvider.getCredentials(Mockito.eq(new AuthScope(host, "realm1", StandardAuthScheme.DIGEST)),
                                                             Mockito.any())).thenReturn(credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authExchange.setState(AuthExchange.State.CHALLENGED);
        this.authExchange.select(new BasicScheme());

        Assertions.assertTrue(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));
        Assertions.assertEquals(AuthExchange.State.CHALLENGED, this.authExchange.getState());

        final Queue<AuthScheme> options = this.authExchange.getAuthOptions();
        Assertions.assertNotNull(options);
        final AuthScheme authScheme1 = options.poll();
        Assertions.assertNotNull(authScheme1);
        Assertions.assertEquals(StandardAuthScheme.DIGEST, authScheme1.getName());
        Assertions.assertNull(options.poll());
    }

    @Test
    void testAuthenticationException() throws AuthenticationException, MalformedChallengeException {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "blah blah blah"));

        this.authExchange.setState(AuthExchange.State.CHALLENGED);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assertions.assertFalse(this.httpAuthenticator.handleResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authExchange, this.context));

        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, this.authExchange.getState());
        Assertions.assertNull(this.authExchange.getAuthScheme());
    }

    @Test
    void testAuthFailureState() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authExchange.setState(AuthExchange.State.FAILURE);
        this.authExchange.select(this.authScheme);

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authExchange, context);

        Assertions.assertFalse(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme, Mockito.never()).generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

    @Test
    void testAuthChallengeStateNoOption() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authExchange.setState(AuthExchange.State.CHALLENGED);
        this.authExchange.select(this.authScheme);

        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authExchange, context);

        Assertions.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testAuthChallengeStateOneOptions() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authExchange.setState(AuthExchange.State.CHALLENGED);
        final LinkedList<AuthScheme> authOptions = new LinkedList<>();
        authOptions.add(this.authScheme);
        this.authExchange.setOptions(authOptions);

        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authExchange, context);

        Assertions.assertSame(this.authScheme, this.authExchange.getAuthScheme());
        Assertions.assertNull(this.authExchange.getAuthOptions());

        Assertions.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testAuthChallengeStateMultipleOption() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authExchange.setState(AuthExchange.State.CHALLENGED);

        final LinkedList<AuthScheme> authOptions = new LinkedList<>();
        final AuthScheme authScheme1 = Mockito.mock(AuthScheme.class);
        Mockito.doThrow(new AuthenticationException()).when(authScheme1).generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
        final AuthScheme authScheme2 = Mockito.mock(AuthScheme.class);
        Mockito.when(authScheme2.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");
        authOptions.add(authScheme1);
        authOptions.add(authScheme2);
        this.authExchange.setOptions(authOptions);

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authExchange, context);

        Assertions.assertSame(authScheme2, this.authExchange.getAuthScheme());
        Assertions.assertNull(this.authExchange.getAuthOptions());

        Assertions.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testAuthSuccess() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authExchange.setState(AuthExchange.State.SUCCESS);
        this.authExchange.select(this.authScheme);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.FALSE);
        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authExchange, context);

        Assertions.assertSame(this.authScheme, this.authExchange.getAuthScheme());
        Assertions.assertNull(this.authExchange.getAuthOptions());

        Assertions.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testAuthSuccessConnectionBased() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authExchange.setState(AuthExchange.State.SUCCESS);
        this.authExchange.select(this.authScheme);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.TRUE);
        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authExchange, context);

        Assertions.assertFalse(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme, Mockito.never()).generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

}
