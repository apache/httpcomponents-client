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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestHttpClientBuilderUserAgent extends AbstractIntegrationTestBase {

    private static final String SEEN_USER_AGENT = "X-Seen-User-Agent";

    public TestHttpClientBuilderUserAgent() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD);
    }

    @BeforeEach
    void before() {
        configureServer(bootstrap -> bootstrap
                .register("/ua", (request, response, context) -> {
                    final Header userAgent = request.getFirstHeader(HttpHeaders.USER_AGENT);
                    if (userAgent != null) {
                        response.setHeader(SEEN_USER_AGENT, userAgent.getValue());
                    }
                    response.setCode(200);
                }));
    }

    private HttpResponse execute(final HttpClientBuilder builder) throws Exception {
        final HttpHost target = startServer();
        try (final CloseableHttpClient client = builder.build()) {
            return client.execute(target, new HttpGet("/ua"), response -> {
                EntityUtils.consume(response.getEntity());
                return response;
            });
        }
    }

    @Test
    void testDefaultUserAgentIsSent() throws Exception {
        final HttpResponse response = execute(HttpClientBuilder.create());

        final Header seen = response.getFirstHeader(SEEN_USER_AGENT);
        Assertions.assertNotNull(seen);
        Assertions.assertTrue(seen.getValue().startsWith("Apache-HttpClient/"), seen.getValue());
    }

    @Test
    void testCustomUserAgentIsSent() throws Exception {
        final HttpResponse response = execute(HttpClientBuilder.create()
                .setUserAgent("custom/1.0"));

        final Header seen = response.getFirstHeader(SEEN_USER_AGENT);
        Assertions.assertNotNull(seen);
        Assertions.assertEquals("custom/1.0", seen.getValue());
    }

    @Test
    @SuppressWarnings("deprecation")
    void testDisableDefaultUserAgentIsIgnored() throws Exception {
        final HttpResponse response = execute(HttpClientBuilder.create()
                .disableDefaultUserAgent());

        final Header seen = response.getFirstHeader(SEEN_USER_AGENT);
        Assertions.assertNotNull(seen);
        Assertions.assertTrue(seen.getValue().startsWith("Apache-HttpClient/"), seen.getValue());
    }

}
