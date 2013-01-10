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

package org.apache.http.impl.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

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
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.apache.http.impl.client.DefaultHttpClient;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultRequestDirector}
 */
public class TestDefaultClientRequestDirector extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        this.localServer = new LocalTestServer(null, null);
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        this.httpclient = new DefaultHttpClient();
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
        this.localServer.register("*", new SimpleService());

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = this.httpclient.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        // Check that Host header is generated as expected
        Header[] headers = reqWrapper.getHeaders("host");
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals("localhost:" + port, headers[0].getValue());
    }

    @Test
    // HTTPCLIENT-1092
    public void testVirtualHostHeader() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

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

    private static class FaultyHttpClient extends DefaultHttpClient {

        private final String failureMsg;

        public FaultyHttpClient() {
            this("Oppsie");
        }

        public FaultyHttpClient(String failureMsg) {
            this.failureMsg = failureMsg;
        }

        @Override
        protected HttpRequestExecutor createRequestExecutor() {
            return new FaultyHttpRequestExecutor(failureMsg);
        }

    }

    @Test
    public void testAutoGeneratedHeaders() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        FaultyHttpClient client = new FaultyHttpClient();

        client.addRequestInterceptor(new HttpRequestInterceptor() {

            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                request.addHeader("my-header", "stuff");
            }

        }) ;

        client.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception,
                    int executionCount,
                    final HttpContext context) {
                return true;
            }

        });

        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpGet httpget = new HttpGet(s);

        HttpResponse response = client.execute(getServerHttp(), httpget, context);
        EntityUtils.consume(response.getEntity());

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Assert.assertTrue(reqWrapper instanceof RequestWrapper);
        Header[] myheaders = reqWrapper.getHeaders("my-header");
        Assert.assertNotNull(myheaders);
        Assert.assertEquals(1, myheaders.length);
    }

    @Test(expected=ClientProtocolException.class)
    public void testNonRepeatableEntity() throws Exception {
        int port = this.localServer.getServiceAddress().getPort();
        this.localServer.register("*", new SimpleService());

        String failureMsg = "a message showing that this failed";

        FaultyHttpClient client = new FaultyHttpClient(failureMsg);
        client.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {

            public boolean retryRequest(
                    final IOException exception,
                    int executionCount,
                    final HttpContext context) {
                return true;
            }

        });
        HttpContext context = new BasicHttpContext();

        String s = "http://localhost:" + port;
        HttpPost httppost = new HttpPost(s);
        httppost.setEntity(new InputStreamEntity(
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 } ),
                        -1));

        try {
            client.execute(getServerHttp(), httppost, context);
        } catch (ClientProtocolException ex) {
            Assert.assertTrue(ex.getCause() instanceof NonRepeatableRequestException);
            NonRepeatableRequestException nonRepeat = (NonRepeatableRequestException)ex.getCause();
            Assert.assertTrue(nonRepeat.getCause() instanceof IOException);
            Assert.assertEquals(failureMsg, nonRepeat.getCause().getMessage());
            throw ex;
        }
    }

    @Test
    public void testDefaultPortVirtualHost() throws Exception {
        this.localServer.register("*", new SimpleService());
        this.httpclient = new DefaultHttpClient();
        HttpHost target = getServerHttp();
        HttpHost hostHost = new HttpHost(target.getHostName(),-1,target.getSchemeName());

        httpclient.getParams().setParameter(ClientPNames.DEFAULT_HOST,target);
        httpclient.getParams().setParameter(ClientPNames.VIRTUAL_HOST,hostHost);

        HttpGet httpget = new HttpGet("/stuff");
        HttpContext context = new BasicHttpContext();

        HttpResponse response = this.httpclient.execute(null, httpget, context);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        EntityUtils.consume(response.getEntity());
    }

    @Test
    public void testRelativeRequestURIWithFragment() throws Exception {
        this.localServer.register("*", new SimpleService());
        HttpHost target = getServerHttp();

        HttpGet httpget = new HttpGet("/stuff#blahblah");
        HttpContext context = new BasicHttpContext();

        HttpResponse response = this.httpclient.execute(target, httpget, context);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        EntityUtils.consume(response.getEntity());

        HttpRequest request = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
        Assert.assertEquals("/stuff", request.getRequestLine().getUri());
    }

    @Test
    public void testAbsoluteRequestURIWithFragment() throws Exception {
        this.localServer.register("*", new SimpleService());
        HttpHost target = getServerHttp();

        URI uri = new URIBuilder()
            .setHost(target.getHostName())
            .setPort(target.getPort())
            .setScheme(target.getSchemeName())
            .setPath("/stuff")
            .setFragment("blahblah")
            .build();

        HttpGet httpget = new HttpGet(uri);
        HttpContext context = new BasicHttpContext();

        HttpResponse response = this.httpclient.execute(httpget, context);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        EntityUtils.consume(response.getEntity());

        HttpRequest request = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
        Assert.assertEquals("/stuff", request.getRequestLine().getUri());
    }

}
