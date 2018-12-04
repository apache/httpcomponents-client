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

package org.apache.hc.client5.http.impl.routing;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link DefaultProxyRoutePlanner}.
 */
public class TestDefaultProxyRoutePlanner {

    private HttpHost defaultProxy;
    private SchemePortResolver schemePortResolver;
    private DefaultProxyRoutePlanner routePlanner;

    @Before
    public void setup() {
        defaultProxy = new HttpHost("default.proxy.host", 8888);
        schemePortResolver = Mockito.mock(SchemePortResolver.class);
        routePlanner = new DefaultProxyRoutePlanner(defaultProxy,
            schemePortResolver);
    }

    @Test
    public void testDefaultProxyDirect() throws Exception {
        final HttpHost target = new HttpHost("http", "somehost", 80);

        final HttpContext context = new BasicHttpContext();
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(target, route.getTargetHost());
        Assert.assertEquals(defaultProxy, route.getProxyHost());
        Assert.assertEquals(2, route.getHopCount());
        Assert.assertFalse(route.isSecure());
    }

    @Test
    public void testViaProxy() throws Exception {
        final HttpHost target = new HttpHost("http", "somehost", 80);
        final HttpHost proxy = new HttpHost("custom.proxy.host", 8080);
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom().setProxy(proxy).build());
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(target, route.getTargetHost());
        Assert.assertEquals(proxy, route.getProxyHost());
        Assert.assertEquals(2, route.getHopCount());
        Assert.assertFalse(route.isSecure());
    }

}
