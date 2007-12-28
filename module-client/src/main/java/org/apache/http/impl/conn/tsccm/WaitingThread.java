/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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


/**
 * Represents a thread waiting for a connection.
 * This class implements throwaway objects. It is instantiated whenever
 * a thread needs to wait. Instances are not re-used, except if the
 * waiting thread experiences a spurious wakeup and continues to wait.
 * <br/>
 * All methods assume external synchronization on the condition
 * passed to the constructor.
 * Instances of this class do <i>not</i> synchronize access!
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 */
public class WaitingThread {

    /** The condition on which the thread is waiting. */
    private final Condition cond;

    /** The route specific pool on which the thread is waiting. */
    //@@@ replace with generic pool interface
    private final RouteSpecificPool pool;

    /** The thread that is waiting for an entry. */
    private Thread waiter;


    /**
     * Indicates the source of an interruption.
     * Set to <code>true</code> inside
     * {@link #notifyWaitingThread(RouteSpecificPool)}
     * and {@link #shutdown shutdown()}
     * before the thread is interrupted.
     * If not set, the thread was interrupted from the outside.
     */
    //@@@ to be removed in HTTPCLIENT-677
    /*default@@@*/ boolean interruptedByConnectionPool;


    /**
     * Creates a new entry for a waiting thread.
     *
     * @param cond      the condition for which to wait
     * @param pool      the pool on which the thread will be waiting,
     *                  or <code>null</code>
     */
    public WaitingThread(Condition cond, RouteSpecificPool pool) {

        if (cond == null) {
            throw new IllegalArgumentException("Condition must not be null.");
        }

        this.cond = cond;
        this.pool = pool;
    }


    /**
     * Blocks the calling thread.
     * This method returns when the thread is notified or interrupted,
     * if a timeout occurrs, or if there is a spurious wakeup.
     * <br/>
     * This method assumes external synchronization.
     *
     * @param timeout   the timeout in milliseconds, or 0 for no timeout
     *
     * @see #wakeup
     */
    public void await(long timeout)
        throws InterruptedException {

        //@@@ check timeout for negative, or assume overflow?
        //@@@ for now, leave the check to the condition

        // This is only a sanity check. We cannot not synchronize here,
        // the lock would not be released on calling cond.await() below.
        if (this.waiter != null) {
            throw new IllegalStateException
                ("A thread is already waiting on this object." +
                 "\ncaller: " + Thread.currentThread() +
                 "\nwaiter: " + this.waiter);
        }

        this.waiter = Thread.currentThread();

        try {
            this.cond.await(timeout, TimeUnit.MILLISECONDS);
        } finally {
            this.waiter = null;
        }
    } // await


    /**
     * Wakes up the waiting thread.
     * <br/>
     * This method assumes external synchronization.
     */
    public void wakeup() {

        // If external synchronization and pooling works properly,
        // this cannot happen. Just a sanity check.
        if (this.waiter == null) {
            throw new IllegalStateException
                ("Nobody waiting on this object.");
        }

        // One condition might be shared by several WaitingThread instances.
        // It probably isn't, but just in case: wake all, not just one.
        this.cond.signalAll();
    }


    /**
     * Obtains the condition.
     *
     * @return  the condition on which to wait, never <code>null</code>
     */
    public final Condition getCondition() {
        // not synchronized
        return this.cond;
    }


    /**
     * Obtains the pool, if there is one.
     *
     * @return  the pool on which a thread is or was waiting,
     *          or <code>null</code>
     */
    public final RouteSpecificPool getPool() {
        // not synchronized
        return this.pool;
    }


    /**
     * Obtains the thread, if there is one.
     *
     * @return  the thread which is waiting, or <code>null</code>
     */
    public final Thread getThread() {
        // not synchronized
        return this.waiter;
    }


} // class WaitingThread
