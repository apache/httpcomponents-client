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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.security.Principal;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.NegotiateScheme;
import org.apache.http.impl.auth.NegotiateSchemeFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for {@link NegotiateScheme}.
 */
public class TestNegotiateScheme extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        localServer = new LocalTestServer(null, null);

        localServer.registerDefaultHandlers();
        localServer.start();
    }

    /**
     * This service will continue to ask for authentication.
     */
    private static class PleaseNegotiateService implements HttpRequestHandler {
        public void handle(final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(401);
            response.addHeader(new BasicHeader("WWW-Authenticate", "Negotiate blablabla"));
            response.setEntity(new StringEntity("auth required "));
            response.addHeader(new BasicHeader("Connection", "Keep-Alive"));
        }
    }


    /**
     * NegotatieScheme with a custom GSSManager that does not require any Jaas or
     * Kerberos configuration.
     *
     */
    private static class NegotiateSchemeWithMockGssManager extends NegotiateScheme {
        GSSManager manager = mock(GSSManager.class);
        GSSName name = mock(GSSName.class);
        GSSContext context = mock(GSSContext.class);

        NegotiateSchemeWithMockGssManager() throws Exception {
            super(null, true);

            when(context.initSecContext(any(byte[].class), anyInt(), anyInt()))
                .thenReturn("12345678".getBytes());
            when(manager.createName(any(String.class), any(Oid.class)))
                .thenReturn(name);
            when(manager.createContext(any(GSSName.class), any(Oid.class), any(GSSCredential.class), anyInt()))
                .thenReturn(context);

        }

        @Override
        protected GSSManager getManager() {
            return manager;
        }
    }

    private static class UseJaasCredentials implements Credentials {
        public String getPassword() {
            return null;
        }

        public Principal getUserPrincipal() {
            return null;
        }
    }

    private static class NegotiateSchemeFactoryWithMockGssManager extends NegotiateSchemeFactory {
        NegotiateSchemeWithMockGssManager scheme;
        NegotiateSchemeFactoryWithMockGssManager() throws Exception {
            scheme = new NegotiateSchemeWithMockGssManager();
        }
        @Override
        public AuthScheme newInstance(HttpParams params) {
            return scheme;
        }
    }

    /**
     * Tests that the client will stop connecting to the server if
     * the server still keep asking for a valid ticket.
     */
    @Test
    @Ignore
    public void testDontTryToAuthenticateEndlessly() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new PleaseNegotiateService());

        HttpHost target = new HttpHost("localhost", port);
        DefaultHttpClient client = new DefaultHttpClient();
        NegotiateSchemeFactory nsf = new NegotiateSchemeFactoryWithMockGssManager();
        client.getAuthSchemes().register(AuthPolicy.SPNEGO, nsf);

        Credentials use_jaas_creds = new UseJaasCredentials();
        client.getCredentialsProvider().setCredentials(
                new AuthScope(null, -1, null), use_jaas_creds);
        client.getParams().setParameter(ClientPNames.DEFAULT_HOST, target);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);
        HttpResponse response = client.execute(httpget);
        HttpEntity e = response.getEntity();
        e.consumeContent();
    }


    /**
     * Javadoc specifies that {@link GSSContext#initSecContext(byte[], int, int)} can return null
     * if no token is generated. Client should be able to deal with this response.
     *
     */
    @Test
    @Ignore
    public void testNoTokenGeneratedGenerateAnError() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new PleaseNegotiateService());

        HttpHost target = new HttpHost("localhost", port);
        DefaultHttpClient client = new DefaultHttpClient();
        NegotiateSchemeFactoryWithMockGssManager nsf = new NegotiateSchemeFactoryWithMockGssManager();
        when(nsf.scheme.context.initSecContext(any(byte[].class), anyInt(), anyInt())).thenReturn(null);
        client.getAuthSchemes().register(AuthPolicy.SPNEGO, nsf);

        Credentials use_jaas_creds = new UseJaasCredentials();
        client.getCredentialsProvider().setCredentials(
                new AuthScope(null, -1, null), use_jaas_creds);
        client.getParams().setParameter(ClientPNames.DEFAULT_HOST, target);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);
        HttpResponse response = client.execute(httpget);
        HttpEntity e = response.getEntity();
        e.consumeContent();
    }

}
