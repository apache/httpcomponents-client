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

package org.apache.http.conn.routing;


import java.net.InetAddress;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.conn.routing.RouteInfo.TunnelType;
import org.apache.http.conn.routing.RouteInfo.LayerType;


/**
 * Tests for <code>BasicRouteDirector</code>.
 */
public class TestRouteDirector extends TestCase {

    // a selection of constants for generating routes
    public final static
        HttpHost TARGET1 = new HttpHost("target1.test.invalid");
    public final static
        HttpHost TARGET2 = new HttpHost("target2.test.invalid", 8080);
    // It is not necessary to have extra targets for https.
    // The 'layered' and 'secure' flags are specified explicitly
    // for routes, they will not be determined from the scheme.

    public final static
        HttpHost PROXY1 = new HttpHost("proxy1.test.invalid");
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

        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }


    public TestRouteDirector(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestRouteDirector.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestRouteDirector.class);
    }


    public void testIllegal() {

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route = new HttpRoute(TARGET1);

        try {
            rowdy.nextStep(null, route);
            fail("null argument not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }


    public void testDirect() {

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route1   = new HttpRoute(TARGET1);
        HttpRoute route2   = new HttpRoute(TARGET2);
        HttpRoute route1p1 = new HttpRoute(TARGET1, null, PROXY1, false);

        int step = rowdy.nextStep(route1, null);
        assertEquals("wrong step to route1",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route2, null);
        assertEquals("wrong step to route2",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route1, route1);
        assertEquals("complete route1 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route2, route2);
        assertEquals("complete route2 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1, route2);
        assertEquals("unreachable target not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1, route1p1);
        assertEquals("invalid proxy not detected",
                     HttpRouteDirector.UNREACHABLE, step);
    }


    public void testProxy() {

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route1p1 = new HttpRoute(TARGET1, null, PROXY1, false);
        HttpRoute route1p2 = new HttpRoute(TARGET1, null, PROXY2, false);
        HttpRoute route2p1 = new HttpRoute(TARGET2, null, PROXY1, false);
        HttpRoute route0   = new HttpRoute(PROXY1);
        HttpRoute route1   = new HttpRoute(TARGET1);

        int step = rowdy.nextStep(route1p1, null);
        assertEquals("wrong step to route1p1",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1p2, null);
        assertEquals("wrong step to route1p2",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1p1, route1p1);
        assertEquals("complete route1p1 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1p2, route1p2);
        assertEquals("complete route1p2 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route2p1, route2p1);
        assertEquals("complete route2p1 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1p1, route1p2);
        assertEquals("unreachable route1p1 via route1p2 not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1p1, route2p1);
        assertEquals("unreachable route1p1 via route2p1 not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1p1, route0);
        assertEquals("unreachable route1p1 via route0 not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1p1, route1);
        assertEquals("unreachable route1p1 via route1 not detected",
                     HttpRouteDirector.UNREACHABLE, step);
    }


    public void testProxyChain() {
        HttpHost[] chainA = { PROXY1 };
        HttpHost[] chainB = { PROXY1, PROXY2 };
        HttpHost[] chainC = { PROXY2, PROXY1 };

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route1cA  = new HttpRoute(TARGET1, null, chainA, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1cB  = new HttpRoute(TARGET1, null, chainB, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1cC  = new HttpRoute(TARGET1, null, chainC, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1cD  = new HttpRoute(TARGET1, null, chainC, false,
                                            TunnelType.PLAIN, LayerType.PLAIN);

        int step = rowdy.nextStep(route1cA, null);
        assertEquals("wrong step to route1cA",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1cB, null);
        assertEquals("wrong step to route1cB",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1cC, null);
        assertEquals("wrong step to route1cC",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1cD, null);
        assertEquals("wrong step to route1cD",
                     HttpRouteDirector.CONNECT_PROXY, step);


        step = rowdy.nextStep(route1cB, route1cA);
        assertEquals("wrong step to route 1cB from 1cA",
                     HttpRouteDirector.TUNNEL_PROXY, step);

        step = rowdy.nextStep(route1cB, route1cB);
        assertEquals("complete route 1cB not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1cB, route1cC);
        assertEquals("unreachable route 1cB from 1cC not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1cB, route1cD);
        assertEquals("unreachable route 1cB from 1cD not detected",
                     HttpRouteDirector.UNREACHABLE, step);


        step = rowdy.nextStep(route1cA, route1cB);
        assertEquals("unreachable route 1cA from 1cB not detected",
                     HttpRouteDirector.UNREACHABLE, step);
    }


    public void testLocalDirect() {

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route1l41 = new HttpRoute(TARGET1, LOCAL41, false);
        HttpRoute route1l42 = new HttpRoute(TARGET1, LOCAL42, false);
        HttpRoute route1l61 = new HttpRoute(TARGET1, LOCAL61, false);
        HttpRoute route1l00 = new HttpRoute(TARGET1, null, false);

        int step = rowdy.nextStep(route1l41, null);
        assertEquals("wrong step to route1l41",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route1l42, null);
        assertEquals("wrong step to route1l42",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route1l61, null);
        assertEquals("wrong step to route1l61",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route1l00, null);
        assertEquals("wrong step to route1l00",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route1l41, route1l41);
        assertEquals("complete route1l41 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1l42, route1l42);
        assertEquals("complete route1l42 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1l61, route1l61);
        assertEquals("complete route1l61 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1l00, route1l00);
        assertEquals("complete route1l00 not detected",
                     HttpRouteDirector.COMPLETE, step);


        step = rowdy.nextStep(route1l41, route1l42);
        assertEquals("unreachable route1l41 via route1l42 not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1l41, route1l61);
        assertEquals("unreachable route1l41 via route1l61 not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1l41, route1l00);
        assertEquals("unreachable route1l41 via route1l00 not detected",
                     HttpRouteDirector.UNREACHABLE, step);


        step = rowdy.nextStep(route1l00, route1l41);
        assertEquals("complete route1l00 as route1l41 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1l00, route1l42);
        assertEquals("complete route1l00 as route1l42 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1l00, route1l61);
        assertEquals("complete route1l00 as route1l61 not detected",
                     HttpRouteDirector.COMPLETE, step);
    }


    public void testDirectSecure() {

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route1u   = new HttpRoute(TARGET1, null, false);
        HttpRoute route1s   = new HttpRoute(TARGET1, null, true);
        HttpRoute route1p1u = new HttpRoute(TARGET1, null, PROXY1, false);
        HttpRoute route1p1s = new HttpRoute(TARGET1, null, PROXY1, true);

        int step = rowdy.nextStep(route1u, null);
        assertEquals("wrong step to route1u",
                     HttpRouteDirector.CONNECT_TARGET, step);

        step = rowdy.nextStep(route1s, null);
        assertEquals("wrong step to route1s",
                     HttpRouteDirector.CONNECT_TARGET, step);

        // unrequested security is currently not tolerated
        step = rowdy.nextStep(route1u, route1s);
        assertEquals("unreachable route 1u from 1s not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        // secure layering of direct connections is currently not supported
        step = rowdy.nextStep(route1s, route1u);
        assertEquals("unreachable route 1s from 1u not detected",
                     HttpRouteDirector.UNREACHABLE, step);



        step = rowdy.nextStep(route1s, route1p1u);
        assertEquals("unreachable route 1s from 1p1u not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1s, route1p1s);
        assertEquals("unreachable route 1s from 1p1s not detected",
                     HttpRouteDirector.UNREACHABLE, step);
    }


    public void testProxyTLS() {

        HttpRouteDirector rowdy = new BasicRouteDirector();
        HttpRoute route1    = new HttpRoute
            (TARGET1, null, PROXY1, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1t   = new HttpRoute
            (TARGET1, null, PROXY1, false,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute route1tl  = new HttpRoute
            (TARGET1, null, PROXY1, false,
             TunnelType.TUNNELLED, LayerType.LAYERED);
        HttpRoute route1s   = new HttpRoute
            (TARGET1, null, PROXY1, true,
             TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1ts  = new HttpRoute
            (TARGET1, null, PROXY1, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute route1tls = new HttpRoute
            (TARGET1, null, PROXY1, true,
             TunnelType.TUNNELLED, LayerType.LAYERED);

        // we don't consider a route that is layered but not tunnelled

        int step = rowdy.nextStep(route1, null);
        assertEquals("wrong step to route1",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1t, null);
        assertEquals("wrong step to route1t",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1tl, null);
        assertEquals("wrong step to route1tl",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1s, null);
        assertEquals("wrong step to route1s",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1ts, null);
        assertEquals("wrong step to route1ts",
                     HttpRouteDirector.CONNECT_PROXY, step);

        step = rowdy.nextStep(route1tls, null);
        assertEquals("wrong step to route1tls",
                     HttpRouteDirector.CONNECT_PROXY, step);


        step = rowdy.nextStep(route1, route1);
        assertEquals("complete route1 not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1t, route1t);
        assertEquals("complete route1t not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1tl, route1tl);
        assertEquals("complete route1tl not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1s, route1s);
        assertEquals("complete route1s not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1ts, route1ts);
        assertEquals("complete route1ts not detected",
                     HttpRouteDirector.COMPLETE, step);

        step = rowdy.nextStep(route1tls, route1tls);
        assertEquals("complete route1tls not detected",
                     HttpRouteDirector.COMPLETE, step);



        step = rowdy.nextStep(route1, route1t);
        assertEquals("unreachable route1 from 1t not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1, route1tl);
        assertEquals("unreachable route1 from 1tl not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        // unrequested security is currently not tolerated
        step = rowdy.nextStep(route1, route1s);
        assertEquals("unreachable route1 from 1s not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1, route1ts);
        assertEquals("unreachable route1 from 1ts not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        step = rowdy.nextStep(route1, route1tls);
        assertEquals("unreachable route1 from 1tls not detected",
                     HttpRouteDirector.UNREACHABLE, step);


        // securing requires layering
        step = rowdy.nextStep(route1s, route1);
        assertEquals("unreachable route1s from 1 not detected",
                     HttpRouteDirector.UNREACHABLE, step);

        // securing requires layering, and multiple layers are not supported
        step = rowdy.nextStep(route1tls, route1tl);
        assertEquals("unreachable route1tls from 1tl not detected",
                     HttpRouteDirector.UNREACHABLE, step);


        // cases where tunnelling to the target is required
        step = rowdy.nextStep(route1t, route1);
        assertEquals("wrong step to route1t from 1",
                     HttpRouteDirector.TUNNEL_TARGET, step);

        step = rowdy.nextStep(route1tl, route1);
        assertEquals("wrong step to route1tl from 1",
                     HttpRouteDirector.TUNNEL_TARGET, step);

        step = rowdy.nextStep(route1tls, route1);
        assertEquals("wrong step to route1tls from 1",
                     HttpRouteDirector.TUNNEL_TARGET, step);


        // cases where layering on the tunnel is required
        step = rowdy.nextStep(route1tl, route1t);
        assertEquals("wrong step to route1tl from 1t",
                     HttpRouteDirector.LAYER_PROTOCOL, step);

        step = rowdy.nextStep(route1tl, route1ts);
        assertEquals("wrong step to route1tl from 1ts",
                     HttpRouteDirector.LAYER_PROTOCOL, step);

        step = rowdy.nextStep(route1tls, route1t);
        assertEquals("wrong step to route1tls from 1t",
                     HttpRouteDirector.LAYER_PROTOCOL, step);

        step = rowdy.nextStep(route1tls, route1ts);
        assertEquals("wrong step to route1tls from 1ts",
                     HttpRouteDirector.LAYER_PROTOCOL, step);

        // There are some odd cases left over, like having a secure tunnel
        // that becomes unsecure by layering, or a secure connection to a
        // proxy that becomes unsecure by tunnelling to another proxy.
    }


} // class TestRouteDirector
