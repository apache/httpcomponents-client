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

import java.net.URI;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link DefaultRoutePlanner}.
 */
@SuppressWarnings({"boxing","static-access"}) // test code
public class TestDefaultRoutePlanner {

    private SchemePortResolver schemePortResolver;
    private DefaultRoutePlanner routePlanner;

    @Before
    public void setup() {
        schemePortResolver = Mockito.mock(SchemePortResolver.class);
        routePlanner = new DefaultRoutePlanner(schemePortResolver);
    }

    @Test
    public void testDirect() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80, "http");

        final HttpContext context = new BasicHttpContext();
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(target, route.getTargetHost());
        Assert.assertEquals(1, route.getHopCount());
        Assert.assertFalse(route.isSecure());
        Mockito.verify(schemePortResolver, Mockito.never()).resolve(Mockito.<HttpHost>any());
    }

    @Test
    public void testDirectDefaultPort() throws Exception {
        final HttpHost target = new HttpHost("somehost", -1, "https");
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(443);

        final HttpContext context = new BasicHttpContext();
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(new HttpHost("somehost", 443, "https"), route.getTargetHost());
        Assert.assertEquals(1, route.getHopCount());
        Assert.assertTrue(route.isSecure());
    }

    @Test
    public void testViaProxy() throws Exception {
        final HttpHost target = new HttpHost("somehost", 80, "http");
        final HttpHost proxy = new HttpHost("proxy", 8080);

        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom().setProxy(proxy).build());
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(target, route.getTargetHost());
        Assert.assertEquals(proxy, route.getProxyHost());
        Assert.assertEquals(2, route.getHopCount());
        Assert.assertFalse(route.isSecure());
        Mockito.verify(schemePortResolver, Mockito.never()).resolve(Mockito.<HttpHost>any());
    }

    @Test(expected= ProtocolException.class)
    public void testNullTarget() throws Exception {
        final HttpContext context = new BasicHttpContext();
        routePlanner.determineRoute(null, context);
    }

    @Test
    public void testDetermineHost() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpRequest request1 = new BasicHttpRequest("GET", "/");
        final HttpHost host1 = routePlanner.determineTargetHost(request1, context);
        Assert.assertThat(host1, CoreMatchers.nullValue());

        final HttpRequest request2 = new BasicHttpRequest("GET", new URI("https://somehost:8443/"));
        final HttpHost host2 = routePlanner.determineTargetHost(request2, context);
        Assert.assertThat(host2, CoreMatchers.equalTo(new HttpHost("somehost", 8443, "https")));
    }

    @Test(expected = ProtocolException.class)
    public void testDetermineHostMissingScheme() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpRequest request1 = new BasicHttpRequest("GET", "/");
        request1.setAuthority(new URIAuthority("host"));
        routePlanner.determineTargetHost(request1, context);
    }

}
