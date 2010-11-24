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

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Server;

public class TestJettyHttpClient implements TestHttpAgent {

    private final HttpClient client;

    public TestJettyHttpClient() {
        super();
        this.client = new HttpClient();
        this.client.setRequestBufferSize(8 * 1024);
        this.client.setResponseBufferSize(8 * 1024);
        this.client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        this.client.setTimeout(15000);
    }

    public void init() throws Exception {
        this.client.start();
    }

    public void shutdown() throws Exception {
        this.client.stop();
    }

    Stats execute(final URI targetURI, byte[] content, int n, int c) throws Exception {
        this.client.setMaxConnectionsPerAddress(c);
        Stats stats = new Stats(n, c);

        for (int i = 0; i < n; i++) {
            SimpleHttpExchange exchange = new SimpleHttpExchange(stats);
            exchange.setURL(targetURI.toASCIIString());

            if (content != null) {
                exchange.setMethod("POST");
                exchange.setRequestContent(new ByteArrayBuffer(content));
            }
            try {
                this.client.send(exchange);
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
        return "Jetty " + Server.getVersion();
    }

    static class SimpleHttpExchange extends HttpExchange {

        private final Stats stats;
        private int status = 0;
        private long contentLen = 0;

        SimpleHttpExchange(final Stats stats) {
            super();
            this.stats = stats;
        }

        protected void onResponseStatus(
                final Buffer version, int status, final Buffer reason) throws IOException {
            this.status = status;
            super.onResponseStatus(version, status, reason);
        }

        @Override
        protected void onResponseContent(final Buffer content) throws IOException {
            byte[] tmp = new byte[content.length()];
            content.get(tmp, 0, tmp.length);
            this.contentLen += tmp.length;
            super.onResponseContent(content);
        }

        @Override
        protected void onResponseComplete() throws IOException {
            if (this.status == 200) {
                this.stats.success(this.contentLen);
            } else {
                this.stats.failure(this.contentLen);
            }
            super.onResponseComplete();
        }

        @Override
        protected void onConnectionFailed(final Throwable x) {
            this.stats.failure(this.contentLen);
            super.onConnectionFailed(x);
        }

        @Override
        protected void onException(final Throwable x) {
            this.stats.failure(this.contentLen);
            super.onException(x);
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

        TestJettyHttpClient test = new TestJettyHttpClient();
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