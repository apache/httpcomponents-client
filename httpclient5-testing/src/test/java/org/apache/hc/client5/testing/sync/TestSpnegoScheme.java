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
package org.apache.hc.client5.testing.sync;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.gss.GssConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.auth.gss.SpnegoScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests for {@link org.apache.hc.client5.http.impl.auth.gss.SpnegoScheme}.
 */
public class TestSpnegoScheme extends AbstractIntegrationTestBase {

    protected TestSpnegoScheme() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD);
    }

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private static final String GOOD_TOKEN = "GOOD_TOKEN";
    private static final byte[] GOOD_TOKEN_BYTES = GOOD_TOKEN.getBytes(StandardCharsets.UTF_8);
    private static final byte[] GOOD_TOKEN_B64_BYTES = Base64.encodeBase64(GOOD_TOKEN_BYTES);
    private static final String GOOD_TOKEN_B64 = new String(GOOD_TOKEN_B64_BYTES);

    private static final String NO_TOKEN = "";
    private static final byte[] NO_TOKEN_BYTES = NO_TOKEN.getBytes(StandardCharsets.UTF_8);

    private static final String GOOD_MUTUAL_AUTH_TOKEN = "GOOD_MUTUAL_AUTH_TOKEN";
    private static final byte[] GOOD_MUTUAL_AUTH_TOKEN_BYTES = GOOD_MUTUAL_AUTH_TOKEN.getBytes(StandardCharsets.UTF_8);
    private static final byte[] GOOD_MUTUAL_AUTH_TOKEN_B64_BYTES = Base64.encodeBase64(GOOD_MUTUAL_AUTH_TOKEN_BYTES);

    private static final String BAD_MUTUAL_AUTH_TOKEN = "BAD_MUTUAL_AUTH_TOKEN";
    private static final byte[] BAD_MUTUAL_AUTH_TOKEN_BYTES = BAD_MUTUAL_AUTH_TOKEN.getBytes(StandardCharsets.UTF_8);
    private static final byte[] BAD_MUTUAL_AUTH_TOKEN_B64_BYTES = Base64.encodeBase64(BAD_MUTUAL_AUTH_TOKEN_BYTES);

    static GssConfig MUTUAL_KERBEROS_CONFIG = GssConfig.DEFAULT;

    private static class SpnegoAuthenticationStrategy extends DefaultAuthenticationStrategy {

        private static final List<String> SPNEGO_SCHEME_PRIORITY =
                Collections.unmodifiableList(
                    Arrays.asList(StandardAuthScheme.SPNEGO,
                        StandardAuthScheme.BEARER,
                        StandardAuthScheme.DIGEST,
                        StandardAuthScheme.BASIC));

        @Override
        protected final List<String> getSchemePriority() {
            return SPNEGO_SCHEME_PRIORITY;
        }
    }

    final AuthenticationStrategy spnegoAuthenticationStrategy = new SpnegoAuthenticationStrategy();

    final CredentialsProvider jaasCredentialsProvider = CredentialsProviderBuilder.create()
            .add(new AuthScope(null, null, -1, null, null), new UseJaasCredentials())
            .build();

    /**
     * This service will continue to ask for authentication.
     */
    private static class PleaseNegotiateService implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            response.addHeader(new BasicHeader("WWW-Authenticate", StandardAuthScheme.SPNEGO + " blablabla"));
            response.addHeader(new BasicHeader("Connection", "Keep-Alive"));
            response.setEntity(new StringEntity("auth required "));
        }
    }

    /**
     * This service implements a normal mutualAuth flow
     */
    private static class SpnegoService implements HttpRequestHandler {

        int callCount = 1;
        final boolean sendMutualToken;
        final byte[] encodedMutualAuthToken;

        SpnegoService (final boolean sendMutualToken, final byte[] encodedMutualAuthToken) {
            this.sendMutualToken = sendMutualToken;
            this.encodedMutualAuthToken = encodedMutualAuthToken;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            if (callCount == 1) {
                callCount++;
                // Send the empty challenge
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
                response.addHeader(new BasicHeader("WWW-Authenticate", StandardAuthScheme.SPNEGO));
                response.addHeader(new BasicHeader("Connection", "Keep-Alive"));
                response.setEntity(new StringEntity("auth required "));
            } else if (callCount == 2) {
                callCount++;
                if (request.getHeader("Authorization").getValue().contains(GOOD_TOKEN_B64)) {
                    response.setCode(HttpStatus.SC_OK);
                    if (sendMutualToken) {
                        response.addHeader(new BasicHeader("WWW-Authenticate", StandardAuthScheme.SPNEGO + " " + new String(encodedMutualAuthToken)));
                    }
                    response.addHeader(new BasicHeader("Connection", "Keep-Alive"));
                    response.setEntity(new StringEntity("auth successful "));
                } else {
                    response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    /**
     * NegotatieScheme with a custom GSSManager that does not require any Jaas or
     * Kerberos configuration.
     *
     */
    private static class NegotiateSchemeWithMockGssManager extends SpnegoScheme {

        final GSSManager manager = Mockito.mock(GSSManager.class);
        final GSSName name = Mockito.mock(GSSName.class);
        final GSSContext context = Mockito.mock(GSSContext.class);

        NegotiateSchemeWithMockGssManager() throws Exception {
            super(GssConfig.DEFAULT, SystemDefaultDnsResolver.INSTANCE);
            Mockito.when(context.initSecContext(
                    ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                    .thenReturn("12345678".getBytes());
            Mockito.when(manager.createName(
                    ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                    .thenReturn(name);
            Mockito.when(manager.createContext(
                    ArgumentMatchers.any(), ArgumentMatchers.any(),
                    ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenReturn(context);
        }

        @Override
        protected GSSManager getManager() {
            return manager;
        }

    }

    private static class MutualNegotiateSchemeWithMockGssManager extends SpnegoScheme {

        final GSSManager manager = Mockito.mock(GSSManager.class);
        final GSSName name = Mockito.mock(GSSName.class);
        final GSSContext context = Mockito.mock(GSSContext.class);

        MutualNegotiateSchemeWithMockGssManager(final boolean established, final boolean mutual) throws Exception {
            super(MUTUAL_KERBEROS_CONFIG, SystemDefaultDnsResolver.INSTANCE);
            // Initial empty WWW-Authenticate response header
            Mockito.when(context.initSecContext(
                AdditionalMatchers.aryEq(NO_TOKEN_BYTES), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(GOOD_TOKEN_BYTES);
            // Valid mutual token
            Mockito.when(context.initSecContext(
                    AdditionalMatchers.aryEq(GOOD_MUTUAL_AUTH_TOKEN_BYTES), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                    .thenReturn(NO_TOKEN_BYTES);
            // Invalid mutual token
            Mockito.when(context.initSecContext(
                    AdditionalMatchers.aryEq(BAD_MUTUAL_AUTH_TOKEN_BYTES), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                    .thenThrow(new GSSException(GSSException.DEFECTIVE_CREDENTIAL));
            // It's hard to mock state, so instead we specify the complete and mutualAuth states
            // in the constructor
            Mockito.when(context.isEstablished()).thenReturn(established);
            Mockito.when(context.getMutualAuthState()).thenReturn(mutual);
            Mockito.when(manager.createName(
                    ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                    .thenReturn(name);
            Mockito.when(manager.createContext(
                    ArgumentMatchers.any(), ArgumentMatchers.any(),
                    ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                    .thenReturn(context);
        }

        @Override
        protected GSSManager getManager() {
            return manager;
        }

    }

    private static class UseJaasCredentials implements Credentials {

        @Override
        public char[] getPassword() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

    }

    private static class TestAuthSchemeFactory implements AuthSchemeFactory {

        AuthScheme scheme;

        TestAuthSchemeFactory(final AuthScheme scheme) throws Exception {
            this.scheme = scheme;
        }

        @Override
        public AuthScheme create(final HttpContext context) {
            return scheme;
        }

    }


    /**
     * Tests that the client will stop connecting to the server if
     * the server still keep asking for a valid ticket.
     */
    //@Disabled
    @Test
    void testDontTryToAuthenticateEndlessly() throws Exception {
        configureServer(t -> {
            t.register("*", new PleaseNegotiateService());
        });

        final AuthSchemeFactory nsf = new TestAuthSchemeFactory(new NegotiateSchemeWithMockGssManager());
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();
        configureClient(t -> {
            t.setTargetAuthenticationStrategy(spnegoAuthenticationStrategy);
            t.setDefaultAuthSchemeRegistry(authSchemeRegistry);
            t.setDefaultCredentialsProvider(jaasCredentialsProvider);
        });

        final HttpHost target = startServer();
        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        try {
            client().execute(target, httpget, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
                return null;
            });
            Assertions.fail();
        } catch (final IllegalStateException e) {
            // Expected
        }
    }

    /**
     * Test the success case for mutual auth
     */
    @Test
    void testMutualSuccess() throws Exception {
        configureServer(t -> {
            t.register("*", new SpnegoService(true, GOOD_MUTUAL_AUTH_TOKEN_B64_BYTES));
        });
        final HttpHost target = startServer();

        final MutualNegotiateSchemeWithMockGssManager mockAuthScheme = new MutualNegotiateSchemeWithMockGssManager(true, true);
        final AuthSchemeFactory nsf = new TestAuthSchemeFactory(mockAuthScheme);
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();

        configureClient(t -> {
            t.setTargetAuthenticationStrategy(spnegoAuthenticationStrategy);
            t.setDefaultAuthSchemeRegistry(authSchemeRegistry);
            t.setDefaultCredentialsProvider(jaasCredentialsProvider);
        });

        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        client().execute(target, httpget, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return null;
        });

        Mockito.verify(mockAuthScheme.context, Mockito.atLeastOnce()).isEstablished();
        Mockito.verify(mockAuthScheme.context, Mockito.atLeastOnce()).getMutualAuthState();
    }

    /**
     * No mutual auth response token sent by server.
     */
    @Test
    void testMutualFailureNoToken() throws Exception {
        configureServer(t -> {
            t.register("*", new SpnegoService(false, null));
        });

        final MutualNegotiateSchemeWithMockGssManager mockAuthScheme = new MutualNegotiateSchemeWithMockGssManager(false, false);
        final AuthSchemeFactory nsf = new TestAuthSchemeFactory(mockAuthScheme);
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();

        configureClient(t -> {
            t.setTargetAuthenticationStrategy(spnegoAuthenticationStrategy);
            t.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        });

        final HttpClientContext context = new HttpClientContext();
        context.setCredentialsProvider(jaasCredentialsProvider);

        final HttpHost target = startServer();
        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        try {
            client().execute(target, httpget, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.fail();
                return null;
            });
            Assertions.fail();
        } catch (final Exception e) {
            Assertions.assertTrue(e instanceof ClientProtocolException);
            Assertions.assertTrue(e.getCause() instanceof AuthenticationException);
        }

        Mockito.verify(mockAuthScheme.context, Mockito.never()).isEstablished();
        Mockito.verify(mockAuthScheme.context, Mockito.never()).getMutualAuthState();
    }

    /**
     * Server sends a "valid" token, but we mock the established status to false
     */
    @Test
    void testMutualFailureEstablishedStatusFalse() throws Exception {
        configureServer(t -> {
            t.register("*", new SpnegoService(true, GOOD_MUTUAL_AUTH_TOKEN_B64_BYTES));
        });

        final MutualNegotiateSchemeWithMockGssManager mockAuthScheme = new MutualNegotiateSchemeWithMockGssManager(false, false);
        final AuthSchemeFactory nsf = new TestAuthSchemeFactory(mockAuthScheme);
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();
        configureClient(t -> {
            t.setTargetAuthenticationStrategy(spnegoAuthenticationStrategy);
            t.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        });

        final HttpClientContext context = new HttpClientContext();
        context.setCredentialsProvider(jaasCredentialsProvider);

        final HttpHost target = startServer();
        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        try {
            client().execute(target, httpget, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.fail();
                return null;
            });
            Assertions.fail();
        } catch (final Exception e) {
            Assertions.assertTrue(e instanceof ClientProtocolException);
            Assertions.assertTrue(e.getCause() instanceof AuthenticationException);
        }

        Mockito.verify(mockAuthScheme.context, Mockito.atLeastOnce()).isEstablished();
        Mockito.verify(mockAuthScheme.context, Mockito.never()).getMutualAuthState();
    }

    /**
     * Server sends a "valid" token, but we mock the mutual auth status to false
     */
    @Test
    void testMutualFailureMutualStatusFalse() throws Exception {
        configureServer(t -> {
            t.register("*", new SpnegoService(true, GOOD_MUTUAL_AUTH_TOKEN_B64_BYTES));
        });

        final MutualNegotiateSchemeWithMockGssManager mockAuthScheme = new MutualNegotiateSchemeWithMockGssManager(true, false);
        final AuthSchemeFactory nsf = new TestAuthSchemeFactory(mockAuthScheme);
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();
        configureClient(t -> {
            t.setTargetAuthenticationStrategy(spnegoAuthenticationStrategy);
            t.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        });

        final HttpClientContext context = new HttpClientContext();
        context.setCredentialsProvider(jaasCredentialsProvider);

        final HttpHost target = startServer();
        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        try {
            client().execute(target, httpget, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.fail();
                return null;
            });
            Assertions.fail();
        } catch (final Exception e) {
            Assertions.assertTrue(e instanceof ClientProtocolException);
            Assertions.assertTrue(e.getCause() instanceof AuthenticationException);
        }

        Mockito.verify(mockAuthScheme.context, Mockito.atLeastOnce()).isEstablished();
        Mockito.verify(mockAuthScheme.context, Mockito.atLeastOnce()).getMutualAuthState();
    }

    /**
     * Server sends a "bad" token, and GSS throws an exception.
     */
    @Test
    void testMutualFailureBadToken() throws Exception {
        configureServer(t -> {
            t.register("*", new SpnegoService(true, BAD_MUTUAL_AUTH_TOKEN_B64_BYTES));
        });

        // We except that the initSecContent throws an exception, so the status is irrelevant
        final MutualNegotiateSchemeWithMockGssManager mockAuthScheme = new MutualNegotiateSchemeWithMockGssManager(true, true);
        final AuthSchemeFactory nsf = new TestAuthSchemeFactory(mockAuthScheme);
        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();

        configureClient(t -> {
            t.setTargetAuthenticationStrategy(spnegoAuthenticationStrategy);
            t.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        });

        final HttpClientContext context = new HttpClientContext();
        context.setCredentialsProvider(jaasCredentialsProvider);

        final HttpHost target = startServer();
        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        try {
            client().execute(target, httpget, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.fail();
                return null;
            });
            Assertions.fail();
        } catch (final Exception e) {
            Assertions.assertTrue(e instanceof ClientProtocolException);
            Assertions.assertTrue(e.getCause() instanceof AuthenticationException);
        }

        Mockito.verify(mockAuthScheme.context, Mockito.never()).isEstablished();
        Mockito.verify(mockAuthScheme.context, Mockito.never()).getMutualAuthState();
    }
}
