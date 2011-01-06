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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.HttpHost;
import org.apache.http.conn.routing.RouteInfo.TunnelType;
import org.apache.http.conn.routing.RouteInfo.LayerType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link HttpRoute}.
 */
public class TestHttpRoute {

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

    @Test
    public void testCstrFullRoute() {
        // create a route with all arguments and check the details
        HttpHost[] chain3 = { PROXY1, PROXY2, PROXY3 };

        HttpRoute route = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                        TunnelType.PLAIN, LayerType.PLAIN);
        Assert.assertEquals("wrong target",
                     TARGET1, route.getTargetHost());
        Assert.assertEquals("wrong local address",
                     LOCAL41, route.getLocalAddress());
        Assert.assertEquals("wrong proxy host",
                     PROXY1, route.getProxyHost());
        Assert.assertEquals("wrong hop count",
                     4, route.getHopCount());
        Assert.assertEquals("wrong hop 0",
                     PROXY1, route.getHopTarget(0));
        Assert.assertEquals("wrong hop 1",
                     PROXY2, route.getHopTarget(1));
        Assert.assertEquals("wrong hop 2",
                     PROXY3, route.getHopTarget(2));
        Assert.assertEquals("wrong hop 3",
                     TARGET1, route.getHopTarget(3));
        Assert.assertFalse("wrong flag: secured", route.isSecure());
        Assert.assertFalse("wrong flag: tunnelled", route.isTunnelled());
        Assert.assertFalse("wrong flag: layered", route.isLayered());

