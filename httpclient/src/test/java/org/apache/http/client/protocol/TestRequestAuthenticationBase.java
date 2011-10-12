/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.protocol;

import java.io.IOException;
import java.util.LinkedList;

import junit.framework.Assert;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.ContextAwareAuthScheme;
import org.apache.http.auth.Credentials;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRequestAuthenticationBase {

    static class TestRequestAuthentication extends RequestAuthenticationBase {

        public void process(
                final HttpRequest request,
                final HttpContext context) throws HttpException, IOException {
            AuthState authState = (AuthState) context.getAttribute("test-auth-state");
            super.process(authState, request, context);
        }

    }

    private ContextAwareAuthScheme authScheme;
    private Credentials credentials;
    private AuthState authState;
    private HttpContext context;
    private HttpRequestInterceptor interceptor;

    @Before
    public void setUp() throws Exception {
        this.authScheme = Mockito.mock(ContextAwareAuthScheme.class);
        this.credentials = Mockito.mock(Credentials.class);
        this.authState = new AuthState();
        this.context = new BasicHttpContext();
        this.context.setAttribute("test-auth-state", this.authState);
        this.interceptor = new TestRequestAuthentication();
    }

    @Test
    public void testAuthFailureState() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.FAILURE);
        this.authState.update(this.authScheme, this.credentials);

        this.interceptor.process(request, this.context);

        Assert.assertFalse(request.containsHeader(AUTH.WWW_AUTH_RESP));

        Mockito.verify(this.authScheme, Mockito.never()).authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

    @Test
    public void testAuthChallengeStateNoOption() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(this.authScheme, this.credentials);

        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(AUTH.WWW_AUTH_RESP, "stuff"));

        this.interceptor.process(request, this.context);

        Assert.assertTrue(request.containsHeader(AUTH.WWW_AUTH_RESP));

        Mockito.verify(this.authScheme).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthChallengeStateOneOptions() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.CHALLENGED);
        LinkedList<AuthOption> authOptions = new LinkedList<AuthOption>();
        authOptions.add(new AuthOption(this.authScheme, this.credentials));
        this.authState.update(authOptions);

        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(AUTH.WWW_AUTH_RESP, "stuff"));

        this.interceptor.process(request, this.context);

        Assert.assertSame(this.authScheme, this.authState.getAuthScheme());
        Assert.assertSame(this.credentials, this.authState.getCredentials());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(AUTH.WWW_AUTH_RESP));

        Mockito.verify(this.authScheme).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthChallengeStateMultipleOption() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.CHALLENGED);

        LinkedList<AuthOption> authOptions = new LinkedList<AuthOption>();
        ContextAwareAuthScheme authScheme1 = Mockito.mock(ContextAwareAuthScheme.class);
        Mockito.doThrow(new AuthenticationException()).when(authScheme1).authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
        ContextAwareAuthScheme authScheme2 = Mockito.mock(ContextAwareAuthScheme.class);
        Mockito.when(authScheme2.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(AUTH.WWW_AUTH_RESP, "stuff"));
        authOptions.add(new AuthOption(authScheme1, this.credentials));
        authOptions.add(new AuthOption(authScheme2, this.credentials));
        this.authState.update(authOptions);

        this.interceptor.process(request, this.context);

        Assert.assertSame(authScheme2, this.authState.getAuthScheme());
        Assert.assertSame(this.credentials, this.authState.getCredentials());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(AUTH.WWW_AUTH_RESP));

        Mockito.verify(authScheme1, Mockito.times(1)).authenticate(this.credentials, request, this.context);
        Mockito.verify(authScheme2, Mockito.times(1)).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthSuccess() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.SUCCESS);
        this.authState.update(this.authScheme, this.credentials);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.FALSE);
        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(AUTH.WWW_AUTH_RESP, "stuff"));

        this.interceptor.process(request, this.context);

        Assert.assertSame(this.authScheme, this.authState.getAuthScheme());
        Assert.assertSame(this.credentials, this.authState.getCredentials());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(AUTH.WWW_AUTH_RESP));

        Mockito.verify(this.authScheme).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthSuccessConnectionBased() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.SUCCESS);
        this.authState.update(this.authScheme, this.credentials);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.TRUE);
        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(AUTH.WWW_AUTH_RESP, "stuff"));

        this.interceptor.process(request, this.context);

        Assert.assertFalse(request.containsHeader(AUTH.WWW_AUTH_RESP));

        Mockito.verify(this.authScheme, Mockito.never()).authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

}
