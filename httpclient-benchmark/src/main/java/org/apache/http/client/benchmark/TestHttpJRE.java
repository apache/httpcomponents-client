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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class TestHttpJRE implements TestHttpAgent {

    public TestHttpJRE() {
        super();
    }

    public void init() {
    }

    public Stats execute(final URI targetURI, byte[] content, int n) throws Exception {

        Stats stats = new Stats();

        int successCount = 0;
        int failureCount = 0;
        long contentLen = 0;
        long totalContentLen = 0;

        byte[] buffer = new byte[4096];

        URL url = targetURI.toURL();
        for (int i = 0; i < n; i++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();

            if (content != null) {
                c.setRequestMethod("POST");
                c.setFixedLengthStreamingMode(content.length);
                c.setUseCaches (false);
                c.setDoInput(true);
                c.setDoOutput(true);
                OutputStream out = c.getOutputStream();
                out.write(content);
                out.flush ();
                out.close();
            }
            InputStream instream = c.getInputStream();
            try {
                contentLen = 0;
                if (instream != null) {
                    int l = 0;
                    while ((l = instream.read(buffer)) != -1) {
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
                stats.setServerName(s);
            }
        }
        stats.setSuccessCount(successCount);
        stats.setFailureCount(failureCount);
        stats.setContentLen(contentLen);
        stats.setTotalContentLen(totalContentLen);
        return stats;
    }

    public Stats get(final URI target, int n) throws Exception {
        return execute(target, null, n);
    }

    public Stats post(final URI target, byte[] content, int n) throws Exception {
        return execute(target, content, n);
    }

    public String getClientName() {
        return "JRE HTTP " + System.getProperty("java.version");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);

        TestHttpJRE test = new TestHttpJRE();

        long startTime = System.currentTimeMillis();
        Stats stats = test.get(targetURI, n);
        long finishTime = System.currentTimeMillis();

        Stats.printStats(targetURI, startTime, finishTime, stats);
    }

}