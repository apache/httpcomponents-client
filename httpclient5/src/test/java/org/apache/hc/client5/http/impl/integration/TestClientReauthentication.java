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
package org.apache.hc.client5.http.impl.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeProvider;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.localserver.RequestBasicAuth;
import org.apache.hc.client5.http.methods.CloseableHttpResponse;
import org.apache.hc.client5.http.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.entity.StringEntity;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestClientReauthentication extends LocalServerTestBase {

    public class ResponseBasicUnauthorized implements HttpResponseInterceptor {

        @Override
        public void process(
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "MyBasic realm=\"test realm\"");
            }
        }

    }

    @Before @Override
    public void setUp() throws Exception {
        super.setUp();
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer(LocalServerTestBase.ORIGIN))
            .add(new ResponseContent())
            .add(new ResponseConnControl())
            .add(new RequestBasicAuth())
            .add(new ResponseBasicUnauthorized()).build();
        this.serverBootstrap.setHttpProcessor(httpproc);
    }

    static class AuthHandler implements HttpRequestHandler {

        private final AtomicLong count = new AtomicLong(0);

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            final String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals("test:test")) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                // Make client re-authenticate on each fourth request
                if (this.count.incrementAndGet() % 4 == 0) {
                    response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
                } else {
                    response.setStatusCode(HttpStatus.SC_OK);
                    final StringEntity entity = new StringEntity("success", StandardCharsets.US_ASCII);
                    response.setEntity(entity);
                }
            }
        }

    }

    static class TestCredentialsProvider implements CredentialsProvider {

        private final Credentials creds;
        private AuthScope authscope;

        TestCredentialsProvider(final Credentials creds) {
            super();
            this.creds = creds;
        }

        @Override
        public Credentials getCredentials(final AuthScope authscope) {
            this.authscope = authscope;
            return this.creds;
        }

    }

    @Test
    public void testBasicAuthenticationSuccess() throws Exception {
        this.serverBootstrap.registerHandler("*", new AuthHandler());

        final BasicSchemeFactory myBasicAuthSchemeFactory = new BasicSchemeFactory() {

            @Override
            public AuthScheme create(final HttpContext context) {
                return new BasicScheme() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getName() {
                        return "MyBasic";
                    }

                };
            }

        };

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));

        final RequestConfig config = RequestConfig.custom()
            .setTargetPreferredAuthSchemes(Arrays.asList("MyBasic"))
            .build();
        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("MyBasic", myBasicAuthSchemeFactory)
            .build();
        this.httpclient = this.clientBuilder
            .setDefaultAuthSchemeRegistry(authSchemeRegistry)
            .setDefaultCredentialsProvider(credsProvider)
            .build();

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        for (int i = 0; i < 10; i++) {
            final HttpGet httpget = new HttpGet("/");
            httpget.setConfig(config);
            try (final CloseableHttpResponse response = this.httpclient.execute(target, httpget, context)) {
                final HttpEntity entity = response.getEntity();
                Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertNotNull(entity);
                EntityUtils.consume(entity);
            }
        }
    }

}
