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
        this.client.setConnectorType(HttpClient.CONNECTOR_SOCKET);
        this.client.setRequestBufferSize(8 * 1024);
        this.client.setResponseBufferSize(8 * 1024);
    }

    public void init() throws Exception {
        this.client.start();
    }

    public Stats execute(final URI targetURI, byte[] content, int n) throws Exception {

        Stats stats = new Stats();

        int successCount = 0;
        int failureCount = 0;
        long totalContentLen = 0;

        for (int i = 0; i < n; i++) {
            SimpleHttpExchange exchange = new SimpleHttpExchange();
            exchange.setURL(targetURI.toASCIIString());

            if (content != null) {
                exchange.setMethod("POST");
                exchange.setRequestContent(new ByteArrayBuffer(content));
            }

            try {
                this.client.send(exchange);
                exchange.waitForDone();
                if (exchange.status == 200) {
                    successCount++;
                } else {
                    failureCount++;
                }
                stats.setContentLen(exchange.contentLen);
                totalContentLen += exchange.contentLen;
            } catch (IOException ex) {
                failureCount++;
            }
            if (exchange.server != null) {
                stats.setServerName(exchange.server);
            }
        }
        stats.setSuccessCount(successCount);
        stats.setFailureCount(failureCount);
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
        return "Jetty " + Server.getVersion();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);

        TestJettyHttpClient test = new TestJettyHttpClient();

        long startTime = System.currentTimeMillis();
        Stats stats = test.get(targetURI, n);
        long finishTime = System.currentTimeMillis();

        Stats.printStats(targetURI, startTime, finishTime, stats);
    }

    static class SimpleHttpExchange extends HttpExchange {

        private final Buffer serverHeader = new ByteArrayBuffer("Server");

        long contentLen = 0;
        int status = 0;
        String server;

        protected void onResponseStatus(
                final Buffer version, int status, final Buffer reason) throws IOException {
            this.status = status;
            super.onResponseStatus(version, status, reason);
        }

        @Override
        protected void onResponseHeader(final Buffer name, final Buffer value) throws IOException {
            super.onResponseHeader(name, value);
            if (name.equalsIgnoreCase(this.serverHeader)) {
                this.server = value.toString("ASCII");
            }
        }

        @Override
        protected void onResponseContent(final Buffer content) throws IOException {
            this.contentLen += content.length();
            super.onResponseContent(content);
        }

    };

}