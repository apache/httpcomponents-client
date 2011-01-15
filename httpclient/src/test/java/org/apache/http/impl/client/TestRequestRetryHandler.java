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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestRetryHandler {

    @Test(expected=UnknownHostException.class)
    public void testUseRetryHandlerInConnectionTimeOutWithThreadSafeClientConnManager()
            throws Exception {

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, new OppsieSchemeSocketFactory()));
        ClientConnectionManager connManager = new ThreadSafeClientConnManager(schemeRegistry);

        assertOnRetry(connManager);
    }

    @Test(expected=UnknownHostException.class)
    public void testUseRetryHandlerInConnectionTimeOutWithSingleClientConnManager()
            throws Exception {

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, new OppsieSchemeSocketFactory()));
        ClientConnectionManager connManager = new SingleClientConnManager(schemeRegistry);
        assertOnRetry(connManager);
    }

    protected void assertOnRetry(ClientConnectionManager connManager) throws Exception {
        DefaultHttpClient client = new DefaultHttpClient(connManager);
        TestHttpRequestRetryHandler testRetryHandler = new TestHttpRequestRetryHandler();
        client.setHttpRequestRetryHandler(testRetryHandler);

        HttpRequestBase request = new HttpGet("http://www.example.com/");

        HttpConnectionParams.setConnectionTimeout(request.getParams(), 1);
        try {
            client.execute(request);
        } catch (UnknownHostException ex) {
            Assert.assertEquals(2, testRetryHandler.retryNumber);
            throw ex;
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

    static class OppsieSchemeSocketFactory implements SchemeSocketFactory {

        public boolean isSecure(final Socket sock) throws IllegalArgumentException {
            return false;
        }

        public Socket createSocket(final HttpParams params) throws IOException {
            return new Socket();
        }

        public Socket connectSocket(
                final Socket sock,
                final InetSocketAddress remoteAddress,
                final InetSocketAddress localAddress,
                final HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
            throw new UnknownHostException("Ooopsie");
        }
    }

}
