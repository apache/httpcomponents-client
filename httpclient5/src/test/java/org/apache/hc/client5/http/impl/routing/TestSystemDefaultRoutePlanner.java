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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests for {@link SystemDefaultRoutePlanner}.
 */
@SuppressWarnings("boxing") // test code
public class TestSystemDefaultRoutePlanner {

    private SchemePortResolver schemePortResolver;
    private ProxySelector proxySelector;
    private SystemDefaultRoutePlanner routePlanner;

    @Before
    public void setup() {
        schemePortResolver = Mockito.mock(SchemePortResolver.class);
        proxySelector = Mockito.mock(ProxySelector.class);
        routePlanner = new SystemDefaultRoutePlanner(schemePortResolver, proxySelector);
    }

    @Test
    public void testDirect() throws Exception {
        final HttpHost target = new HttpHost("http", "somehost", 80);

        final HttpContext context = new BasicHttpContext();
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(target, route.getTargetHost());
        Assert.assertEquals(1, route.getHopCount());
        Assert.assertFalse(route.isSecure());
        Mockito.verify(schemePortResolver, Mockito.never()).resolve(ArgumentMatchers.<HttpHost>any());
    }

    @Test
    public void testDirectDefaultPort() throws Exception {
        final HttpHost target = new HttpHost("https", "somehost", -1);
        Mockito.when(schemePortResolver.resolve(target)).thenReturn(443);

        final HttpContext context = new BasicHttpContext();
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(new HttpHost("https", "somehost", 443), route.getTargetHost());
        Assert.assertEquals(1, route.getHopCount());
        Assert.assertTrue(route.isSecure());
    }

    @Test
    public void testProxy() throws Exception {

        final InetAddress ia = InetAddress.getByAddress(new byte[] {
            (byte)127, (byte)0, (byte)0, (byte)1
        });
        final InetSocketAddress isa1 = new InetSocketAddress(ia, 11111);
        final InetSocketAddress isa2 = new InetSocketAddress(ia, 22222);

        final List<Proxy> proxies = new ArrayList<>(2);
        proxies.add(new Proxy(Proxy.Type.HTTP, isa1));
        proxies.add(new Proxy(Proxy.Type.HTTP, isa2));

        Mockito.when(proxySelector.select(new URI("http://somehost:80"))).thenReturn(proxies);

        final HttpHost target = new HttpHost("http", "somehost", 80);

        final HttpContext context = new BasicHttpContext();
        final HttpRoute route = routePlanner.determineRoute(target, context);

        Assert.assertEquals(target, route.getTargetHost());
        Assert.assertEquals(2, route.getHopCount());
        Assert.assertEquals(isa1.getPort(), route.getProxyHost().getPort());
    }

}
