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
import java.util.concurrent.locks.ReentrantLock;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.conn.routing.HttpRoute;



/**
 * Tests for <code>WaitingThread</code>.
 */
public class TestWaitingThread extends TestCase {

    public final static
        HttpHost TARGET = new HttpHost("target.test.invalid");


    public TestWaitingThread(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestWaitingThread.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestWaitingThread.class);
    }


    public void testConstructor() {
        try {
            new WaitingThread(null, null);
            fail("null condition not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        Lock      lck = new ReentrantLock();
        Condition cnd = lck.newCondition();

        WaitingThread wt = new WaitingThread(cnd, null);
        assertEquals("wrong condition", cnd, wt.getCondition());
        assertNull  ("pool from nowhere", wt.getPool());
        assertNull  ("thread from nowhere", wt.getThread());

        HttpRoute         route = new HttpRoute(TARGET);
        RouteSpecificPool rospl = new RouteSpecificPool(route, 10);
        wt = new WaitingThread(cnd, rospl);
        assertEquals("wrong condition", cnd, wt.getCondition());
        assertEquals("wrong pool", rospl, wt.getPool());
        assertNull  ("thread from nowhere", wt.getThread());
    }


    public void testAwaitWakeup() throws InterruptedException {

        Lock      lck = new ReentrantLock();
        Condition cnd = lck.newCondition();
        WaitingThread wt = new WaitingThread(cnd, null);

        AwaitThread ath = new AwaitThread(wt, lck, null);
        ath.start();
        Thread.sleep(100); // give extra thread time to block

        assertNull("thread caught exception", ath.getException());
        assertTrue("thread not waiting", ath.isWaiting());
        assertEquals("wrong thread", ath, wt.getThread());

        Thread.sleep(500); // just for fun, let it wait for some time
        // this may fail due to a spurious wakeup
        assertTrue("thread not waiting, spurious wakeup?", ath.isWaiting());

        try {
            lck.lock();
            wt.wakeup();
        } finally {
            lck.unlock();
        }
        ath.join(10000);

        assertFalse("thread still waiting", ath.isWaiting());
        assertNull("thread caught exception", ath.getException());
        assertNull("thread still there", wt.getThread());
    }


    public void testInterrupt() throws InterruptedException {

        Lock      lck = new ReentrantLock();
        Condition cnd = lck.newCondition();
        WaitingThread wt = new WaitingThread(cnd, null);

        AwaitThread ath = new AwaitThread(wt, lck, null);
        ath.start();
        Thread.sleep(100); // give extra thread time to block

        assertNull("thread caught exception", ath.getException());
        assertTrue("thread not waiting", ath.isWaiting());
        assertEquals("wrong thread", ath, wt.getThread());

        ath.interrupt();
        Thread.sleep(100); // give extra thread time to wake up

        assertFalse("thread still waiting", ath.isWaiting());
        assertNotNull("thread didn't catch exception", ath.getException());
        assertTrue("thread caught wrong exception",
                   ath.getException() instanceof InterruptedException);
        assertNull("thread still there", wt.getThread());
    }


    public void testIllegal() throws InterruptedException {

        Lock      lck = new ReentrantLock();
        Condition cnd = lck.newCondition();
        WaitingThread wt = new WaitingThread(cnd, null);

        try {
            lck.lock();
            wt.wakeup();
            fail("missing waiter not detected");
        } catch (IllegalStateException isx) {
            // expected
        } finally {
            lck.unlock();
        }

        AwaitThread ath1 = new AwaitThread(wt, lck, null);
        ath1.start();
        Thread.sleep(100); // give extra thread time to block

        assertNull("thread caught exception", ath1.getException());
        assertTrue("thread not waiting", ath1.isWaiting());
        assertEquals("wrong thread", ath1, wt.getThread());

        AwaitThread ath2 = new AwaitThread(wt, lck, null);
        ath2.start();
        Thread.sleep(100); // give extra thread time to try to block

        assertFalse("thread waiting", ath2.isWaiting());
        assertNotNull("thread didn't catch exception", ath2.getException());
        assertTrue("thread caught wrong exception",
                   ath2.getException() instanceof IllegalStateException);

        // clean up by letting the threads terminate
        ath1.interrupt();
        ath2.interrupt();
    }


} // class TestWaitingThread
