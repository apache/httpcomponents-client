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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.MinimalHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Client protocol handling tests.
 */
public abstract class TestMinimalClientRequestExecution {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources;

    protected TestMinimalClientRequestExecution(final URIScheme scheme) {
        this.testResources = new TestClientResources(scheme, TIMEOUT);
    }

    public URIScheme scheme() {
        return testResources.scheme();
    }
    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setCode(HttpStatus.SC_OK);
            final StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    @Test
    public void testNonCompliantURIWithContext() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandler("*", new SimpleService());
        final HttpHost target = testResources.targetHost();

        final MinimalHttpClient client = testResources.startMinimalClient();

        final HttpClientContext context = HttpClientContext.create();
        for (int i = 0; i < 10; i++) {
            final HttpGet request = new HttpGet("/");
            client.execute(target, request, context, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                return null;
            });

            final HttpRequest reqWrapper = context.getRequest();
            Assertions.assertNotNull(reqWrapper);

            final Header[] headers = reqWrapper.getHeaders();
            final Set<String> headerSet = new HashSet<>();
            for (final Header header: headers) {
                headerSet.add(header.getName().toLowerCase(Locale.ROOT));
            }
            Assertions.assertEquals(3, headerSet.size());
            Assertions.assertTrue(headerSet.contains("connection"));
            Assertions.assertTrue(headerSet.contains("host"));
            Assertions.assertTrue(headerSet.contains("user-agent"));
        }
    }

    @Test
    public void testNonCompliantURIWithoutContext() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandler("*", new SimpleService());
        final HttpHost target = testResources.targetHost();

        final MinimalHttpClient client = testResources.startMinimalClient();

        for (int i = 0; i < 10; i++) {
            final HttpGet request = new HttpGet("/");
            client.execute(target, request, response -> {
                EntityUtils.consume(response.getEntity());
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                return null;
            });
        }
    }

}
