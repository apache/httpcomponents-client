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
package org.apache.http.impl.client;

import java.io.IOException;
import java.net.UnknownHostException;

import junit.framework.TestCase;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;

public class TestRequestRetryHandler extends TestCase {

    public void testUseRetryHandlerInConnectionTimeOutWithThreadSafeClientConnManager()
            throws Exception {

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        ClientConnectionManager connManager = new ThreadSafeClientConnManager(schemeRegistry);

        assertOnRetry(connManager);
    }

    public void testUseRetryHandlerInConnectionTimeOutWithSingleClientConnManager()
            throws Exception {

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        ClientConnectionManager connManager = new SingleClientConnManager(schemeRegistry);
        assertOnRetry(connManager);
    }

    protected void assertOnRetry(ClientConnectionManager connManager) throws Exception {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

        DefaultHttpClient client = new DefaultHttpClient(connManager);
        TestHttpRequestRetryHandler testRetryHandler = new TestHttpRequestRetryHandler();
        client.setHttpRequestRetryHandler(testRetryHandler);

        HttpRequestBase request = new HttpGet("http://www.complete.garbage");

        HttpConnectionParams.setConnectionTimeout(request.getParams(), 1);
        try {
            client.execute(request);
        } catch (UnknownHostException ex) {
            assertEquals(2, testRetryHandler.retryNumber);
        }
    }

    static class TestHttpRequestRetryHandler implements HttpRequestRetryHandler {

        int retryNumber = 0;

        public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
            retryNumber++;
            if (executionCount < 2) {
                return true;
            }
            return false;
        }
        
    }
    
}
