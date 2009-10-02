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
 
package org.apache.http.examples.client;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * An example that performs GETs from multiple threads.
 * 
 */
public class ClientMultiThreadedExecution {

    public static void main(String[] args) throws Exception {
        // Create and initialize scheme registry 
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        
        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(schemeRegistry);
        cm.setMaxTotalConnections(100);
        
        HttpClient httpClient = new DefaultHttpClient(cm);
        
        // create an array of URIs to perform GETs on
        String[] urisToGet = {
            "http://hc.apache.org/",
            "http://hc.apache.org/httpcomponents-core/",
            "http://hc.apache.org/httpcomponents-client/",
            "http://svn.apache.org/viewvc/httpcomponents/"
        };
        
        // create a thread for each URI
        GetThread[] threads = new GetThread[urisToGet.length];
        for (int i = 0; i < threads.length; i++) {
            HttpGet httpget = new HttpGet(urisToGet[i]);
            threads[i] = new GetThread(httpClient, httpget, i + 1);
        }
        
        // start the threads
        for (int j = 0; j < threads.length; j++) {
            threads[j].start();
        }
        
        // join the threads
        for (int j = 0; j < threads.length; j++) {
            threads[j].join();
        }

        // When HttpClient instance is no longer needed, 
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpClient.getConnectionManager().shutdown();        
    }
    
    /**
     * A thread that performs a GET.
     */
    static class GetThread extends Thread {
        
        private final HttpClient httpClient;
        private final HttpContext context;
        private final HttpGet httpget;
        private final int id;
        
        public GetThread(HttpClient httpClient, HttpGet httpget, int id) {
            this.httpClient = httpClient;
            this.context = new BasicHttpContext();
            this.httpget = httpget;
            this.id = id;
        }
        
        /**
         * Executes the GetMethod and prints some status information.
         */
        @Override
        public void run() {
            
            System.out.println(id + " - about to get something from " + httpget.getURI());

            try {
                
                // execute the method
                HttpResponse response = httpClient.execute(httpget, context);
                
                System.out.println(id + " - get executed");
                // get the response body as an array of bytes
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    byte[] bytes = EntityUtils.toByteArray(entity);
                    System.out.println(id + " - " + bytes.length + " bytes read");
                }
                
            } catch (Exception e) {
                httpget.abort();
                System.out.println(id + " - error: " + e);
            }
        }
       
    }
    
}
