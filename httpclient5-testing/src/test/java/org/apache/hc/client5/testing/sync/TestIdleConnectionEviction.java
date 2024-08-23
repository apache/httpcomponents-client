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
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

class TestIdleConnectionEviction extends AbstractIntegrationTestBase {

    public TestIdleConnectionEviction() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD);
    }

    @Test
    void testIdleConnectionEviction() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        final TestClient client = client();

        final PoolingHttpClientConnectionManager connManager = client.getConnectionManager();

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
