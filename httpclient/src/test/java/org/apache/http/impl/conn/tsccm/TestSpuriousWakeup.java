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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

// test imports
import org.apache.http.impl.conn.GetConnThread;



/**
 * Tests for spurious wakeups in <code>WaitingThread</code>.
 * Requires some wrapping code to get at the lock and condition,
 * which is required to trigger a wakeup without actually
 * satisfying the condition.
 *
 */
public class TestSpuriousWakeup extends TestCase {

    public final static
        HttpHost TARGET = new HttpHost("target.test.invalid");
    public final static
        HttpRoute ROUTE = new HttpRoute(TARGET);


    public TestSpuriousWakeup(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestSpuriousWakeup.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestSpuriousWakeup.class);
    }


    /**
     * An extended connection pool that gives access to some internals.
     */
    private static class XConnPoolByRoute extends ConnPoolByRoute {

        /** The last WaitingThread object created. */
        protected WaitingThread newestWT;


        public XConnPoolByRoute(ClientConnectionOperator operator, HttpParams params) {
            super(operator, params);
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


        public XTSCCM(HttpParams params, SchemeRegistry schreg) {
            super(params, schreg);
        }

        @Override
        protected AbstractConnPool createConnectionPool(HttpParams params) {
            extendedCPBR = new XConnPoolByRoute(connOperator, params);
            // no connection GC required
            return extendedCPBR;
        }

    } // class XTSCCM



    public void testSpuriousWakeup() throws Exception {

        // parameters with connection limit 1
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(1));
        ConnManagerParams.setMaxTotalConnections(params, 1);

        SchemeRegistry schreg = new SchemeRegistry();
        SocketFactory sf = PlainSocketFactory.getSocketFactory();
        schreg.register(new Scheme("http", sf, 80));

        XTSCCM mgr = new XTSCCM(params, schreg);

        try {
            // take out the only connection
            ClientConnectionRequest connRequest = mgr.requestConnection(ROUTE, null);
            ManagedClientConnection conn = connRequest.getConnection(0, null);
            assertNotNull(conn);

            // send a thread waiting
            GetConnThread gct = new GetConnThread(mgr, ROUTE, 0L);
            gct.start();
            Thread.sleep(100); // give extra thread time to block

            assertEquals("thread not waiting",
                         Thread.State.WAITING, gct.getState());

            // get access to the objects we need
            Lock      lck = mgr.extendedCPBR.poolLock;
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

                assertEquals("thread no longer waiting, iteration " + i,
                             Thread.State.WAITING, gct.getState());
            }
        } finally {
            // don't worry about releasing the connection, just shut down
            mgr.shutdown();
        }
    } // testSpuriousWakeup


} // class TestSpuriousWakeup
