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
package org.apache.http.impl.auth;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthExchange;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.CredentialsProvider;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.client.DefaultAuthenticationStrategy;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings({"boxing","static-access"})
public class TestHttpAuthenticator {

    private AuthExchange authState;
    private AuthScheme authScheme;
    private HttpContext context;
    private HttpHost defaultHost;
    private CredentialsProvider credentialsProvider;
    private Lookup<AuthSchemeProvider> authSchemeRegistry;
    private AuthCache authCache;
    private HttpAuthenticator httpAuthenticator;

    @Before
    public void setUp() throws Exception {
        this.authState = new AuthExchange();
        this.authScheme = Mockito.mock(AuthScheme.class);
        Mockito.when(this.authScheme.getName()).thenReturn("Basic");
        Mockito.when(this.authScheme.isChallengeComplete()).thenReturn(Boolean.TRUE);
        this.context = new BasicHttpContext();
        this.defaultHost = new HttpHost("localhost", 80);
        this.context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, this.defaultHost);
        this.credentialsProvider = Mockito.mock(CredentialsProvider.class);
        this.context.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credentialsProvider);
        this.authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory())
            .register("ntlm", new NTLMSchemeFactory()).build();
        this.context.setAttribute(HttpClientContext.AUTHSCHEME_REGISTRY, this.authSchemeRegistry);
        this.authCache = Mockito.mock(AuthCache.class);
        this.context.setAttribute(HttpClientContext.AUTH_CACHE, this.authCache);
        this.httpAuthenticator = new HttpAuthenticator();
    }

    @Test
    public void testUpdateAuthState() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");
        Assert.assertTrue(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Mockito.verifyZeroInteractions(this.authCache);
    }

    @Test
    public void testAuthenticationRequestedAfterSuccess() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");

        this.authState.select(this.authScheme);
        this.authState.setState(AuthExchange.State.SUCCESS);

        Assert.assertTrue(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));

        Mockito.verify(this.authCache).remove(this.defaultHost);
    }

    @Test
    public void testAuthenticationNotRequestedUnchallenged() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");

        Assert.assertFalse(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.UNCHALLENGED, this.authState.getState());
    }

    @Test
    public void testAuthenticationNotRequestedSuccess1() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        this.authState.select(this.authScheme);
        this.authState.setState(AuthExchange.State.CHALLENGED);

        Assert.assertFalse(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.SUCCESS, this.authState.getState());

        Mockito.verify(this.authCache).put(this.defaultHost, this.authScheme);
    }

    @Test
    public void testAuthenticationNotRequestedSuccess2() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        this.authState.select(this.authScheme);
        this.authState.setState(AuthExchange.State.HANDSHAKE);

        Assert.assertFalse(this.httpAuthenticator.isChallenged(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.SUCCESS, this.authState.getState());

        Mockito.verify(this.authCache).put(this.defaultHost, this.authScheme);
    }

    @Test
    public void testAuthentication() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final Credentials credentials = new UsernamePasswordCredentials("user:pass");
        Mockito.when(this.credentialsProvider.getCredentials(Mockito.<AuthScope>any())).thenReturn(credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertTrue(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.CHALLENGED, this.authState.getState());

        final Queue<AuthScheme> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        final AuthScheme authScheme1 = options.poll();
        Assert.assertNotNull(authScheme1);
        Assert.assertEquals("digest", authScheme1.getName());
        final AuthScheme authScheme2 = options.poll();
        Assert.assertNotNull(authScheme2);
        Assert.assertEquals("basic", authScheme2.getName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationCredentialsForBasic() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        final Credentials credentials = new UsernamePasswordCredentials("user:pass");
        Mockito.when(this.credentialsProvider.getCredentials(new AuthScope(host, "test", "basic"))).thenReturn(credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertTrue(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.CHALLENGED, this.authState.getState());

        final Queue<AuthScheme> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        final AuthScheme authScheme1 = options.poll();
        Assert.assertNotNull(authScheme1);
        Assert.assertEquals("basic", authScheme1.getName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationNoChallenges() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationNoSupportedChallenges() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "This realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "That realm=\"realm1\", nonce=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationNoCredentials() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationFailed() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.authState.setState(AuthExchange.State.CHALLENGED);
        this.authState.select(this.authScheme);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthExchange.State.FAILURE, this.authState.getState());

        Mockito.verify(this.authCache).remove(host);
    }

    @Test
    public void testAuthenticationFailedPreviously() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.authState.setState(AuthExchange.State.FAILURE);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthExchange.State.FAILURE, this.authState.getState());
    }

    @Test
    public void testAuthenticationFailure() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authState.setState(AuthExchange.State.CHALLENGED);
        this.authState.select(new BasicScheme());

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.FAILURE, this.authState.getState());
    }

    @Test
    public void testAuthenticationHandshaking() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", stale=true, nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authState.setState(AuthExchange.State.CHALLENGED);
        this.authState.select(new DigestScheme());

        Assert.assertTrue(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthExchange.State.HANDSHAKE, this.authState.getState());
    }

    @Test
    public void testAuthenticationNoMatchingChallenge() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final Credentials credentials = new UsernamePasswordCredentials("user:pass");
        Mockito.when(this.credentialsProvider.getCredentials(new AuthScope(host, "realm1", "digest"))).thenReturn(credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authState.setState(AuthExchange.State.CHALLENGED);
        this.authState.select(new BasicScheme());

        Assert.assertTrue(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthExchange.State.CHALLENGED, this.authState.getState());

        final Queue<AuthScheme> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        final AuthScheme authScheme1 = options.poll();
        Assert.assertNotNull(authScheme1);
        Assert.assertEquals("digest", authScheme1.getName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationException() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "blah blah blah"));

        this.authState.setState(AuthExchange.State.CHALLENGED);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.prepareAuthResponse(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthExchange.State.UNCHALLENGED, this.authState.getState());
        Assert.assertNull(this.authState.getAuthScheme());
    }

    @Test
    public void testAuthFailureState() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthExchange.State.FAILURE);
        this.authState.select(this.authScheme);

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authState, context);

        Assert.assertFalse(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme, Mockito.never()).generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

    @Test
    public void testAuthChallengeStateNoOption() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthExchange.State.CHALLENGED);
        this.authState.select(this.authScheme);

        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authState, context);

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void testAuthChallengeStateOneOptions() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthExchange.State.CHALLENGED);
        final LinkedList<AuthScheme> authOptions = new LinkedList<>();
        authOptions.add(this.authScheme);
        this.authState.setOptions(authOptions);

        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authState, context);

        Assert.assertSame(this.authScheme, this.authState.getAuthScheme());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void testAuthChallengeStateMultipleOption() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthExchange.State.CHALLENGED);

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
        this.authState.setOptions(authOptions);

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authState, context);

        Assert.assertSame(authScheme2, this.authState.getAuthScheme());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void testAuthSuccess() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthExchange.State.SUCCESS);
        this.authState.select(this.authScheme);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.FALSE);
        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authState, context);

        Assert.assertSame(this.authScheme, this.authState.getAuthScheme());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void testAuthSuccessConnectionBased() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthExchange.State.SUCCESS);
        this.authState.select(this.authScheme);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.TRUE);
        Mockito.when(this.authScheme.generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn("stuff");

        this.httpAuthenticator.addAuthResponse(defaultHost, ChallengeType.TARGET, request, authState, context);

        Assert.assertFalse(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme, Mockito.never()).generateAuthResponse(
                Mockito.eq(defaultHost),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

}
