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

package org.apache.http.impl.conn.tsccm;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.impl.conn.GetConnThread;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for spurious wakeups in <code>WaitingThread</code>.
 * Requires some wrapping code to get at the lock and condition,
 * which is required to trigger a wakeup without actually
 * satisfying the condition.
 *
 */
public class TestSpuriousWakeup {

    public final static
        HttpHost TARGET = new HttpHost("target.test.invalid");
    public final static
        HttpRoute ROUTE = new HttpRoute(TARGET);

    /**
     * An extended connection pool that gives access to some internals.
     */
    private static class XConnPoolByRoute extends ConnPoolByRoute {

        /** The last WaitingThread object created. */
        protected WaitingThread newestWT;


        public XConnPoolByRoute(
                final ClientConnectionOperator operator,
                final ConnPerRoute connPerRoute,
                int maxTotalConnections) {
            super(operator, connPerRoute, maxTotalConnections);
        }

        @Override
        protected synchronized
            WaitingThread newWaitingThread(Condition cond,
                                           RouteSpecificPool rospl) {
            WaitingThread wt = super.newWaitingThread(cond, rospl);
            newestWT = wt;
            return wt;
        }

    } // class XConnPoolByRoute


    /**
     * An extended TSCCM that uses XConnPoolByRoute.
     */
    private static class XTSCCM extends ThreadSafeClientConnManager {

        /** The extended connection pool. */
        protected XConnPoolByRoute extendedCPBR;


        public XTSCCM(SchemeRegistry schreg) {
            super(schreg);
        }

        @Override
        protected ConnPoolByRoute createConnectionPool(long connTTL, TimeUnit connTTLUnit) {
            extendedCPBR = new XConnPoolByRoute(connOperator, connPerRoute, 20);
            // no connection GC required
            return extendedCPBR;
        }

    } // class XTSCCM

    @Test
    public void testSpuriousWakeup() throws Exception {
        SchemeRegistry schreg = new SchemeRegistry();
        SchemeSocketFactory sf = PlainSocketFactory.getSocketFactory();
        schreg.register(new Scheme("http", 80, sf));

        XTSCCM mgr = new XTSCCM(schreg);
        try {
            mgr.setMaxTotal(1);
            mgr.setDefaultMaxPerRoute(1);

            // take out the only connection
            ClientConnectionRequest connRequest = mgr.requestConnection(ROUTE, null);
            ManagedClientConnection conn = connRequest.getConnection(0, null);
            Assert.assertNotNull(conn);

            // send a thread waiting
            GetConnThread gct = new GetConnThread(mgr, ROUTE, 0L);
            gct.start();
            Thread.sleep(100); // give extra thread time to block

            Assert.assertEquals("thread not waiting",
                         Thread.State.WAITING, gct.getState());

            // get access to the objects we need
            Lock      lck = mgr.extendedCPBR.getLock();
            Condition cnd = mgr.extendedCPBR.newestWT.getCondition();

            // Now trigger spurious wakeups. We'll do it several times
            // in a loop, just to be sure the connection manager has a
            // fair chance of misbehaving, and the gct to register it.

            for (int i=0; i<3; i++) {
                if (i > 0)
                    Thread.sleep(333); // don't go too fast

                try {
                    lck.lock();
                    cnd.signalAll(); // this is the spurious wakeup
                } finally {
                    lck.unlock();
                }

                // now give the waiting thread some time to register a wakeup
                Thread.sleep(100);

                Assert.assertEquals("thread no longer waiting, iteration " + i,
                             Thread.State.WAITING, gct.getState());
            }
        } finally {
            // don't worry about releasing the connection, just shut down
            mgr.shutdown();
        }
    }

}
