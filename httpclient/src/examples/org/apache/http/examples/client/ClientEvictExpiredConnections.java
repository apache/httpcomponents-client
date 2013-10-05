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
package org.apache.http.examples.client;

import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

/**
 * Example demonstrating how to evict expired and idle connections
 * from the connection pool.
 */
public class ClientEvictExpiredConnections {

    public static void main(String[] args) throws Exception {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();
        try {
            // create an array of URIs to perform GETs on
            String[] urisToGet = {
                "http://hc.apache.org/",
                "http://hc.apache.org/httpcomponents-core-ga/",
                "http://hc.apache.org/httpcomponents-client-ga/",
            };

            IdleConnectionEvictor connEvictor = new IdleConnectionEvictor(cm);
            connEvictor.start();

            for (int i = 0; i < urisToGet.length; i++) {
                String requestURI = urisToGet[i];
                HttpGet request = new HttpGet(requestURI);

                System.out.println("Executing request " + requestURI);

                CloseableHttpResponse response = httpclient.execute(request);
                try {
                    System.out.println("----------------------------------------");
                    System.out.println(response.getStatusLine());
                    EntityUtils.consume(response.getEntity());
                } finally {
                    response.close();
                }
            }

            // Sleep 10 sec and let the connection evictor do its job
            Thread.sleep(20000);

            // Shut down the evictor thread
            connEvictor.shutdown();
            connEvictor.join();

        } finally {
            httpclient.close();
        }
    }

    public static class IdleConnectionEvictor extends Thread {

        private final HttpClientConnectionManager connMgr;

        private volatile boolean shutdown;

        public IdleConnectionEvictor(HttpClientConnectionManager connMgr) {
            super();
            this.connMgr = connMgr;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);
                        // Close expired connections
                        connMgr.closeExpiredConnections();
                        // Optionally, close connections
                        // that have been idle longer than 5 sec
                        connMgr.closeIdleConnections(5, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                // terminate
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }

    }

}
