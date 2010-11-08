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

    public void shutdown() {
    }

    Stats execute(final URI targetURI, byte[] content, int n, int c) throws Exception {
        System.setProperty("http.maxConnections", Integer.toString(c));
        URL target = targetURI.toURL();
        Stats stats = new Stats(n, c);
        WorkerThread[] workers = new WorkerThread[c];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(stats, target, content);
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
        }
        return stats;
    }

    class WorkerThread extends Thread {

        private final Stats stats;
        private final URL target;
        private final byte[] content;

        WorkerThread(final Stats stats, final URL target, final byte[] content) {
            super();
            this.stats = stats;
            this.target = target;
            this.content = content;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            while (!this.stats.isComplete()) {
                long contentLen = 0;
                try {
                    HttpURLConnection conn = (HttpURLConnection) this.target.openConnection();
                    conn.setReadTimeout(15000);

                    if (this.content != null) {
                        conn.setRequestMethod("POST");
                        conn.setFixedLengthStreamingMode(this.content.length);
                        conn.setUseCaches (false);
                        conn.setDoInput(true);
                        conn.setDoOutput(true);
                        OutputStream out = conn.getOutputStream();
                        try {
                            out.write(this.content);
                            out.flush ();
                        } finally {
                            out.close();
                        }
                    }
                    InputStream instream = conn.getInputStream();
                    if (instream != null) {
                        try {
                            int l = 0;
                            while ((l = instream.read(buffer)) != -1) {
                                contentLen += l;
                            }
                        } finally {
                            instream.close();
                        }
                    }
                    if (conn.getResponseCode() == 200) {
                        this.stats.success(contentLen);
                    } else {
                        this.stats.failure(contentLen);
                    }
                } catch (IOException ex) {
                    this.stats.failure(contentLen);
                }
            }
        }
    }

    public Stats get(final URI target, int n, int c) throws Exception {
        return execute(target, null, n, c);
    }

    public Stats post(final URI target, byte[] content, int n, int c) throws Exception {
        return execute(target, content, n, c);
    }

    public String getClientName() {
        return "JRE HTTP " + System.getProperty("java.version");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests> <concurrent connections>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);
        int c = 1;
        if (args.length > 2) {
            c = Integer.parseInt(args[2]);
        }

        TestHttpJRE test = new TestHttpJRE();
        test.init();
        try {
            long startTime = System.currentTimeMillis();
            Stats stats = test.get(targetURI, n, c);
            long finishTime = System.currentTimeMillis();

            Stats.printStats(targetURI, startTime, finishTime, stats);
        } finally {
            test.shutdown();
        }
    }

}