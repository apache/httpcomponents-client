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
import java.util.function.Consumer;

import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Unit tests for some of the NTLM auth functionality..
 */
public class TestClientAuthenticationFakeNTLM {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

    public ClassicTestServer startServer() throws IOException {
        return testResources.startServer(null, null, null);
    }

    public CloseableHttpClient startClient(final Consumer<HttpClientBuilder> clientCustomizer) {
        return testResources.startClient(clientCustomizer);
    }

    public CloseableHttpClient startClient() {
        return testResources.startClient(builder -> {});
    }

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    static class NtlmResponseHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            response.setHeader("Connection", "Keep-Alive");
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.NTLM);
        }
    }

    @Test
    public void testNTLMAuthenticationFailure() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("*", new NtlmResponseHandler());
        final HttpHost target = targetHost();

        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(target, new NTCredentials("test", "test".toCharArray(), null, null))
                .build();

        final CloseableHttpClient client = startClient(builder -> builder
                .setDefaultCredentialsProvider(credsProvider)
        );

        final HttpContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });
    }

    static class NtlmType2MessageResponseHandler implements HttpRequestHandler {

        private final String authenticateHeaderValue;

        public NtlmType2MessageResponseHandler(final String type2Message) {
            this.authenticateHeaderValue = StandardAuthScheme.NTLM + " " + type2Message;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            response.setHeader("Connection", "Keep-Alive");
            if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.NTLM);
            } else {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeaderValue);
            }
        }
    }

    @Test
    public void testNTLMv1Type2Message() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("*", new NtlmType2MessageResponseHandler("TlRMTVNTUAACAA" +
                "AADAAMADgAAAAzggLiASNFZ4mrze8AAAAAAAAAAAAAAAAAAAAABgBwFwAAAA9T" +
                "AGUAcgB2AGUAcgA="));
        final HttpHost target = targetHost();

        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(target, new NTCredentials("test", "test".toCharArray(), null, null))
                .build();

        final CloseableHttpClient client = startClient(builder -> builder
                .setDefaultCredentialsProvider(credsProvider)
        );

        final HttpContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });
    }

    @Test
    public void testNTLMv2Type2Message() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("*", new NtlmType2MessageResponseHandler("TlRMTVNTUAACAA" +
                "AADAAMADgAAAAzgoriASNFZ4mrze8AAAAAAAAAACQAJABEAAAABgBwFwAAAA9T" +
                "AGUAcgB2AGUAcgACAAwARABvAG0AYQBpAG4AAQAMAFMAZQByAHYAZQByAAAAAAA="));
        final HttpHost target = targetHost();

        final CredentialsProvider credsProvider = CredentialsProviderBuilder.create()
                .add(target, new NTCredentials("test", "test".toCharArray(), null, null))
                .build();

        final CloseableHttpClient client = startClient(builder -> builder
                .setDefaultCredentialsProvider(credsProvider)
        );

        final HttpContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });
    }

    static class NtlmType2MessageOnlyResponseHandler implements HttpRequestHandler {

        private final String authenticateHeaderValue;

        public NtlmType2MessageOnlyResponseHandler(final String type2Message) {
            this.authenticateHeaderValue = StandardAuthScheme.NTLM + " " + type2Message;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            response.setHeader("Connection", "Keep-Alive");
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeaderValue);
        }
    }

    @Test
    public void testNTLMType2MessageOnlyAuthenticationFailure() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("*", new NtlmType2MessageOnlyResponseHandler("TlRMTVNTUAACAA" +
                "AADAAMADgAAAAzggLiASNFZ4mrze8AAAAAAAAAAAAAAAAAAAAABgBwFwAAAA9T" +
                "AGUAcgB2AGUAcgA="));
        final HttpHost target = targetHost();

        final CloseableHttpClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new NTCredentials("test", "test".toCharArray(), null, null))
                .build());
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });
    }

    @Test
    public void testNTLMType2NonUnicodeMessageOnlyAuthenticationFailure() throws Exception {
        final ClassicTestServer server = startServer();
        server.registerHandler("*", new NtlmType2MessageOnlyResponseHandler("TlRMTVNTUAACAA" +
                "AABgAGADgAAAAyggLiASNFZ4mrze8AAAAAAAAAAAAAAAAAAAAABgBwFwAAAA9T" +
                "ZXJ2ZXI="));
        final HttpHost target = targetHost();


        final CloseableHttpClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new NTCredentials("test", "test".toCharArray(), null, null))
                .build());
        final HttpGet httpget = new HttpGet("/");

        client.execute(target, httpget, context, response -> {
            EntityUtils.consume(response.getEntity());
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            return null;
        });
    }

}
