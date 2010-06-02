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

package org.apache.http.impl.conn;

import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class TestIdleConnectionEviction extends ServerTestBase {

    public TestIdleConnectionEviction(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestIdleConnectionEviction.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestIdleConnectionEviction.class);
    }

    @Override
    protected void setUp() throws Exception {
        this.localServer = new LocalTestServer(null, null);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
    }
    
    public void testIdleConnectionEviction() throws Exception {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        ConnManagerParams.setMaxTotalConnections(params, 50);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(10));
        
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        DefaultHttpClient httpclient = new DefaultHttpClient(cm, params);
        
        IdleConnectionMonitor idleConnectionMonitor = new IdleConnectionMonitor(cm);          
        idleConnectionMonitor.start();
        
        HttpHost target = new HttpHost(this.localServer.getServiceHostName(), 
                this.localServer.getServicePort());
        HttpGet httpget = new HttpGet("/random/1024"); 
        WorkerThread[] workers = new WorkerThread[5];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(httpclient, target, httpget, 2000);
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
            Exception ex = workers[i].getException();
            if (ex != null) {
                throw ex;
            }
        }
        idleConnectionMonitor.shutdown();
    }
    
    static class WorkerThread extends Thread {

        private final HttpClient httpclient;
        private final HttpHost target;
        private final HttpUriRequest request;
        private final int count;
        
        private volatile Exception ex;
        
        public WorkerThread(
                final HttpClient httpclient, 
                final HttpHost target, 
                final HttpUriRequest request, 
                int count) {
            super();
            this.httpclient = httpclient;
            this.target = target;
            this.request = request;
            this.count = count;
        }
        
        @Override
        public void run() {
            try {
                for (int i = 0; i < this.count; i++) {
                    HttpResponse response = this.httpclient.execute(this.target, this.request);
                    int status = response.getStatusLine().getStatusCode();
                    if (status != 200) {
                        this.request.abort();
                        throw new ClientProtocolException("Unexpected status code: " + status);
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        entity.consumeContent();
                    }
                }
            } catch (Exception ex) {
                this.ex = ex;
            }
        }

        public Exception getException() {
            return ex;
        }
        
    }

    public static class IdleConnectionMonitor extends Thread {
        
        private final ClientConnectionManager cm;
        private volatile boolean shutdown;
        
        public IdleConnectionMonitor(final ClientConnectionManager cm) {
            super();
            this.cm = cm;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (!this.shutdown) {
                    synchronized (this) {
                        wait(250);
                        this.cm.closeIdleConnections(1, TimeUnit.MILLISECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                // terminate
            }
        }
        
        public void shutdown() {
            this.shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
        
    }
    
}
