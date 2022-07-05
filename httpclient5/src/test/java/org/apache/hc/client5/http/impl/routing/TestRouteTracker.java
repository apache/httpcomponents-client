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
import java.util.HashSet;
import java.util.Set;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo.LayerType;
import org.apache.hc.client5.http.RouteInfo.TunnelType;
import org.apache.hc.client5.http.RouteTracker;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RouteTracker}.
 */
@SuppressWarnings("boxing") // test code
public class TestRouteTracker {

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

    @SuppressWarnings("unused")
    @Test
    public void testCstrTargetLocal() {

        RouteTracker rt = new RouteTracker(TARGET1, null);
        Assertions.assertEquals(TARGET1, rt.getTargetHost(), "wrong target (target,null)");
        Assertions.assertNull(rt.getLocalAddress(), "wrong local address (target,null)");
        Assertions.assertEquals(0, rt.getHopCount(), "wrong hop count (target,null)");
        Assertions.assertNull(rt.getProxyHost(), "wrong proxy (target,null)");
        Assertions.assertNull(rt.toRoute(), "wrong route (target,null)");
        checkCTLS(rt, false, false, false, false);


        rt = new RouteTracker(TARGET2, LOCAL61);
        Assertions.assertEquals(TARGET2, rt.getTargetHost(), "wrong target (target,local)");
        Assertions.assertEquals(LOCAL61, rt.getLocalAddress(), "wrong local address (target,local)");
        Assertions.assertEquals(0, rt.getHopCount(), "wrong hop count (target,local)");
        Assertions.assertNull(rt.getProxyHost(), "wrong proxy (target,local)");
        Assertions.assertNull(rt.toRoute(), "wrong route (target,local)");
        checkCTLS(rt, false, false, false, false);

        Assertions.assertThrows(NullPointerException.class, () -> new RouteTracker(null, LOCAL41));
    }

    @SuppressWarnings("unused")
    @Test
    public void testCstrRoute() {

        HttpRoute r  = new HttpRoute(TARGET1);
        RouteTracker rt = new RouteTracker(r);
        Assertions.assertEquals(TARGET1, rt.getTargetHost(), "wrong target (r1)");
        Assertions.assertNull(rt.getLocalAddress(), "wrong local address (r1)");
        Assertions.assertEquals(0, rt.getHopCount(), "wrong hop count (r1)");
        Assertions.assertNull(rt.getProxyHost(), "wrong proxy (r1)");
        Assertions.assertNull(rt.toRoute(), "wrong route (r1)");
        checkCTLS(rt, false, false, false, false);

        r  = new HttpRoute(TARGET2, LOCAL61, true);
        rt = new RouteTracker(r);
        Assertions.assertEquals(TARGET2, rt.getTargetHost(), "wrong target (r2)");
        Assertions.assertEquals(LOCAL61, rt.getLocalAddress(), "wrong local address (r2)");
        Assertions.assertEquals(0, rt.getHopCount(), "wrong hop count (r2)");
        Assertions.assertNull(rt.getProxyHost(), "wrong proxy (r2)");
        Assertions.assertNull(rt.toRoute(), "wrong route (r2)");
        checkCTLS(rt, false, false, false, false);


        r  = new HttpRoute(TARGET1, LOCAL42, PROXY3, true);
        rt = new RouteTracker(r);
        Assertions.assertEquals(TARGET1, rt.getTargetHost(), "wrong target (r3)");
        Assertions.assertEquals(LOCAL42, rt.getLocalAddress(), "wrong local address (r3)");
        Assertions.assertEquals(0, rt.getHopCount(), "wrong hop count (r3)");
        Assertions.assertNull(rt.getProxyHost(), "wrong proxy (r3)");
        Assertions.assertNull(rt.toRoute(), "wrong route (r3)");
        checkCTLS(rt, false, false, false, false);

        Assertions.assertThrows(NullPointerException.class, () -> new RouteTracker(null));
    }

