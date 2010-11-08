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
package org.apache.http.client.benchmark;

import java.net.URI;

public class Stats {

    private final int expectedCount;
    private final int concurrency;

    private int successCount = 0;
    private int failureCount = 0;
    private long contentLen = 0;
    private long totalContentLen = 0;

    public Stats(int expectedCount, int concurrency) {
        super();
        this.expectedCount = expectedCount;
        this.concurrency = concurrency;
    }

    public synchronized boolean isComplete() {
        return this.successCount + this.failureCount >= this.expectedCount;
    }

    public synchronized void success(long contentLen) {
        if (isComplete()) return;
        this.successCount++;
        this.contentLen = contentLen;
        this.totalContentLen += contentLen;
        notifyAll();
    }

    public synchronized void failure(long contentLen) {
        if (isComplete()) return;
        this.failureCount++;
        this.contentLen = contentLen;
        this.totalContentLen += contentLen;
        notifyAll();
    }

    public int getConcurrency() {
        return this.concurrency;
    }

    public synchronized int getSuccessCount() {
        return successCount;
    }

    public synchronized int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public synchronized long getContentLen() {
        return contentLen;
    }

    public synchronized long getTotalContentLen() {
        return totalContentLen;
    }

    public synchronized void waitFor() throws InterruptedException {
        while (!isComplete()) {
            wait();
        }
    }

    public static void printStats(
            final URI targetURI, long startTime, long finishTime, final Stats stats) {
        float totalTimeSec = (float) (finishTime - startTime) / 1000;
        float reqsPerSec = (float) stats.getSuccessCount() / totalTimeSec;
        float timePerReqMs = (float) (finishTime - startTime) / (float) stats.getSuccessCount();

        System.out.print("Document URI:\t\t");
        System.out.println(targetURI);
        System.out.print("Document Length:\t");
        System.out.print(stats.getContentLen());
        System.out.println(" bytes");
        System.out.println();
        System.out.print("Concurrency level:\t");
        System.out.println(stats.getConcurrency());
        System.out.print("Time taken for tests:\t");
        System.out.print(totalTimeSec);
        System.out.println(" seconds");
        System.out.print("Complete requests:\t");
        System.out.println(stats.getSuccessCount());
        System.out.print("Failed requests:\t");
        System.out.println(stats.getFailureCount());
        System.out.print("Content transferred:\t");
        System.out.print(stats.getTotalContentLen());
        System.out.println(" bytes");
        System.out.print("Requests per second:\t");
        System.out.print(reqsPerSec);
        System.out.println(" [#/sec] (mean)");
        System.out.print("Time per request:\t");
        System.out.print(timePerReqMs);
        System.out.println(" [ms] (mean)");
    }

}