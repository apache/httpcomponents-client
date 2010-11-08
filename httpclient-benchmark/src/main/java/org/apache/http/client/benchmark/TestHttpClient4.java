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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.VersionInfo;

public class TestHttpClient4 implements TestHttpAgent {

    private final ThreadSafeClientConnManager mgr;
    private final DefaultHttpClient httpclient;

    public TestHttpClient4() {
        super();
        HttpParams params = new SyncBasicHttpParams();
        params.setParameter(HttpProtocolParams.PROTOCOL_VERSION,
                HttpVersion.HTTP_1_1);
        params.setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE,
                false);
        params.setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK,
                false);
        params.setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE,
                8 * 1024);
        params.setIntParameter(HttpConnectionParams.SO_TIMEOUT,
                15000);
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
        schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));
        this.mgr = new ThreadSafeClientConnManager(schemeRegistry);
        this.httpclient = new DefaultHttpClient(this.mgr, params);
        this.httpclient.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception, int executionCount, final HttpContext context) {
                return false;
            }

        });
    }

    public void init() {
    }

    public void shutdown() {
        this.mgr.shutdown();
    }

    Stats execute(final URI target, final byte[] content, int n, int c) throws Exception {
        this.mgr.setMaxTotal(2000);
        this.mgr.setDefaultMaxPerRoute(c);
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
                HttpUriRequest request;
                if (this.content == null) {
                    HttpGet httpget = new HttpGet(target);
                    request = httpget;
                } else {
                    HttpPost httppost = new HttpPost(target);
                    httppost.setEntity(new ByteArrayEntity(content));
                    request = httppost;
                }
                long contentLen = 0;
                try {
                    HttpResponse response = httpclient.execute(request);
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        InputStream instream = entity.getContent();
                        try {
                            contentLen = 0;
                            if (instream != null) {
                                int l = 0;
                                while ((l = instream.read(buffer)) != -1) {
                                    contentLen += l;
                                }
                            }
                        } finally {
                            instream.close();
                        }
                    }
                    this.stats.success(contentLen);
                } catch (IOException ex) {
                    this.stats.failure(contentLen);
                    request.abort();
                }
            }
        }

    }

    public Stats get(final URI target, int n, int c) throws Exception {
        return execute(target, null, n ,c);
    }

    public Stats post(final URI target, byte[] content, int n, int c) throws Exception {
        return execute(target, content, n, c);
    }

    public String getClientName() {
        VersionInfo vinfo = VersionInfo.loadVersionInfo("org.apache.http.client",
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpClient 4 (ver: " +
            ((vinfo != null) ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
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

        TestHttpClient4 test = new TestHttpClient4();
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