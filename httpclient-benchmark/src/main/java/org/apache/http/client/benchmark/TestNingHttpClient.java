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
import java.net.URI;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Request;

public class TestNingHttpClient implements TestHttpAgent {

    private AsyncHttpClient client;

    public TestNingHttpClient() {
        super();
    }

    public void init() throws Exception {
    }

    public void shutdown() throws Exception {
        this.client.close();
    }

    Stats execute(final URI targetURI, byte[] content, int n, int c) throws Exception {
        if (this.client != null) {
            this.client.close();
        }
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
            .setAllowPoolingConnection(true)
            .setCompressionEnabled(false)
            .setMaximumConnectionsPerHost(c)
            .setMaximumConnectionsTotal(2000)
            .setRequestTimeoutInMs(15000)
            .build();
        this.client = new AsyncHttpClient(config);

        Stats stats = new Stats(n, c);

        for (int i = 0; i < n; i++) {
            Request request;
            if (content == null) {
                request = this.client.prepareGet(targetURI.toASCIIString())
                    .build();
            } else {
                request = this.client.preparePost(targetURI.toASCIIString())
                    .setBody(content)
                    .build();
            }
            try {
                this.client.executeRequest(request, new SimpleAsyncHandler(stats));
            } catch (IOException ex) {
            }
        }
        stats.waitFor();
        return stats;
    }

    public Stats get(final URI target, int n, int c) throws Exception {
        return execute(target, null, n, c);
    }

    public Stats post(final URI target, byte[] content, int n, int c) throws Exception {
        return execute(target, content, n, c);
    }

    public String getClientName() {
        return "Ning Async HTTP client 1.4.0";
    }

    static class SimpleAsyncHandler implements AsyncHandler<Object> {

        private final Stats stats;
        private int status = 0;
        private long contentLen = 0;

        SimpleAsyncHandler(final Stats stats) {
            super();
            this.stats = stats;
        }

        public STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
            this.status = responseStatus.getStatusCode();
            return STATE.CONTINUE;
        }

        public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
            return STATE.CONTINUE;
        }

        public STATE onBodyPartReceived(final HttpResponseBodyPart bodyPart) throws Exception {
            this.contentLen += bodyPart.getBodyPartBytes().length;
            return STATE.CONTINUE;
        }

        public Object onCompleted() throws Exception {
            if (this.status == 200) {
                this.stats.success(this.contentLen);
            } else {
                this.stats.failure(this.contentLen);
            }
            return STATE.CONTINUE;
        }

        public void onThrowable(final Throwable t) {
            this.stats.failure(this.contentLen);
        }

    };

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

        TestNingHttpClient test = new TestNingHttpClient();
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