        String routestr = route.toString();
        Assert.assertTrue("missing target in toString",
                   routestr.indexOf(TARGET1.getHostName()) >= 0);
        Assert.assertTrue("missing local address in toString",
                   routestr.indexOf(LOCAL41.toString()) >= 0);
        Assert.assertTrue("missing proxy 1 in toString",
                   routestr.indexOf(PROXY1.getHostName()) >= 0);
        Assert.assertTrue("missing proxy 2 in toString",
                   routestr.indexOf(PROXY2.getHostName()) >= 0);
        Assert.assertTrue("missing proxy 3 in toString",
                   routestr.indexOf(PROXY3.getHostName()) >= 0);
    }

    @Test
    public void testCstrFullFlags() {
        // tests the flag parameters in the full-blown constructor

        HttpHost[] chain3 = { PROXY1, PROXY2, PROXY3 };

        HttpRoute routefff = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute routefft = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.PLAIN, LayerType.LAYERED);
        HttpRoute routeftf = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute routeftt = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.TUNNELLED, LayerType.LAYERED);
        HttpRoute routetff = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute routetft = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.PLAIN, LayerType.LAYERED);
        HttpRoute routettf = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute routettt = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.TUNNELLED, LayerType.LAYERED);

        Assert.assertFalse("routefff.secure", routefff.isSecure());
        Assert.assertFalse("routefff.tunnel", routefff.isTunnelled());
        Assert.assertFalse("routefff.layer" , routefff.isLayered());

        Assert.assertFalse("routefft.secure", routefft.isSecure());
        Assert.assertFalse("routefft.tunnel", routefft.isTunnelled());
        Assert.assertTrue ("routefft.layer" , routefft.isLayered());

        Assert.assertFalse("routeftf.secure", routeftf.isSecure());
        Assert.assertTrue ("routeftf.tunnel", routeftf.isTunnelled());
        Assert.assertFalse("routeftf.layer" , routeftf.isLayered());

        Assert.assertFalse("routeftt.secure", routeftt.isSecure());
        Assert.assertTrue ("routeftt.tunnel", routeftt.isTunnelled());
        Assert.assertTrue ("routeftt.layer" , routeftt.isLayered());

        Assert.assertTrue ("routetff.secure", routetff.isSecure());
        Assert.assertFalse("routetff.tunnel", routetff.isTunnelled());
        Assert.assertFalse("routetff.layer" , routetff.isLayered());

        Assert.assertTrue ("routetft.secure", routetft.isSecure());
        Assert.assertFalse("routetft.tunnel", routetft.isTunnelled());
        Assert.assertTrue ("routetft.layer" , routetft.isLayered());

        Assert.assertTrue ("routettf.secure", routettf.isSecure());
        Assert.assertTrue ("routettf.tunnel", routettf.isTunnelled());
        Assert.assertFalse("routettf.layer" , routettf.isLayered());

        Assert.assertTrue ("routettt.secure", routettt.isSecure());
        Assert.assertTrue ("routettt.tunnel", routettt.isTunnelled());
        Assert.assertTrue ("routettt.layer" , routettt.isLayered());


        Set<HttpRoute> routes = new HashSet<HttpRoute>();
        routes.add(routefff);
        routes.add(routefft);
        routes.add(routeftf);
        routes.add(routeftt);
        routes.add(routetff);
        routes.add(routetft);
        routes.add(routettf);
        routes.add(routettt);
        Assert.assertEquals("some flagged routes are equal", 8, routes.size());

        // we can't test hashCode in general due to its dependency
        // on InetAddress and HttpHost, but we can check for the flags
        Set<Integer> routecodes = new HashSet<Integer>();
        routecodes.add(Integer.valueOf(routefff.hashCode()));
        routecodes.add(Integer.valueOf(routefft.hashCode()));
        routecodes.add(Integer.valueOf(routeftf.hashCode()));
        routecodes.add(Integer.valueOf(routeftt.hashCode()));
        routecodes.add(Integer.valueOf(routetff.hashCode()));
        routecodes.add(Integer.valueOf(routetft.hashCode()));
        routecodes.add(Integer.valueOf(routettf.hashCode()));
        routecodes.add(Integer.valueOf(routettt.hashCode()));
        Assert.assertEquals("some flagged routes have same hashCode",
                     8, routecodes.size());

        Set<String> routestrings = new HashSet<String>();
        routestrings.add(routefff.toString());
        routestrings.add(routefft.toString());
        routestrings.add(routeftf.toString());
        routestrings.add(routeftt.toString());
        routestrings.add(routetff.toString());
        routestrings.add(routetft.toString());
        routestrings.add(routettf.toString());
        routestrings.add(routettt.toString());
        Assert.assertEquals("some flagged route.toString() are equal",
                     8, routestrings.size());
    }

    @Test
    public void testInvalidArguments() {
        HttpHost[] chain0 = { null };
        HttpHost[] chain1 = { PROXY1 };
        HttpHost[] chain4 = { PROXY1, PROXY2, null, PROXY3 };

        // for reference: this one should succeed
        HttpRoute route = new HttpRoute(TARGET1, null, chain1, false,
                                        TunnelType.TUNNELLED, LayerType.PLAIN);
        Assert.assertNotNull(route);

        try {
            route = new HttpRoute(null, null, chain1, false,
                                  TunnelType.TUNNELLED, LayerType.PLAIN);
            Assert.fail("missing target not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            route = new HttpRoute(TARGET1, null, (HttpHost[]) null, false,
                                  TunnelType.TUNNELLED, LayerType.PLAIN);
            Assert.fail("missing proxy for tunnel not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        // for the next two, we don't indicate a tunnel anymore
        try {
            new HttpRoute(TARGET1, null, chain0, false,
                                  TunnelType.PLAIN, LayerType.PLAIN);
            Assert.fail("invalid proxy chain (0) not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            new HttpRoute(TARGET1, null, chain4, false,
                                  TunnelType.PLAIN, LayerType.PLAIN);
            Assert.fail("invalid proxy chain (4) not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }

    @Test
    public void testNullEnums() {

        // tests the default values for the enum parameters
        // also covers the accessors for the enum attributes

        HttpRoute route = new HttpRoute(TARGET1, null, PROXY1, false,
                                        null, null); // here are defaults

        Assert.assertFalse("default tunnelling", route.isTunnelled());
        Assert.assertEquals("untunnelled", TunnelType.PLAIN, route.getTunnelType());

        Assert.assertFalse("default layering", route.isLayered());
        Assert.assertEquals("unlayered", LayerType.PLAIN, route.getLayerType());
    }

    @Test
    public void testEqualsHashcodeClone() throws CloneNotSupportedException {
        HttpHost[] chain0 = { };
        HttpHost[] chain1 = { PROXY1 };
        HttpHost[] chain3 = { PROXY1, PROXY2, PROXY3 };
        HttpHost[] chain4 = { PROXY1, PROXY3, PROXY2 };

        // create some identical routes
        HttpRoute route1a = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1b = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route1c = (HttpRoute) route1a.clone();

        Assert.assertEquals("1a 1a", route1a, route1a);
        Assert.assertEquals("1a 1b", route1a, route1b);
        Assert.assertEquals("1a 1c", route1a, route1c);

        Assert.assertEquals("hashcode 1a", route1a.hashCode(), route1a.hashCode());
        Assert.assertEquals("hashcode 1b", route1a.hashCode(), route1b.hashCode());
        Assert.assertEquals("hashcode 1c", route1a.hashCode(), route1c.hashCode());

        Assert.assertEquals("toString 1a", route1a.toString(), route1a.toString());
        Assert.assertEquals("toString 1b", route1a.toString(), route1b.toString());
        Assert.assertEquals("toString 1c", route1a.toString(), route1c.toString());

        // now create some differing routes
        HttpRoute route2a = new HttpRoute(TARGET2, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2b = new HttpRoute(TARGET1, LOCAL42, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2c = new HttpRoute(TARGET1, LOCAL61, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2d = new HttpRoute(TARGET1, null, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2e = new HttpRoute(TARGET1, LOCAL41, (HttpHost[]) null,
                                          false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2f = new HttpRoute(TARGET1, LOCAL41, chain0, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2g = new HttpRoute(TARGET1, LOCAL41, chain1, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2h = new HttpRoute(TARGET1, LOCAL41, chain4, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2i = new HttpRoute(TARGET1, LOCAL41, chain3, true,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2j = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                        TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute route2k = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.LAYERED);

        // check a special case first: 2f should be the same as 2e
        Assert.assertEquals("2e 2f", route2e, route2f);
        Assert.assertEquals("hashcode 2e 2f", route2e.hashCode(), route2f.hashCode());
        Assert.assertEquals("toString 2e 2f", route2e.toString(), route2f.toString());

        Assert.assertFalse("1a 2a", route1a.equals(route2a));
        Assert.assertFalse("1a 2b", route1a.equals(route2b));
        Assert.assertFalse("1a 2c", route1a.equals(route2c));
        Assert.assertFalse("1a 2d", route1a.equals(route2d));
        Assert.assertFalse("1a 2e", route1a.equals(route2e));
        Assert.assertFalse("1a 2f", route1a.equals(route2f));
        Assert.assertFalse("1a 2g", route1a.equals(route2g));
        Assert.assertFalse("1a 2h", route1a.equals(route2h));
        Assert.assertFalse("1a 2i", route1a.equals(route2i));
        Assert.assertFalse("1a 2j", route1a.equals(route2j));
        Assert.assertFalse("1a 2k", route1a.equals(route2k));

        // repeat the checks in the other direction
        // there could be problems with detecting null attributes

        Assert.assertFalse("2a 1a", route2a.equals(route1a));
        Assert.assertFalse("2b 1a", route2b.equals(route1a));
        Assert.assertFalse("2c 1a", route2c.equals(route1a));
        Assert.assertFalse("2d 1a", route2d.equals(route1a));
        Assert.assertFalse("2e 1a", route2e.equals(route1a));
        Assert.assertFalse("2f 1a", route2f.equals(route1a));
        Assert.assertFalse("2g 1a", route2g.equals(route1a));
        Assert.assertFalse("2h 1a", route2h.equals(route1a));
        Assert.assertFalse("2i 1a", route2i.equals(route1a));
        Assert.assertFalse("2j 1a", route2j.equals(route1a));
        Assert.assertFalse("2k 1a", route2k.equals(route1a));

        // don't check hashCode, it's not guaranteed to be different

        Assert.assertFalse("toString 1a 2a",
                    route1a.toString().equals(route2a.toString()));
        Assert.assertFalse("toString 1a 2b",
                    route1a.toString().equals(route2b.toString()));
        Assert.assertFalse("toString 1a 2c",
                    route1a.toString().equals(route2c.toString()));
        Assert.assertFalse("toString 1a 2d",
                    route1a.toString().equals(route2d.toString()));
        Assert.assertFalse("toString 1a 2e",
                    route1a.toString().equals(route2e.toString()));
        Assert.assertFalse("toString 1a 2f",
                    route1a.toString().equals(route2f.toString()));
        Assert.assertFalse("toString 1a 2g",
                    route1a.toString().equals(route2g.toString()));
        Assert.assertFalse("toString 1a 2h",
                    route1a.toString().equals(route2h.toString()));
        Assert.assertFalse("toString 1a 2i",
                    route1a.toString().equals(route2i.toString()));
        Assert.assertFalse("toString 1a 2j",
                    route1a.toString().equals(route2j.toString()));
        Assert.assertFalse("toString 1a 2k",
                    route1a.toString().equals(route2k.toString()));

        // now check that all of the routes are different from eachother
        // except for those that aren't :-)
        Set<HttpRoute> routes = new HashSet<HttpRoute>();
        routes.add(route1a);
        routes.add(route2a);
        routes.add(route2b);
        routes.add(route2c);
        routes.add(route2d);
        routes.add(route2e);
        //routes.add(route2f); // 2f is the same as 2e
        routes.add(route2g);
        routes.add(route2h);
        routes.add(route2i);
        routes.add(route2j);
        routes.add(route2k);
        Assert.assertEquals("some routes are equal", 11, routes.size());

        // and a run of cloning over the set
        Iterator<HttpRoute> iter = routes.iterator();
        while (iter.hasNext()) {
            HttpRoute origin = iter.next();
            HttpRoute cloned = (HttpRoute) origin.clone();
            Assert.assertEquals("clone of " + origin, origin, cloned);
            Assert.assertTrue("clone of " + origin, routes.contains(cloned));
        }

        // and don't forget toString
        Set<String> routestrings = new HashSet<String>();
        routestrings.add(route1a.toString());
        routestrings.add(route2a.toString());
        routestrings.add(route2b.toString());
        routestrings.add(route2c.toString());
        routestrings.add(route2d.toString());
        routestrings.add(route2e.toString());
        //routestrings.add(route2f.toString()); // 2f is the same as 2e
        routestrings.add(route2g.toString());
        routestrings.add(route2h.toString());
        routestrings.add(route2i.toString());
        routestrings.add(route2j.toString());
        routestrings.add(route2k.toString());
        Assert.assertEquals("some route.toString() are equal",
                     11, routestrings.size());

        // finally, compare with nonsense
        Assert.assertFalse("route equals null", route1a.equals(null));
        Assert.assertFalse("route equals string", route1a.equals("route1a"));
    }

    @Test
    public void testHopping() {
        // test getHopCount() and getHopTarget() with different proxy chains
        HttpHost[] proxies = null;
        HttpRoute  route   = new HttpRoute(TARGET1, null, proxies, true,
                                           TunnelType.PLAIN, LayerType.PLAIN);
        Assert.assertEquals("A: hop count", 1, route.getHopCount());
        Assert.assertEquals("A: hop 0", TARGET1, route.getHopTarget(0));
        try {
            HttpHost beyond = route.getHopTarget(1);
            Assert.fail("A: hop 1 is " + beyond);
        } catch (IllegalArgumentException iax) {
            // expected
        }
        try {
            HttpHost before = route.getHopTarget(-1);
            Assert.fail("A: hop -1 is " + before);
        } catch (IllegalArgumentException iax) {
            // expected
        }


        proxies = new HttpHost[]{ PROXY3 };
        route   = new HttpRoute(TARGET1, LOCAL62, proxies, false,
                                TunnelType.TUNNELLED, LayerType.PLAIN);
        Assert.assertEquals("B: hop count", 2, route.getHopCount());
        Assert.assertEquals("B: hop 0", PROXY3, route.getHopTarget(0));
        Assert.assertEquals("B: hop 1", TARGET1, route.getHopTarget(1));
        try {
            HttpHost beyond = route.getHopTarget(2);
            Assert.fail("B: hop 2 is " + beyond);
        } catch (IllegalArgumentException iax) {
            // expected
        }
        try {
            HttpHost before = route.getHopTarget(-2);
            Assert.fail("B: hop -2 is " + before);
        } catch (IllegalArgumentException iax) {
            // expected
        }


        proxies = new HttpHost[]{ PROXY3, PROXY1, PROXY2 };
        route   = new HttpRoute(TARGET1, LOCAL42, proxies, false,
                                TunnelType.PLAIN, LayerType.LAYERED);
        Assert.assertEquals("C: hop count", 4, route.getHopCount());
        Assert.assertEquals("C: hop 0", PROXY3 , route.getHopTarget(0));
        Assert.assertEquals("C: hop 1", PROXY1 , route.getHopTarget(1));
        Assert.assertEquals("C: hop 2", PROXY2 , route.getHopTarget(2));
        Assert.assertEquals("C: hop 3", TARGET1, route.getHopTarget(3));
        try {
            HttpHost beyond = route.getHopTarget(4);
            Assert.fail("C: hop 4 is " + beyond);
        } catch (IllegalArgumentException iax) {
            // expected
        }
        try {
            HttpHost before = route.getHopTarget(Integer.MIN_VALUE);
            Assert.fail("C: hop -<min> is " + before);
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }

    @Test
    public void testCstr1() {
        HttpRoute route = new HttpRoute(TARGET2);
        HttpRoute should = new HttpRoute
            (TARGET2, null, (HttpHost[]) null, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        Assert.assertEquals("bad convenience route", route, should);
    }

    @Test
    public void testCstr3() {
        // test convenience constructor with 3 arguments
        HttpRoute route = new HttpRoute(TARGET2, LOCAL61, false);
        HttpRoute should = new HttpRoute
            (TARGET2, LOCAL61, (HttpHost[]) null, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        Assert.assertEquals("bad convenience route 3/insecure", route, should);

        route = new HttpRoute(TARGET2, null, true);
        should = new HttpRoute(TARGET2, null, (HttpHost[]) null, true,
                               TunnelType.PLAIN, LayerType.PLAIN);
        Assert.assertEquals("bad convenience route 3/secure", route, should);
    }

    @Test
    public void testCstr4() {
        // test convenience constructor with 4 arguments
        HttpRoute route = new HttpRoute(TARGET2, null, PROXY2, false);
        HttpRoute should = new HttpRoute
            (TARGET2, null, new HttpHost[]{ PROXY2 }, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        Assert.assertEquals("bad convenience route 4/insecure", route, should);

        route = new HttpRoute(TARGET2, LOCAL42, PROXY1, true);
        should = new HttpRoute
            (TARGET2, LOCAL42, new HttpHost[]{ PROXY1 }, true,
             TunnelType.TUNNELLED, LayerType.LAYERED);
        Assert.assertEquals("bad convenience route 4/secure", route, should);

        // this constructor REQUIRES a proxy to be specified
        try {
            new HttpRoute(TARGET1, LOCAL61, null, false);
            Assert.fail("missing proxy not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }

    @Test
    public void testCstr6() {
        // test convenience constructor with 6 arguments
        HttpRoute route = new HttpRoute
            (TARGET2, null, PROXY2, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute should = new HttpRoute
            (TARGET2, null, new HttpHost[]{ PROXY2 }, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        Assert.assertEquals("bad convenience route 6/proxied", route, should);

        route = new HttpRoute
            (TARGET2, null, (HttpHost) null, true,
             TunnelType.PLAIN, LayerType.LAYERED);
        should = new HttpRoute
            (TARGET2, null, (HttpHost[]) null, true,
             TunnelType.PLAIN, LayerType.LAYERED);
        Assert.assertEquals("bad convenience route 6/direct", route, should);

        // handling of null vs. empty chain is checked in the equals tests
    }

    @Test
    public void testImmutable() throws CloneNotSupportedException {

        HttpHost[] proxies = new HttpHost[]{ PROXY1, PROXY2, PROXY3 };
        HttpRoute route1 = new HttpRoute(TARGET1, null, proxies, false,
                                         TunnelType.PLAIN, LayerType.PLAIN);
        HttpRoute route2 = (HttpRoute) route1.clone();
        HttpRoute route3 = new HttpRoute(TARGET1, null,
                                         proxies.clone(), false,
                                         TunnelType.PLAIN, LayerType.PLAIN);

        // modify the array that was passed to the constructor of route1
        proxies[1] = PROXY3;
        proxies[2] = PROXY2;

        Assert.assertEquals("route differs from clone", route2, route1);
        Assert.assertEquals("route was modified", route3, route1);
    }

}
