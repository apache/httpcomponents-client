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

package org.apache.hc.client5.http;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.RouteInfo.LayerType;
import org.apache.hc.client5.http.RouteInfo.TunnelType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpRoute}.
 */
class TestHttpRoute {

    // a selection of constants for generating routes
    public final static
        HttpHost TARGET1 = new HttpHost("target1.test.invalid", 80);
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

        } catch (final Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    @Test
    void testCstrFullRoute() {
        // create a route with all arguments and check the details
        final HttpHost[] chain3 = { PROXY1, PROXY2, PROXY3 };

        final HttpRoute route = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                        TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertEquals(TARGET1, route.getTargetHost(), "wrong target");
        Assertions.assertEquals(LOCAL41, route.getLocalAddress(), "wrong local address");
        Assertions.assertEquals(PROXY1, route.getProxyHost(), "wrong proxy host");
        Assertions.assertEquals(4, route.getHopCount(), "wrong hop count");
        Assertions.assertEquals(PROXY1, route.getHopTarget(0), "wrong hop 0");
        Assertions.assertEquals(PROXY2, route.getHopTarget(1), "wrong hop 1");
        Assertions.assertEquals(PROXY3, route.getHopTarget(2), "wrong hop 2");
        Assertions.assertEquals(TARGET1, route.getHopTarget(3), "wrong hop 3");
        Assertions.assertFalse(route.isSecure(), "wrong flag: secured");
        Assertions.assertFalse(route.isTunnelled(), "wrong flag: tunnelled");
        Assertions.assertFalse(route.isLayered(), "wrong flag: layered");

        final String routestr = route.toString();
        Assertions.assertTrue(routestr.contains(TARGET1.getHostName()), "missing target in toString");
        Assertions.assertTrue(routestr.contains(LOCAL41.toString()), "missing local address in toString");
        Assertions.assertTrue(routestr.contains(PROXY1.getHostName()), "missing proxy 1 in toString");
        Assertions.assertTrue(routestr.contains(PROXY2.getHostName()), "missing proxy 2 in toString");
        Assertions.assertTrue(routestr.contains(PROXY3.getHostName()), "missing proxy 3 in toString");
    }

    @Test
    void testCstrFullFlags() {
        // tests the flag parameters in the full-blown constructor

        final HttpHost[] chain3 = { PROXY1, PROXY2, PROXY3 };

        final HttpRoute routefff = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute routefft = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.PLAIN, LayerType.LAYERED);
        final HttpRoute routeftf = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        final HttpRoute routeftt = new HttpRoute
            (TARGET1, LOCAL41, chain3, false,
             TunnelType.TUNNELLED, LayerType.LAYERED);
        final HttpRoute routetff = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute routetft = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.PLAIN, LayerType.LAYERED);
        final HttpRoute routettf = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        final HttpRoute routettt = new HttpRoute
            (TARGET1, LOCAL41, chain3, true,
             TunnelType.TUNNELLED, LayerType.LAYERED);

        Assertions.assertFalse(routefff.isSecure(), "routefff.secure");
        Assertions.assertFalse(routefff.isTunnelled(), "routefff.tunnel");
        Assertions.assertFalse(routefff.isLayered(), "routefff.layer");

        Assertions.assertFalse(routefft.isSecure(), "routefft.secure");
        Assertions.assertFalse(routefft.isTunnelled(), "routefft.tunnel");
        Assertions.assertTrue (routefft.isLayered(), "routefft.layer");

        Assertions.assertFalse(routeftf.isSecure(), "routeftf.secure");
        Assertions.assertTrue (routeftf.isTunnelled(), "routeftf.tunnel");
        Assertions.assertFalse(routeftf.isLayered(), "routeftf.layer");

        Assertions.assertFalse(routeftt.isSecure(), "routeftt.secure");
        Assertions.assertTrue (routeftt.isTunnelled(), "routeftt.tunnel");
        Assertions.assertTrue (routeftt.isLayered(), "routeftt.layer");

        Assertions.assertTrue (routetff.isSecure(), "routetff.secure");
        Assertions.assertFalse(routetff.isTunnelled(), "routetff.tunnel");
        Assertions.assertFalse(routetff.isLayered(), "routetff.layer");

        Assertions.assertTrue (routetft.isSecure(), "routetft.secure");
        Assertions.assertFalse(routetft.isTunnelled(), "routetft.tunnel");
        Assertions.assertTrue (routetft.isLayered(), "routetft.layer");

        Assertions.assertTrue (routettf.isSecure(), "routettf.secure");
        Assertions.assertTrue (routettf.isTunnelled(), "routettf.tunnel");
        Assertions.assertFalse(routettf.isLayered(), "routettf.layer");

        Assertions.assertTrue (routettt.isSecure(), "routettt.secure");
        Assertions.assertTrue (routettt.isTunnelled(), "routettt.tunnel");
        Assertions.assertTrue (routettt.isLayered(), "routettt.layer");
    }

    @Test
    void testInvalidArguments() {
        final HttpHost[] chain1 = { PROXY1 };

        // for reference: this one should succeed
        final HttpRoute route = new HttpRoute(TARGET1, null, chain1, false,
                                        TunnelType.TUNNELLED, LayerType.PLAIN);
        Assertions.assertNotNull(route);

        Assertions.assertThrows(NullPointerException.class, () ->
                new HttpRoute(null, null, chain1, false,TunnelType.TUNNELLED, LayerType.PLAIN));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new HttpRoute(TARGET1, null, (HttpHost[]) null, false, TunnelType.TUNNELLED, LayerType.PLAIN));
    }

    @Test
    void testNullEnums() {

        // tests the default values for the enum parameters
        // also covers the accessors for the enum attributes

        final HttpRoute route = new HttpRoute(TARGET1, null, PROXY1, false,
                                        null, null); // here are defaults

        Assertions.assertFalse(route.isTunnelled(), "default tunnelling");
        Assertions.assertEquals(TunnelType.PLAIN, route.getTunnelType(), "untunnelled");

        Assertions.assertFalse(route.isLayered(), "default layering");
        Assertions.assertEquals(LayerType.PLAIN, route.getLayerType(), "unlayered");
    }

    @Test
    void testEqualsHashcodeClone() throws CloneNotSupportedException {
        final HttpHost[] chain0 = { };
        final HttpHost[] chain1 = { PROXY1 };
        final HttpHost[] chain3 = { PROXY1, PROXY2, PROXY3 };
        final HttpHost[] chain4 = { PROXY1, PROXY3, PROXY2 };

        // create some identical routes
        final HttpRoute route1a = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1b = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route1c = (HttpRoute) route1a.clone();

        Assertions.assertEquals(route1a, route1a, "1a 1a");
        Assertions.assertEquals(route1a, route1b, "1a 1b");
        Assertions.assertEquals(route1a, route1c, "1a 1c");

        Assertions.assertEquals(route1a.hashCode(), route1a.hashCode(), "hashcode 1a");
        Assertions.assertEquals(route1a.hashCode(), route1b.hashCode(), "hashcode 1b");
        Assertions.assertEquals(route1a.hashCode(), route1c.hashCode(), "hashcode 1c");

        Assertions.assertEquals(route1a.toString(), route1b.toString(), "toString 1b");
        Assertions.assertEquals(route1a.toString(), route1a.toString(), "toString 1a");
        Assertions.assertEquals(route1a.toString(), route1c.toString(), "toString 1c");

        // now create some differing routes
        final HttpRoute route2a = new HttpRoute(TARGET2, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2b = new HttpRoute(TARGET1, LOCAL42, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2c = new HttpRoute(TARGET1, LOCAL61, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2d = new HttpRoute(TARGET1, null, chain3, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2e = new HttpRoute(TARGET1, LOCAL41, (HttpHost[]) null,
                                          false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2f = new HttpRoute(TARGET1, LOCAL41, chain0, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2g = new HttpRoute(TARGET1, LOCAL41, chain1, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2h = new HttpRoute(TARGET1, LOCAL41, chain4, false,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2i = new HttpRoute(TARGET1, LOCAL41, chain3, true,
                                          TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2j = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                        TunnelType.TUNNELLED, LayerType.PLAIN);
        final HttpRoute route2k = new HttpRoute(TARGET1, LOCAL41, chain3, false,
                                          TunnelType.PLAIN, LayerType.LAYERED);
        final HttpRoute route2m = new HttpRoute(TARGET2,
                new URIAuthority(TARGET2.getHostName(), TARGET2.getPort()), LOCAL41, chain3, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        // check a special case first: 2f should be the same as 2e
        Assertions.assertEquals(route2e, route2f, "2e 2f");
        Assertions.assertEquals(route2e.hashCode(), route2f.hashCode(), "hashcode 2e 2f");
        Assertions.assertEquals(route2e.toString(), route2f.toString(), "toString 2e 2f");

        Assertions.assertNotEquals(route1a, route2a, "1a 2a");
        Assertions.assertNotEquals(route1a, route2b, "1a 2b");
        Assertions.assertNotEquals(route1a, route2c, "1a 2c");
        Assertions.assertNotEquals(route1a, route2d, "1a 2d");
        Assertions.assertNotEquals(route1a, route2e, "1a 2e");
        Assertions.assertNotEquals(route1a, route2f, "1a 2f");
        Assertions.assertNotEquals(route1a, route2g, "1a 2g");
        Assertions.assertNotEquals(route1a, route2h, "1a 2h");
        Assertions.assertNotEquals(route1a, route2i, "1a 2i");
        Assertions.assertNotEquals(route1a, route2j, "1a 2j");
        Assertions.assertNotEquals(route1a, route2k, "1a 2k");
        Assertions.assertNotEquals(route1a, route2m, "1a 2k");

        // repeat the checks in the other direction
        // there could be problems with detecting null attributes

        Assertions.assertNotEquals(route2b, route1a, "2b 1a");
        Assertions.assertNotEquals(route2c, route1a, "2c 1a");
        Assertions.assertNotEquals(route2d, route1a, "2d 1a");
        Assertions.assertNotEquals(route2e, route1a, "2e 1a");
        Assertions.assertNotEquals(route2a, route1a, "2a 1a");
        Assertions.assertNotEquals(route2f, route1a, "2f 1a");
        Assertions.assertNotEquals(route2g, route1a, "2g 1a");
        Assertions.assertNotEquals(route2h, route1a, "2h 1a");
        Assertions.assertNotEquals(route2i, route1a, "2i 1a");
        Assertions.assertNotEquals(route2j, route1a, "2j 1a");
        Assertions.assertNotEquals(route2k, route1a, "2k 1a");
        Assertions.assertNotEquals(route2m, route1a, "2k 1a");

        // don't check hashCode, it's not guaranteed to be different

        Assertions.assertNotEquals(route1a.toString(), route2a.toString(), "toString 1a 2a");
        Assertions.assertNotEquals(route1a.toString(), route2b.toString(), "toString 1a 2b");
        Assertions.assertNotEquals(route1a.toString(), route2c.toString(), "toString 1a 2c");
        Assertions.assertNotEquals(route1a.toString(), route2d.toString(), "toString 1a 2d");
        Assertions.assertNotEquals(route1a.toString(), route2e.toString(), "toString 1a 2e");
        Assertions.assertNotEquals(route1a.toString(), route2f.toString(), "toString 1a 2f");
        Assertions.assertNotEquals(route1a.toString(), route2g.toString(), "toString 1a 2g");
        Assertions.assertNotEquals(route1a.toString(), route2h.toString(), "toString 1a 2h");
        Assertions.assertNotEquals(route1a.toString(), route2i.toString(), "toString 1a 2i");
        Assertions.assertNotEquals(route1a.toString(), route2j.toString(), "toString 1a 2j");
        Assertions.assertNotEquals(route1a.toString(), route2k.toString(), "toString 1a 2k");
        Assertions.assertNotEquals(route1a.toString(), route2m.toString(), "toString 1a 2k");

        // now check that all of the routes are different from eachother
        // except for those that aren't :-)
        final Set<HttpRoute> routes = new HashSet<>();
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
        routes.add(route2m);

        // and a run of cloning over the set
        for (final HttpRoute origin : routes) {
            final HttpRoute cloned = (HttpRoute) origin.clone();
            Assertions.assertEquals(origin, cloned, "clone of " + origin);
            Assertions.assertTrue(routes.contains(cloned), "clone of " + origin);
        }

        // finally, compare with nonsense
        Assertions.assertNotEquals(null, route1a, "route equals null");
        Assertions.assertNotEquals("route1a", route1a, "route equals string");
    }

    @Test
    void testHopping() {
        // test getHopCount() and getHopTarget() with different proxy chains
        final HttpHost[] proxies = null;
        final HttpRoute route = new HttpRoute(TARGET1, null, proxies, true,
                TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertEquals(1, route.getHopCount(), "A: hop count");
        Assertions.assertEquals(TARGET1, route.getHopTarget(0), "A: hop 0");
        Assertions.assertThrows(IllegalArgumentException.class, () -> route.getHopTarget(1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> route.getHopTarget(-1));

        final HttpHost[] proxies2 = new HttpHost[]{PROXY3};
        final HttpRoute route2 = new HttpRoute(TARGET1, LOCAL62, proxies2, false,
                TunnelType.TUNNELLED, LayerType.PLAIN);
        Assertions.assertEquals(2, route2.getHopCount(), "B: hop count");
        Assertions.assertEquals(PROXY3, route2.getHopTarget(0), "B: hop 0");
        Assertions.assertEquals(TARGET1, route2.getHopTarget(1), "B: hop 1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> route2.getHopTarget(2));
        Assertions.assertThrows(IllegalArgumentException.class, () -> route2.getHopTarget(-2));

        final HttpHost[] proxies3 = new HttpHost[]{PROXY3, PROXY1, PROXY2};
        final HttpRoute route3 = new HttpRoute(TARGET1, LOCAL42, proxies3, false,
                TunnelType.PLAIN, LayerType.LAYERED);
        Assertions.assertEquals(4, route3.getHopCount(), "C: hop count");
        Assertions.assertEquals(PROXY3, route3.getHopTarget(0), "C: hop 0");
        Assertions.assertEquals(PROXY1, route3.getHopTarget(1), "C: hop 1");
        Assertions.assertEquals(PROXY2, route3.getHopTarget(2), "C: hop 2");
        Assertions.assertEquals(TARGET1, route3.getHopTarget(3), "C: hop 3");
        Assertions.assertThrows(IllegalArgumentException.class, () -> route3.getHopTarget(4));
        Assertions.assertThrows(IllegalArgumentException.class, () -> route3.getHopTarget(Integer.MIN_VALUE));
    }

    @Test
    void testCstr1() {
        final HttpRoute route = new HttpRoute(TARGET2);
        final HttpRoute should = new HttpRoute
            (TARGET2, null, (HttpHost[]) null, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertEquals(route, should, "bad convenience route");
    }

    @Test
    void testCstr3() {
        // test convenience constructor with 3 arguments
        HttpRoute route = new HttpRoute(TARGET2, LOCAL61, false);
        HttpRoute should = new HttpRoute
            (TARGET2, LOCAL61, (HttpHost[]) null, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertEquals(route, should, "bad convenience route 3/insecure");

        route = new HttpRoute(TARGET2, null, true);
        should = new HttpRoute(TARGET2, null, (HttpHost[]) null, true,
                               TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertEquals(route, should, "bad convenience route 3/secure");
    }

    @SuppressWarnings("unused")
    @Test
    void testCstr4() {
        // test convenience constructor with 4 arguments
        HttpRoute route = new HttpRoute(TARGET2, null, PROXY2, false);
        HttpRoute should = new HttpRoute
            (TARGET2, null, new HttpHost[]{ PROXY2 }, false,
             TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertEquals(route, should, "bad convenience route 4/insecure");

        route = new HttpRoute(TARGET2, LOCAL42, PROXY1, true);
        should = new HttpRoute
            (TARGET2, LOCAL42, new HttpHost[]{ PROXY1 }, true,
             TunnelType.TUNNELLED, LayerType.LAYERED);
        Assertions.assertEquals(route, should, "bad convenience route 4/secure");

        // this constructor REQUIRES a proxy to be specified
        Assertions.assertThrows(NullPointerException.class, () ->
                new HttpRoute(TARGET1, LOCAL61, null, false));
    }

    @Test
    void testCstr6() {
        // test convenience constructor with 6 arguments
        HttpRoute route = new HttpRoute
            (TARGET2, null, PROXY2, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        HttpRoute should = new HttpRoute
            (TARGET2, null, new HttpHost[]{ PROXY2 }, true,
             TunnelType.TUNNELLED, LayerType.PLAIN);
        Assertions.assertEquals(route, should, "bad convenience route 6/proxied");

        route = new HttpRoute
            (TARGET2, null, (HttpHost) null, true,
             TunnelType.PLAIN, LayerType.LAYERED);
        should = new HttpRoute
            (TARGET2, null, (HttpHost[]) null, true,
             TunnelType.PLAIN, LayerType.LAYERED);
        Assertions.assertEquals(route, should, "bad convenience route 6/direct");

        // handling of null vs. empty chain is checked in the equals tests
    }

    @Test
    void testImmutable() throws CloneNotSupportedException {

        final HttpHost[] proxies = new HttpHost[]{ PROXY1, PROXY2, PROXY3 };
        final HttpRoute route1 = new HttpRoute(TARGET1, null, proxies, false,
                                         TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route2 = (HttpRoute) route1.clone();
        final HttpRoute route3 = new HttpRoute(TARGET1, null,
                                         proxies.clone(), false,
                                         TunnelType.PLAIN, LayerType.PLAIN);

        // modify the array that was passed to the constructor of route1
        proxies[1] = PROXY3;
        proxies[2] = PROXY2;

        Assertions.assertEquals(route2, route1, "route differs from clone");
        Assertions.assertEquals(route3, route1, "route was modified");
    }

    @Test
    void testUnixDomainSocketModeling() {
        final Path uds1 = Paths.get("/var/run/docker.sock");
        final HttpRoute route1 = new HttpRoute(TARGET1, uds1);

        Assertions.assertEquals(uds1, route1.getUnixDomainSocket());
        Assertions.assertEquals(1, route1.getHopCount(), "A UDS is not considered a proxy hop");
        Assertions.assertNull(route1.getProxyHost(), "A UDS is not considered a proxy for routing purposes");
        Assertions.assertEquals(String.format("%s->{}->[http://target1.test.invalid:80]", uds1), route1.toString());

        final Path uds2 = Paths.get("/var/run/docker.sock");
        final HttpRoute route2 = new HttpRoute(TARGET1, null, null, Collections.emptyList(), uds2, false,
            TunnelType.PLAIN, LayerType.PLAIN);
        final HttpRoute route3 = new HttpRoute(TARGET1, null, null, Collections.emptyList(), null, false,
            TunnelType.PLAIN, LayerType.PLAIN);

        Assertions.assertEquals(route1, route2,
            "The UDS convenience constructor should produce an equivalent HttpRoute to the full constructor");
        Assertions.assertNotEquals(route2, route3, "HttpRoute equality should consider the UDS field");
        Assertions.assertNotEquals(route2.hashCode(), route3.hashCode(),
            "HttpRoute hashing should consider the UDS field");
    }

    @Test
    void testUnixDomainSocketValidation() {
        final Path uds = Paths.get("/var/run/docker.sock");
        final List<HttpHost> noProxies = Collections.emptyList();
        final List<HttpHost> oneProxy = Collections.singletonList(PROXY1);
        new HttpRoute(TARGET1, null, null, noProxies, uds, false, TunnelType.PLAIN, LayerType.PLAIN);
        new HttpRoute(TARGET1, null, null, null, uds, true, TunnelType.PLAIN, LayerType.PLAIN);
        Assertions.assertThrows(RuntimeException.class, () ->
            new HttpRoute(TARGET1, null, LOCAL41, noProxies, uds, false, TunnelType.PLAIN, LayerType.PLAIN));
        Assertions.assertThrows(RuntimeException.class, () ->
            new HttpRoute(TARGET1, null, null, oneProxy, uds, false, TunnelType.PLAIN, LayerType.PLAIN));
        Assertions.assertThrows(RuntimeException.class, () ->
            new HttpRoute(TARGET1, null, null, noProxies, uds, false, TunnelType.TUNNELLED, LayerType.PLAIN));
        Assertions.assertThrows(RuntimeException.class, () ->
            new HttpRoute(TARGET1, null, null, noProxies, uds, false, TunnelType.PLAIN, LayerType.LAYERED));
    }
}
