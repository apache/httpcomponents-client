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
import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests cookie matching when using Virtual Host.
 */
public class TestCookieVirtualHost extends LocalServerTestBase {

    @Test
    public void testCookieMatchingWithVirtualHosts() throws Exception {
        this.server.registerHandlerVirtual("app.mydomain.fr", "*", new HttpRequestHandler() {
            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final int n = Integer.parseInt(request.getFirstHeader("X-Request").getValue());
                switch (n) {
                case 1:
                    // Assert Host is forwarded from URI
                    Assert.assertEquals("app.mydomain.fr", request
                            .getFirstHeader("Host").getValue());

                    response.setCode(HttpStatus.SC_OK);
                    // Respond with Set-Cookie on virtual host domain. This
                    // should be valid.
                    response.addHeader(new BasicHeader("Set-Cookie",
                            "name1=value1; domain=mydomain.fr; path=/"));
                    break;

                case 2:
                    // Assert Host is still forwarded from URI
                    Assert.assertEquals("app.mydomain.fr", request
                            .getFirstHeader("Host").getValue());

                    // We should get our cookie back.
                    Assert.assertNotNull("We must get a cookie header",
                            request.getFirstHeader("Cookie"));
                    response.setCode(HttpStatus.SC_OK);
                    break;

                case 3:
                    // Assert Host is forwarded from URI
                    Assert.assertEquals("app.mydomain.fr", request
                            .getFirstHeader("Host").getValue());

                    response.setCode(HttpStatus.SC_OK);
                    break;
                default:
                    Assert.fail("Unexpected value: " + n);
                    break;
                }
            }

        });

        final HttpHost target = start();

        final CookieStore cookieStore = new BasicCookieStore();
        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        // First request : retrieve a domain cookie from remote server.
        URI uri = new URI("http://app.mydomain.fr");
        HttpGet httpRequest = new HttpGet(uri);
        httpRequest.addHeader("X-Request", "1");
        try (CloseableHttpResponse response1 = this.httpclient.execute(target, httpRequest, context)) {
            EntityUtils.consume(response1.getEntity());
        }

        // We should have one cookie set on domain.
        final List<Cookie> cookies = cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Assert.assertEquals("name1", cookies.get(0).getName());

        // Second request : send the cookie back.
        uri = new URI("http://app.mydomain.fr");
        httpRequest = new HttpGet(uri);
        httpRequest.addHeader("X-Request", "2");
        try (CloseableHttpResponse response2 = this.httpclient.execute(target, httpRequest, context)) {
            EntityUtils.consume(response2.getEntity());
        }

        // Third request : Host header
        uri = new URI("http://app.mydomain.fr");
        httpRequest = new HttpGet(uri);
        httpRequest.addHeader("X-Request", "3");
        try (CloseableHttpResponse response3 = this.httpclient.execute(target, httpRequest, context)) {
            EntityUtils.consume(response3.getEntity());
        }
    }

}
