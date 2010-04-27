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

package org.apache.http.impl.conn;

import java.net.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;

import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;

import org.apache.http.mockup.ProxySelectorMockup;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for <code>ProxySelectorRoutePlanner</code>.
 */
public class TestProxySelRoutePlanner {

    /**
     * Instantiates a default scheme registry.
     *
     * @return the default scheme registry
     */
    public SchemeRegistry createSchemeRegistry() {

        SchemeRegistry schreg = new SchemeRegistry();
        schreg.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        return schreg;
    }

    @Test
    public void testDirect() throws Exception {

        HttpRoutePlanner hrp =
            new ProxySelectorRoutePlanner(createSchemeRegistry(),
                                          new ProxySelectorMockup(null));

        HttpHost target =
            new HttpHost("www.test.invalid", 80, "http");
        HttpRequest request =
            new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);

        HttpRoute route = hrp.determineRoute(target, request, null);

        Assert.assertEquals("wrong target", target, route.getTargetHost());
        Assert.assertEquals("not direct", 1, route.getHopCount());
    }

    @Test
    public void testProxy() throws Exception {

        InetAddress ia = InetAddress.getByAddress(new byte[] {
            (byte)127, (byte)0, (byte)0, (byte)1
        });
        InetSocketAddress isa1 = new InetSocketAddress(ia, 11111);
        InetSocketAddress isa2 = new InetSocketAddress(ia, 22222);

        List<Proxy> proxies = new ArrayList<Proxy>(2);
        proxies.add(new Proxy(Proxy.Type.HTTP, isa1));
        proxies.add(new Proxy(Proxy.Type.HTTP, isa2));

        HttpRoutePlanner hrp =
            new ProxySelectorRoutePlanner(createSchemeRegistry(),
                                          new ProxySelectorMockup(proxies));

        HttpHost target =
            new HttpHost("www.test.invalid", 80, "http");
        HttpRequest request =
            new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);

        HttpRoute route = hrp.determineRoute(target, request, null);

        Assert.assertEquals("wrong target", target, route.getTargetHost());
        Assert.assertEquals("not via proxy", 2, route.getHopCount());
        Assert.assertEquals("wrong proxy", isa1.getPort(),
                     route.getProxyHost().getPort());
    }

}
