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
import java.util.concurrent.CountDownLatch;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *  Tests for Abort handling.
 */
public class TestAbortHandling extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        this.localServer = new LocalTestServer(null, null);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        this.httpclient = new DefaultHttpClient();
    }

    @Test
    public void testAbortRetry_HTTPCLIENT_1120() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        final CountDownLatch wait = new CountDownLatch(1);
        
        this.localServer.register("*", new HttpRequestHandler(){
            public void handle(HttpRequest request, HttpResponse response,
                    HttpContext context) throws HttpException, IOException {
                try {
                    wait.countDown(); // trigger abort
                    Thread.sleep(2000); // allow time for abort to happen
                    response.setStatusCode(HttpStatus.SC_OK);
                    StringEntity entity = new StringEntity("Whatever");
                    response.setEntity(entity);
                } catch (Exception e) {
                    response.setStatusCode(HttpStatus.SC_REQUEST_TIMEOUT);
                }
            }});

        String s = "http://localhost:" + port + "/path";
        final HttpGet httpget = new HttpGet(s);

        Thread t = new Thread() {
             @Override
            public void run(){
                 try {
                    wait.await();
                } catch (InterruptedException e) {
                }
                 httpget.abort();
             }
        };

        t.start();
        
        HttpContext context = new BasicHttpContext();
        try {
            this.httpclient.execute(getServerHttp(), httpget, context);
        } catch (IOException e) {
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
        Assert.assertNotNull("Request should exist",reqWrapper);
        Assert.assertEquals(1,((RequestWrapper) reqWrapper).getExecCount());
    }

    // TODO add similar test for connection abort
}