    @Test
    public void testIllegalArgs() {

        final RouteTracker rt = new RouteTracker(TARGET2, null);

        Assertions.assertThrows(NullPointerException.class, () -> rt.connectProxy(null, true));
        Assertions.assertThrows(NullPointerException.class, () -> rt.connectProxy(null, false));

        rt.connectProxy(PROXY1, false);

        Assertions.assertThrows(NullPointerException.class, () -> rt.tunnelProxy(null, false));
        Assertions.assertThrows(NullPointerException.class, () -> rt.tunnelProxy(null, true));
        Assertions.assertThrows(IllegalArgumentException.class, () -> rt.getHopTarget(-1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> rt.getHopTarget(2));
    }

    @Test
    public void testIllegalStates() {

        final RouteTracker rt = new RouteTracker(TARGET1, null);

        Assertions.assertThrows(IllegalStateException.class, () -> rt.tunnelTarget(false));
        Assertions.assertThrows(IllegalStateException.class, () -> rt.tunnelProxy(PROXY1, false));
        Assertions.assertThrows(IllegalStateException.class, () -> rt.layerProtocol(true));

        // connect directly
        rt.connectTarget(false);

        Assertions.assertThrows(IllegalStateException.class, () -> rt.connectTarget(false));
        Assertions.assertThrows(IllegalStateException.class, () -> rt.connectProxy(PROXY2, false));
        Assertions.assertThrows(IllegalStateException.class, () -> rt.tunnelTarget(false));
        Assertions.assertThrows(IllegalStateException.class, () -> rt.tunnelProxy(PROXY1, false));
    }

    @Test
    public void testDirectRoutes() {

        final HttpRouteDirector rd = BasicRouteDirector.INSTANCE;
        HttpRoute r = new HttpRoute(TARGET1, LOCAL41, false);
        RouteTracker rt = new RouteTracker(r);
        boolean complete = checkVia(rt, r, rd, 2);
        Assertions.assertTrue(complete, "incomplete route 1");

        r = new HttpRoute(TARGET2, LOCAL62, true);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 2);
        Assertions.assertTrue(complete, "incomplete route 2");
    }

    @Test
    public void testProxyRoutes() {

        final HttpRouteDirector rd = BasicRouteDirector.INSTANCE;
        HttpRoute r = new HttpRoute(TARGET2, null, PROXY1, false);
        RouteTracker rt = new RouteTracker(r);
        boolean complete = checkVia(rt, r, rd, 2);
        Assertions.assertTrue(complete, "incomplete route 1");

        // tunnelled, but neither secure nor layered
        r = new HttpRoute(TARGET1, LOCAL61, PROXY3, false,
                          TunnelType.TUNNELLED, LayerType.PLAIN);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 3);
        Assertions.assertTrue(complete, "incomplete route 2");

