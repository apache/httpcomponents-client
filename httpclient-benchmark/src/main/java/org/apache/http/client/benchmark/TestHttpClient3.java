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
import java.net.URI;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

public class TestHttpClient3 implements TestHttpAgent {

    private final MultiThreadedHttpConnectionManager mgr;
    private final HttpClient httpclient;

    public TestHttpClient3() {
        super();
        this.mgr = new MultiThreadedHttpConnectionManager();
        this.httpclient = new HttpClient(this.mgr);
        this.httpclient.getParams().setVersion(
                HttpVersion.HTTP_1_1);
        this.httpclient.getParams().setBooleanParameter(
                HttpMethodParams.USE_EXPECT_CONTINUE, false);
        this.httpclient.getHttpConnectionManager().getParams()
                .setStaleCheckingEnabled(false);
        this.httpclient.getParams().setSoTimeout(15000);

        HttpMethodRetryHandler retryhandler = new HttpMethodRetryHandler() {

            public boolean retryMethod(final HttpMethod httpmethod, final IOException ex, int count) {
                return false;
            }

        };
        this.httpclient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, retryhandler);    }

    public void init() {
    }

    public void shutdown() {
        this.mgr.shutdown();
    }

    Stats execute(final URI target, final byte[] content, int n, int c) throws Exception {
        this.mgr.getParams().setMaxTotalConnections(2000);
        this.mgr.getParams().setDefaultMaxConnectionsPerHost(c);
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
        private final URI target;
        private final byte[] content;

        WorkerThread(final Stats stats, final URI target, final byte[] content) {
            super();
            this.stats = stats;
            this.target = target;
            this.content = content;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            while (!this.stats.isComplete()) {
                HttpMethod httpmethod;
                if (this.content == null) {
                    GetMethod httpget = new GetMethod(target.toASCIIString());
                    httpmethod = httpget;
                } else {
                    PostMethod httppost = new PostMethod(target.toASCIIString());
                    httppost.setRequestEntity(new ByteArrayRequestEntity(content));
                    httpmethod = httppost;
                }
                long contentLen = 0;
                try {
                    httpclient.executeMethod(httpmethod);
                    InputStream instream = httpmethod.getResponseBodyAsStream();
                    if (instream != null) {
                        int l = 0;
                        while ((l = instream.read(buffer)) != -1) {
                            contentLen += l;
                        }
                    }
                    this.stats.success(contentLen);
                } catch (IOException ex) {
                    this.stats.failure(contentLen);
                } finally {
                    httpmethod.releaseConnection();
                }
            }
        }

    }

    public Stats get(final URI target, int n, int c) throws Exception {
        return execute(target, null, n, c);
    }

    public Stats post(URI target, byte[] content, int n, int c) throws Exception {
        return execute(target, content, n, c);
    }

    public String getClientName() {
        return "Apache HttpClient 3.1";
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

        TestHttpClient3 test = new TestHttpClient3();
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
