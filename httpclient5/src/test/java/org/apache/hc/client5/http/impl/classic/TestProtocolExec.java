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
package org.apache.hc.client5.http.impl.classic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.NTLMScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings({"static-access"}) // test code
public class TestProtocolExec {

    @Mock
    private HttpProcessor httpProcessor;
    @Mock
    private AuthenticationStrategy targetAuthStrategy;
    @Mock
    private AuthenticationStrategy proxyAuthStrategy;
    @Mock
    private ExecChain chain;
    @Mock
    private ExecRuntime execRuntime;

    private ProtocolExec protocolExec;
    private HttpHost target;
    private HttpHost proxy;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        protocolExec = new ProtocolExec(httpProcessor, targetAuthStrategy, proxyAuthStrategy);
        target = new HttpHost("foo", 80);
        proxy = new HttpHost("bar", 8888);
    }

    @Test
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        protocolExec.execute(request, scope, chain);

        Mockito.verify(httpProcessor).process(request, null, context);
        Mockito.verify(chain).proceed(request, scope);
        Mockito.verify(httpProcessor).process(response, null, context);

        Assert.assertEquals(route, context.getHttpRoute());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());
    }

    @Test
    public void testUserInfoInRequestURI() throws Exception {
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 8080));
        final ClassicHttpRequest request = new HttpGet("http://somefella:secret@bar/test");
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(new BasicCredentialsProvider());

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        protocolExec.execute(request, scope, chain);
        Assert.assertEquals(new URI("http://bar/test"), request.getUri());
        final CredentialsProvider credentialsProvider = context.getCredentialsProvider();
        final Credentials creds = credentialsProvider.getCredentials(new AuthScope(null, "bar", -1, null, null), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("somefella", creds.getUserPrincipal().getName());
    }

    @Test(expected = HttpException.class)
    public void testPostProcessHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.doThrow(new HttpException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.<EntityDetails>isNull(), Mockito.<HttpContext>any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        try {
            protocolExec.execute(request, scope, chain);
        } catch (final Exception ex) {
            Mockito.verify(execRuntime).discardEndpoint();
            throw ex;
        }
    }

    @Test(expected = IOException.class)
    public void testPostProcessIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.doThrow(new IOException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.<EntityDetails>isNull(), Mockito.<HttpContext>any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        try {
            protocolExec.execute(request, scope, chain);
        } catch (final Exception ex) {
            Mockito.verify(execRuntime).discardEndpoint();
            throw ex;
        }
    }

    @Test(expected = RuntimeException.class)
    public void testPostProcessRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.<EntityDetails>isNull(), Mockito.<HttpContext>any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        try {
            protocolExec.execute(request, scope, chain);
        } catch (final Exception ex) {
            Mockito.verify(execRuntime).discardEndpoint();
            throw ex;
        }
    }

    @Test
    public void testExecRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");
        final InputStream inStream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(inStream2)
                .build());

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(target), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.<ExecChain.Scope>any())).thenReturn(response1, response2);
        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Mockito.verify(chain, Mockito.times(2)).proceed(request, scope);
        Mockito.verify(inStream1).close();
        Mockito.verify(inStream2, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(200, finalResponse.getCode());
    }

    @Test
    public void testExecEntityEnclosingRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target, proxy);
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");
        final InputStream inStream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(inStream2)
                .build());

        final HttpClientContext context = new HttpClientContext();

        final AuthExchange proxyAuthExchange = new AuthExchange();
        proxyAuthExchange.setState(AuthExchange.State.SUCCESS);
        proxyAuthExchange.select(new NTLMScheme());
        context.setAuthExchange(proxy, proxyAuthExchange);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(target), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.<ExecChain.Scope>any())).thenReturn(response1, response2);

        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Mockito.verify(chain, Mockito.times(2)).proceed(request, scope);
        Mockito.verify(execRuntime).disconnectEndpoint();
        Mockito.verify(inStream2, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(200, finalResponse.getCode());
        Assert.assertNull(proxyAuthExchange.getAuthScheme());
    }

    @Test
    public void testExecEntityEnclosingRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpPost request = new HttpPost("http://foo/test");
        final InputStream inStream0 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        request.setEntity(EntityBuilder.create()
                .setStream(inStream0)
                .build());
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(target), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.<ExecChain.Scope>any())).thenAnswer(new Answer<HttpResponse>() {

            @Override
            public HttpResponse answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final ClassicHttpRequest requestEE = (ClassicHttpRequest) args[0];
                requestEE.getEntity().writeTo(new ByteArrayOutputStream());
                return response1;
            }

        });

        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse response = protocolExec.execute(request, scope, chain);
        Assert.assertEquals(401, response.getCode());
    }

}
