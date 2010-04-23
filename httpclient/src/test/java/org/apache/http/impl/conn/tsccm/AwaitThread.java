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


import java.util.Date;
import java.util.concurrent.locks.Lock;


/**
 * Thread to await something.
 */
public class AwaitThread extends Thread {

    protected final WaitingThread wait_object;
    protected final Lock          wait_lock;
    protected final Date          wait_deadline;

    protected volatile boolean       waiting;
    protected volatile Throwable     exception;


    /**
     * Creates a new thread.
     * When this thread is started, it will wait on the argument object.
     */
    public AwaitThread(WaitingThread where, Lock lck, Date deadline) {

        wait_object   = where;
        wait_lock     = lck;
        wait_deadline = deadline;
    }


    /**
     * This method is executed when the thread is started.
     */
    @Override
    public void run() {
        try {
            wait_lock.lock();
            waiting = true;
            wait_object.await(wait_deadline);
        } catch (Throwable dart) {
            exception = dart;
        } finally {
            waiting = false;
            wait_lock.unlock();
        }
        // terminate
    }


    public Throwable getException() {
        return exception;
    }

    public boolean isWaiting() {
        try {
            wait_lock.lock();
            return waiting;
        } finally {
            wait_lock.unlock();
        }
    }

}
