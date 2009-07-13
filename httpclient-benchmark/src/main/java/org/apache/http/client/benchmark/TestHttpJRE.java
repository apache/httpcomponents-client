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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class TestHttpJRE {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);
       
        URL url = targetURI.toURL();
       
        byte[] buffer = new byte[4096];
       
        long startTime;
        long finishTime;
        int successCount = 0;
        int failureCount = 0;
        String serverName = "unknown";
        long total = 0;
        long contentLen = 0;
        long totalContentLen = 0;
       
        startTime = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.connect();
            InputStream instream = c.getInputStream();
            try {
                contentLen = 0;
                if (instream != null) {
                    int l = 0;
                    while ((l = instream.read(buffer)) != -1) {
                        total += l;
                        contentLen += l;
                    }
                }
                if (c.getResponseCode() == 200) {
                    successCount++;
                } else {
                    failureCount++;
                }
                totalContentLen += contentLen;
            } catch (IOException ex) {
                failureCount++;
            }
            String s = c.getHeaderField("Server");
            if (s != null) {
                serverName = s;                
            }
        }
        finishTime = System.currentTimeMillis();
       
        float totalTimeSec = (float) (finishTime - startTime) / 1000;
        float reqsPerSec = (float) successCount / totalTimeSec;
        float timePerReqMs = (float) (finishTime - startTime) / (float) successCount;
       
        System.out.print("Server Software:\t");
        System.out.println(serverName);
        System.out.println();
        System.out.print("Document URI:\t\t");
        System.out.println(targetURI);
        System.out.print("Document Length:\t");
        System.out.print(contentLen);
        System.out.println(" bytes");
        System.out.println();
        System.out.print("Time taken for tests:\t");
        System.out.print(totalTimeSec);
        System.out.println(" seconds");
        System.out.print("Complete requests:\t");
        System.out.println(successCount);
        System.out.print("Failed requests:\t");
        System.out.println(failureCount);
        System.out.print("Content transferred:\t");
        System.out.print(total);
        System.out.println(" bytes");
        System.out.print("Requests per second:\t");
        System.out.print(reqsPerSec);
        System.out.println(" [#/sec] (mean)");
        System.out.print("Time per request:\t");
        System.out.print(timePerReqMs);
        System.out.println(" [ms] (mean)");
    }
 
} 