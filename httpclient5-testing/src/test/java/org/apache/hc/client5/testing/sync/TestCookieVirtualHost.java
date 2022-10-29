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

import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * This class tests cookie matching when using Virtual Host.
 */
public class TestCookieVirtualHost {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

    @Test
    public void testCookieMatchingWithVirtualHosts() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandlerVirtual("app.mydomain.fr", "*", (request, response, context) -> {

            final int n = Integer.parseInt(request.getFirstHeader("X-Request").getValue());
            switch (n) {
            case 1:
                // Assert Host is forwarded from URI
                Assertions.assertEquals("app.mydomain.fr", request
                        .getFirstHeader("Host").getValue());

                response.setCode(HttpStatus.SC_OK);
                // Respond with Set-Cookie on virtual host domain. This
                // should be valid.
                response.addHeader(new BasicHeader("Set-Cookie",
                        "name1=value1; domain=mydomain.fr; path=/"));
                break;

            case 2:
                // Assert Host is still forwarded from URI
                Assertions.assertEquals("app.mydomain.fr", request
                        .getFirstHeader("Host").getValue());

                // We should get our cookie back.
                Assertions.assertNotNull(request.getFirstHeader("Cookie"), "We must get a cookie header");
                response.setCode(HttpStatus.SC_OK);
                break;

            case 3:
                // Assert Host is forwarded from URI
                Assertions.assertEquals("app.mydomain.fr", request
                        .getFirstHeader("Host").getValue());

                response.setCode(HttpStatus.SC_OK);
                break;
            default:
                Assertions.fail("Unexpected value: " + n);
                break;
            }
        });

        final HttpHost target = testResources.targetHost();

        final CloseableHttpClient client = testResources.startClient(b -> {});

        final CookieStore cookieStore = new BasicCookieStore();
        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        // First request : retrieve a domain cookie from remote server.
        final HttpGet request1 = new HttpGet(new URI("http://app.mydomain.fr"));
        request1.addHeader("X-Request", "1");
        client.execute(target, request1, context, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        // We should have one cookie set on domain.
        final List<Cookie> cookies = cookieStore.getCookies();
        Assertions.assertNotNull(cookies);
        Assertions.assertEquals(1, cookies.size());
        Assertions.assertEquals("name1", cookies.get(0).getName());

        // Second request : send the cookie back.
        final HttpGet request2 = new HttpGet(new URI("http://app.mydomain.fr"));
        request2.addHeader("X-Request", "2");
        client.execute(target, request2, context, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });

        // Third request : Host header
        final HttpGet request3 = new HttpGet(new URI("http://app.mydomain.fr"));
        request3.addHeader("X-Request", "3");
        client.execute(target, request3, context, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });
    }

}
