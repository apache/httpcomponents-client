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

    private int successCount = 0;
    private int failureCount = 0;
    private String serverName = "unknown";
    private long contentLen = 0;
    private long totalContentLen = 0;
        
    public Stats() {
        super();
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public long getContentLen() {
        return contentLen;
    }

    public void setContentLen(long contentLen) {
        this.contentLen = contentLen;
    }

    public long getTotalContentLen() {
        return totalContentLen;
    }

    public void setTotalContentLen(long totalContentLen) {
        this.totalContentLen = totalContentLen;
    }
    
    public static void printStats(
            final URI targetURI, long startTime, long finishTime, final Stats stats) {
        float totalTimeSec = (float) (finishTime - startTime) / 1000;
        float reqsPerSec = (float) stats.getSuccessCount() / totalTimeSec;
        float timePerReqMs = (float) (finishTime - startTime) / (float) stats.getSuccessCount();
       
        System.out.print("Server Software:\t");
        System.out.println(stats.getServerName());
        System.out.println();
        System.out.print("Document URI:\t\t");
        System.out.println(targetURI);
        System.out.print("Document Length:\t");
        System.out.print(stats.getContentLen());
        System.out.println(" bytes");
        System.out.println();
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