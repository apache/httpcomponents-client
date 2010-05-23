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

import java.io.IOException;
import java.security.Principal;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.StringEntity;
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
import org.junit.Test;

import org.easymock.classextension.EasyMock;

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
    private static class NegotiateSchemeWithMockGssManager extends NegotiateScheme {

        GSSManager manager = EasyMock.createNiceMock(GSSManager.class);
        GSSName name = EasyMock.createNiceMock(GSSName.class);
        GSSContext context = EasyMock.createNiceMock(GSSContext.class);

        NegotiateSchemeWithMockGssManager() throws Exception {
            super(null, true);

            EasyMock.expect(context.initSecContext(EasyMock.<byte[]>anyObject(),
                    EasyMock.anyInt(), EasyMock.anyInt())).andReturn("12345678".getBytes());

            EasyMock.expect(manager.createName(EasyMock.isA(String.class),
                    EasyMock.<Oid>anyObject())).andReturn(name);

            EasyMock.expect(manager.createContext(
                    EasyMock.isA(GSSName.class),EasyMock.isA(Oid.class),
                    EasyMock.<GSSCredential>anyObject(), EasyMock.anyInt()))
                    .andReturn(context);

            EasyMock.expect(name.canonicalize(EasyMock.isA(Oid.class)))
                    .andReturn(name);

            EasyMock.replay(context);
            EasyMock.replay(name);
            EasyMock.replay(manager);
        }

        @Override
        protected GSSManager getManager() {
            return manager;
        }

        public void verify() {
            EasyMock.verify(context);
            EasyMock.verify(name);
            EasyMock.verify(manager);
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
        if (e != null) {
            e.consumeContent();
        }

        ((NegotiateSchemeFactoryWithMockGssManager)nsf).scheme.verify();

        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
    }

    /**
     * Javadoc specifies that {@link GSSContext#initSecContext(byte[], int, int)} can return null
     * if no token is generated. Client should be able to deal with this response.
     */
    @Test
    public void testNoTokenGeneratedError() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new PleaseNegotiateService());

        HttpHost target = new HttpHost("localhost", port);
        DefaultHttpClient client = new DefaultHttpClient();

        NegotiateSchemeFactoryWithMockGssManager nsf = new NegotiateSchemeFactoryWithMockGssManager();

        client.getAuthSchemes().register(AuthPolicy.SPNEGO, nsf);

        Credentials use_jaas_creds = new UseJaasCredentials();
        client.getCredentialsProvider().setCredentials(
                new AuthScope(null, -1, null), use_jaas_creds);
        client.getParams().setParameter(ClientPNames.DEFAULT_HOST, target);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);
        HttpResponse response = client.execute(httpget);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        nsf.scheme.verify();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
    }

}
