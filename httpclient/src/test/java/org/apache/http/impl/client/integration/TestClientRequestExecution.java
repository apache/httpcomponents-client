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
 */

package org.apache.http.impl.client.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Client protocol handling tests.
 */
public class TestClientRequestExecution extends IntegrationTestBase {

    @Before
    public void setUp() throws Exception {
        startServer();
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
    public void testDefaultHostAtClientLevel() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpHost target = new HttpHost("localhost", port);

        this.httpclient = HttpClients.custom().build();
        this.httpclient.getParams().setParameter(ClientPNames.DEFAULT_HOST, target);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = this.httpclient.execute(httpget);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testDefaultHostHeader() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        String hostname = getServerHttp().getHostName();
        this.localServer.register("*", new SimpleService());

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        this.httpclient = HttpClients.custom().build();
        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        // Check that Host header is generated as expected
        Header[] headers = reqWrapper.getHeaders("host");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(hostname + ":" + port, headers[0].getValue());
    }

    @Test
    // HTTPCLIENT-1092
    public void testVirtualHostHeader() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        this.httpclient = HttpClients.custom().build();
        String virtHost = "virtual";
        httpget.getParams().setParameter(ClientPNames.VIRTUAL_HOST, new HttpHost(virtHost, port));
        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        // Check that Host header is generated as expected
        Header[] headers = reqWrapper.getHeaders("host");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(virtHost+":"+port,headers[0].getValue());
    }

    @Test
    // Test that virtual port is propagated if provided
    // This is not expected to be used much, if ever
    // HTTPCLIENT-1092
    public void testVirtualHostPortHeader() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        this.httpclient = HttpClients.custom().build();
        String virtHost = "virtual";
        int virtPort = 9876;
        httpget.getParams().setParameter(ClientPNames.VIRTUAL_HOST, new HttpHost(virtHost, virtPort));
        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        // Check that Host header is generated as expected
        Header[] headers = reqWrapper.getHeaders("host");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(virtHost+":"+virtPort,headers[0].getValue());
    }

    @Test
    public void testClientLevelVirtualHostHeader() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        this.httpclient = HttpClients.custom().build();
        String virtHost = "virtual";
        this.httpclient.getParams().setParameter(ClientPNames.VIRTUAL_HOST, new HttpHost(virtHost, port));
        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        // Check that Host header is generated as expected
        Header[] headers = reqWrapper.getHeaders("host");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(virtHost+":"+port,headers[0].getValue());
    }

    @Test
    public void testDefaultHostAtRequestLevel() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpHost target1 = new HttpHost("whatever", 80);
        HttpHost target2 = new HttpHost("localhost", port);

        this.httpclient = HttpClients.custom().build();
        this.httpclient.getParams().setParameter(ClientPNames.DEFAULT_HOST, target1);

        String s = "/path";
        HttpGet httpget = new HttpGet(s);
        httpget.getParams().setParameter(ClientPNames.DEFAULT_HOST, target2);

        HttpResponse response = this.httpclient.execute(httpget);
        EntityUtils.consume(response.getEntity());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    private static class FaultyHttpRequestExecutor extends HttpRequestExecutor {

        private static final String MARKER = "marker";

        private final String failureMsg;

        public FaultyHttpRequestExecutor(String failureMsg) {
            this.failureMsg = failureMsg;
        }

        @Override
        public HttpResponse execute(
                final HttpRequest request,
                final HttpClientConnection conn,
                final HttpContext context) throws IOException, HttpException {

            HttpResponse response = super.execute(request, conn, context);
            Object marker = context.getAttribute(MARKER);
            if (marker == null) {
                context.setAttribute(MARKER, Boolean.TRUE);
                throw new IOException(failureMsg);
            }
            return response;
        }

    }

    @Test
    public void testAutoGeneratedHeaders() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpRequestInterceptor interceptor = new HttpRequestInterceptor() {

            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                request.addHeader("my-header", "stuff");
            }

        };

        HttpRequestRetryHandler requestRetryHandler = new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception,
                    int executionCount,
                    final HttpContext context) {
                return true;
            }

        };

        this.httpclient = HttpClients.custom()
            .addInterceptorFirst(interceptor)
            .setRequestExecutor(new FaultyHttpRequestExecutor("Oppsie"))
            .setRetryHandler(requestRetryHandler)
            .build();

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Header[] myheaders = reqWrapper.getHeaders("my-header");
        Assert.assertNotNull(myheaders);
        Assert.assertEquals(1, myheaders.length);
    }

    @Test(expected=ClientProtocolException.class)
    public void testNonRepeatableEntity() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpRequestRetryHandler requestRetryHandler = new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception,
                    int executionCount,
                    final HttpContext context) {
                return true;
            }

        };

        this.httpclient = HttpClients.custom()
            .setRequestExecutor(new FaultyHttpRequestExecutor("a message showing that this failed"))
            .setRetryHandler(requestRetryHandler)
            .build();

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpPost httppost = new HttpPost(s);
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));

        try {
            this.httpclient.execute(getServerHttp(), httppost, context);
        } catch (ClientProtocolException ex) {
            Assert.assertTrue(ex.getCause() instanceof NonRepeatableRequestException);
            NonRepeatableRequestException nonRepeat = (NonRepeatableRequestException)ex.getCause();
            Assert.assertTrue(nonRepeat.getCause() instanceof IOException);
            Assert.assertEquals("a message showing that this failed", nonRepeat.getCause().getMessage());
            throw ex;
        }
    }

}
