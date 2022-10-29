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
import java.security.Principal;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.KerberosConfig;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.auth.SPNegoScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
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
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests for {@link SPNegoScheme}.
 */
public class TestSPNegoScheme {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

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
     * NegotatieScheme with a custom GSSManager that does not require any Jaas or
     * Kerberos configuration.
     *
     */
    private static class NegotiateSchemeWithMockGssManager extends SPNegoScheme {

        final GSSManager manager = Mockito.mock(GSSManager.class);
        final GSSName name = Mockito.mock(GSSName.class);
        final GSSContext context = Mockito.mock(GSSContext.class);

        NegotiateSchemeWithMockGssManager() throws Exception {
            super(KerberosConfig.DEFAULT, SystemDefaultDnsResolver.INSTANCE);
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

    private static class NegotiateSchemeFactoryWithMockGssManager implements AuthSchemeFactory {

        NegotiateSchemeWithMockGssManager scheme;

        NegotiateSchemeFactoryWithMockGssManager() throws Exception {
            scheme = new NegotiateSchemeWithMockGssManager();
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
    @Test
    public void testDontTryToAuthenticateEndlessly() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandler("*", new PleaseNegotiateService());
        final HttpHost target = testResources.targetHost();

        final AuthSchemeFactory nsf = new NegotiateSchemeFactoryWithMockGssManager();
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(null, null, -1, null, null), new UseJaasCredentials())
                .build();

        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();

        final CloseableHttpClient client = testResources.startClient(builder -> builder
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setDefaultCredentialsProvider(credentialsProvider)
        );

        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        client.execute(target, httpget, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });
    }

    /**
     * Javadoc specifies that {@link GSSContext#initSecContext(byte[], int, int)} can return null
     * if no token is generated. Client should be able to deal with this response.
     */
    @Test
    public void testNoTokenGeneratedError() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandler("*", new PleaseNegotiateService());
        final HttpHost target = testResources.targetHost();

        final AuthSchemeFactory nsf = new NegotiateSchemeFactoryWithMockGssManager();

        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(null, null, -1, null, null), new UseJaasCredentials())
                .build();

        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, nsf)
            .build();

        final CloseableHttpClient client = testResources.startClient(builder -> builder
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setDefaultCredentialsProvider(credentialsProvider)
        );

        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        client.execute(target, httpget, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });

    }

}