        // tunnelled, layered, but not secure
        r = new HttpRoute(TARGET1, LOCAL61, PROXY3, false,
                          TunnelType.TUNNELLED, LayerType.LAYERED);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 4);
        Assertions.assertTrue(complete, "incomplete route 3");

        // tunnelled, layered, secure
        r = new HttpRoute(TARGET1, LOCAL61, PROXY3, true);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 4);
        Assertions.assertTrue(complete, "incomplete route 4");
    }

    @Test
    public void testProxyChainRoutes() {

        final HttpRouteDirector rd = BasicRouteDirector.INSTANCE;
        HttpHost[] proxies = { PROXY1, PROXY2 };
        HttpRoute r = new HttpRoute(TARGET2, LOCAL42, proxies, false,
                                    TunnelType.PLAIN, LayerType.PLAIN);
        RouteTracker rt = new RouteTracker(r);
        boolean complete = checkVia(rt, r, rd, 3);
        Assertions.assertTrue(complete, "incomplete route 1");

        // tunnelled, but neither secure nor layered
        proxies = new HttpHost[]{ PROXY3, PROXY2 };
        r = new HttpRoute(TARGET1, null, proxies, false,
                          TunnelType.TUNNELLED, LayerType.PLAIN);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 4);
        Assertions.assertTrue(complete, "incomplete route 2");

        // tunnelled, layered, but not secure
        proxies = new HttpHost[]{ PROXY3, PROXY2, PROXY1 };
        r = new HttpRoute(TARGET2, LOCAL61, proxies, false,
                          TunnelType.TUNNELLED, LayerType.LAYERED);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 6);
        Assertions.assertTrue(complete, "incomplete route 3");

        // tunnelled, layered, secure
        proxies = new HttpHost[]{ PROXY1, PROXY3 };
        r = new HttpRoute(TARGET1, LOCAL61, proxies, true,
                          TunnelType.TUNNELLED, LayerType.LAYERED);
        rt = new RouteTracker(r);
        complete = checkVia(rt, r, rd, 5);
        Assertions.assertTrue(complete, "incomplete route 4");
    }

    @Test
    public void testEqualsHashcodeCloneToString()
        throws CloneNotSupportedException {

        final RouteTracker rt0 = new RouteTracker(TARGET1, null);
        final RouteTracker rt1 = new RouteTracker(TARGET2, null);
        final RouteTracker rt2 = new RouteTracker(TARGET1, null);
        final RouteTracker rt3 = new RouteTracker(TARGET1, null);
        final RouteTracker rt4 = new RouteTracker(TARGET1, LOCAL41);
        final RouteTracker rt6 = new RouteTracker(TARGET1, LOCAL62);

        Assertions.assertNotEquals(null, rt0, "rt0");
        Assertions.assertEquals(rt0, rt0, "rt0");
        Assertions.assertNotEquals("rt0", rt0, rt0.toString());

        Assertions.assertNotEquals(rt0, rt4, "rt0 == rt4");
        Assertions.assertNotEquals(rt0, rt1, "rt0 == rt1"); // Check host takes part in equals

        // Check that connection takes part in equals
        Assertions.assertEquals(rt0, rt2, "rt0 != rt2");
        rt2.connectTarget(false);
        Assertions.assertNotEquals(rt0, rt2, "rt0 == rt2");

        Assertions.assertEquals(rt0, rt3, "rt0 != rt3");
        rt3.connectTarget(true);
        Assertions.assertNotEquals(rt0, rt3, "rt0 == rt3");
        Assertions.assertNotEquals(rt2, rt3, "rt2 == rt3"); // Test secure takes part

        // TODO needs tests for tunnel and layered

        Assertions.assertNotEquals(rt4, rt0, "rt4 == rt0");
        Assertions.assertNotEquals(rt0, rt6, "rt0 == rt6");
        Assertions.assertNotEquals(rt6, rt0, "rt6 == rt0");
        Assertions.assertNotEquals(rt4, rt6, "rt4 == rt6");
        Assertions.assertNotEquals(rt6, rt4, "rt6 == rt4");

        // it is likely but not guaranteed that the hashcodes are different
        Assertions.assertNotEquals(rt0.hashCode(), rt4.hashCode(), "rt0 == rt4 (hashcode)");
        Assertions.assertNotEquals(rt0.hashCode(), rt6.hashCode(), "rt0 == rt6 (hashcode)");
        Assertions.assertNotEquals(rt6.hashCode(), rt4.hashCode(), "rt6 == rt4 (hashcode)");

        Assertions.assertEquals(rt0, rt0.clone(), "rt0 (clone)");
        Assertions.assertEquals(rt4, rt4.clone(), "rt4 (clone)");
        Assertions.assertEquals(rt6, rt6.clone(), "rt6 (clone)");


        // we collect (clones of) the different tracked routes along the way
        // rt0 -> direct connection
        // rt1 -> via single proxy
        // rt2 -> via proxy chain
        final Set<RouteTracker> hs = new HashSet<>();

        // we also collect hashcodes for the different paths
        // since we can't guarantee what influence the HttpHost hashcodes have,
        // we keep separate sets here
        final Set<Integer> hc0 = new HashSet<>();
        final Set<Integer> hc4 = new HashSet<>();
        final Set<Integer> hc6 = new HashSet<>();

        RouteTracker rt = null;

        Assertions.assertTrue(hs.add(rt0));
        Assertions.assertTrue(hs.add(rt4));
        Assertions.assertTrue(hs.add(rt6));

        Assertions.assertTrue(hc0.add(rt0.hashCode()));
        Assertions.assertTrue(hc4.add(rt4.hashCode()));
        Assertions.assertTrue(hc6.add(rt6.hashCode()));

        rt = (RouteTracker) rt0.clone();
        rt.connectTarget(false);
        Assertions.assertTrue(hs.add(rt));
        Assertions.assertTrue(hc0.add(rt.hashCode()));

        rt = (RouteTracker) rt0.clone();
        rt.connectTarget(true);
        Assertions.assertTrue(hs.add(rt));
        Assertions.assertTrue(hc0.add(rt.hashCode()));


        // proxy (insecure) -> tunnel (insecure) -> layer (secure)
        rt = (RouteTracker) rt4.clone();
        rt.connectProxy(PROXY1, false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc4.add(rt.hashCode()));

        rt.tunnelTarget(false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc4.add(rt.hashCode()));

        rt.layerProtocol(true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc4.add(rt.hashCode()));


        // proxy (secure) -> tunnel (secure) -> layer (insecure)
        rt = (RouteTracker) rt4.clone();
        rt.connectProxy(PROXY1, true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc4.add(rt.hashCode()));

        rt.tunnelTarget(true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc4.add(rt.hashCode()));

        rt.layerProtocol(false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc4.add(rt.hashCode()));


        // PROXY1/i -> PROXY2/i -> tunnel/i -> layer/s
        rt = (RouteTracker) rt6.clone();
        rt.connectProxy(PROXY1, false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.tunnelProxy(PROXY2, false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.tunnelTarget(false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.layerProtocol(true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc6.add(rt.hashCode()));


        // PROXY1/s -> PROXY2/s -> tunnel/s -> layer/i
        rt = (RouteTracker) rt6.clone();
        rt.connectProxy(PROXY1, true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.tunnelProxy(PROXY2, true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.tunnelTarget(true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.layerProtocol(false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        Assertions.assertTrue(hc6.add(rt.hashCode()));


        // PROXY2/i -> PROXY1/i -> tunnel/i -> layer/s
        rt = (RouteTracker) rt6.clone();
        rt.connectProxy(PROXY2, false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // this is not guaranteed to be unique...
        Assertions.assertTrue(hc6.add(rt.hashCode()));

        rt.tunnelProxy(PROXY1, false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // proxy chain sequence does not affect hashcode, so duplicate:
        // Assertions.assertTrue(hc6.add(Integer.valueOf(rt.hashCode())));

        rt.tunnelTarget(false);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // proxy chain sequence does not affect hashcode, so duplicate:
        // Assertions.assertTrue(hc6.add(Integer.valueOf(rt.hashCode())));

        rt.layerProtocol(true);
        Assertions.assertTrue(hs.add((RouteTracker) rt.clone()));
        // proxy chain sequence does not affect hashcode, so duplicate:
        // Assertions.assertTrue(hc6.add(Integer.valueOf(rt.hashCode())));


        // check that all toString are OK and different
        final Set<String> rtstrings = new HashSet<>();
        for (final RouteTracker current: hs) {
            final String rts = checkToString(current);
            Assertions.assertTrue(rtstrings.add(rts), "duplicate toString: " + rts);
        }
    }


    /** Helper to check the status of the four flags. */
    public final static void checkCTLS(final RouteTracker rt,
                                       final boolean c, final boolean t,
                                       final boolean l, final boolean s) {
        final String rts = rt.toString();
        Assertions.assertEquals(c, rt.isConnected(), "wrong flag connected: " + rts);
        Assertions.assertEquals(t, rt.isTunnelled(), "wrong flag tunnelled: " + rts);
        Assertions.assertEquals(t ? TunnelType.TUNNELLED : TunnelType.PLAIN,
                     rt.getTunnelType(), "wrong enum tunnelled: " + rts);
        Assertions.assertEquals(l, rt.isLayered(), "wrong flag layered: "   + rts);
        Assertions.assertEquals(l ? LayerType.LAYERED : LayerType.PLAIN,
                     rt.getLayerType(), "wrong enum layered: "   + rts);
        Assertions.assertEquals(s, rt.isSecure(), "wrong flag secure: " + rts);
    }


    /**
     * Helper to check tracking of a route.
     * This uses a {@link HttpRouteDirector} to fake establishing the route,
     * checking the intermediate steps.
     *
     * @param rt        the tracker to check with
     * @param r         the route to establish
     * @param rd        the director to check with
     * @param steps     the step count for this invocation
     *
     * @return  {@code true} iff the route is complete
     */
    public final static boolean checkVia(final RouteTracker rt, final HttpRoute r,
                                         final HttpRouteDirector rd, final int steps) {

        final String msg = r + " @ " + rt;

        boolean complete = false;
        int n = steps;
        while (!complete && (n > 0)) {

            final int action = rd.nextStep(r, rt.toRoute());
            switch (action) {

            case HttpRouteDirector.COMPLETE:
                complete = true;
                Assertions.assertEquals(r, rt.toRoute());
                break;

            case HttpRouteDirector.CONNECT_TARGET: {
                final boolean sec = r.isSecure();
                rt.connectTarget(sec);
                checkCTLS(rt, true, false, false, sec);
                Assertions.assertEquals(1, rt.getHopCount(), "wrong hop count "+msg);
                Assertions.assertEquals(r.getTargetHost(), rt.getHopTarget(0), "wrong hop0 "+msg);
            } break;

            case HttpRouteDirector.CONNECT_PROXY: {
                // we assume an insecure proxy connection
                final boolean sec = false;
                rt.connectProxy(r.getProxyHost(), sec);
                checkCTLS(rt, true, false, false, sec);
                Assertions.assertEquals(2, rt.getHopCount(), "wrong hop count "+msg);
                Assertions.assertEquals(r.getProxyHost(), rt.getHopTarget(0), "wrong hop0 "+msg);
                Assertions.assertEquals(r.getTargetHost(), rt.getHopTarget(1), "wrong hop1 "+msg);
            } break;

            case HttpRouteDirector.TUNNEL_TARGET: {
                final int hops = rt.getHopCount();
                // we assume an insecure tunnel
                final boolean sec = false;
                rt.tunnelTarget(sec);
                checkCTLS(rt, true, true, false, sec);
                Assertions.assertEquals(hops, rt.getHopCount(), "wrong hop count "+msg);
                Assertions.assertEquals(r.getProxyHost(), rt.getHopTarget(0), "wrong hop0 "+msg);
                Assertions.assertEquals(r.getTargetHost(), rt.getHopTarget(hops-1), "wrong hopN "+msg);
            } break;

            case HttpRouteDirector.TUNNEL_PROXY: {
                final int hops = rt.getHopCount(); // before tunnelling
                // we assume an insecure tunnel
                final boolean  sec = false;
                final HttpHost pxy = r.getHopTarget(hops-1);
                rt.tunnelProxy(pxy, sec);
                // Since we're tunnelling to a proxy and not the target,
                // the 'tunelling' flag is false: no end-to-end tunnel.
                checkCTLS(rt, true, false, false, sec);
                Assertions.assertEquals(hops+1, rt.getHopCount(), "wrong hop count "+msg);
                Assertions.assertEquals(r.getProxyHost(), rt.getHopTarget(0), "wrong hop0 "+msg);
                Assertions.assertEquals(pxy, rt.getHopTarget(hops-1), "wrong hop"+hops+" "+msg);
                Assertions.assertEquals(r.getTargetHost(), rt.getHopTarget(hops), "wrong hopN "+msg);
            } break;

            case HttpRouteDirector.LAYER_PROTOCOL: {
                final int    hops = rt.getHopCount();
                final boolean tun = rt.isTunnelled();
                final boolean sec = r.isSecure();
                rt.layerProtocol(sec);
                checkCTLS(rt, true, tun, true, sec);
                Assertions.assertEquals(hops, rt.getHopCount(), "wrong hop count "+msg);
                Assertions.assertEquals(r.getProxyHost(), rt.getProxyHost(), "wrong proxy "+msg);
                Assertions.assertEquals(r.getTargetHost(), rt.getTargetHost(), "wrong target "+msg);
            } break;


            // UNREACHABLE
            default:
                Assertions.fail("unexpected action " + action + " from director, "+msg);
                break;

            } // switch
            n--;
        }

        return complete;
    } // checkVia


    /**
     * Checks the output of {@code toString}.
     *
     * @param rt        the tracker for which to check the output
     *
     * @return  the result of {@code rt.toString()}
     */
    public final static String checkToString(final RouteTracker rt) {
        if (rt == null) {
            return null;
        }

        final String rts = rt.toString();

        if (rt.getLocalAddress() != null) {
            final String las = rt.getLocalAddress().toString();
            Assertions.assertTrue(rts.contains(las), "no local address in toString(): " + rts);
        }

        for (int i=0; i<rt.getHopCount(); i++) {
            final String hts = rt.getHopTarget(i).toString();
            Assertions.assertTrue(rts.contains(hts), "hop " + i + " (" + hts + ") missing in toString(): " + rts);
        }

        return rts;
    }

}
