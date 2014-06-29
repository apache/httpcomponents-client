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
package org.apache.http.impl.client.integration;

import java.io.IOException;
import java.security.Principal;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

/**
 * Tests for {@link SPNegoScheme}.
 */
public class TestSPNegoScheme extends LocalServerTestBase {

    /**
     * This service will continue to ask for authentication.
     */
    private static class PleaseNegotiateService implements HttpRequestHandler {

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            response.addHeader(new BasicHeader("WWW-Authenticate", "Negotiate blablabla"));
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

        GSSManager manager = Mockito.mock(GSSManager.class);
        GSSName name = Mockito.mock(GSSName.class);
        GSSContext context = Mockito.mock(GSSContext.class);

        NegotiateSchemeWithMockGssManager() throws Exception {
            super(true);
            Mockito.when(context.initSecContext(
                    Matchers.any(byte[].class), Matchers.anyInt(), Matchers.anyInt()))
                    .thenReturn("12345678".getBytes());
            Mockito.when(manager.createName(
                    Matchers.any(String.class), Matchers.any(Oid.class)))
                    .thenReturn(name);
            Mockito.when(manager.createContext(
                    Matchers.any(GSSName.class), Matchers.any(Oid.class),
                    Matchers.any(GSSCredential.class), Matchers.anyInt()))
                    .thenReturn(context);
        }

        @Override
        protected GSSManager getManager() {
            return manager;
        }

    }

    private static class UseJaasCredentials implements Credentials {

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

    }

    private static class NegotiateSchemeProviderWithMockGssManager implements AuthSchemeProvider {

        NegotiateSchemeWithMockGssManager scheme;

        NegotiateSchemeProviderWithMockGssManager() throws Exception {
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
        this.serverBootstrap.registerHandler("*", new PleaseNegotiateService());
        final HttpHost target = start();

        final AuthSchemeProvider nsf = new NegotiateSchemeProviderWithMockGssManager();
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials use_jaas_creds = new UseJaasCredentials();
        credentialsProvider.setCredentials(new AuthScope(null, -1, null), use_jaas_creds);

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.SPNEGO, nsf)
            .build();
        this.httpclient = HttpClients.custom()
            .setDefaultAuthSchemeRegistry(authSchemeRegistry)
            .setDefaultCredentialsProvider(credentialsProvider)
            .build();

        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        final HttpResponse response = this.httpclient.execute(target, httpget);
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
    }

    /**
     * Javadoc specifies that {@link GSSContext#initSecContext(byte[], int, int)} can return null
     * if no token is generated. Client should be able to deal with this response.
     */
    @Test
    public void testNoTokenGeneratedError() throws Exception {
        this.serverBootstrap.registerHandler("*", new PleaseNegotiateService());
        final HttpHost target = start();

        final AuthSchemeProvider nsf = new NegotiateSchemeProviderWithMockGssManager();

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials use_jaas_creds = new UseJaasCredentials();
        credentialsProvider.setCredentials(new AuthScope(null, -1, null), use_jaas_creds);

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.SPNEGO, nsf)
            .build();
        this.httpclient = HttpClients.custom()
            .setDefaultAuthSchemeRegistry(authSchemeRegistry)
            .setDefaultCredentialsProvider(credentialsProvider)
            .build();

        final String s = "/path";
        final HttpGet httpget = new HttpGet(s);
        final HttpResponse response = this.httpclient.execute(target, httpget);
        EntityUtils.consume(response.getEntity());

        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
    }

}
