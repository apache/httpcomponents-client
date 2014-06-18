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

import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for some of the NTLM auth functionality..
 */
public class TestClientAuthenticationFakeNTLM extends LocalServerTestBase {

    static class NtlmResponseHandler implements HttpRequestHandler {

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusLine(new BasicStatusLine(
                    HttpVersion.HTTP_1_1,
                    HttpStatus.SC_UNAUTHORIZED,
                    "Authentication Required"));
            response.setHeader("Connection", "Keep-Alive");
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "NTLM");
        }
    }

    @Test
    public void testNTLMAuthenticationFailure() throws Exception {
        this.serverBootstrap.registerHandler("*", new NtlmResponseHandler());

        final HttpHost target = start();

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new NTCredentials("test", "test", null, null));

        this.httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        final HttpContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED,
                response.getStatusLine().getStatusCode());
    }

    static class NtlmType2MessageResponseHandler implements HttpRequestHandler {

        private final String authenticateHeaderValue;

        public NtlmType2MessageResponseHandler(final String type2Message) {
            this.authenticateHeaderValue = "NTLM " + type2Message;
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusLine(new BasicStatusLine(
                    HttpVersion.HTTP_1_1,
                    HttpStatus.SC_UNAUTHORIZED,
                    "Authentication Required"));
            response.setHeader("Connection", "Keep-Alive");
            if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "NTLM");
            } else {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeaderValue);
            }
        }
    }

    @Test
    public void testNTLMv1Type2Message() throws Exception {
        this.serverBootstrap.registerHandler("*", new NtlmType2MessageResponseHandler("TlRMTVNTUAACAA" +
                "AADAAMADgAAAAzggLiASNFZ4mrze8AAAAAAAAAAAAAAAAAAAAABgBwFwAAAA9T" +
                "AGUAcgB2AGUAcgA="));
        final HttpHost target = start();

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new NTCredentials("test", "test", null, null));

        this.httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        final HttpContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED,
                response.getStatusLine().getStatusCode());
    }

    @Test
    public void testNTLMv2Type2Message() throws Exception {
        this.serverBootstrap.registerHandler("*", new NtlmType2MessageResponseHandler("TlRMTVNTUAACAA" +
                "AADAAMADgAAAAzgoriASNFZ4mrze8AAAAAAAAAACQAJABEAAAABgBwFwAAAA9T" +
                "AGUAcgB2AGUAcgACAAwARABvAG0AYQBpAG4AAQAMAFMAZQByAHYAZQByAAAAAAA="));
        final HttpHost target = start();

        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new NTCredentials("test", "test", null, null));

        this.httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        final HttpContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED,
                response.getStatusLine().getStatusCode());
    }

    static class NtlmType2MessageOnlyResponseHandler implements HttpRequestHandler {

        private final String authenticateHeaderValue;

        public NtlmType2MessageOnlyResponseHandler(final String type2Message) {
            this.authenticateHeaderValue = "NTLM " + type2Message;
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusLine(new BasicStatusLine(
                    HttpVersion.HTTP_1_1,
                    HttpStatus.SC_UNAUTHORIZED,
                    "Authentication Required"));
            response.setHeader("Connection", "Keep-Alive");
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeaderValue);
        }
    }

    @Test
    public void testNTLMType2MessageOnlyAuthenticationFailure() throws Exception {
        this.serverBootstrap.registerHandler("*", new NtlmType2MessageOnlyResponseHandler("TlRMTVNTUAACAA" +
                "AADAAMADgAAAAzggLiASNFZ4mrze8AAAAAAAAAAAAAAAAAAAAABgBwFwAAAA9T" +
                "AGUAcgB2AGUAcgA="));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new NTCredentials("test", "test", null, null));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED,
                response.getStatusLine().getStatusCode());
    }

    @Test
    public void testNTLMType2NonUnicodeMessageOnlyAuthenticationFailure() throws Exception {
        this.serverBootstrap.registerHandler("*", new NtlmType2MessageOnlyResponseHandler("TlRMTVNTUAACAA" +
                "AABgAGADgAAAAyggLiASNFZ4mrze8AAAAAAAAAAAAAAAAAAAAABgBwFwAAAA9T" +
                "ZXJ2ZXI="));

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new NTCredentials("test", "test", null, null));
        context.setCredentialsProvider(credsProvider);
        final HttpGet httpget = new HttpGet("/");

        final HttpResponse response = this.httpclient.execute(target, httpget, context);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED,
                response.getStatusLine().getStatusCode());
    }

}
