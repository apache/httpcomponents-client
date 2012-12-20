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
 */
package org.apache.http.impl.client;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * This class tests cookie matching when using Virtual Host.
 */
public class TestCookieVirtualHost extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        this.localServer = new LocalTestServer(null, null);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        this.httpclient = new DefaultHttpClient();
    }

    @Test
    public void testCookieMatchingWithVirtualHosts() throws Exception {
        this.localServer.register("*", new HttpRequestHandler() {
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                int n = Integer.parseInt(request.getFirstHeader("X-Request").getValue());
                switch (n) {
                case 1:
                    // Assert Host is forwarded from URI
                    Assert.assertEquals("app.mydomain.fr", request
                            .getFirstHeader("Host").getValue());

                    response.setStatusLine(HttpVersion.HTTP_1_1,
                            HttpStatus.SC_OK);
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
                    response.setStatusLine(HttpVersion.HTTP_1_1,
                            HttpStatus.SC_OK);
                    break;

                case 3:
                    // Assert Host is forwarded from URI
                    Assert.assertEquals("app.mydomain.fr", request
                            .getFirstHeader("Host").getValue());

                    response.setStatusLine(HttpVersion.HTTP_1_1,
                            HttpStatus.SC_OK);
                    break;
                }
            }

        });

        this.httpclient.getParams().setParameter(ClientPNames.COOKIE_POLICY,
                CookiePolicy.BEST_MATCH);

        CookieStore cookieStore = new BasicCookieStore();
        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

        // First request : retrieve a domain cookie from remote server.
        URI uri = new URI("http://app.mydomain.fr");
        HttpRequest httpRequest = new HttpGet(uri);
        httpRequest.addHeader("X-Request", "1");
        HttpResponse response1 = this.httpclient.execute(getServerHttp(),
                httpRequest, context);
        HttpEntity e1 = response1.getEntity();
        EntityUtils.consume(e1);

        // We should have one cookie set on domain.
        List<Cookie> cookies = cookieStore.getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(1, cookies.size());
        Assert.assertEquals("name1", cookies.get(0).getName());

        // Second request : send the cookie back.
        uri = new URI("http://app.mydomain.fr");
        httpRequest = new HttpGet(uri);
        httpRequest.addHeader("X-Request", "2");
        HttpResponse response2 = this.httpclient.execute(getServerHttp(),
                httpRequest, context);
        HttpEntity e2 = response2.getEntity();
        EntityUtils.consume(e2);

        // Third request : Host header
        uri = new URI("http://app.mydomain.fr");
        httpRequest = new HttpGet(uri);
        httpRequest.addHeader("X-Request", "3");
        HttpResponse response3 = this.httpclient.execute(getServerHttp(),
                httpRequest, context);
        HttpEntity e3 = response3.getEntity();
        EntityUtils.consume(e3);
    }

}
