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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo.LayerType;
import org.apache.hc.client5.http.RouteInfo.TunnelType;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicRouteDirector}.
 */
public class TestRouteDirector {

    // a selection of constants for generating routes
    public final static
        HttpHost TARGET1 = new HttpHost("target1.test.invalid", 80);
    public final static
        HttpHost TARGET2 = new HttpHost("target2.test.invalid", 8080);
    // It is not necessary to have extra targets for https.
    // The 'layered' and 'secure' flags are specified explicitly
    // for routes, they will not be determined from the scheme.

    public final static
        HttpHost PROXY1 = new HttpHost("proxy1.test.invalid", 80);
    public final static
        HttpHost PROXY2 = new HttpHost("proxy2.test.invalid", 1080);
    public final static
        HttpHost PROXY3 = new HttpHost("proxy3.test.invalid", 88);

    public final static InetAddress LOCAL41;
    public final static InetAddress LOCAL42;
    public final static InetAddress LOCAL61;
    public final static InetAddress LOCAL62;

    // need static initializer to deal with exceptions
    static {
        try {
            LOCAL41 = InetAddress.getByAddress(new byte[]{ 127, 0, 0, 1 });
            LOCAL42 = InetAddress.getByAddress(new byte[]{ 127, 0, 0, 2 });

            LOCAL61 = InetAddress.getByAddress(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
            });
            LOCAL62 = InetAddress.getByAddress(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2
            });

        } catch (final Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    @Test
    public void testIllegal() {
        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route = new HttpRoute(TARGET1);
        Assertions.assertThrows(NullPointerException.class, () ->
                rowdy.nextStep(null, route));
    }

    @Test
    public void testDirect() {

        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route1   = new HttpRoute(TARGET1);
        final HttpRoute route2   = new HttpRoute(TARGET2);
        final HttpRoute route1p1 = new HttpRoute(TARGET1, null, PROXY1, false);

        int step = rowdy.nextStep(route1, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1");

        step = rowdy.nextStep(route2, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route2");

        step = rowdy.nextStep(route1, route1);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1 not detected");

        step = rowdy.nextStep(route2, route2);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route2 not detected");

        step = rowdy.nextStep(route1, route2);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable target not detected");

        step = rowdy.nextStep(route1, route1p1);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "invalid proxy not detected");
    }

    @Test
    public void testProxy() {

        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route1p1 = new HttpRoute(TARGET1, null, PROXY1, false);
        final HttpRoute route1p2 = new HttpRoute(TARGET1, null, PROXY2, false);
        final HttpRoute route2p1 = new HttpRoute(TARGET2, null, PROXY1, false);
        final HttpRoute route0   = new HttpRoute(PROXY1);
        final HttpRoute route1   = new HttpRoute(TARGET1);

        int step = rowdy.nextStep(route1p1, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1p1");

        step = rowdy.nextStep(route1p2, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1p2");

        step = rowdy.nextStep(route1p1, route1p1);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1p1 not detected");

        step = rowdy.nextStep(route1p2, route1p2);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1p2 not detected");

        step = rowdy.nextStep(route2p1, route2p1);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route2p1 not detected");

        step = rowdy.nextStep(route1p1, route1p2);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1p1 via route1p2 not detected");

        step = rowdy.nextStep(route1p1, route2p1);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1p1 via route2p1 not detected");

        step = rowdy.nextStep(route1p1, route0);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1p1 via route0 not detected");

        step = rowdy.nextStep(route1p1, route1);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1p1 via route1 not detected");
    }

    @Test
    public void testProxyChain() {
        final HttpHost[] chainA = { PROXY1 };
        final HttpHost[] chainB = { PROXY1, PROXY2 };
        final HttpHost[] chainC = { PROXY2, PROXY1 };

        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route1cA  = new HttpRoute(TARGET1, null, chainA, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1cB  = new HttpRoute(TARGET1, null, chainB, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1cC  = new HttpRoute(TARGET1, null, chainC, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1cD  = new HttpRoute(TARGET1, null, chainC, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);

        int step = rowdy.nextStep(route1cA, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1cA");

        step = rowdy.nextStep(route1cB, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1cB");

        step = rowdy.nextStep(route1cC, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1cC");

        step = rowdy.nextStep(route1cD, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1cD");


        step = rowdy.nextStep(route1cB, route1cA);
        Assertions.assertEquals(HttpRouteDirector.TUNNEL_PROXY, step, "wrong step to route 1cB from 1cA");

        step = rowdy.nextStep(route1cB, route1cB);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route 1cB not detected");

        step = rowdy.nextStep(route1cB, route1cC);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1cB from 1cC not detected");

        step = rowdy.nextStep(route1cB, route1cD);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1cB from 1cD not detected");


        step = rowdy.nextStep(route1cA, route1cB);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1cA from 1cB not detected");
    }

    @Test
    public void testLocalDirect() {

        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route1l41 = new HttpRoute(TARGET1, LOCAL41, false);
        final HttpRoute route1l42 = new HttpRoute(TARGET1, LOCAL42, false);
        final HttpRoute route1l61 = new HttpRoute(TARGET1, LOCAL61, false);
        final HttpRoute route1l00 = new HttpRoute(TARGET1, null, false);

        int step = rowdy.nextStep(route1l41, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1l41");

        step = rowdy.nextStep(route1l42, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1l42");

        step = rowdy.nextStep(route1l61, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1l61");

        step = rowdy.nextStep(route1l00, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1l00");

        step = rowdy.nextStep(route1l41, route1l41);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l41 not detected");

        step = rowdy.nextStep(route1l42, route1l42);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l42 not detected");

        step = rowdy.nextStep(route1l61, route1l61);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l61 not detected");

        step = rowdy.nextStep(route1l00, route1l00);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l00 not detected");


        step = rowdy.nextStep(route1l41, route1l42);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1l41 via route1l42 not detected");

        step = rowdy.nextStep(route1l41, route1l61);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1l41 via route1l61 not detected");

        step = rowdy.nextStep(route1l41, route1l00);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1l41 via route1l00 not detected");


        step = rowdy.nextStep(route1l00, route1l41);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l00 as route1l41 not detected");

        step = rowdy.nextStep(route1l00, route1l42);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l00 as route1l42 not detected");

        step = rowdy.nextStep(route1l00, route1l61);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1l00 as route1l61 not detected");
    }

    @Test
    public void testDirectSecure() {

        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route1u   = new HttpRoute(TARGET1, null, false);
        final HttpRoute route1s   = new HttpRoute(TARGET1, null, true);
        final HttpRoute route1p1u = new HttpRoute(TARGET1, null, PROXY1, false);
        final HttpRoute route1p1s = new HttpRoute(TARGET1, null, PROXY1, true);

        int step = rowdy.nextStep(route1u, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1u");

        step = rowdy.nextStep(route1s, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_TARGET, step, "wrong step to route1s");

        // unrequested security is currently not tolerated
        step = rowdy.nextStep(route1u, route1s);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1u from 1s not detected");

        // secure layering of direct connections is currently not supported
        step = rowdy.nextStep(route1s, route1u);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1s from 1u not detected");



        step = rowdy.nextStep(route1s, route1p1u);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1s from 1p1u not detected");

        step = rowdy.nextStep(route1s, route1p1s);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route 1s from 1p1s not detected");
    }

    @Test
    public void testProxyTLS() {

        final HttpRouteDirector rowdy = BasicRouteDirector.INSTANCE;
        final HttpRoute route1    = new HttpRoute
            (TARGET1, null, PROXY1, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1t   = new HttpRoute
            (TARGET1, null, PROXY1, false,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        final HttpRoute route1tl  = new HttpRoute
            (TARGET1, null, PROXY1, false,
             TunnelType.TUNNELLED, LayerType.LAYERED);
        final HttpRoute route1s   = new HttpRoute
            (TARGET1, null, PROXY1, true,
             TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1ts  = new HttpRoute
            (TARGET1, null, PROXY1, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        final HttpRoute route1tls = new HttpRoute
            (TARGET1, null, PROXY1, true,
             TunnelType.TUNNELLED, LayerType.LAYERED);

        // we don't consider a route that is layered but not tunnelled

        int step = rowdy.nextStep(route1, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1");

        step = rowdy.nextStep(route1t, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1t");

        step = rowdy.nextStep(route1tl, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1tl");

        step = rowdy.nextStep(route1s, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1s");

        step = rowdy.nextStep(route1ts, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1ts");

        step = rowdy.nextStep(route1tls, null);
        Assertions.assertEquals(HttpRouteDirector.CONNECT_PROXY, step, "wrong step to route1tls");


        step = rowdy.nextStep(route1, route1);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1 not detected");

        step = rowdy.nextStep(route1t, route1t);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1t not detected");

        step = rowdy.nextStep(route1tl, route1tl);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1tl not detected");

        step = rowdy.nextStep(route1s, route1s);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1s not detected");

        step = rowdy.nextStep(route1ts, route1ts);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1ts not detected");

        step = rowdy.nextStep(route1tls, route1tls);
        Assertions.assertEquals(HttpRouteDirector.COMPLETE, step, "complete route1tls not detected");



        step = rowdy.nextStep(route1, route1t);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1 from 1t not detected");

        step = rowdy.nextStep(route1, route1tl);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1 from 1tl not detected");

        // unrequested security is currently not tolerated
        step = rowdy.nextStep(route1, route1s);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1 from 1s not detected");

        step = rowdy.nextStep(route1, route1ts);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1 from 1ts not detected");

        step = rowdy.nextStep(route1, route1tls);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1 from 1tls not detected");


        // securing requires layering
        step = rowdy.nextStep(route1s, route1);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1s from 1 not detected");

        // securing requires layering, and multiple layers are not supported
        step = rowdy.nextStep(route1tls, route1tl);
        Assertions.assertEquals(HttpRouteDirector.UNREACHABLE, step, "unreachable route1tls from 1tl not detected");


        // cases where tunnelling to the target is required
        step = rowdy.nextStep(route1t, route1);
        Assertions.assertEquals(HttpRouteDirector.TUNNEL_TARGET, step, "wrong step to route1t from 1");

        step = rowdy.nextStep(route1tl, route1);
        Assertions.assertEquals(HttpRouteDirector.TUNNEL_TARGET, step, "wrong step to route1tl from 1");

        step = rowdy.nextStep(route1tls, route1);
        Assertions.assertEquals(HttpRouteDirector.TUNNEL_TARGET, step, "wrong step to route1tls from 1");


        // cases where layering on the tunnel is required
        step = rowdy.nextStep(route1tl, route1t);
        Assertions.assertEquals(HttpRouteDirector.LAYER_PROTOCOL, step, "wrong step to route1tl from 1t");

        step = rowdy.nextStep(route1tl, route1ts);
        Assertions.assertEquals(HttpRouteDirector.LAYER_PROTOCOL, step, "wrong step to route1tl from 1ts");

        step = rowdy.nextStep(route1tls, route1t);
        Assertions.assertEquals(HttpRouteDirector.LAYER_PROTOCOL, step, "wrong step to route1tls from 1t");

        step = rowdy.nextStep(route1tls, route1ts);
        Assertions.assertEquals(HttpRouteDirector.LAYER_PROTOCOL, step, "wrong step to route1tls from 1ts");

        // There are some odd cases left over, like having a secure tunnel
        // that becomes unsecure by layering, or a secure connection to a
        // proxy that becomes unsecure by tunnelling to another proxy.
    }

}
