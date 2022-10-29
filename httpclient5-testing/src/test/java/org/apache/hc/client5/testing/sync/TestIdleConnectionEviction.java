/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.client5.testing.sync;

import java.net.URI;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.IdleConnectionEvictor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestIdleConnectionEviction {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

    @Test
    public void testIdleConnectionEviction() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandler("/random/*", new RandomHandler());
        final HttpHost target = testResources.targetHost();

        final CloseableHttpClient client = testResources.startClient(b -> {});

        final PoolingHttpClientConnectionManager connManager = testResources.connManager();

        connManager.setDefaultMaxPerRoute(10);
        connManager.setMaxTotal(50);

        final IdleConnectionEvictor idleConnectionMonitor = new IdleConnectionEvictor(connManager, TimeValue.ofMilliseconds(50));
        idleConnectionMonitor.start();

        final URI requestUri = new URI("/random/1024");
        final WorkerThread[] workers = new WorkerThread[5];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(client, target, requestUri, 200);
        }
        for (final WorkerThread worker : workers) {
            worker.start();
        }
        for (final WorkerThread worker : workers) {
            worker.join();
            final Exception ex = worker.getException();
            if (ex != null) {
                throw ex;
            }
        }
        idleConnectionMonitor.shutdown();
    }

    static class WorkerThread extends Thread {

        private final CloseableHttpClient httpclient;
        private final HttpHost target;
        private final URI requestUri;
        private final int count;

        private volatile Exception ex;

        public WorkerThread(
                final CloseableHttpClient httpclient,
                final HttpHost target,
                final URI requestUri,
                final int count) {
            super();
            this.httpclient = httpclient;
            this.target = target;
            this.requestUri = requestUri;
            this.count = count;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < this.count; i++) {
                    final HttpGet httpget = new HttpGet(this.requestUri);
                    this.httpclient.execute(this.target, httpget, response -> {
                        final int status = response.getCode();
                        if (status != 200) {
                            throw new ClientProtocolException("Unexpected status code: " + status);
                        }
                        EntityUtils.consume(response.getEntity());
                        try {
                            Thread.sleep(10);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });
                }
            } catch (final Exception ex) {
                this.ex = ex;
            }
        }

        public Exception getException() {
            return ex;
        }

    }

}
