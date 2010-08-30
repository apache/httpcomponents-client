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
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *  Simple tests for {@link RequestWrapper}.
 */
public class TestRequestWrapper extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        localServer = new LocalTestServer(null, null);
        localServer.registerDefaultHandlers();
        localServer.start();
    }

    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_OK);
            StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    @Test
    public void testRequestURIRewriting() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        DefaultHttpClient client = new DefaultHttpClient();
        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port + "/path";
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = client.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Assert.assertTrue(reqWrapper instanceof RequestWrapper);
        Assert.assertEquals("/path", reqWrapper.getRequestLine().getUri());
    }

    @Test
    public void testRequestURIRewritingEmptyPath() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        DefaultHttpClient client = new DefaultHttpClient();
        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = client.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Assert.assertTrue(reqWrapper instanceof RequestWrapper);
        Assert.assertEquals("/", reqWrapper.getRequestLine().getUri());
    }

}
