/*
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

package org.apache.http.impl.conn;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.tsccm.BasicPoolEntry;
import org.apache.http.impl.conn.tsccm.ConnPoolByRoute;
import org.apache.http.impl.conn.tsccm.PoolEntryRequest;

@SuppressWarnings("deprecation")
public class ConnPoolBench {

    private final static HttpRoute ROUTE = new HttpRoute(new HttpHost("localhost"));

    public static void main(String[] args) throws Exception {
        int c = 200;
        long reps = 100000;
        oldPool(c, reps);
        newPool(c, reps);
    }

    public static void newPool(int c, long reps) throws Exception {
        Log log = LogFactory.getLog(ConnPoolBench.class);

        DefaultClientConnectionOperator connOperator = new DefaultClientConnectionOperator(
            SchemeRegistryFactory.createDefault());
        HttpConnPool pool = new HttpConnPool(log, connOperator, c, c * 10, -1, TimeUnit.MILLISECONDS);

        WorkerThread1[] workers = new WorkerThread1[c];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread1(pool, reps);
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
        }
        long finish = System.currentTimeMillis();
        float totalTimeSec = (float) (finish - start) / 1000;
        System.out.print("Concurrency level:\t");
        System.out.println(c);
        System.out.print("Total operations:\t");
        System.out.println(c * reps);
        System.out.print("Time taken for tests:\t");
        System.out.print(totalTimeSec);
        System.out.println(" seconds");
    }

    static void oldPool(int c, long reps) throws Exception {
        ClientConnectionOperator operator = new DefaultClientConnectionOperator(
                SchemeRegistryFactory.createDefault());
        ConnPerRoute connPerRoute = new ConnPerRouteBean(c);
        ConnPoolByRoute pool = new ConnPoolByRoute(operator, connPerRoute, c * 10);

        WorkerThread2[] workers = new WorkerThread2[c];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread2(pool, reps);
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
        }
        long finish = System.currentTimeMillis();
        float totalTimeSec = (float) (finish - start) / 1000;
        System.out.print("Concurrency level:\t");
        System.out.println(c);
        System.out.print("Total operations:\t");
        System.out.println(c * reps);
        System.out.print("Time taken for tests:\t");
        System.out.print(totalTimeSec);
        System.out.println(" seconds");
    }

    static class WorkerThread1 extends Thread {

        private final HttpConnPool pool;
        private final long reps;

        WorkerThread1(final HttpConnPool pool, final long reps) {
            super();
            this.pool = pool;
            this.reps = reps;
        }

        @Override
        public void run() {
            for (long c = 0; c < this.reps; c++) {
                Future<HttpPoolEntry> future = this.pool.lease(ROUTE, null);
                try {
                    HttpPoolEntry entry = future.get(-1, TimeUnit.MILLISECONDS);
                    this.pool.release(entry, true);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (TimeoutException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    static class WorkerThread2 extends Thread {

        private final ConnPoolByRoute pool;
        private final long reps;

        WorkerThread2(final ConnPoolByRoute pool, final long reps) {
            super();
            this.pool = pool;
            this.reps = reps;
        }

        @Override
        public void run() {
            for (long c = 0; c < this.reps; c++) {
                PoolEntryRequest request = this.pool.requestPoolEntry(ROUTE, null);
                BasicPoolEntry entry;
                try {
                    entry = request.getPoolEntry(-1, TimeUnit.MILLISECONDS);
                    this.pool.freeEntry(entry, true, -1, TimeUnit.MILLISECONDS);
                } catch (ConnectionPoolTimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}

