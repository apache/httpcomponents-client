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
import java.util.Collections;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@SuppressWarnings({"static-access"}) // test code
public class TestProtocolExec {

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

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        protocolExec = new ProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, true);
        target = new HttpHost("foo", 80);
        proxy = new HttpHost("bar", 8888);
    }

    @Test
    public void testUserInfoInRequestURI() throws Exception {
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 8080));
        final ClassicHttpRequest request = new HttpGet("http://somefella:secret@bar/test");
        final HttpClientContext context = HttpClientContext.create();

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(ProtocolException.class, () -> protocolExec.execute(request, scope, chain));
    }

    @Test
    public void testPostProcessHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenReturn(response);
        Mockito.doThrow(new HttpException("Ooopsie")).when(chain).proceed(Mockito.any(), Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(HttpException.class, () ->
                protocolExec.execute(request, scope, chain));
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    public void testPostProcessIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenReturn(response);
        Mockito.doThrow(new IOException("Ooopsie")).when(chain).proceed(Mockito.any(), Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(IOException.class, () ->
                protocolExec.execute(request, scope, chain));
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    public void testPostProcessRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(chain).proceed(Mockito.any(), Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(RuntimeException.class, () ->
                protocolExec.execute(request, scope, chain));
        Mockito.verify(execRuntime).discardEndpoint();
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

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response1, response2);
        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(new BasicScheme()));
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Mockito.verify(chain, Mockito.times(2)).proceed(request, scope);
        Mockito.verify(inStream1).close();
        Mockito.verify(inStream2, Mockito.never()).close();

        Assertions.assertNotNull(finalResponse);
        Assertions.assertEquals(200, finalResponse.getCode());
    }

    @Test
    public void testExecEntityEnclosingRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
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

        final AuthExchange authExchange = new AuthExchange();
        authExchange.setState(AuthExchange.State.SUCCESS);
        authExchange.select(new BasicScheme() {

            @Override
            public boolean isConnectionBased() {
                return true;
            }

        });
        context.setAuthExchange(target, authExchange);

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response1, response2);

        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Mockito.verify(chain, Mockito.times(2)).proceed(request, scope);
        Mockito.verify(execRuntime).disconnectEndpoint();
        Mockito.verify(inStream2, Mockito.never()).close();

        Assertions.assertNotNull(finalResponse);
        Assertions.assertEquals(200, finalResponse.getCode());
        Assertions.assertNotNull(authExchange.getAuthScheme());
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

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenAnswer((Answer<HttpResponse>) invocationOnMock -> {
                    final Object[] args = invocationOnMock.getArguments();
                    final ClassicHttpRequest requestEE = (ClassicHttpRequest) args[0];
                    requestEE.getEntity().writeTo(new ByteArrayOutputStream());
                    return response1;
                });

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse response = protocolExec.execute(request, scope, chain);
        Assertions.assertEquals(401, response.getCode());
    }

}
